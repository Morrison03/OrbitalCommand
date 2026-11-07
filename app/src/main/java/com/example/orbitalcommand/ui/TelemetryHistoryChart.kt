
package com.example.orbitalcommand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.orbitalcommand.model.SatelliteTelemetry
import com.example.orbitalcommand.ui.theme.OrbitalColors
import java.util.Locale
import kotlin.math.max

/**
 * Defines the measurements available in the
 * historical telemetry visualization.
 */
private enum class TelemetryMetric(
    val label: String,
    val unit: String,
    val color: Color
) {

    TEMPERATURE(
        "Temperature",
        "°C",
        OrbitalColors.Accent
    ),

    BATTERY(
        "Battery",
        "%",
        OrbitalColors.Healthy
    ),

    ALTITUDE(
        "Altitude",
        "km",
        Color(0xFFA78BFA)
    ),

    VELOCITY(
        "Velocity",
        "km/s",
        Color(0xFFFBBF24)
    );

    /**
     * Extracts this metric's value from a telemetry packet.
     */
    fun reading(telemetry: SatelliteTelemetry): Double {

        return when (this) {

            TEMPERATURE -> telemetry.temperatureC

            BATTERY -> telemetry.batteryPercent

            ALTITUDE -> telemetry.altitudeKm

            VELOCITY -> telemetry.velocityKmS
        }
    }

    /**
     * Formats a measurement with an appropriate
     * decimal precision and engineering unit.
     */
    fun format(value: Double): String {

        val pattern = if (this == VELOCITY) {
            "%.3f"
        } else {
            "%.1f"
        }

        val formattedValue = String.format(
            Locale.US,
            pattern,
            value
        )

        return "$formattedValue $unit"
    }
}

/**
 * Displays historical telemetry as an interactive line chart.
 *
 * Operators can select a measurement using filter chips.
 * The chart updates whenever the supplied history changes.
 *
 * Missing telemetry intervals are represented by breaks
 * in the plotted line rather than interpolated values.
 */
@Composable
fun TelemetryHistoryChart(
    history: List<SatelliteTelemetry>,
    isLinkLost: Boolean
) {

    var selectedMetric by rememberSaveable {
        mutableStateOf(TelemetryMetric.TEMPERATURE)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Measurement selection controls.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            TelemetryMetric.entries.forEach { metric ->

                FilterChip(
                    selected = selectedMetric == metric,
                    onClick = {
                        selectedMetric = metric
                    },
                    label = {
                        Text(metric.label)
                    }
                )
            }
        }

        if (history.isEmpty()) {

            Text(
                text = "Waiting for telemetry...",
                color = OrbitalColors.Muted,
                fontSize = 13.sp
            )

        } else {

            val latestTelemetry = history.last()

            val latestReading =
                selectedMetric.reading(latestTelemetry)

            // Display the most recent received measurement.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp)
                ) {

                    Text(
                        text = "LAST RECEIVED",
                        color = OrbitalColors.Muted,
                        fontSize = 11.sp
                    )

                    Text(
                        text = selectedMetric.format(latestReading),
                        color = selectedMetric.color,
                        fontSize = 22.sp
                    )
                }

                Text(
                    text = "${history.size} samples",
                    color = OrbitalColors.Muted,
                    fontSize = 11.sp
                )
            }

            // Calculate vertical bounds for the selected metric.
            val values = history.map {
                selectedMetric.reading(it)
            }

            val minimumValue = values.minOrNull() ?: 0.0
            val maximumValue = values.maxOrNull() ?: 0.0

            val minimumPadding =
                if (selectedMetric == TelemetryMetric.VELOCITY) {
                    0.005
                } else {
                    0.25
                }

            val verticalPadding = max(
                (maximumValue - minimumValue) * 0.15,
                minimumPadding
            )

            val lowerBound = minimumValue - verticalPadding
            val upperBound = maximumValue + verticalPadding

            // Draw the value scale and plotted measurements.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .height(190.dp),
                    verticalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        text = selectedMetric.format(upperBound),
                        color = OrbitalColors.Muted,
                        fontSize = 10.sp
                    )

                    Text(
                        text = selectedMetric.format(
                            (lowerBound + upperBound) / 2.0
                        ),
                        color = OrbitalColors.Muted,
                        fontSize = 10.sp
                    )

                    Text(
                        text = selectedMetric.format(lowerBound),
                        color = OrbitalColors.Muted,
                        fontSize = 10.sp
                    )
                }

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(190.dp)
                        .background(
                            OrbitalColors.PanelRaised,
                            RoundedCornerShape(8.dp)
                        )
                ) {

                    val left = 8.dp.toPx()
                    val right = size.width - 8.dp.toPx()

                    val top = 8.dp.toPx()
                    val bottom = size.height - 8.dp.toPx()

                    val plotWidth = right - left
                    val plotHeight = bottom - top

                    val firstSecond =
                        history.first().sampleSecond

                    val lastSecond =
                        history.last().sampleSecond

                    val timeSpan = (
                            lastSecond.toLong() -
                                    firstSecond.toLong()
                            ).coerceAtLeast(1L).toFloat()

                    val valueRange =
                        (upperBound - lowerBound).toFloat()

                    /**
                     * Converts a telemetry measurement into
                     * coordinates within the plotting area.
                     */
                    fun positionOf(
                        telemetry: SatelliteTelemetry
                    ): Offset {

                        val elapsed = (
                                telemetry.sampleSecond.toLong() -
                                        firstSecond.toLong()
                                ).toFloat()

                        val x = left +
                                (elapsed / timeSpan) * plotWidth

                        val normalizedValue = (
                                (selectedMetric.reading(telemetry) -
                                        lowerBound) / valueRange
                                ).toFloat()

                        val y = bottom -
                                normalizedValue * plotHeight

                        return Offset(x, y)
                    }

                    // Horizontal reference lines.
                    for (index in 0..4) {

                        val y = top +
                                (plotHeight / 4f) * index

                        drawLine(
                            color = Color.White.copy(
                                alpha = 0.09f
                            ),
                            start = Offset(left, y),
                            end = Offset(right, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Vertical time reference lines.
                    for (index in 0..4) {

                        val x = left +
                                (plotWidth / 4f) * index

                        drawLine(
                            color = Color.White.copy(
                                alpha = 0.05f
                            ),
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Connect only consecutive received seconds.
                    for (index in 1 until history.size) {

                        val previous = history[index - 1]
                        val current = history[index]

                        val previousPosition =
                            positionOf(previous)

                        val currentPosition =
                            positionOf(current)

                        val elapsedBetweenSamples =
                            current.sampleSecond.toLong() -
                                    previous.sampleSecond.toLong()

                        if (elapsedBetweenSamples == 1L) {

                            drawLine(
                                color = selectedMetric.color,
                                start = previousPosition,
                                end = currentPosition,
                                strokeWidth = 2.5.dp.toPx()
                            )

                        } else {

                            // Mark the start of a missing-data interval.
                            drawLine(
                                color = OrbitalColors.Warning.copy(
                                    alpha = 0.35f
                                ),
                                start = Offset(
                                    currentPosition.x,
                                    top
                                ),
                                end = Offset(
                                    currentPosition.x,
                                    bottom
                                ),
                                strokeWidth = 1.dp.toPx()
                            )

                            drawCircle(
                                color = selectedMetric.color,
                                radius = 3.dp.toPx(),
                                center = previousPosition
                            )
                        }
                    }

                    // Highlight the most recently received sample.
                    drawCircle(
                        color = selectedMetric.color,
                        radius = 5.dp.toPx(),
                        center = positionOf(history.last())
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = positionOf(history.last())
                    )
                }
            }

            // Time scale of the received measurements.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "T+${history.first().sampleSecond}s",
                    color = OrbitalColors.Muted,
                    fontSize = 11.sp
                )

                Text(
                    text = "T+${history.last().sampleSecond}s",
                    color = OrbitalColors.Muted,
                    fontSize = 11.sp
                )
            }

            if (isLinkLost) {

                Text(
                    text = "COMMUNICATION LOST — " +
                            "Historical measurements are not updating.",
                    color = OrbitalColors.Warning,
                    fontSize = 12.sp
                )

            } else {

                Text(
                    text = "Receiving simulated telemetry " +
                            "once per second.",
                    color = OrbitalColors.Healthy,
                    fontSize = 12.sp
                )
            }
        }
    }
}

