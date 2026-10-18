package com.example.automkvdubber.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.automkvdubber.model.DubberSettings
import com.example.automkvdubber.model.GeneratedOutputs
import com.example.automkvdubber.model.LogEntry
import com.example.automkvdubber.model.LogLevel
import com.example.automkvdubber.model.PipelineStep
import com.example.automkvdubber.model.SubtitleItem
import com.example.automkvdubber.service.DubbingPipelineManager
import com.example.automkvdubber.util.StorageHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class DubberUiState(
    val selectedMkvUri: Uri? = null,
    val selectedMkvName: String = "",
    val selectedMkvSize: String = "",
    val currentStep: PipelineStep = PipelineStep.IDLE,
    val progressPercent: Int = 0,
    val statusMessage: String = "মুভি ফাইল নির্বাচন করে ডাবিং শুরু করুন",
    val isTtsReady: Boolean = false,
    val ttsLanguageSupported: Boolean = true,
    val ttsEngineName: String = "Google TTS",
    val subtitles: List<SubtitleItem> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val outputs: GeneratedOutputs? = null,
    val settings: DubberSettings = DubberSettings(),
    val isPlayingAudio: Boolean = false,
    val audioCurrentPositionMs: Int = 0,
    val audioDurationMs: Int = 0
)

class DubberViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val _uiState = MutableStateFlow(DubberUiState())
    val uiState: StateFlow<DubberUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private val pipelineManager = DubbingPipelineManager(application.applicationContext)
    private var pipelineJob: Job? = null

    private var mediaPlayer: MediaPlayer? = null
    private val playerHandler = Handler(Looper.getMainLooper())
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    _uiState.update {
                        it.copy(
                            audioCurrentPositionMs = player.currentPosition,
                            audioDurationMs = player.duration.coerceAtLeast(1)
                        )
                    }
                    playerHandler.postDelayed(this, 300)
                }
            }
        }
    }

    init {
        initTtsEngine()
    }

    fun initTtsEngine() {
        val context = getApplication<Application>().applicationContext
        addLog("Google Text-to-Speech ইঞ্জিন যুক্ত করার চেষ্টা করা হচ্ছে...", LogLevel.INFO)
        // Prefer com.google.android.tts package, fall back to default if unavailable
        tts = try {
            TextToSpeech(context, this, "com.google.android.tts")
        } catch (e: Exception) {
            TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val ttsInstance = tts ?: return
            val engine = ttsInstance.defaultEngine ?: "Google TTS"
            val bnLocale = Locale("bn", "BD")
            val langResult = ttsInstance.setLanguage(bnLocale)

            val supported = langResult != TextToSpeech.LANG_MISSING_DATA && langResult != TextToSpeech.LANG_NOT_SUPPORTED

            _uiState.update {
                it.copy(
                    isTtsReady = true,
                    ttsLanguageSupported = supported,
                    ttsEngineName = if (engine.contains("google", ignoreCase = true)) "Google TTS" else engine
                )
            }

            if (supported) {
                addLog("✅ Google Text-to-Speech (বাংলা) প্রস্তুত!", LogLevel.SUCCESS)
            } else {
                addLog("⚠️ বাংলা ভয়েস ডাটা ফোনে ইনস্টল নেই। Settings > Text-to-speech থেকে বাংলা ডাউনলোড করুন।", LogLevel.WARNING)
            }
        } else {
            _uiState.update { it.copy(isTtsReady = false) }
            addLog("❌ Text-to-Speech শুরু করতে সমস্যা হয়েছে।", LogLevel.ERROR)
        }
    }

    fun selectMkvUri(uri: Uri) {
        val context = getApplication<Application>().applicationContext
        val fileName = StorageHelper.getFileName(context, uri)
        val fileSize = StorageHelper.getFileSizeFormatted(context, uri)

        _uiState.update {
            it.copy(
                selectedMkvUri = uri,
                selectedMkvName = fileName,
                selectedMkvSize = fileSize,
                currentStep = PipelineStep.IDLE,
                progressPercent = 0,
                statusMessage = "নির্বাচিত: $fileName ($fileSize)",
                outputs = null,
                subtitles = emptyList()
            )
        }

        addLog("📁 MKV ফাইল লোড হয়েছে: $fileName ($fileSize)", LogLevel.INFO)
    }

    fun startPipeline() {
        val uri = _uiState.value.selectedMkvUri ?: return
        val currentTts = tts
        if (currentTts == null || !_uiState.value.isTtsReady) {
            addLog("⚠️ TTS ইঞ্জিন এখনও প্রস্তুত হয়নি, অনুগ্রহ করে অপেক্ষা করুন।", LogLevel.WARNING)
            return
        }

        stopAudio()
        pipelineJob?.cancel()

        pipelineJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentStep = PipelineStep.PREPARING,
                    progressPercent = 5,
                    statusMessage = "প্রসেসিং শুরু হচ্ছে..."
                )
            }

            try {
                val outputs = pipelineManager.executePipeline(
                    mkvUri = uri,
                    tts = currentTts,
                    settings = _uiState.value.settings,
                    onStepChanged = { step, progress, msg ->
                        _uiState.update {
                            it.copy(
                                currentStep = step,
                                progressPercent = progress,
                                statusMessage = msg
                            )
                        }
                    },
                    onLog = { msg, level ->
                        addLog(msg, level)
                    },
                    onSubtitlesParsed = { parsedList ->
                        _uiState.update { it.copy(subtitles = parsedList) }
                    }
                )

                _uiState.update {
                    it.copy(
                        currentStep = PipelineStep.COMPLETED,
                        progressPercent = 100,
                        statusMessage = "🎉 সমস্ত কাজ সফলভাবে সম্পন্ন হয়েছে!",
                        outputs = outputs
                    )
                }

                // If dubbed audio was created, initialize player
                outputs.banglaDubbedAudioUri?.let { audioUri ->
                    prepareMediaPlayer(audioUri)
                }

            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: "অপ্রত্যাশিত সমস্যা দেখা দিয়েছে"
                _uiState.update {
                    it.copy(
                        currentStep = PipelineStep.ERROR,
                        statusMessage = "ত্রুটি: $errorMsg"
                    )
                }
                addLog("❌ ত্রুটি: $errorMsg", LogLevel.ERROR)
            }
        }
    }

    fun cancelPipeline() {
        pipelineJob?.cancel()
        pipelineManager.cancel()
        _uiState.update {
            it.copy(
                currentStep = PipelineStep.CANCELLED,
                statusMessage = "প্রসেসিং বাতিল করা হয়েছে।"
            )
        }
        addLog("🛑 ব্যবহারকারী দ্বারা প্রসেস বাতিল করা হয়েছে।", LogLevel.WARNING)
    }

    fun updateSettings(newSettings: DubberSettings) {
        _uiState.update { it.copy(settings = newSettings) }
        tts?.setSpeechRate(newSettings.speechRate)
        tts?.setPitch(newSettings.pitch)
    }

    fun addLog(msg: String, level: LogLevel = LogLevel.INFO) {
        val entry = LogEntry(message = msg, level = level)
        _uiState.update {
            it.copy(logs = it.logs + entry)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    // Media Player methods
    private fun prepareMediaPlayer(audioUri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(getApplication<Application>().applicationContext, audioUri)
                prepare()
                setOnCompletionListener {
                    _uiState.update { it.copy(isPlayingAudio = false, audioCurrentPositionMs = 0) }
                    playerHandler.removeCallbacks(updateProgressRunnable)
                }
            }
            mediaPlayer?.let { player ->
                _uiState.update {
                    it.copy(
                        audioDurationMs = player.duration,
                        audioCurrentPositionMs = 0
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleAudioPlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _uiState.update { it.copy(isPlayingAudio = false) }
            playerHandler.removeCallbacks(updateProgressRunnable)
        } else {
            player.start()
            _uiState.update { it.copy(isPlayingAudio = true) }
            playerHandler.post(updateProgressRunnable)
        }
    }

    fun seekAudio(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _uiState.update { it.copy(audioCurrentPositionMs = positionMs) }
    }

    private fun stopAudio() {
        playerHandler.removeCallbacks(updateProgressRunnable)
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        _uiState.update { it.copy(isPlayingAudio = false, audioCurrentPositionMs = 0, audioDurationMs = 0) }
    }

    override fun onCleared() {
        stopAudio()
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onCleared()
    }
}
