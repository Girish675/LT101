# Product Requirements & Architecture Document: LiveTranslate Android

## 1. Executive Summary
An offline-first, highly optimized Android translation application that provides standard text/speech translation and a continuous "Live Duplex" mode for real-time conversational translation. 

**Strict Constraint:** The maximum application size (APK) must not exceed 300MB.

## 2. System Architecture Overview
The application follows a clean architecture pattern utilizing Unidirectional Data Flow (UDF) with MVI (Model-View-Intent) in the presentation layer.

### 2.1 Layer Breakdown
*   **UI Layer (Jetpack Compose):** Subscribes to UI States emitted by ViewModels. Handles permissions, rendering, and user inputs.
*   **Domain Layer:** Contains Use Cases for formatting transcripts, managing audio sessions, and orchestrating the translation pipeline.
*   **Data/ML Layer:** Houses the quantized models and handles C++ interoperability via JNI for model execution.

## 3. The 300MB AI Stack
To adhere to the strict 300MB size limit, the application relies on lightweight, task-specific models executed via ONNX Runtime and C++ ports, rather than large generative LLMs.

*   **VAD (Voice Activity Detection):** Silero VAD (~2MB). Detects speech onset/offset to chunk audio efficiently.
*   **STT (Speech-to-Text):** Whisper.cpp `tiny.en` or `tiny` (~75MB). Runs via JNI wrapper.
*   **Translation Engine:** Opus-MT / MarianMT exported to ONNX (~80MB per language pair).
*   **TTS (Text-to-Speech):** Piper TTS ONNX models (~20MB per voice profile).

*Note: Due to size constraints, the app will ship with English and ONE target language by default. Additional language pairs must be downloaded on-demand (Dynamic Feature Modules) to maintain the base 300MB size.*

## 4. Audio Pipeline & Hardware Routing (Solving AEC)
The most complex component is managing simultaneous input/output without creating an infinite audio feedback loop.

### 4.1 Hardware Routing Strategy
*   **Earbud User (English):** Input from `TYPE_BLUETOOTH_SCO`. Output routed to `TYPE_BUILTIN_SPEAKER` (for the other person to hear).
*   **External User (Target Language):** Input from `TYPE_BUILTIN_MIC`. Output routed to `TYPE_BLUETOOTH_A2DP` (for the earbud user to hear).

### 4.2 Acoustic Echo Cancellation (AEC)
Software AEC is mandatory. The audio capture service will utilize Android's `android.media.audiofx.AcousticEchoCanceler`.
1.  Initialize `AudioRecord`.
2.  Retrieve Audio Session ID.
3.  Bind `AcousticEchoCanceler.create(audioSessionId)`.
4.  Bind `NoiseSuppressor.create(audioSessionId)`.

## 5. User Interface Specifications
*   **Design System:** Material Design 3.
*   **Core Screens:**
    *   `TranslationScreen`: Text input, push-to-talk button, scrollable chat history.
    *   `LiveDuplexScreen`: Immersive dark UI. Split screen horizontally. Top half shows Target Language transcript (inverted for the other person to read), bottom half shows Earbud User transcript.
    *   `LanguageAndVoicePicker`: Modal Bottom Sheet. Search bar at top. List items contain Flag Emoji, Language Name, and a sub-dropdown for Voice Profiles (e.g., "en_US-lessac-medium").

## 6. Execution Flow (Live Mode)
1.  **Start:** User enables Live Mode. `AudioRecordService` starts as a Foreground Service.
2.  **Buffering:** Microphone captures 16kHz PCM audio into a circular buffer.
3.  **VAD Trigger:** Silero VAD detects 500ms of silence -> extracts the active speech chunk.
4.  **STT:** Audio chunk passed to Whisper.cpp -> Returns String (e.g., "Hello").
5.  **MT:** String passed to Opus-MT ONNX -> Returns Target String (e.g., "Hola").
6.  **TTS:** Target String passed to Piper TTS -> Generates PCM audio.
7.  **Playback:** PCM audio pushed to `AudioTrack` outputting to the designated speaker.

## 7. Implementation Details & Module Structure
The core implementation is structured as follows:

*   **`app/build.gradle.kts`**: Configured to shrink resources aggressively (`isMinifyEnabled = true`) and import `kotlinx-coroutines`, Jetpack Compose (Material 3), and `onnxruntime-android`. The NDK builds are configured to use C++17.
*   **`AudioPipelineManager.kt`**: This is the heart of the hardware routing and AEC. It dynamically checks `AudioDeviceInfo` and forces `isSpeakerphoneOn` logic depending on who is speaking (Earbud User vs. Target Language). It strictly uses `MediaRecorder.AudioSource.VOICE_COMMUNICATION` and applies both `AcousticEchoCanceler` and `NoiseSuppressor` to the `AudioRecord` session.
*   **`InferenceEngines.kt`**: Contains the abstractions for our ML stack:
    *   `WhisperSTTImpl`: JNI layer that calls into `whisper.cpp`.
    *   `OpusMTImpl`: ONNX Runtime abstraction for MarianMT encoder/decoder.
    *   `PiperTTSImpl`: ONNX Runtime abstraction for Piper TTS synthesis.
*   **`LiveDuplexScreen.kt`**: Material 3 UI utilizing a split-screen OLED Dark Mode design. The Target Language section is inverted 180 degrees. It implements a fully responsive Canvas-based voice waveform animation and includes a searchable ModalBottomSheet for language/voice selection.