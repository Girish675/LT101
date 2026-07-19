import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.livetranslate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.livetranslate"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
        
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true // Essential for the <300MB constraint
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // AndroidX Core & Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ViewModel Compose integration (needed for viewModel() in Compose)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Material Icons Extended (for Headphones, SwapHoriz, Send icons)
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // ONNX Runtime for Opus-MT, Piper TTS, and Silero VAD
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ---------------------------------------------------------
// CUSTOM TASKS FOR AUTOMATED ASSET & C++ HEADER DOWNLOADS
// ---------------------------------------------------------

tasks.register("downloadModelsAndHeaders") {
    doLast {
        val assetsDir = file("src/main/assets/models")
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        
        // 1. Download Whisper Tiny Model
        val whisperModel = file("${assetsDir.absolutePath}/ggml-tiny.en.bin")
        if (!whisperModel.exists()) {
            println("Downloading Whisper Model...")
            val url = URL("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin")
            url.openStream().use { input ->
                whisperModel.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            println("Whisper Model already exists.")
        }

        // 2. Download Silero VAD Model (~2MB)
        val vadModel = file("${assetsDir.absolutePath}/silero_vad.onnx")
        if (!vadModel.exists()) {
            println("Downloading Silero VAD Model...")
            val vadUrl = URL("https://github.com/snakers4/silero-vad/raw/master/src/silero_vad/data/silero_vad.onnx")
            vadUrl.openStream().use { input ->
                vadModel.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            println("Silero VAD Model already exists.")
        }
        
        // 3. Create mock/dummy files for Opus-MT and Piper TTS (Since we are using mock implementations)
        val dummyModels = listOf("opus-mt-en-es-encoder.onnx", "opus-mt-en-es-decoder.onnx", "vocab.json", "piper")
        dummyModels.forEach { fileName ->
            val mockFile = file("${assetsDir.absolutePath}/$fileName")
            if (!mockFile.exists()) {
                println("Creating mock model file: $fileName")
                mockFile.writeText("MOCK_MODEL_DATA_FOR_ONNX_STUB")
            }
        }
        
        // 2. Download Whisper.cpp headers and sources for JNI compilation
        val cppDir = file("src/main/cpp")
        val cppFilesToDownload = mapOf(
            "whisper.h" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/whisper.h",
            "whisper.cpp" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/whisper.cpp",
            "ggml.h" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml.h",
            "ggml.c" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml.c",
            "ggml-alloc.h" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml-alloc.h",
            "ggml-alloc.c" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml-alloc.c",
            "ggml-backend.h" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml-backend.h",
            "ggml-backend.c" to "https://raw.githubusercontent.com/ggerganov/whisper.cpp/master/ggml-backend.c"
        )
        
        cppFilesToDownload.forEach { (fileName, downloadUrl) ->
            val targetFile = file("${cppDir.absolutePath}/$fileName")
            if (!targetFile.exists()) {
                println("Downloading $fileName...")
                val url = URL(downloadUrl)
                url.openStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}

// Hook the download task before the standard build process starts
tasks.whenTaskAdded {
    if (name == "preBuild") {
        dependsOn("downloadModelsAndHeaders")
    }
}
