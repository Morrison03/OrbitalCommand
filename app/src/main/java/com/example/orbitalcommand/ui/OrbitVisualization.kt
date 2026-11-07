package com.example.orbitalcommand.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.ui.theme.OrbitalColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays a schematic orbital view of the monitored fleet.
 *
 * Each spacecraft is rendered on an independent circular
 * track with a distinct color. The selected spacecraft
 * receives a larger marker and an orbital trail.
 *
 * Orbital distances and motion are illustrative.
 */
@Composable
fun OrbitVisualization(
    satellites: List<SatelliteState>,
    selectedSatelliteId: String
) {

    val satelliteColors = listOf(
        OrbitalColors.Accent,
        Color(0xFFFBBF24),
        Color(0xFFA78BFA)
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                OrbitalColors.PanelRaised,
                RoundedCornerShape(14.dp)
            )
    ) {

        val orbitCenter = center
        val earthRadius = size.minDimension * 0.15f

        // Render a fixed background starfield.
        val stars = listOf(
            Offset(0.06f, 0.12f),
            Offset(0.17f, 0.30f),
            Offset(0.31f, 0.09f),
            Offset(0.47f, 0.20f),
            Offset(0.74f, 0.12f),
            Offset(0.91f, 0.29f),
            Offset(0.11f, 0.77f),
            Offset(0.28f, 0.93f),
            Offset(0.66f, 0.87f),
            Offset(0.89f, 0.79f),
            Offset(0.95f, 0.53f)
        )

        stars.forEach { star ->

            drawCircle(
                color = Color.White.copy(alpha = 0.45f),
                radius = 1.dp.toPx(),
                center = Offset(
                    star.x * size.width,
                    star.y * size.height
                )
            )
        }

        // Draw Earth using a simple radial gradient.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbitalColors.EarthBlue,
                    OrbitalColors.EarthDark
                ),
                center = Offset(
                    orbitCenter.x - earthRadius * 0.3f,
                    orbitCenter.y - earthRadius * 0.3f
                ),
                radius = earthRadius * 1.8f
            ),
            radius = earthRadius,
            center = orbitCenter
        )

        // Draw a stylized land feature on Earth.
        drawCircle(
            color = OrbitalColors.EarthLand.copy(alpha = 0.7f),
            radius = earthRadius * 0.38f,
            center = Offset(
                orbitCenter.x - earthRadius * 0.24f,
                orbitCenter.y - earthRadius * 0.25f
            )
        )

        // Draw Earth's atmospheric outline.
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.6f),
            radius = earthRadius,
            center = orbitCenter,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw the orbital rings.
        satellites.forEachIndexed { index, satellite ->

            val color = satelliteColors[
                index % satelliteColors.size
            ]

            val orbitRadius =
                size.minDimension *
                        satellite.definition.orbitRadiusFraction

            drawCircle(
                color = color.copy(alpha = 0.30f),
                radius = orbitRadius,
                center = orbitCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw satellite markers and selection highlights.
        satellites.forEachIndexed { index, satellite ->

            val selected =
                satellite.definition.id == selectedSatelliteId

            val baseColor = satelliteColors[
                index % satelliteColors.size
            ]

            val color = if (satellite.hasFault) {
                OrbitalColors.Warning
            } else {
                baseColor
            }

            val orbitRadius =
                size.minDimension *
                        satellite.definition.orbitRadiusFraction

            val angleRadians = Math.toRadians(
                satellite.telemetry.orbitAngleDegrees.toDouble()
            )

            val satelliteX = orbitCenter.x +
                    orbitRadius * cos(angleRadians).toFloat()

            val satelliteY = orbitCenter.y +
                    orbitRadius * sin(angleRadians).toFloat()

            val satellitePosition = Offset(
                satelliteX,
                satelliteY
            )

            // Highlight the selected spacecraft's position.
            if (selected) {

                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = 23.dp.toPx(),
                    center = satellitePosition
                )

                // Display recent orbital positions.
                for (trailIndex in 1..10) {

                    val trailRadians = Math.toRadians(
                        (
                                satellite.telemetry.orbitAngleDegrees -
                                        trailIndex * 4.5f
                                ).toDouble()
                    )

                    val trailPosition = Offset(
                        orbitCenter.x +
                                orbitRadius *
                                cos(trailRadians).toFloat(),
                        orbitCenter.y +
                                orbitRadius *
                                sin(trailRadians).toFloat()
                    )

                    drawCircle(
                        color = color.copy(
                            alpha = (11 - trailIndex) / 20f
                        ),
                        radius = 2.dp.toPx(),
                        center = trailPosition
                    )
                }
            }

            // Render two solar panels.
            drawRect(
                color = color,
                topLeft = Offset(
                    satelliteX - 16.dp.toPx(),
                    satelliteY - 4.dp.toPx()
                ),
                size = Size(
                    9.dp.toPx(),
                    8.dp.toPx()
                )
            )

            drawRect(
                color = color,
                topLeft = Offset(
                    satelliteX + 7.dp.toPx(),
                    satelliteY - 4.dp.toPx()
                ),
                size = Size(
                    9.dp.toPx(),
                    8.dp.toPx()
                )
            )

            // Render the central satellite body.
            drawRect(
                color = Color.White,
                topLeft = Offset(
                    satelliteX - 6.dp.toPx(),
                    satelliteY - 7.dp.toPx()
                ),
                size = Size(
                    12.dp.toPx(),
                    14.dp.toPx()
                )
            )

            // Render an additional selection outline.
            if (selected) {

                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 18.dp.toPx(),
                    center = satellitePosition,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}
