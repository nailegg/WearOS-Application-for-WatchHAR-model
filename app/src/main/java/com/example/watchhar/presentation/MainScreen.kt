package com.example.watchhar.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleButton
import androidx.wear.compose.material.ToggleButtonDefaults
import com.example.watchhar.util.ActivityLabels

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val isDetected by viewModel.isEventDetected.collectAsState(initial = false)
    val eventDetectorEnabled by viewModel.eventDetectorEnabled.collectAsState(initial = true)
    val classificationResult by viewModel.classificationResult.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isDetected) "Event Detected!" else "Listening...",
                color = if (isDetected) Color.Green else Color.White
            )
            classificationResult?.let { result ->
                Text(
                    text = if (isDetected) {
                        val label = ActivityLabels.labelFor(result.classCount, result.classIndex)
                        "Class: $label  (${"%.2f".format(result.score)})"
                    } else "",
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            ToggleButton(
                checked = eventDetectorEnabled,
                onCheckedChange = viewModel::setEventDetectorEnabled,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(ToggleButtonDefaults.SmallToggleButtonSize)
            ) {
                Text(text = if (eventDetectorEnabled) "ON" else "OFF")
            }
        }
    }
}
