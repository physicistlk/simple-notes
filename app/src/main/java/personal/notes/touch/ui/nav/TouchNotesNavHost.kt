package personal.notes.touch.ui.nav

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import personal.notes.touch.TouchNotesApp
import personal.notes.touch.ui.canvas.CanvasScreen
import personal.notes.touch.ui.editor.SplitSide
import personal.notes.touch.ui.editor.SplitWorkspaceScreen
import personal.notes.touch.ui.editor.TextEditorScreen
import personal.notes.touch.ui.editor.TextEditorViewModel
import personal.notes.touch.ui.notes.NoteListScreen
import personal.notes.touch.ui.notes.NoteListViewModel
import personal.notes.touch.ui.settings.SettingsScreen
import personal.notes.touch.ui.settings.SettingsViewModel
import personal.notes.touch.ui.splash.SplashScreen

@Composable
fun TouchNotesNavHost(
    app: TouchNotesApp,
    navController: NavHostController = rememberNavController()
) {
    val openNotes = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    // Edge-to-edge (targetSdk 35+): every screen must clear the status bar, or app
    // top bars collide with the battery/time/notification icons. Insets become 0
    // automatically while the full-screen setting hides the system bars.
    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route
        ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(Screen.NoteList.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.NoteList.route) {
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<NoteListViewModel>(
                factory = NoteListViewModel.Factory(app.noteRepository)
            )
            NoteListScreen(
                viewModel = viewModel,
                onNoteClick = { noteId ->
                    navController.navigate(Screen.Canvas.createRoute(noteId))
                },
                onOpenEditorClick = { noteId ->
                    navController.navigate(Screen.Editor.createRoute(noteId))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onFlashcardsClick = {
                    navController.navigate(Screen.Flashcards.route)
                }
            )
        }

        composable(
            route = Screen.Canvas.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<personal.notes.touch.ui.canvas.CanvasViewModel>(
                factory = personal.notes.touch.ui.canvas.CanvasViewModel.Factory(
                    noteId = noteId,
                    repository = app.noteRepository,
                    fileStore = app.fileStore,
                    settingsRepository = app.settingsRepository
                ),
                key = "canvas_$noteId"
            )
            if (noteId.isNotBlank() && noteId !in openNotes) openNotes.add(noteId)
            CanvasScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onOpenEditorClick = { id ->
                    navController.navigate(Screen.Editor.createRoute(id))
                },
                openNotes = openNotes.toList(),
                onSwitchNote = { id ->
                    if (id != noteId) {
                        navController.navigate(Screen.Canvas.createRoute(id)) {
                            popUpTo(Screen.Canvas.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onCloseTab = { id ->
                    openNotes.remove(id)
                    if (id == noteId) navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Editor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId") ?: ""
            val viewModel = editorViewModel(app, noteId)
            TextEditorScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                // Wiki link / backlink tap: open that note in place. Single-top
                // keeps a repeated tap from stacking the same editor twice.
                onOpenNote = { id ->
                    navController.navigate(Screen.Editor.createRoute(id)) {
                        launchSingleTop = true
                    }
                },
                // Multi-document workspace: this note on the left, the picked
                // note on the right. A picker that returns this same note can
                // only come from a stale list, so it opens the editor instead
                // of a split with two identical panes.
                onOpenBeside = { picked ->
                    if (picked == noteId) {
                        Toast.makeText(
                            context,
                            "That note is already open",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        navController.navigate(Screen.Split.createRoute(noteId, picked))
                    }
                }
            )
        }

        composable(
            route = Screen.Split.route,
            arguments = listOf(
                navArgument("leftId") { type = NavType.StringType },
                navArgument("rightId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val leftId = backStackEntry.arguments?.getString("leftId") ?: ""
            val rightId = backStackEntry.arguments?.getString("rightId") ?: ""
            if (leftId.isBlank() || rightId.isBlank() || leftId == rightId) {
                // A split needs two different notes; anything else is a stale
                // route, so fall back to the single editor rather than showing
                // the same ViewModel twice.
                LaunchedEffect(Unit) {
                    val target = leftId.ifBlank { rightId }
                    if (target.isBlank()) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Screen.Editor.createRoute(target)) {
                            popUpTo(backStackEntry.destination.id) { inclusive = true }
                        }
                    }
                }
                return@composable
            }

            // Panes are state inside this destination, not route arguments:
            // swapping, closing or replacing a pane must not destroy the
            // destination, because the editors autosave on a 1s debounce and a
            // torn-down ViewModel would race the write.
            var leftNoteId by rememberSaveable { mutableStateOf(leftId) }
            var rightNoteId by rememberSaveable { mutableStateOf(rightId) }

            val leftViewModel = if (leftNoteId.isNotBlank()) {
                editorViewModel(app, leftNoteId)
            } else {
                null
            }
            val rightViewModel = if (rightNoteId.isNotBlank()) {
                editorViewModel(app, rightNoteId)
            } else {
                null
            }

            fun replacePane(side: SplitSide, id: String) {
                val other = if (side == SplitSide.LEFT) rightNoteId else leftNoteId
                if (id.isNotBlank() && id == other) {
                    Toast.makeText(
                        context,
                        "That note is already open in the other pane",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                when (side) {
                    SplitSide.LEFT -> leftNoteId = id
                    SplitSide.RIGHT -> rightNoteId = id
                }
            }

            SplitWorkspaceScreen(
                leftViewModel = leftViewModel,
                rightViewModel = rightViewModel,
                onExit = { navController.popBackStack() },
                onSwap = {
                    val previousLeft = leftNoteId
                    leftNoteId = rightNoteId
                    rightNoteId = previousLeft
                },
                // Closing leaves the survivor full width; the workspace stays
                // open so its editor keeps its unsaved buffer.
                onClosePane = { side ->
                    val survivor = if (side == SplitSide.LEFT) rightNoteId else leftNoteId
                    if (survivor.isBlank()) {
                        // Closing the last pane leaves nothing to show; leave
                        // the workspace instead of an empty frame.
                        navController.popBackStack()
                    } else if (side == SplitSide.LEFT) {
                        leftNoteId = ""
                    } else {
                        rightNoteId = ""
                    }
                },
                onReplacePane = { side, id -> replacePane(side, id) },
                onMaximizePane = { side ->
                    when (side) {
                        SplitSide.LEFT -> rightNoteId = ""
                        SplitSide.RIGHT -> leftNoteId = ""
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
                factory = SettingsViewModel.Factory(app.settingsRepository)
            )
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Flashcards.route) {
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<personal.notes.touch.ui.flashcards.FlashcardViewModel>(
                factory = personal.notes.touch.ui.flashcards.FlashcardViewModel.Factory(app.flashCardRepository)
            )
            personal.notes.touch.ui.flashcards.FlashcardScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // TODO: Re-enable Graph screen after fixing Canvas API
        // composable(Screen.Graph.route) { ... }
        }
    }
}

/**
 * One editor ViewModel per note, cached on the destination's store. Used by
 * both the single-editor route and each half of the split workspace, so a note
 * never gets two competing editors inside one destination.
 */
@Composable
private fun editorViewModel(
    app: TouchNotesApp,
    noteId: String
): TextEditorViewModel =
    androidx.lifecycle.viewmodel.compose.viewModel(
        factory = TextEditorViewModel.Factory(
            noteId = noteId,
            repository = app.noteRepository,
            fileStore = app.fileStore,
            settingsRepository = app.settingsRepository,
            flashCardRepository = app.flashCardRepository
        ),
        key = "editor_$noteId"
    )
