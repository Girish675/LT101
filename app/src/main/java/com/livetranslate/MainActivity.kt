package com.livetranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.livetranslate.ui.*

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Black,
                    surface = Color(0xFF121212),
                    primary = Color(0xFF4285F4)
                )
            ) {
                val vm: LiveTranslateViewModel = viewModel()
                val state by vm.uiState.collectAsState()

                LiveTranslateApp(
                    state = state,
                    onIntent = vm::onIntent
                )
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTranslateApp(
    state: LiveTranslateUiState,
    onIntent: (TranslateIntent) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Show loading screen while models are being copied
    if (!state.isModelReady) {
        ModelLoadingScreen(message = state.modelLoadingMessage)
        return
    }

    // Error Snackbar
    state.errorMessage?.let { msg ->
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { onIntent(TranslateIntent.DismissError) }) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(msg)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = state.currentMode == TranslationMode.STANDARD,
                    onClick = {
                        if (state.currentMode != TranslationMode.STANDARD) {
                            onIntent(TranslateIntent.ToggleMode)
                        }
                    },
                    icon = { Icon(Icons.Default.Translate, contentDescription = null) },
                    label = { Text("Standard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4285F4),
                        selectedTextColor = Color(0xFF4285F4),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF2A2A2A)
                    )
                )
                NavigationBarItem(
                    selected = state.currentMode == TranslationMode.LIVE_DUPLEX,
                    onClick = {
                        if (state.currentMode != TranslationMode.LIVE_DUPLEX) {
                            onIntent(TranslateIntent.ToggleMode)
                        }
                    },
                    icon = { Icon(Icons.Default.Headphones, contentDescription = null) },
                    label = { Text("Live Duplex") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF4285F4),
                        selectedTextColor = Color(0xFF4285F4),
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color(0xFF2A2A2A)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (state.currentMode) {
                TranslationMode.STANDARD -> {
                    TranslationScreen(
                        inputText = state.standardInputText,
                        outputText = state.standardOutputText,
                        isListening = state.isListening,
                        isProcessing = state.isProcessing,
                        sourceLang = state.sourceLanguage,
                        targetLangDisplay = state.targetLanguage.displayName,
                        lastLatencyMs = state.lastLatencyMs,
                        conversationHistory = state.conversationHistory,
                        onInputChange = { onIntent(TranslateIntent.SetStandardInput(it)) },
                        onTranslate = { onIntent(TranslateIntent.TranslateStandardInput) },
                        onToggleListen = { onIntent(TranslateIntent.ToggleListening) },
                        onOpenLanguagePicker = { onIntent(TranslateIntent.ShowLanguagePicker) },
                        onCopy = { onIntent(TranslateIntent.CopyToClipboard(it)) },
                        onShare = { onIntent(TranslateIntent.ShareText(it)) }
                    )
                }
                TranslationMode.LIVE_DUPLEX -> {
                    LiveDuplexScreen(
                        earbudTranscript = state.earbudTranscript,
                        targetTranscript = state.targetTranscript,
                        isListening = state.isListening,
                        isProcessing = state.isProcessing,
                        targetLangDisplay = state.targetLanguage.displayName,
                        onToggleListen = { onIntent(TranslateIntent.ToggleListening) },
                        onOpenLanguagePicker = { onIntent(TranslateIntent.ShowLanguagePicker) }
                    )
                }
            }
        }
    }

    // Language & Voice Picker Sheet
    if (state.showLanguagePicker) {
        LanguageVoicePickerSheet(
            onDismissRequest = { onIntent(TranslateIntent.HideLanguagePicker) },
            sheetState = sheetState,
            onLanguageSelected = { config ->
                onIntent(TranslateIntent.SelectLanguage(config))
            }
        )
    }
}

/**
 * Splash/loading screen shown while models are being copied from assets to internal storage.
 */
@Composable
fun ModelLoadingScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "LiveTranslate",
                color = Color(0xFF4285F4),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = Color(0xFF4285F4),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }
}
