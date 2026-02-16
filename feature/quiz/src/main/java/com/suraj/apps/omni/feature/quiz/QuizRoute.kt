package com.suraj.apps.omni.feature.quiz

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suraj.apps.omni.core.data.quiz.FREE_MAX_QUIZ_QUESTIONS
import com.suraj.apps.omni.core.data.quiz.MIN_QUIZ_QUESTIONS
import com.suraj.apps.omni.core.model.QuizDifficulty
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun QuizRoute(
    documentId: String,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: QuizViewModel = viewModel(
        key = "quiz-$documentId",
        factory = QuizViewModel.factory(application = application, documentId = documentId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { toneGenerator.release() }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeError()
    }

    LaunchedEffect(uiState.shouldOpenPaywall) {
        if (!uiState.shouldOpenPaywall) return@LaunchedEffect
        viewModel.consumePaywallNavigation()
        onOpenPaywall()
    }

    LaunchedEffect(uiState.answerFeedback?.nonce) {
        val feedback = uiState.answerFeedback ?: return@LaunchedEffect
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        if (uiState.settings.soundsEnabled) {
            toneGenerator.startTone(
                if (feedback.wasCorrect) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK,
                100
            )
        }
        viewModel.consumeAnswerFeedback()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.mode) {
                            QuizScreenMode.CONFIG -> "Configure Quiz"
                            QuizScreenMode.GENERATING -> "Generating Quiz"
                            QuizScreenMode.PLAYING -> "${uiState.documentTitle} Quiz"
                            QuizScreenMode.RESULT -> "Quiz Complete"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.mode == QuizScreenMode.PLAYING || uiState.mode == QuizScreenMode.RESULT) {
                        IconButton(
                            onClick = viewModel::replayActiveQuiz,
                            enabled = uiState.activeQuizId != null
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Replay")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues: PaddingValues ->
        when (uiState.mode) {
            QuizScreenMode.CONFIG -> QuizConfigScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onQuestionCountChanged = viewModel::onQuestionCountChanged,
                onDifficultyChanged = viewModel::onDifficultyChanged,
                onShowSourceSnippetChanged = viewModel::onShowSourceSnippetChanged,
                onSoundsEnabledChanged = viewModel::onSoundsEnabledChanged,
                onGenerateQuiz = viewModel::generateQuiz,
                onOpenHistoryReview = viewModel::openHistoryReview,
                onReplayHistory = viewModel::replayHistoryQuiz
            )

            QuizScreenMode.GENERATING -> QuizGeneratingScreen(
                providerNotice = uiState.providerNotice,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            QuizScreenMode.PLAYING -> QuizPlayingScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onAnswer = viewModel::answerCurrentQuestion,
                onBackToConfig = viewModel::backToConfiguration
            )

            QuizScreenMode.RESULT -> QuizResultScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onReplay = viewModel::replayActiveQuiz,
                onBackToConfig = viewModel::backToConfiguration
            )
        }
    }
}

@Composable
private fun QuizConfigScreen(
    uiState: QuizUiState,
    modifier: Modifier = Modifier,
    onQuestionCountChanged: (Int) -> Unit,
    onDifficultyChanged: (QuizDifficulty) -> Unit,
    onShowSourceSnippetChanged: (Boolean) -> Unit,
    onSoundsEnabledChanged: (Boolean) -> Unit,
    onGenerateQuiz: () -> Unit,
    onOpenHistoryReview: () -> Unit,
    onReplayHistory: () -> Unit
) {
    val sliderValue = uiState.settings.questionCount.toFloat()

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quiz Configuration",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Number of Questions: ${uiState.questionCountLabel}",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { onQuestionCountChanged(it.roundToInt()) },
                    valueRange = MIN_QUIZ_QUESTIONS.toFloat()..uiState.maxQuestionCount.toFloat(),
                    steps = (uiState.maxQuestionCount - MIN_QUIZ_QUESTIONS - 1).coerceAtLeast(0)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DifficultyChip(
                        title = "Easy",
                        selected = uiState.settings.difficulty == QuizDifficulty.EASY,
                        onClick = { onDifficultyChanged(QuizDifficulty.EASY) },
                        modifier = Modifier.weight(1f)
                    )
                    DifficultyChip(
                        title = "Medium",
                        selected = uiState.settings.difficulty == QuizDifficulty.MEDIUM,
                        onClick = { onDifficultyChanged(QuizDifficulty.MEDIUM) },
                        modifier = Modifier.weight(1f)
                    )
                    DifficultyChip(
                        title = "Hard",
                        selected = uiState.settings.difficulty == QuizDifficulty.HARD,
                        onClick = { onDifficultyChanged(QuizDifficulty.HARD) },
                        modifier = Modifier.weight(1f)
                    )
                }

                ToggleRow(
                    label = "Show Source Snippet",
                    checked = uiState.settings.showSourceSnippet,
                    onCheckedChange = onShowSourceSnippetChanged
                )
                ToggleRow(
                    label = "Play Sounds",
                    checked = uiState.settings.soundsEnabled,
                    onCheckedChange = onSoundsEnabledChanged
                )
            }
        }

        if (!uiState.isPremiumUnlocked) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Free limit: up to $FREE_MAX_QUIZ_QUESTIONS questions per quiz. Upgrade for longer sets.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        uiState.providerNotice?.let { notice ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = notice,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        uiState.historyPreview?.let { history ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (history.completed) "Latest quiz result" else "Resume latest quiz",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (history.completed) {
                            "Score ${history.correctCount}/${history.questionCount}"
                        } else {
                            "Progress ${history.answeredCount}/${history.questionCount}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenHistoryReview, modifier = Modifier.weight(1f)) {
                            Text(if (history.completed) "Review" else "Resume")
                        }
                        Button(
                            onClick = onReplayHistory,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("Replay")
                        }
                    }
                }
            }
        }

        Button(
            onClick = onGenerateQuiz,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            )
        ) {
            Text(
                text = "Generate Quiz",
                modifier = Modifier.padding(vertical = 4.dp),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DifficultyChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(title) },
        modifier = modifier
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun QuizGeneratingScreen(
    providerNotice: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Generating your quiz...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Preparing question set, options, and review summary.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        )
        providerNotice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun QuizPlayingScreen(
    uiState: QuizUiState,
    modifier: Modifier = Modifier,
    onAnswer: (String) -> Unit,
    onBackToConfig: () -> Unit
) {
    val question = uiState.questions.getOrNull(uiState.currentQuestionIndex)

    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBackToConfig) {
                Text("Exit")
            }
            Text(
                text = "Streak ${uiState.streak}  |  Best ${uiState.bestStreak}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LinearProgressIndicator(
            progress = {
                if (uiState.questions.isEmpty()) 0f else {
                    uiState.questions.count { it.userAnswer != null }.toFloat() / uiState.questions.size.toFloat()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(uiState.questionProgressLabel, style = MaterialTheme.typography.labelLarge)
            Text("Correct: ${uiState.correctCount}", style = MaterialTheme.typography.labelLarge)
        }

        if (question == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "No active question. Return to config and generate a new quiz.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            return
        }

        SwipeQuestionCard(
            question = question,
            modifier = Modifier.weight(1f),
            onSwipeLeft = { onAnswer("B") },
            onSwipeRight = { onAnswer("A") }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onAnswer("B") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Text("Choose B")
            }
            Button(
                onClick = { onAnswer("A") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Choose A")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeQuestionCard(
    question: QuizQuestionUi,
    modifier: Modifier = Modifier,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit
) {
    var dragOffset by remember(question.id) { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragOffset.roundToInt(), 0) }
            .pointerInput(question.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                    },
                    onDragEnd = {
                        when {
                            dragOffset > 120f -> onSwipeRight()
                            dragOffset < -120f -> onSwipeLeft()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                )
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = question.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionBox(
                    label = "Option B",
                    text = question.optionB,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.errorContainer
                )
                OptionBox(
                    label = "Option A",
                    text = question.optionA,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.primaryContainer
                )
            }

            question.sourceSnippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = snippet,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Swipe right for A, left for B",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun OptionBox(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = color, shape = RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuizResultScreen(
    uiState: QuizUiState,
    modifier: Modifier = Modifier,
    onReplay: () -> Unit,
    onBackToConfig: () -> Unit
) {
    val total = uiState.questions.size.coerceAtLeast(1)
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Quiz Complete!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "${uiState.correctCount} / $total",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Correct Answers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onReplay, modifier = Modifier.weight(1f)) {
                    Text("Retry Quiz")
                }
                Button(
                    onClick = onBackToConfig,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("New Setup")
                }
            }
        }

        item {
            Text(
                text = "Review",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        items(uiState.questions) { question ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                val isCorrect = question.isCorrect == true
                Icon(
                    imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = if (isCorrect) "Correct" else "Incorrect",
                    tint = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                    Text(question.prompt, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Your answer: ${question.userAnswer ?: "-"} • Correct: ${question.correctAnswer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
