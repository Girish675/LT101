package com.livetranslate.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.livetranslate.audio.AudioPipelineManager
import com.livetranslate.audio.AudioRecordService
import com.livetranslate.audio.CircularAudioBuffer
import com.livetranslate.ml.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * MVI ViewModel for LiveTranslate.
 * Design §2.1: "Unidirectional Data Flow (UDF) with MVI in the presentation layer."
 */

// --- Conversation History ---

data class ConversationEntry(
    val sourceText: String,
    val translatedText: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val latencyMs: Long = 0L
)

// --- UI State ---

data class LanguageConfig(
    val code: String = "es",
    val displayName: String = "🇪🇸 Spanish (Mexico)",
    val voiceProfile: String = "es_MX-claude-medium"
)

data class LiveTranslateUiState(
    val isListening: Boolean = false,
    val currentMode: TranslationMode = TranslationMode.STANDARD,
    val earbudTranscript: String = "",
    val targetTranscript: String = "",
    val standardInputText: String = "",
    val standardOutputText: String = "",
    val isProcessing: Boolean = false,
    val sourceLanguage: String = "en",
    val targetLanguage: LanguageConfig = LanguageConfig(),
    val showLanguagePicker: Boolean = false,
    val errorMessage: String? = null,
    // New features
    val isModelReady: Boolean = false,
    val modelLoadingMessage: String = "Loading models…",
    val conversationHistory: List<ConversationEntry> = emptyList(),
    val lastLatencyMs: Long = 0L,
    val hapticFeedbackEnabled: Boolean = true
)

enum class TranslationMode {
    STANDARD,
    LIVE_DUPLEX
}

// --- Intents ---

sealed class TranslateIntent {
    data object ToggleListening : TranslateIntent()
    data object ToggleMode : TranslateIntent()
    data class SetStandardInput(val text: String) : TranslateIntent()
    data object TranslateStandardInput : TranslateIntent()
    data class SelectLanguage(val config: LanguageConfig) : TranslateIntent()
    data object ShowLanguagePicker : TranslateIntent()
    data object HideLanguagePicker : TranslateIntent()
    data object DismissError : TranslateIntent()
    data class CopyToClipboard(val text: String) : TranslateIntent()
    data class ShareText(val text: String) : TranslateIntent()
    data object ToggleHapticFeedback : TranslateIntent()
    data object ClearHistory : TranslateIntent()
}

class LiveTranslateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveTranslateUiState())
    val uiState: StateFlow<LiveTranslateUiState> = _uiState.asStateFlow()

    private val audioPipeline = AudioPipelineManager(application)
    private val modelManager = ModelManager(application)

    // 30 seconds of audio at 16kHz
    private val circularBuffer = CircularAudioBuffer(capacityInSamples = 16000 * 30)

    private var pipeline: TranslationPipeline? = null
    private var mtEngine: OpusMT? = null
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    init {
        viewModelScope.launch {
            setupModelsAndPipeline()
        }
    }

    /**
     * First-launch model setup: copy assets → filesDir, then init pipeline.
     */
    private suspend fun setupModelsAndPipeline() {
        _uiState.update { it.copy(modelLoadingMessage = "Preparing models…") }

        when (val result = modelManager.ensureModelsReady()) {
            is ModelSetupResult.Ready -> {
                _uiState.update { it.copy(modelLoadingMessage = "Initializing engines…") }
                initializePipeline()
                _uiState.update { it.copy(isModelReady = true) }
            }
            is ModelSetupResult.MissingModels -> {
                _uiState.update {
                    it.copy(
                        isModelReady = false,
                        errorMessage = "Missing models: ${result.fileNames.joinToString()}. " +
                            "Please rebuild the project to trigger the Gradle download task."
                    )
                }
            }
        }
    }

    private fun initializePipeline() {
        val modelsDir = modelManager.modelsDir

        try {
            val stt: WhisperSTT = WhisperSTTImpl(
                modelManager.getModelPath("ggml-tiny.en.bin")
            )
            val mt: OpusMT = OpusMTImpl(
                File(modelsDir, "opus-mt-en-es-encoder.onnx"),
                File(modelsDir, "opus-mt-en-es-decoder.onnx"),
                File(modelsDir, "vocab.json")
            )
            val tts: PiperTTS = PiperTTSImpl(File(modelsDir, "piper"))
            val vad: VoiceActivityDetector = SileroVADImpl(
                modelManager.getModelPath("silero_vad.onnx")
            )

            mtEngine = mt
            pipeline = TranslationPipeline(vad, stt, mt, tts)
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Failed to load models: ${e.message}") }
        }
    }

    fun onIntent(intent: TranslateIntent) {
        when (intent) {
            is TranslateIntent.ToggleListening -> toggleListening()
            is TranslateIntent.ToggleMode -> toggleMode()
            is TranslateIntent.SetStandardInput -> {
                _uiState.update { it.copy(standardInputText = intent.text) }
            }
            is TranslateIntent.TranslateStandardInput -> translateStandardInput()
            is TranslateIntent.SelectLanguage -> {
                _uiState.update { it.copy(targetLanguage = intent.config, showLanguagePicker = false) }
            }
            is TranslateIntent.ShowLanguagePicker -> {
                _uiState.update { it.copy(showLanguagePicker = true) }
            }
            is TranslateIntent.HideLanguagePicker -> {
                _uiState.update { it.copy(showLanguagePicker = false) }
            }
            is TranslateIntent.DismissError -> {
                _uiState.update { it.copy(errorMessage = null) }
            }
            is TranslateIntent.CopyToClipboard -> copyToClipboard(intent.text)
            is TranslateIntent.ShareText -> shareText(intent.text)
            is TranslateIntent.ToggleHapticFeedback -> {
                _uiState.update { it.copy(hapticFeedbackEnabled = !it.hapticFeedbackEnabled) }
            }
            is TranslateIntent.ClearHistory -> {
                _uiState.update { it.copy(conversationHistory = emptyList()) }
            }
        }
    }

    private fun toggleMode() {
        if (_uiState.value.isListening) {
            stopListening()
        }
        _uiState.update {
            it.copy(
                currentMode = if (it.currentMode == TranslationMode.STANDARD)
                    TranslationMode.LIVE_DUPLEX else TranslationMode.STANDARD,
                earbudTranscript = "",
                targetTranscript = "",
                standardInputText = "",
                standardOutputText = ""
            )
        }
    }

    private fun toggleListening() {
        if (_uiState.value.isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        val ctx = getApplication<Application>()

        val serviceIntent = Intent(ctx, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_START
        }
        ctx.startForegroundService(serviceIntent)

        _uiState.update { it.copy(isListening = true) }
        circularBuffer.clear()

        // Haptic feedback: speech detection start
        if (_uiState.value.hapticFeedbackEnabled) {
            triggerHaptic()
        }

        recordingJob = viewModelScope.launch {
            audioPipeline.startRecording(isEarbudUser = true).collect { audioChunk ->
                circularBuffer.write(audioChunk)

                val recentAudio = circularBuffer.readLast(16000) // last 1 second
                val speechEnded = pipeline?.detectEndOfSpeech(recentAudio) ?: false

                if (speechEnded && processingJob?.isActive != true) {
                    val fullChunk = circularBuffer.drainAll()
                    processingJob = launch { processAudioChunk(fullChunk) }
                }
            }
        }
    }

    private fun stopListening() {
        recordingJob?.cancel()
        recordingJob = null
        audioPipeline.stop()

        val ctx = getApplication<Application>()
        val serviceIntent = Intent(ctx, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_STOP
        }
        ctx.startService(serviceIntent)

        _uiState.update { it.copy(isListening = false) }
    }

    private suspend fun processAudioChunk(audioChunk: ShortArray) {
        val startTime = System.currentTimeMillis()
        _uiState.update { it.copy(isProcessing = true) }
        try {
            val state = _uiState.value
            val result = pipeline?.process(
                audioChunk = audioChunk,
                sourceLang = state.sourceLanguage,
                targetLang = state.targetLanguage.code,
                voiceProfile = state.targetLanguage.voiceProfile
            )

            val latency = System.currentTimeMillis() - startTime

            if (result != null && result.sourceText.isNotBlank()) {
                val entry = ConversationEntry(
                    sourceText = result.sourceText,
                    translatedText = result.translatedText,
                    latencyMs = latency
                )

                _uiState.update {
                    it.copy(
                        earbudTranscript = result.sourceText,
                        targetTranscript = result.translatedText,
                        lastLatencyMs = latency,
                        conversationHistory = it.conversationHistory + entry
                    )
                }

                // Haptic feedback on translation complete
                if (_uiState.value.hapticFeedbackEnabled) {
                    triggerHaptic()
                }

                if (result.synthesizedAudio.isNotEmpty()) {
                    audioPipeline.playAudio(result.synthesizedAudio, isEarbudUser = true)
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Processing failed: ${e.message}") }
        } finally {
            _uiState.update { it.copy(isProcessing = false) }
        }
    }

    private fun translateStandardInput() {
        val text = _uiState.value.standardInputText
        if (text.isBlank()) return

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val state = _uiState.value
                val mtResult = mtEngine?.translate(text, state.sourceLanguage, state.targetLanguage.code)
                    ?: "[Translation engine not loaded]"

                val latency = System.currentTimeMillis() - startTime
                val entry = ConversationEntry(
                    sourceText = text,
                    translatedText = mtResult,
                    latencyMs = latency
                )

                _uiState.update {
                    it.copy(
                        standardOutputText = mtResult,
                        isProcessing = false,
                        lastLatencyMs = latency,
                        conversationHistory = it.conversationHistory + entry
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isProcessing = false) }
            }
        }
    }

    // --- New Feature: Copy to Clipboard ---
    private fun copyToClipboard(text: String) {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LiveTranslate", text))
    }

    // --- New Feature: Share ---
    private fun shareText(text: String) {
        val ctx = getApplication<Application>()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(Intent.createChooser(shareIntent, "Share Translation").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // --- New Feature: Haptic Feedback ---
    private fun triggerHaptic() {
        val ctx = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        pipeline?.release()
    }
}
