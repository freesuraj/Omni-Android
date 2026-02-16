package com.suraj.apps.omni.feature.notes

import android.app.Application
import android.graphics.Color.parseColor
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotesRoute(
    documentId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: NotesViewModel = viewModel(
        key = "notes-$documentId",
        factory = NotesViewModel.factory(application = application, documentId = documentId)
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbars = remember { SnackbarHostState() }

    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                engine.language = Locale.getDefault()
            }
        }
        textToSpeech = engine

        onDispose {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            ttsReady = false
        }
    }

    val speakCurrent: (String) -> Unit = { text ->
        if (text.isNotBlank() && ttsReady) {
            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "note-${System.currentTimeMillis()}"
            )
        }
    }

    val visibleNotes = uiState.visibleNotes
    val currentNote = visibleNotes.getOrNull(uiState.currentPage)

    LaunchedEffect(uiState.autoReadEnabled, currentNote?.id, currentNote?.isFlipped) {
        if (!uiState.autoReadEnabled) return@LaunchedEffect
        val note = currentNote ?: return@LaunchedEffect
        val text = if (note.isFlipped) note.backContent else note.frontContent
        speakCurrent(text)
    }

    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage ?: return@LaunchedEffect
        snackbars.showSnackbar(message)
        viewModel.consumeError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.notes_cd_back))
                    }
                },
                title = {
                    Text(
                        text = if (uiState.mode == NotesScreenMode.CONFIG) {
                            stringResource(R.string.notes_title_new_flashcards)
                        } else {
                            uiState.documentTitle
                        }
                    )
                },
                actions = {
                    if (uiState.mode == NotesScreenMode.CARDS) {
                        IconButton(onClick = viewModel::generateNotes) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.notes_cd_regenerate))
                        }
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.notes_action_done))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbars) }
    ) { paddingValues ->
        when (uiState.mode) {
            NotesScreenMode.CONFIG -> NotesConfigScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onGenerate = viewModel::generateNotes
            )

            NotesScreenMode.GENERATING -> NotesGeneratingScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )

            NotesScreenMode.CARDS -> NotesCardsScreen(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onPageChanged = viewModel::setCurrentPage,
                onToggleFlip = viewModel::toggleCardFlip,
                onToggleBookmark = viewModel::toggleBookmark,
                onBookmarkedFilterChanged = viewModel::setBookmarkedOnly,
                onAutoReadChanged = viewModel::setAutoReadEnabled,
                onSpeak = speakCurrent
            )
        }
    }
}

@Composable
private fun NotesConfigScreen(
    uiState: NotesUiState,
    modifier: Modifier = Modifier,
    onGenerate: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(R.string.notes_config_heading),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = uiState.documentTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.notes_action_create_flashcards))
                }
            }
        }
    }
}

@Composable
private fun NotesGeneratingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.notes_generating_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.notes_generating_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun NotesCardsScreen(
    uiState: NotesUiState,
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit,
    onToggleFlip: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onBookmarkedFilterChanged: (Boolean) -> Unit,
    onAutoReadChanged: (Boolean) -> Unit,
    onSpeak: (String) -> Unit
) {
    val visibleNotes = uiState.visibleNotes

    Column(
        modifier = modifier.padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.notes_filter_bookmarked_only), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = uiState.showBookmarkedOnly, onCheckedChange = onBookmarkedFilterChanged)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.notes_filter_auto_read), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = uiState.autoReadEnabled, onCheckedChange = onAutoReadChanged)
            }
        }

        if (visibleNotes.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.notes_empty_filter_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.notes_empty_filter_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            val pagerState = rememberPagerState(
                initialPage = uiState.currentPage.coerceIn(0, visibleNotes.lastIndex),
                pageCount = { visibleNotes.size }
            )

            LaunchedEffect(pagerState.currentPage) {
                onPageChanged(pagerState.currentPage)
            }

            LaunchedEffect(uiState.currentPage, visibleNotes.size) {
                if (visibleNotes.isEmpty()) return@LaunchedEffect
                val clamped = uiState.currentPage.coerceIn(0, visibleNotes.lastIndex)
                if (pagerState.currentPage != clamped) {
                    pagerState.scrollToPage(clamped)
                }
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val note = visibleNotes[page]
                NoteCard(
                    note = note,
                    modifier = Modifier.fillMaxSize(),
                    onToggleFlip = { onToggleFlip(note.id) },
                    onToggleBookmark = { onToggleBookmark(note.id) },
                    onSpeak = {
                        onSpeak(if (note.isFlipped) note.backContent else note.frontContent)
                    }
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
        ) {
            val totalCards = visibleNotes.size
            val pageLabel = if (totalCards == 0) {
                stringResource(R.string.notes_page_indicator_empty)
            } else {
                val current = uiState.currentPage.coerceIn(0, totalCards - 1) + 1
                stringResource(R.string.notes_page_indicator, current, totalCards)
            }
            Text(
                text = pageLabel,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun NoteCard(
    note: StudyNoteCardUi,
    modifier: Modifier = Modifier,
    onToggleFlip: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSpeak: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (note.isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "noteFlip"
    )

    val fallbackColor = MaterialTheme.colorScheme.surface
    val cardColor = remember(note.colorHex, fallbackColor) {
        runCatching { Color(parseColor(note.colorHex)) }
            .getOrDefault(fallbackColor)
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable(onClick = onToggleFlip),
        shape = RoundedCornerShape(24.dp),
        color = cardColor,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        val showingBack = rotation > 90f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardColor)
                .padding(20.dp)
                .graphicsLayer {
                    if (showingBack) {
                        rotationY = 180f
                    }
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.notes_card_cd_speak))
                    }
                    Text(
                        text = if (showingBack) stringResource(R.string.notes_card_label_answer) else stringResource(R.string.notes_card_label_question),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (note.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.notes_card_cd_bookmark)
                        )
                    }
                }

                Text(
                    text = if (showingBack) note.backContent else note.frontContent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 10.dp)
                )

                Text(
                    text = if (showingBack) stringResource(R.string.notes_card_hint_back_to_question) else stringResource(R.string.notes_card_hint_reveal_answer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
