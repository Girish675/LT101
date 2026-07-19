package com.livetranslate.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

// Language data class for the picker
data class LanguageOption(
    val flag: String,
    val name: String,
    val code: String,
    val voiceProfiles: List<String>
)

val AVAILABLE_LANGUAGES = listOf(
    LanguageOption("🇪🇸", "Spanish (Mexico)", "es", listOf("es_MX-claude-medium (Female)", "es_MX-roberto-medium (Male)")),
    LanguageOption("🇫🇷", "French (France)", "fr", listOf("fr_FR-siwis-medium (Female)", "fr_FR-gilles-medium (Male)")),
    LanguageOption("🇩🇪", "German (Germany)", "de", listOf("de_DE-thorsten-medium (Male)", "de_DE-eva_k-medium (Female)")),
    LanguageOption("🇯🇵", "Japanese", "ja", listOf("ja_JP-kokoro-medium (Female)")),
    LanguageOption("🇮🇹", "Italian", "it", listOf("it_IT-riccardo-medium (Male)")),
    LanguageOption("🇵🇹", "Portuguese (Brazil)", "pt", listOf("pt_BR-edresson-medium (Male)")),
    LanguageOption("🇷🇺", "Russian", "ru", listOf("ru_RU-irina-medium (Female)")),
    LanguageOption("🇰🇷", "Korean", "ko", listOf("ko_KR-default-medium (Female)")),
    LanguageOption("🇨🇳", "Chinese (Mandarin)", "zh", listOf("zh_CN-huayan-medium (Female)"))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDuplexScreen(
    earbudTranscript: String,
    targetTranscript: String,
    isListening: Boolean,
    onToggleListen: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    targetLangDisplay: String = "ES - MX",
    isProcessing: Boolean = false
) {
    // Premium OLED Black Dark Mode Theme
    val darkSurface = Color(0xFF121212)
    val accentColor = Color(0xFF4285F4)
    val onSurfaceText = Color(0xFFE0E0E0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Half: Target Language (Inverted 180 degrees so the person facing you can read it)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(darkSurface, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.rotate(180f) // Invert for the other person
            ) {
                Text(
                    text = "External Speaker (Target Lang)",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = targetTranscript.ifEmpty { "Waiting for speech..." },
                    color = onSurfaceText,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
                if (isListening) {
                    Spacer(modifier = Modifier.height(32.dp))
                    VoiceWaveform(accentColor = Color(0xFFEA4335))
                }
            }
        }

        // Center Divider / Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Processing indicator
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .size(24.dp),
                    color = accentColor,
                    strokeWidth = 2.dp
                )
            }

            FloatingActionButton(
                onClick = onToggleListen,
                containerColor = if (isListening) accentColor else Color.DarkGray,
                contentColor = Color.White,
                shape = RoundedCornerShape(50)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Toggle Listen"
                )
            }
            
            // Language Picker Trigger
            TextButton(
                onClick = onOpenLanguagePicker,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Text(targetLangDisplay, color = Color.White)
            }
        }

        // Bottom Half: Earbud User (English)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(darkSurface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Earbud User (English)",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = earbudTranscript.ifEmpty { "Waiting for speech..." },
                    color = onSurfaceText,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp
                )
                if (isListening) {
                    Spacer(modifier = Modifier.height(32.dp))
                    VoiceWaveform(accentColor = accentColor)
                }
            }
        }
    }
}

@Composable
fun VoiceWaveform(accentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val amplitudes = remember {
        List(5) { Random.nextInt(400, 800) }
    }
    val animatedAmplitudes = amplitudes.mapIndexed { index, duration ->
        infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "amp_$index"
        )
    }

    Canvas(modifier = Modifier.size(100.dp, 60.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = 8.dp.toPx()
        val spacing = 16.dp.toPx()
        val startX = (width - (5 * barWidth + 4 * spacing)) / 2

        animatedAmplitudes.forEachIndexed { index, anim ->
            val x = startX + index * (barWidth + spacing)
            val barHeight = anim.value
            drawLine(
                color = accentColor,
                start = Offset(x, height / 2 - barHeight / 2),
                end = Offset(x, height / 2 + barHeight / 2),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageVoicePickerSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    onLanguageSelected: (LanguageConfig) -> Unit
) {
    // Functional search state (fixed: was previously hardcoded to empty string)
    var searchQuery by remember { mutableStateOf("") }

    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            AVAILABLE_LANGUAGES
        } else {
            AVAILABLE_LANGUAGES.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Language") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF4285F4),
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn {
                items(filteredLanguages) { lang ->
                    LanguageListItem(
                        language = lang,
                        onVoiceSelected = { voiceProfile ->
                            onLanguageSelected(
                                LanguageConfig(
                                    code = lang.code,
                                    displayName = "${lang.flag} ${lang.name}",
                                    voiceProfile = voiceProfile
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LanguageListItem(
    language: LanguageOption,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "${language.flag} ${language.name}",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "▲" else "▼",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 32.dp)) {
                language.voiceProfiles.forEach { profile ->
                    Text(
                        text = "🎙 $profile",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVoiceSelected(profile) }
                            .padding(vertical = 8.dp, horizontal = 8.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
    }
}
