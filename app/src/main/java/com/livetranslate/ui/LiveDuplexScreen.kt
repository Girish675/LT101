package com.livetranslate.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import kotlinx.coroutines.delay
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDuplexScreen(
    earbudTranscript: String,
    targetTranscript: String,
    isListening: Boolean,
    onToggleListen: () -> Unit,
    onOpenLanguagePicker: () -> Unit
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
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
            ) {
                Text("ES - MX", color = Color.White)
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
    val amplitudes = List(5) {
        infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = 60f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Random.nextInt(400, 800), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "amp_$it"
        )
    }

    Canvas(modifier = Modifier.size(100.dp, 60.dp)) {
        val width = size.width
        val height = size.height
        val barWidth = 8.dp.toPx()
        val spacing = 16.dp.toPx()
        val startX = (width - (5 * barWidth + 4 * spacing)) / 2

        amplitudes.forEachIndexed { index, anim ->
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
    sheetState: SheetState
) {
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
                value = "",
                onValueChange = {},
                placeholder = { Text("Search Language") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4285F4),
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn {
                val languages = listOf("🇪🇸 Spanish (Mexico)", "🇫🇷 French (France)", "🇩🇪 German (Germany)", "🇯🇵 Japanese")
                items(languages) { lang ->
                    LanguageListItem(language = lang)
                }
            }
        }
    }
}

@Composable
fun LanguageListItem(language: String) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(text = language, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text(text = "▼", color = Color.Gray, fontSize = 12.sp)
        }
        
        if (expanded) {
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text("🎙 Female (Medium)", color = Color.LightGray, modifier = Modifier.padding(8.dp))
                Text("🎙 Male (Medium)", color = Color.LightGray, modifier = Modifier.padding(8.dp))
            }
        }
        Divider(color = Color.DarkGray)
    }
}
