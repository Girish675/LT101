package com.livetranslate.ml

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full inference pipeline per Design §6:
 *   VAD → STT → MT → TTS
 *
 * Takes raw PCM audio, detects speech, transcribes, translates, and synthesizes.
 */
class TranslationPipeline(
    private val vad: VoiceActivityDetector,
    private val stt: WhisperSTT,
    private val mt: OpusMT,
    private val tts: PiperTTS
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val VAD_FRAME_SIZE = 512 // ~32ms at 16kHz
        private const val SILENCE_THRESHOLD_MS = 500
        private const val SPEECH_THRESHOLD = 0.5f

        // Number of consecutive silent frames needed to trigger end-of-speech
        private val SILENCE_FRAMES_NEEDED = (SILENCE_THRESHOLD_MS * SAMPLE_RATE / 1000) / VAD_FRAME_SIZE
    }

    data class PipelineResult(
        val sourceText: String,
        val translatedText: String,
        val synthesizedAudio: ShortArray
    )

    /**
     * Run VAD over the accumulated audio to detect if speech has ended.
     * Returns true if we've seen [SILENCE_FRAMES_NEEDED] consecutive silent frames.
     */
    suspend fun detectEndOfSpeech(audioChunk: ShortArray): Boolean {
        var consecutiveSilent = 0
        var offset = 0

        while (offset + VAD_FRAME_SIZE <= audioChunk.size) {
            val frame = audioChunk.copyOfRange(offset, offset + VAD_FRAME_SIZE)
            val speechProb = vad.isSpeech(frame)
            if (speechProb < SPEECH_THRESHOLD) {
                consecutiveSilent++
                if (consecutiveSilent >= SILENCE_FRAMES_NEEDED) {
                    return true
                }
            } else {
                consecutiveSilent = 0
            }
            offset += VAD_FRAME_SIZE
        }
        return false
    }

    /**
     * Execute the full pipeline: STT → MT → TTS
     * Called once VAD has detected end-of-speech and the audio chunk is extracted.
     */
    suspend fun process(
        audioChunk: ShortArray,
        sourceLang: String,
        targetLang: String,
        voiceProfile: String
    ): PipelineResult = withContext(Dispatchers.Default) {
        // Step 1 (Design §6.4): STT — transcribe audio to source text
        val sourceText = stt.transcribe(audioChunk)

        if (sourceText.isBlank()) {
            return@withContext PipelineResult("", "", ShortArray(0))
        }

        // Step 2 (Design §6.5): MT — translate source text to target language
        val translatedText = mt.translate(sourceText, sourceLang, targetLang)

        // Step 3 (Design §6.6): TTS — synthesize target text to PCM audio
        val synthesizedAudio = tts.synthesize(translatedText, voiceProfile)

        PipelineResult(sourceText, translatedText, synthesizedAudio)
    }

    fun release() {
        vad.release()
        stt.release()
        mt.release()
        tts.release()
    }
}
