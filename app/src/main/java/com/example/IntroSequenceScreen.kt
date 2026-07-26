package com.example

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntroSequenceScreen(
    accentColor: Color,
    onFinished: () -> Unit
) {
    val neonOrange = Color(0xFFFF6B35)
    val darkBackground = Color(0xFF050508)
    var currentStep by remember { mutableStateOf(0) }
    var isTextVisible by remember { mutableStateOf(true) }
    var isButtonVisible by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (isTextVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600),
        finishedListener = { endedAlpha ->
            if (endedAlpha == 0f) {
                if (currentStep < 2) {
                    currentStep++
                    isTextVisible = true
                } else {
                    onFinished()
                }
            }
        },
        label = "TextAlphaAnimation"
    )

    val buttonAlpha by animateFloatAsState(
        targetValue = if (isButtonVisible && isTextVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "ButtonAlphaAnimation"
    )

    LaunchedEffect(currentStep, isTextVisible) {
        if (isTextVisible) {
            isButtonVisible = false
            kotlinx.coroutines.delay(600)
            isButtonVisible = true
        }
    }

    val messageText = when (currentStep) {
        0 -> stringResource(id = R.string.intro_msg_1)
        1 -> stringResource(id = R.string.intro_msg_2)
        else -> stringResource(id = R.string.intro_msg_3)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .padding(24.dp)
            .testTag("intro_sequence_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Dark Translucent Glassmorphic Message Card
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .alpha(alpha)
                .border(1.dp, neonOrange.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C14).copy(alpha = 0.85f)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 40.dp, horizontal = 24.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = messageText,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = neonOrange,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    modifier = Modifier.testTag("intro_message_text")
                )
            }
        }

        // Bottom Action Button / tap to continue with Neon Orange accent
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .fillMaxWidth()
                .alpha(buttonAlpha)
        ) {
            Button(
                onClick = {
                    if (isTextVisible && isButtonVisible) {
                        isTextVisible = false
                    }
                },
                enabled = isButtonVisible && isTextVisible,
                colors = ButtonDefaults.buttonColors(
                    containerColor = neonOrange,
                    disabledContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("intro_next_button")
            ) {
                Text(
                    text = stringResource(id = R.string.intro_next),
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}
