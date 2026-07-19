#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static struct whisper_context * ctx = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_initModel(JNIEnv* env, jobject /* this */, jstring path) {
    const char * model_path = env->GetStringUTFChars(path, nullptr);
    LOGI("Initializing whisper context from %s", model_path);
    
    struct whisper_context_params cparams = whisper_context_default_params();
    ctx = whisper_init_from_file_with_params(model_path, cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context");
    }
    env->ReleaseStringUTFChars(path, model_path);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_runInference(JNIEnv* env, jobject /* this */, jshortArray audioData) {
    if (ctx == nullptr) {
        LOGE("Context not initialized");
        return env->NewStringUTF("");
    }

    jsize len = env->GetArrayLength(audioData);
    jshort * data = env->GetShortArrayElements(audioData, nullptr);
    
    // Convert 16-bit PCM to 32-bit float as required by whisper.cpp
    std::vector<float> pcmf32(len);
    for (int i = 0; i < len; i++) {
        pcmf32[i] = ((float) data[i]) / 32768.0f;
    }
    env->ReleaseShortArrayElements(audioData, data, JNI_ABORT);

    struct whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.print_progress   = false;
    wparams.print_special    = false;
    wparams.print_realtime   = false;
    wparams.print_timestamps = false;
    wparams.translate        = false;
    wparams.language         = "en";
    wparams.n_threads        = 4;

    if (whisper_full(ctx, wparams, pcmf32.data(), pcmf32.size()) != 0) {
        LOGE("Failed to run whisper inference");
        return env->NewStringUTF("");
    }

    std::string result = "";
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char * text = whisper_full_get_segment_text(ctx, i);
        result += text;
    }

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_freeModel(JNIEnv* env, jobject /* this */) {
    if (ctx != nullptr) {
        whisper_free(ctx);
        ctx = nullptr;
        LOGI("Whisper context freed");
    }
}
