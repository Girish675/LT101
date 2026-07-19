package com.livetranslate.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mode 1: Standard Translation Screen.
 * Design §5: "TranslationScreen: Text input, push-to-talk button, scrollable chat history."
 */
@Composable
fun TranslationScreen(
    inputText: String,
    outputText: String,
    isListening: Boolean,
    isProcessing: Boolean,
    sourceLang: String,
    targetLangDisplay: String,
    lastLatencyMs: Long,
    conversationHistory: List<ConversationEntry>,
    onInputChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onToggleListen: () -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val cardColor = Color(0xFF1E1E1E)
    val accentColor = Color(0xFF4285F4)
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(conversationHistory.size) {
        if (conversationHistory.isNotEmpty()) {
            listState.animateScrollToItem(conversationHistory.size - 1)
        }
    }

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
            Text(text = sourceLang.uppercase(), color = Color.White, fontSize = 16.sp)
            IconButton(onClick = { /* Swap languages */ }) {
                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", tint = accentColor)
            }
            TextButton(onClick = onOpenLanguagePicker) {
                Text(text = targetLangDisplay, color = Color.White, fontSize = 16.sp)
            }
        }

        // Latency indicator
        if (lastLatencyMs > 0) {
            Text(
                text = "⚡ ${lastLatencyMs}ms",
                color = if (lastLatencyMs < 500) Color(0xFF34A853) else Color(0xFFFBBC05),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Conversation History (scrollable chat)
        if (conversationHistory.isNotEmpty()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cardColor, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                items(conversationHistory) { entry ->
                    ConversationBubble(
                        entry = entry,
                        onCopy = onCopy,
                        onShare = onShare
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cardColor, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start a translation to see history here",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Input Area
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter text or tap mic to speak…", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = accentColor,
                unfocusedBorderColor = Color.DarkGray,
                focusedContainerColor = cardColor,
                unfocusedContainerColor = cardColor
            ),
            shape = RoundedCornerShape(16.dp),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // Listening waveform
        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveform(accentColor = Color(0xFFEA4335))
            }
        }
    }
}

@Composable
fun ConversationBubble(
    entry: ConversationEntry,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit
) {
    val accentColor = Color(0xFF4285F4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A2A2A), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        // Source text
        Text(
            text = entry.sourceText,
            color = Color.LightGray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Translated text
        Text(
            text = entry.translatedText,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        // Actions row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ ${entry.latencyMs}ms",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Row {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCopy(entry.translatedText) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onShare(entry.translatedText) }
                )
            }
        }
    }
}
