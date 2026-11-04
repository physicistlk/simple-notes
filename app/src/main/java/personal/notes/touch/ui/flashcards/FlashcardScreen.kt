package personal.notes.touch.ui.flashcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import personal.notes.touch.data.db.entity.FlashCardEntity
import personal.notes.touch.ui.theme.OrangePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(
    viewModel: FlashcardViewModel,
    onBackClick: () -> Unit
) {
    val cards by viewModel.cards.collectAsState()
    val isStudyMode by viewModel.isStudyMode.collectAsState()
    val studyIndex by viewModel.studyIndex.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var newQuestionText by remember { mutableStateOf("") }
    var newAnswerText by remember { mutableStateOf("") }
    var newTipsText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isStudyMode) "Study Session" else "Flashcards & Spaced Repetition",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF0EC)
                        ) {
                            Text(
                                text = "SM-2 Offline",
                                color = OrangePrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isStudyMode) viewModel.exitStudy() else onBackClick()
                    }) {
                        Icon(
                            imageVector = if (isStudyMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!isStudyMode) {
                        if (cards.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.startStudy() },
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Study (${cards.size})")
                            }
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Card", tint = OrangePrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFF8F9FA))
        ) {
            if (isStudyMode && cards.isNotEmpty()) {
                // ==========================================
                // INTERACTIVE STUDY VIEW (SM-2 Spaced Repetition)
                // ==========================================
                val currentCard = cards.getOrNull(studyIndex)
                if (currentCard != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Progress Counter
                        Text(
                            text = "Card ${studyIndex + 1} of ${cards.size}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Study Flip Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            border = BorderStroke(1.5.dp, if (isFlipped) OrangePrimary else Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(380.dp)
                                .clickable { viewModel.flipCard() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(28.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    if (!isFlipped) {
                                        // FRONT: Question
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFEDF2F7),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            Text(
                                                text = "QUESTION",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4A5568),
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = currentCard.cQuestion ?: "",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            color = Color(0xFF1A202C)
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Flip,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Tap card to reveal answer",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    } else {
                                        // BACK: Answer + Tips
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFFF0EC),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            Text(
                                                text = "ANSWER",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OrangePrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = currentCard.cAns ?: "",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            color = OrangePrimary
                                        )
                                        if (!currentCard.cTips.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "💡 ${currentCard.cTips}",
                                                fontSize = 14.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = Color(0xFF718096),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Rating Bar (Visible when flipped)
                        if (isFlipped) {
                            Text(
                                text = "Rate recall difficulty (SM-2):",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(0.85f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.rateCurrentCard(1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Again\n(1d)", fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                                Button(
                                    onClick = { viewModel.rateCurrentCard(2) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDD6B20)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Hard\n(2d)", fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                                Button(
                                    onClick = { viewModel.rateCurrentCard(3) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182CE)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Good\n(4d)", fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                                Button(
                                    onClick = { viewModel.rateCurrentCard(4) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38A169)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Easy\n(7d)", fontSize = 12.sp, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.flipCard() },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Show Answer", color = OrangePrimary)
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // DECK BROWSER (3-Column Tablet Grid)
                // ==========================================
                if (cards.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Style,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Flashcards Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4A5568)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Create cards with questions and answers for spaced repetition review.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create First Flashcard")
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cards, key = { it.id }) { card ->
                            var cardFlipped by remember { mutableStateOf(false) }
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.2f)
                                    .clickable { cardFlipped = !cardFlipped }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (cardFlipped) Color(0xFFFFF0EC) else Color(0xFFEDF2F7)
                                        ) {
                                            Text(
                                                text = if (cardFlipped) "ANS" else "Q",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (cardFlipped) OrangePrimary else Color(0xFF4A5568),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteCard(card.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = if (cardFlipped) (card.cAns ?: "") else (card.cQuestion ?: ""),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (cardFlipped) OrangePrimary else Color(0xFF2D3748),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    HorizontalDivider(
                                        color = Color(0xFFF0F0F0),
                                        thickness = 1.dp,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Reps: ${card.cNum}",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = when (card.reviewStatus) {
                                                2 -> "Learned"
                                                1 -> "Review"
                                                else -> "New"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = when (card.reviewStatus) {
                                                2 -> Color(0xFF38A169)
                                                1 -> Color(0xFFE53E3E)
                                                else -> Color.Gray
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create Flashcard
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Flashcard", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Question", fontSize = 12.sp, color = Color.Gray)
                    TextField(
                        value = newQuestionText,
                        onValueChange = { newQuestionText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = OrangePrimary,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC)
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Answer", fontSize = 12.sp, color = Color.Gray)
                    TextField(
                        value = newAnswerText,
                        onValueChange = { newAnswerText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = OrangePrimary,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC)
                        )
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Tips / Mnemonics (Optional)", fontSize = 12.sp, color = Color.Gray)
                    TextField(
                        value = newTipsText,
                        onValueChange = { newTipsText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = OrangePrimary,
                            unfocusedIndicatorColor = Color(0xFFCCCCCC)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newQuestionText.isNotBlank() && newAnswerText.isNotBlank()) {
                        viewModel.createCard(newQuestionText, newAnswerText, newTipsText)
                        newQuestionText = ""
                        newAnswerText = ""
                        newTipsText = ""
                        showCreateDialog = false
                    }
                }) {
                    Text("Create", color = OrangePrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}
