package com.example.smartstock.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.smartstock.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit,
    welcomeName: String? = null
) {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(24f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                logoAlpha.animateTo(
                    targetValue   = 1f,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
                )
            }
            launch {
                logoScale.animateTo(
                    targetValue   = 1f,
                    animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)
                )
            }
        }

        delay(180)

        coroutineScope {
            launch {
                titleAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }
            launch {
                titleOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                )
            }
        }

        delay(220)

        taglineAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        )

        delay(700)
        onNavigateNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .alpha(logoAlpha.value)
                    .scale(logoScale.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.smartstock_logo),
                    contentDescription = "SmartStock logo",
                    modifier = Modifier.size(140.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text  = "SmartStock+",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier
                    .alpha(titleAlpha.value)
                    .offset { IntOffset(0, titleOffsetY.value.toInt()) }
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (welcomeName.isNullOrBlank()) {
                    stringResource(id = R.string.app_tagline)
                } else {
                    "Welcome back, $welcomeName"
                },
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.alpha(taglineAlpha.value)
            )
        }
    }
}
