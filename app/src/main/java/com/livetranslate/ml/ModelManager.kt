package com.livetranslate.ml

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages model files on the device.
 *
 * Models are bundled in the APK under assets/models/ (placed there by the Gradle download task).
 * On first launch, this class copies them from the read-only assets directory to the app's
 * internal storage (filesDir/models/) where the inference engines can load them.
 *
 * This also handles future on-demand language pack downloads.
 */
class ModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val ASSETS_MODELS_DIR = "models"
        private const val PREFS_NAME = "model_manager_prefs"
        private const val KEY_MODELS_COPIED = "models_copied_v1"

        // Model file names that must be present for the pipeline to work
        val REQUIRED_MODELS = listOf(
            "ggml-tiny.en.bin",     // Whisper STT
            "silero_vad.onnx"       // Silero VAD
        )

        // Optional models that enhance functionality
        val OPTIONAL_MODELS = listOf(
            "opus-mt-en-es-encoder.onnx",
            "opus-mt-en-es-decoder.onnx",
            "vocab.json"
        )
    }

    val modelsDir: File = File(context.filesDir, "models")

    /**
     * Copy all model assets from APK to internal storage on first launch.
     * This is idempotent — if models already exist, it skips them.
     */
    suspend fun ensureModelsReady(): ModelSetupResult = withContext(Dispatchers.IO) {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MODELS_COPIED, false)) {
            Log.d(TAG, "Models already copied, skipping.")
            return@withContext checkModelAvailability()
        }

        try {
            val assetFiles = context.assets.list(ASSETS_MODELS_DIR) ?: emptyArray()
            Log.d(TAG, "Found ${assetFiles.size} model files in assets: ${assetFiles.toList()}")

            for (fileName in assetFiles) {
                val destFile = File(modelsDir, fileName)
                if (!destFile.exists()) {
                    Log.d(TAG, "Copying $fileName to internal storage...")
                    context.assets.open("$ASSETS_MODELS_DIR/$fileName").use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            prefs.edit().putBoolean(KEY_MODELS_COPIED, true).apply()
            Log.d(TAG, "All models copied successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy models: ${e.message}", e)
        }

        checkModelAvailability()
    }

    private fun checkModelAvailability(): ModelSetupResult {
        val missing = REQUIRED_MODELS.filter { !File(modelsDir, it).exists() }
        return if (missing.isEmpty()) {
            ModelSetupResult.Ready
        } else {
            ModelSetupResult.MissingModels(missing)
        }
    }

    fun isModelAvailable(fileName: String): Boolean {
        return File(modelsDir, fileName).exists()
    }

    fun getModelPath(fileName: String): String {
        return File(modelsDir, fileName).absolutePath
    }
}

sealed class ModelSetupResult {
    data object Ready : ModelSetupResult()
    data class MissingModels(val fileNames: List<String>) : ModelSetupResult()
}
