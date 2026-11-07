
package com.example.orbitalcommand.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.PowerMode
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.SolarExposure
import com.example.orbitalcommand.ui.theme.OrbitalColors
import java.util.Locale

/**
 * Displays the simulated spacecraft electrical power
 * system and its current operating state.
 *
 * When communications are unavailable, internal
 * simulation values are explicitly distinguished
 * from received ground telemetry.
 */
@Composable
fun SatellitePowerPanel(
    satellite: SatelliteState
) {

    val onboard = satellite.onboardTelemetry
    val received = satellite.telemetry

    val modeColor = when (onboard.powerMode) {

        PowerMode.NORMAL ->
            OrbitalColors.Healthy

        PowerMode.LOW_POWER ->
            Color(0xFFFBBF24)

        PowerMode.SAFE_MODE ->
            Color(0xFFFB923C)

        PowerMode.OFFLINE ->
            OrbitalColors.Warning
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = OrbitalColors.Panel
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                text = "ELECTRICAL POWER SYSTEM",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = satellite.definition.id,
                color = OrbitalColors.Muted,
                fontSize = 12.sp
            )

            if (!satellite.isConnected) {

                Text(
                    text = "SIMULATOR MODEL — " +
                            "NOT LIVE RECEIVED TELEMETRY",
                    color = OrbitalColors.Warning,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Last received battery: " +
                            String.format(
                                Locale.US,
                                "%.1f%%",
                                received.batteryPercent
                            ),
                    color = OrbitalColors.Muted,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = if (satellite.isConnected)
                        "Battery charge"
                    else
                        "Modeled battery charge",
                    color = Color.White,
                    fontSize = 13.sp
                )

                Text(
                    text = String.format(
                        Locale.US,
                        "%.1f%%",
                        onboard.batteryPercent
                    ),
                    color = modeColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }

            LinearProgressIndicator(
                progress = {
                    (onboard.batteryPercent / 100.0)
                        .toFloat()
                        .coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
                color = modeColor,
                trackColor = OrbitalColors.PanelRaised
            )

            PowerDetailRow(
                label = "Operating mode",
                value = onboard.powerMode.name
                    .replace('_', ' '),
                valueColor = modeColor
            )

            PowerDetailRow(
                label = "Solar exposure",
                value = if (
                    onboard.solarExposure ==
                    SolarExposure.SUNLIGHT
                ) {
                    "SUNLIGHT"
                } else {
                    "ECLIPSE"
                },
                valueColor = if (
                    onboard.solarExposure ==
                    SolarExposure.SUNLIGHT
                ) {
                    Color(0xFFFBBF24)
                } else {
                    OrbitalColors.Muted
                }
            )

            PowerDetailRow(
                label = "Solar generation",
                value = String.format(
                    Locale.US,
                    "%.0f W",
                    onboard.solarGenerationWatts
                )
            )

            PowerDetailRow(
                label = "Electrical consumption",
                value = String.format(
                    Locale.US,
                    "%.0f W",
                    onboard.powerConsumptionWatts
                )
            )

            val powerBalance =
                onboard.solarGenerationWatts -
                        onboard.powerConsumptionWatts

            PowerDetailRow(
                label = "Net electrical power",
                value = String.format(
                    Locale.US,
                    "%+.0f W",
                    powerBalance
                ),
                valueColor = if (powerBalance >= 0.0)
                    OrbitalColors.Healthy
                else
                    OrbitalColors.Warning
            )

            if (onboard.powerMode == PowerMode.OFFLINE) {

                Text(
                    text = "POWER OFFLINE — " +
                            "The simulator is awaiting sufficient " +
                            "battery recovery to restart telemetry.",
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp
                )

            } else if (
                onboard.powerMode == PowerMode.SAFE_MODE
            ) {

                Text(
                    text = "SAFE MODE — Nonessential simulated " +
                            "systems are disabled.",
                    color = Color(0xFFFB923C),
                    fontSize = 12.sp
                )

            } else if (
                onboard.powerMode == PowerMode.LOW_POWER
            ) {

                Text(
                    text = "LOW POWER — Electrical consumption " +
                            "has been reduced.",
                    color = Color(0xFFFBBF24),
                    fontSize = 12.sp
                )
            }

            Text(
                text = "Accelerated power simulation. " +
                        "Values are not spacecraft engineering specifications.",
                color = OrbitalColors.Muted,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Displays a labeled electrical power measurement.
 */
@Composable
private fun PowerDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            text = label,
            color = OrbitalColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Spacer(
            modifier = Modifier.width(8.dp)
        )

        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

