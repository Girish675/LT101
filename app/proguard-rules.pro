# Add project specific ProGuard rules here.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }

# Keep our JNI class
-keep class com.livetranslate.ml.WhisperSTTImpl { *; }
