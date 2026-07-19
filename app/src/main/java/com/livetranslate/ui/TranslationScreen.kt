package com.livetranslate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mode 1: Standard Translation Screen.
 * Design §5: "TranslationScreen: Text input, push-to-talk button, scrollable chat history."
 *
 * Supports both text-input translation and push-to-talk speech translation.
 */
@Composable
fun TranslationScreen(
    inputText: String,
    outputText: String,
    isListening: Boolean,
    isProcessing: Boolean,
    sourceLang: String,
    targetLangDisplay: String,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onToggleListen: () -> Unit,
    onOpenLanguagePicker: () -> Unit
) {
    val darkSurface = Color(0xFF121212)
    val cardColor = Color(0xFF1E1E1E)
    val accentColor = Color(0xFF4285F4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Language Selector Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sourceLang.uppercase(),
                color = Color.White,
                fontSize = 16.sp
            )
            IconButton(onClick = { /* Swap languages */ }) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = "Swap Languages",
                    tint = accentColor
                )
            }
            TextButton(onClick = onOpenLanguagePicker) {
                Text(text = targetLangDisplay, color = Color.White, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Card
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = { Text("Enter text or tap mic to speak…", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = cardColor,
                unfocusedContainerColor = cardColor
            ),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Push-to-talk button
            FloatingActionButton(
                onClick = onToggleListen,
                containerColor = if (isListening) Color(0xFFEA4335) else Color.DarkGray,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                    contentDescription = "Push to Talk"
                )
            }

            // Translate text button
            FloatingActionButton(
                onClick = onTranslate,
                containerColor = accentColor,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Translate"
                )
            }
        }

        // Listening waveform indicator
        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveform(accentColor = Color(0xFFEA4335))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Output Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(cardColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = accentColor
                )
            } else {
                Text(
                    text = outputText.ifEmpty { "Translation will appear here…" },
                    color = if (outputText.isEmpty()) Color.Gray else Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Start,
                    lineHeight = 28.sp
                )
            }
        }
    }
}
