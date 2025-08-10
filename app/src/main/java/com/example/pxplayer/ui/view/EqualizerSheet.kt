package com.example.pxplayer.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pxplayer.data.EqSettings

@Composable
fun EqualizerSheet(
    eqSettings: EqSettings,
    onEnabledChange: (Boolean) -> Unit,
    onBandLevelChange: (Short, Short) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer", style = MaterialTheme.typography.titleLarge)
            Switch(checked = eqSettings.enabled, onCheckedChange = onEnabledChange)
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (eqSettings.bands.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(eqSettings.bands) { bandLevel ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(60.dp)
                    ) {
                        Text(
                            text = "${bandLevel.level / 100} dB",
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                        Slider(
                            value = bandLevel.level.toFloat(),
                            // --- FIX APPLIED HERE ---
                            onValueChange = { onBandLevelChange(bandLevel.band, it.toInt().toShort()) },
                            valueRange = bandLevel.minLevel.toFloat()..bandLevel.maxLevel.toFloat(),
                            modifier = Modifier
                                .height(150.dp)
                                .width(20.dp),
                            enabled = eqSettings.enabled
                        )
                        Text(
                            text = formatFrequency(bandLevel.centerFreq),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Text("Equalizer not available on this device.")
        }
    }
}

private fun formatFrequency(milliHertz: Int): String {
    val hertz = milliHertz / 1000
    return if (hertz >= 1000) {
        "${hertz / 1000}k"
    } else {
        "$hertz"
    }
}
