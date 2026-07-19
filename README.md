# LiveTranslate

LiveTranslate is a highly optimized, fully offline, ultra-low-latency real-time translation app for Android. Designed for seamless cross-language communication, it runs entire NLP pipelines entirely on-device, prioritizing privacy, performance, and battery efficiency.

## Features

- **Fully Offline Processing**: No cloud APIs, no data collection. Everything runs securely on your device.
- **Ultra-Lightweight ML Stack**: 
  - **Speech-to-Text (STT)**: Whisper.cpp via JNI (highly optimized C++ inference).
  - **Machine Translation (MT)**: Opus-MT models via ONNX Runtime.
  - **Text-to-Speech (TTS)**: Piper TTS for natural voice generation via ONNX Runtime.
  - **Voice Activity Detection (VAD)**: Silero VAD for intelligent audio chunking.
- **Live Duplex Mode**: Continuous, real-time conversational translation with a split-screen UI for you and your conversation partner.
- **Material Design 3**: A beautiful, premium OLED-optimized dark UI with dynamic haptic feedback and conversational history.
- **<300MB APK Size**: Through aggressive quantization and model selection, the entire app footprint fits under 300MB.

## Architecture

The app is built natively in Kotlin using **Jetpack Compose** and follows a strictly reactive **MVI (Model-View-Intent)** architecture. 

The inference pipeline (`Audio -> VAD -> STT -> MT -> TTS -> Audio`) runs concurrently on background coroutines, managed by a robust Circular Audio Buffer that captures PCM streams directly from the Android `AudioRecord` APIs.

## Building and Running

1. Open the project in **Android Studio**.
2. Click **Sync Project with Gradle Files**. 
   *Note: During the pre-build phase, the Gradle script will automatically download the necessary Whisper.cpp JNI headers and quantized AI models into the `assets/` directory.*
3. Connect a physical Android device (API 26+) and click **Run**.
4. The first time the app launches, it will extract the ML models from the APK package to the device's internal storage (`filesDir/models`). 

## Permissions Required
- `RECORD_AUDIO` - For microphone access during translation.
- `BLUETOOTH_CONNECT` - For routing audio through earbuds.
- `POST_NOTIFICATIONS` - Required for the Foreground Service that keeps the microphone active in the background.

## License
MIT License
