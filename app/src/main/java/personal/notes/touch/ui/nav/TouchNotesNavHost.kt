package personal.notes.touch.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import personal.notes.touch.TouchNotesApp
import personal.notes.touch.ui.canvas.CanvasScreen
import personal.notes.touch.ui.editor.TextEditorScreen
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
                    navController.navigate(Screen.Canvas.createRoute(id)) {
                        launchSingleTop = true
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
            val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<personal.notes.touch.ui.editor.TextEditorViewModel>(
                factory = personal.notes.touch.ui.editor.TextEditorViewModel.Factory(
                    noteId = noteId,
                    repository = app.noteRepository,
                    fileStore = app.fileStore,
                    settingsRepository = app.settingsRepository
                ),
                key = "editor_$noteId"
            )
            TextEditorScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
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
    }
}
