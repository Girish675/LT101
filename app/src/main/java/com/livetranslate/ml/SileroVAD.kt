package com.livetranslate.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * Voice Activity Detection using Silero VAD (~2MB ONNX model).
 * Design §3: "Silero VAD (~2MB). Detects speech onset/offset to chunk audio efficiently."
 * Design §6.3: "Silero VAD detects 500ms of silence -> extracts the active speech chunk."
 *
 * This wrapper checks small audio frames (e.g. 512 samples at 16kHz = 32ms)
 * and returns a probability that speech is present.
 */
interface VoiceActivityDetector {
    /**
     * Evaluate a small PCM frame and return speech probability [0.0, 1.0].
     */
    suspend fun isSpeech(audioFrame: ShortArray): Float

    fun release()
}

class SileroVADImpl(modelPath: String) : VoiceActivityDetector {
    
    companion object {
        private const val TAG = "SileroVAD"
        private const val SAMPLE_RATE = 16000L
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    // State buffers for the LSTM-based Silero VAD
    // h and c are shape [2, 1, 64]
    private val hBuffer = java.nio.ByteBuffer.allocateDirect(2 * 1 * 64 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
    private val cBuffer = java.nio.ByteBuffer.allocateDirect(2 * 1 * 64 * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()

    init {
        val zeros = FloatArray(128) { 0f }
        hBuffer.put(zeros).rewind()
        cBuffer.put(zeros).rewind()

        session = try {
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setIntraOpNumThreads(1)
            environment.createSession(modelPath, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Silero VAD ONNX model: ${e.message}")
            throw RuntimeException("Failed to initialize VAD", e)
        }
    }

    override suspend fun isSpeech(audioFrame: ShortArray): Float = withContext(Dispatchers.Default) {
        try {
            // Convert ShortArray to FloatArray normalized to [-1, 1]
            val floatData = FloatArray(audioFrame.size) { audioFrame[it] / 32768f }
            val inputBuffer = java.nio.ByteBuffer.allocateDirect(floatData.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            inputBuffer.put(floatData).rewind()
            
            // Create input tensor [1, sequence_length]
            val inputTensor = OnnxTensor.createTensor(
                environment,
                inputBuffer,
                longArrayOf(1, floatData.size.toLong())
            )

            // Create sample rate tensor [1]
            val srBuffer = java.nio.ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.nativeOrder()).asLongBuffer()
            srBuffer.put(SAMPLE_RATE).rewind()
            val srTensor = OnnxTensor.createTensor(
                environment,
                srBuffer,
                longArrayOf(1)
            )

            // Rewind state buffers before creating tensors
            hBuffer.rewind()
            cBuffer.rewind()

            // Create h and c state tensors [2, 1, 64]
            val hTensor = OnnxTensor.createTensor(
                environment,
                hBuffer,
                longArrayOf(2, 1, 64)
            )
            val cTensor = OnnxTensor.createTensor(
                environment,
                cBuffer,
                longArrayOf(2, 1, 64)
            )

            // Run inference
            val inputs = mapOf(
                "input" to inputTensor,
                "sr" to srTensor,
                "h" to hTensor,
                "c" to cTensor
            )
            
            val result = session.run(inputs)

            // Extract output probability
            val outputTensor = result.get(0) as OnnxTensor
            val probBuffer = java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            val outBuf = outputTensor.floatBuffer
            outBuf.rewind()
            val probability = outBuf.get()

            // Extract updated states
            val hnTensor = result.get(1) as OnnxTensor
            val cnTensor = result.get(2) as OnnxTensor
            
            val hnBuf = hnTensor.floatBuffer
            hnBuf.rewind()
            hBuffer.clear()
            hBuffer.put(hnBuf)
            
            val cnBuf = cnTensor.floatBuffer
            cnBuf.rewind()
            cBuffer.clear()
            cBuffer.put(cnBuf)

            // Cleanup tensors for this run
            inputTensor.close()
            srTensor.close()
            hTensor.close()
            cTensor.close()
            result.close()

            probability
        } catch (e: Exception) {
            Log.e(TAG, "VAD inference failed", e)
            0f
        }
    }

    override fun release() {
        try {
            session.close()
            environment.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VAD session", e)
        }
    }
}
