package com.livetranslate.ui

import android.app.Application
import android.content.Intent
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
 *
 * Manages the full translation pipeline state and coordinates
 * AudioPipelineManager, CircularAudioBuffer, and TranslationPipeline.
 */

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
    val errorMessage: String? = null
)

enum class TranslationMode {
    STANDARD,   // Mode 1: Tap mic, speak, translate
    LIVE_DUPLEX // Mode 2: Continuous bidirectional
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
}

class LiveTranslateViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveTranslateUiState())
    val uiState: StateFlow<LiveTranslateUiState> = _uiState.asStateFlow()

    private val audioPipeline = AudioPipelineManager(application)

    // 30 seconds of audio at 16kHz
    private val circularBuffer = CircularAudioBuffer(capacityInSamples = 16000 * 30)

    private var pipeline: TranslationPipeline? = null
    private var mtEngine: OpusMT? = null
    private var recordingJob: Job? = null
    private var processingJob: Job? = null

    init {
        initializePipeline()
    }

    private fun initializePipeline() {
        val ctx = getApplication<Application>()
        val modelsDir = File(ctx.filesDir, "models")

        // In production, these would point to real model files copied from assets on first launch.
        // For now, we create the pipeline with the interfaces wired up.
        try {
            val whisperModelPath = File(modelsDir, "ggml-tiny.en.bin").absolutePath
            val stt: WhisperSTT = WhisperSTTImpl(whisperModelPath)
            val mt: OpusMT = OpusMTImpl(
                File(modelsDir, "opus-mt-en-es-encoder.onnx"),
                File(modelsDir, "opus-mt-en-es-decoder.onnx"),
                File(modelsDir, "vocab.json")
            )
            val tts: PiperTTS = PiperTTSImpl(File(modelsDir, "piper"))
            val vad: VoiceActivityDetector = SileroVADImpl(
                File(modelsDir, "silero_vad.onnx").absolutePath
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

        // Start foreground service (Design §6.1)
        val serviceIntent = Intent(ctx, AudioRecordService::class.java).apply {
            action = AudioRecordService.ACTION_START
        }
        ctx.startForegroundService(serviceIntent)

        _uiState.update { it.copy(isListening = true) }
        circularBuffer.clear()

        recordingJob = viewModelScope.launch {
            audioPipeline.startRecording(isEarbudUser = true).collect { audioChunk ->
                circularBuffer.write(audioChunk)

                // Run VAD on the latest audio
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

    /**
     * Design §6 steps 4-7: STT → MT → TTS → Playback
     */
    private suspend fun processAudioChunk(audioChunk: ShortArray) {
        _uiState.update { it.copy(isProcessing = true) }
        try {
            val state = _uiState.value
            val result = pipeline?.process(
                audioChunk = audioChunk,
                sourceLang = state.sourceLanguage,
                targetLang = state.targetLanguage.code,
                voiceProfile = state.targetLanguage.voiceProfile
            )

            if (result != null && result.sourceText.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        earbudTranscript = result.sourceText,
                        targetTranscript = result.translatedText
                    )
                }

                // Design §6.7: Play synthesized audio through the appropriate output
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

    /**
     * Mode 1 (Standard): Translate text input directly through MT.
     */
    private fun translateStandardInput() {
        val text = _uiState.value.standardInputText
        if (text.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val state = _uiState.value
                // Use the MT engine already initialized in the pipeline
                val mtResult = mtEngine?.translate(text, state.sourceLanguage, state.targetLanguage.code)
                    ?: "[Translation engine not loaded]"

                _uiState.update { it.copy(standardOutputText = mtResult, isProcessing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message, isProcessing = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        pipeline?.release()
    }
}
