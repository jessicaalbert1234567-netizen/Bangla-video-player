package com.example.automkvdubber.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.automkvdubber.model.DubberSettings
import com.example.automkvdubber.model.GeneratedOutputs
import com.example.automkvdubber.model.LogEntry
import com.example.automkvdubber.model.LogLevel
import com.example.automkvdubber.model.PipelineStep
import com.example.automkvdubber.model.SubtitleItem
import com.example.automkvdubber.util.StorageHelper
import com.example.ui.theme.CinemaBackground
import com.example.ui.theme.CinemaBorder
import com.example.ui.theme.CinemaSurface
import com.example.ui.theme.CinemaSurfaceVariant
import com.example.ui.theme.CoralTertiary
import com.example.ui.theme.CyanContainer
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubberScreen(viewModel: DubberViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectMkvUri(it) }
    }

    val isProcessing = uiState.currentStep != PipelineStep.IDLE &&
            uiState.currentStep != PipelineStep.COMPLETED &&
            uiState.currentStep != PipelineStep.ERROR &&
            uiState.currentStep != PipelineStep.CANCELLED

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(CyanPrimary, VioletSecondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "AutoMkvDubber",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                            Text(
                                text = "MKV অটো বাংলা ডাবিং ও অনুবাদক",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = CyanPrimary
                                )
                            )
                        }
                    }
                },
                actions = {
                    // TTS Status Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (uiState.isTtsReady) CyanContainer.copy(alpha = 0.3f) else WarningAmber.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (uiState.isTtsReady) CyanPrimary.copy(alpha = 0.5f) else WarningAmber
                        ),
                        modifier = Modifier
                            .clickable {
                                try {
                                    val intent = Intent("com.android.settings.TTS_SETTINGS")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "TTS: ${uiState.ttsEngineName}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.isTtsReady) SuccessGreen else WarningAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isTtsReady) "TTS Ready" else "TTS Setup",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (uiState.isTtsReady) CyanPrimary else WarningAmber,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "সেটিংস",
                            tint = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.testTag("info_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "তথ্য",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CinemaBackground
                )
            )
        },
        containerColor = CinemaBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 1. Movie Selector Hero Card
            item {
                MovieSelectorCard(
                    uiState = uiState,
                    isProcessing = isProcessing,
                    onSelectClick = {
                        filePicker.launch(arrayOf("video/x-matroska", "video/*", "*/*"))
                    },
                    onStartClick = { viewModel.startPipeline() },
                    onCancelClick = { viewModel.cancelPipeline() }
                )
            }

            // 2. Active Pipeline Progress & Stepper
            if (isProcessing || uiState.currentStep == PipelineStep.COMPLETED || uiState.currentStep == PipelineStep.ERROR) {
                item {
                    PipelineProgressCard(
                        currentStep = uiState.currentStep,
                        progress = uiState.progressPercent,
                        statusMessage = uiState.statusMessage,
                        onViewSubtitles = { showSubtitleDialog = true },
                        hasSubtitles = uiState.subtitles.isNotEmpty()
                    )
                }
            }

            // 3. Audio Player Bar (when audio is ready)
            if (uiState.outputs?.banglaDubbedAudioUri != null) {
                item {
                    DubbedAudioPlayerCard(
                        uiState = uiState,
                        onTogglePlay = { viewModel.toggleAudioPlayback() },
                        onSeek = { viewModel.seekAudio(it) },
                        onShare = {
                            uiState.outputs?.banglaDubbedAudioUri?.let { uri ->
                                val intent = StorageHelper.createShareIntent(
                                    context,
                                    uri,
                                    "audio/mpeg",
                                    "বাংলা ডাবিং অডিও শেয়ার করুন"
                                )
                                context.startActivity(Intent.createChooser(intent, "অডিও শেয়ার করুন"))
                            }
                        }
                    )
                }
            }

            // 4. Generated Output Files Card
            if (uiState.outputs != null) {
                item {
                    OutputFilesCard(
                        outputs = uiState.outputs!!,
                        context = context
                    )
                }
            }

            // 5. Live Console Log
            item {
                LiveConsoleLogCard(
                    logs = uiState.logs,
                    onClear = { viewModel.clearLogs() },
                    onCopy = {
                        val allLogs = uiState.logs.joinToString("\n") { it.message }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AutoMkvDubber Logs", allLogs))
                        Toast.makeText(context, "লগ ক্লিপবোর্ডে কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Settings Bottom Sheet
    if (showSettingsSheet) {
        DubberSettingsSheet(
            settings = uiState.settings,
            onDismiss = { showSettingsSheet = false },
            onSave = { updated ->
                viewModel.updateSettings(updated)
                showSettingsSheet = false
            }
        )
    }

    // Subtitle Inspector Dialog
    if (showSubtitleDialog) {
        SubtitleViewerDialog(
            subtitles = uiState.subtitles,
            onDismiss = { showSubtitleDialog = false }
        )
    }

    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(text = "AutoMkvDubber সম্পর্কে", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "এই অ্যাপটি সম্পূর্ণ স্বয়ংক্রিয়ভাবে MKV মুভি থেকে অডিও ও ইংরেজি সাবটাইটেল বের করে, Google Translate (GTX) দিয়ে বাংলায় অনুবাদ করে এবং Google Text-to-Speech দিয়ে সময়ানুযায়ী ডাবিং অডিও তৈরি করে।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "🚀 বিশেষ সুবিধাসমূহ:\n" +
                                "• কোনো মেমরি অপচয় ছাড়া সরাসরি SAF স্ট্রিমিং\n" +
                                "• Scoped Storage অনুযায়ী Download/FileTranslato ফোল্ডারে অটো সেভ\n" +
                                "• FFmpeg atempo ফিল্টারের মাধ্যমে অটো-স্পিড সিঙ্ক\n" +
                                "• প্রয়োজন অনুযায়ী মুভিতে ডাবিং অডিও মাক্সিং",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanPrimary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("ঠিক আছে", color = CyanPrimary)
                }
            },
            containerColor = CinemaSurface
        )
    }
}

@Composable
fun MovieSelectorCard(
    uiState: DubberUiState,
    isProcessing: Boolean,
    onSelectClick: () -> Unit,
    onStartClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ইনপুট মুভি (.mkv)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = if (uiState.selectedMkvName.isNotEmpty()) uiState.selectedMkvSize else "মুভি ফাইল যুক্ত করুন",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }

                if (uiState.selectedMkvUri != null && !isProcessing) {
                    OutlinedButton(
                        onClick = onSelectClick,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("change_mkv_button")
                    ) {
                        Text("পরিবর্তন", color = CyanPrimary, fontSize = 12.sp)
                    }
                }
            }

            if (uiState.selectedMkvUri == null) {
                Button(
                    onClick = onSelectClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("select_mkv_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "MKV মুভি ফাইল সিলেক্ট করুন",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    )
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CinemaSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = CyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.selectedMkvName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "সাইজ: ${uiState.selectedMkvSize} • ফরম্যাট: Matroska MKV",
                                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Button(
                        onClick = onCancelClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CoralTertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("cancel_pipeline_button")
                    ) {
                        Icon(imageVector = Icons.Default.Cancel, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("প্রক্রিয়া বাতিল করুন", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onStartClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_dubbing_button")
                    ) {
                        Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "স্বয়ংক্রিয় বাংলা ডাবিং শুরু করুন",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PipelineProgressCard(
    currentStep: PipelineStep,
    progress: Int,
    statusMessage: String,
    onViewSubtitles: () -> Unit,
    hasSubtitles: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentStep == PipelineStep.COMPLETED) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                    } else if (currentStep == PipelineStep.ERROR) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = CyanPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = currentStep.titleBengali,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanPrimary
                    )
                )
            }

            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = CyanPrimary,
                trackColor = CinemaSurfaceVariant
            )

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            if (hasSubtitles) {
                OutlinedButton(
                    onClick = onViewSubtitles,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.align(Alignment.End).testTag("view_subtitles_button")
                ) {
                    Icon(imageVector = Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("সাবটাইটেল তালিকা দেখুন", fontSize = 12.sp, color = CyanPrimary)
                }
            }
        }
    }
}

@Composable
fun DubbedAudioPlayerCard(
    uiState: DubberUiState,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(CyanPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, tint = CyanPrimary)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "বাংলা ডাবিং অডিও প্লেয়ার",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            text = "সিঙ্ক সম্পন্ন (.mp3)",
                            style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen)
                        )
                    }
                }

                IconButton(onClick = onShare, modifier = Modifier.testTag("share_audio_button")) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "শেয়ার", tint = CyanPrimary)
                }
            }

            // Scrub Slider
            Slider(
                value = uiState.audioCurrentPositionMs.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..uiState.audioDurationMs.coerceAtLeast(1).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = CinemaSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().testTag("audio_scrub_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(uiState.audioCurrentPositionMs.toLong()),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
                Text(
                    text = formatDuration(uiState.audioDurationMs.toLong()),
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }

            // Play/Pause button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CyanPrimary)
                        .clickable { onTogglePlay() }
                        .testTag("play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (uiState.isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlayingAudio) "বিরতি" else "চালান",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputFilesCard(
    outputs: GeneratedOutputs,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = CyanPrimary)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "তৈরিকৃত ফাইলসমূহ (Download/FileTranslato/)",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "ডিভাইসের ডাউনলোড ফোল্ডারে স্বয়ংক্রিয়ভাবে সংরক্ষিত",
                        style = MaterialTheme.typography.labelSmall.copy(color = SuccessGreen)
                    )
                }
            }

            HorizontalDivider(color = CinemaBorder)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bangla SRT
                outputs.banglaSrtUri?.let { uri ->
                    OutputFileChip(
                        label = "📄 বাংলা SRT",
                        icon = Icons.Default.Subtitles,
                        onClick = {
                            val intent = StorageHelper.createViewIntent(context, uri, "application/x-subrip")
                            context.startActivity(Intent.createChooser(intent, "বাংলা সাবটাইটেল খুলুন"))
                        }
                    )
                }

                // Bangla Dubbed MP3
                outputs.banglaDubbedAudioUri?.let { uri ->
                    OutputFileChip(
                        label = "🔊 বাংলা MP3",
                        icon = Icons.Default.Audiotrack,
                        onClick = {
                            val intent = StorageHelper.createViewIntent(context, uri, "audio/mpeg")
                            context.startActivity(Intent.createChooser(intent, "বাংলা অডিও চালান"))
                        }
                    )
                }

                // Dubbed MKV Video
                outputs.dubbedMkvVideoUri?.let { uri ->
                    OutputFileChip(
                        label = "🎬 ডাব করা MKV মুভি",
                        icon = Icons.Default.Movie,
                        onClick = {
                            val intent = StorageHelper.createViewIntent(context, uri, "video/*")
                            context.startActivity(Intent.createChooser(intent, "মুভি চালান"))
                        }
                    )
                }

                // Original Audio
                outputs.originalAudioUri?.let { uri ->
                    OutputFileChip(
                        label = "🎵 মূল অডিও (.aac)",
                        icon = Icons.Default.Audiotrack,
                        onClick = {
                            val intent = StorageHelper.createViewIntent(context, uri, "audio/aac")
                            context.startActivity(Intent.createChooser(intent, "মূল অডিও শুনুন"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OutputFileChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.width(6.dp))
            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun LiveConsoleLogCard(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onCopy: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CinemaSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "রিয়েলটাইম কনসোল লগ",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Row {
                    IconButton(onClick = onCopy, modifier = Modifier.size(36.dp).testTag("copy_log_button")) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "কপি", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp).testTag("clear_log_button")) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "মুছুন", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF06090F))
                    .border(1.dp, CinemaBorder, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "সিস্টেম প্রস্তুত... লগ এন্ট্রি এখানে প্রদর্শিত হবে।",
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { entry ->
                            val color = when (entry.level) {
                                LogLevel.INFO -> CyanPrimary.copy(alpha = 0.9f)
                                LogLevel.SUCCESS -> SuccessGreen
                                LogLevel.WARNING -> WarningAmber
                                LogLevel.ERROR -> ErrorRed
                            }
                            Text(
                                text = entry.message,
                                color = color,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DubberSettingsSheet(
    settings: DubberSettings,
    onDismiss: () -> Unit,
    onSave: (DubberSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var speechRate by remember { mutableStateOf(settings.speechRate) }
    var pitch by remember { mutableStateOf(settings.pitch) }
    var enableAtempo by remember { mutableStateOf(settings.enableAtempoSync) }
    var muxVideo by remember { mutableStateOf(settings.muxVideoOutput) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CinemaSurface
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "ডাবিং ও সিন্থেসিস সেটিংস",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            // Speech Rate
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ভয়েস স্পিড (Speech Rate)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.2f", speechRate)}x", color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = speechRate,
                    onValueChange = { speechRate = it },
                    valueRange = 0.6f..1.8f,
                    colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
                )
            }

            // Voice Pitch
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ভয়েস পিচ (Voice Pitch)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.2f", pitch)}x", color = CyanPrimary, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    valueRange = 0.6f..1.6f,
                    colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary)
                )
            }

            // Atempo Auto-Sync Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("স্বয়ংক্রিয় atempo স্পিড সিঙ্ক", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("সংলাপ নির্ধারিত সময়ের চেয়ে বড় হলে স্পিড বাড়িয়ে ওভারল্যাপ রোধ করবে", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = enableAtempo,
                    onCheckedChange = { enableAtempo = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary, checkedTrackColor = CyanContainer)
                )
            }

            // Mux into MKV Video Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("নতুন ডাবিং মুভিতে যুক্ত করুন (.mkv)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("ভিডিওর সাথে নতুন বাংলা অডিও মাক্সিং করে পূর্ণাঙ্গ মুভি ফাইল তৈরি করবে", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = muxVideo,
                    onCheckedChange = { muxVideo = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanPrimary, checkedTrackColor = CyanContainer)
                )
            }

            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            speechRate = speechRate,
                            pitch = pitch,
                            enableAtempoSync = enableAtempo,
                            muxVideoOutput = muxVideo
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("সেটিংস সংরক্ষণ করুন", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SubtitleViewerDialog(
    subtitles: List<SubtitleItem>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("সাবটাইটেল তালিকা (${subtitles.size} টি)", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subtitles) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("#${item.index}", color = CyanPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${item.startTimeStr} ➔ ${item.endTimeStr}", color = TextTertiary, fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("EN: ${item.originalText}", color = TextSecondary, fontSize = 12.sp)
                            if (item.translatedText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("BN: ${item.translatedText}", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = CyanPrimary)
            }
        },
        containerColor = CinemaSurface
    )
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
