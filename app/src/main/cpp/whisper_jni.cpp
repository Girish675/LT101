#include <jni.h>
#include <string>

extern "C" JNIEXPORT void JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_initModel(JNIEnv* env, jobject /* this */, jstring path) {
    // Stub for whisper.cpp initialization
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_runInference(JNIEnv* env, jobject /* this */, jshortArray audioData) {
    // Stub for whisper.cpp inference
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_livetranslate_ml_WhisperSTTImpl_freeModel(JNIEnv* env, jobject /* this */) {
    // Stub for whisper.cpp memory cleanup
}
