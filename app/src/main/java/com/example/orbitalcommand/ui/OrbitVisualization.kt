
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
import com.example.orbitalcommand.model.ServicingStation
import com.example.orbitalcommand.ui.theme.OrbitalColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays a schematic orbital view of the satellite
 * fleet and an independently moving servicing station.
 *
 * Each satellite follows its assigned visual track.
 * The station uses its own orbital phase and angular rate.
 *
 * Orbital motion is accelerated and not physically to scale.
 */
@Composable
fun OrbitVisualization(
    satellites: List<SatelliteState>,
    selectedSatelliteId: String,
    servicingStation: ServicingStation
) {

    val satelliteColors = listOf(
        OrbitalColors.Accent,
        Color(0xFFFBBF24),
        Color(0xFFA78BFA)
    )

    val stationColor = Color(0xFF4ADE80)

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

        // Draw a deterministic background starfield.
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

        // Draw the servicing station's orbital track.
        val stationRadius =
            size.minDimension *
                    servicingStation.orbitRadiusFraction

        drawCircle(
            color = stationColor.copy(alpha = 0.5f),
            radius = stationRadius,
            center = orbitCenter,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw the separate satellite orbital tracks.
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

        // Render Earth using a radial blue gradient.
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

        // Add a simplified land feature.
        drawCircle(
            color = OrbitalColors.EarthLand.copy(alpha = 0.7f),
            radius = earthRadius * 0.38f,
            center = Offset(
                orbitCenter.x - earthRadius * 0.24f,
                orbitCenter.y - earthRadius * 0.25f
            )
        )

        // Draw an atmospheric outline.
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.6f),
            radius = earthRadius,
            center = orbitCenter,
            style = Stroke(width = 2.dp.toPx())
        )

        // Render all monitored satellites.
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

            val radians = Math.toRadians(
                satellite.telemetry.orbitAngleDegrees.toDouble()
            )

            val x = orbitCenter.x +
                    orbitRadius * cos(radians).toFloat()

            val y = orbitCenter.y +
                    orbitRadius * sin(radians).toFloat()

            val satellitePosition = Offset(x, y)

            if (selected) {

                drawCircle(
                    color = color.copy(alpha = 0.18f),
                    radius = 23.dp.toPx(),
                    center = satellitePosition
                )

                // Indicate the selected satellite's trail.
                for (trailIndex in 1..10) {

                    val trailAngle = Math.toRadians(
                        (
                                satellite.telemetry.orbitAngleDegrees -
                                        trailIndex * 4.5f
                                ).toDouble()
                    )

                    val trailPosition = Offset(
                        orbitCenter.x +
                                orbitRadius *
                                cos(trailAngle).toFloat(),
                        orbitCenter.y +
                                orbitRadius *
                                sin(trailAngle).toFloat()
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

            // Satellite solar panels.
            drawRect(
                color = color,
                topLeft = Offset(
                    x - 16.dp.toPx(),
                    y - 4.dp.toPx()
                ),
                size = Size(
                    9.dp.toPx(),
                    8.dp.toPx()
                )
            )

            drawRect(
                color = color,
                topLeft = Offset(
                    x + 7.dp.toPx(),
                    y - 4.dp.toPx()
                ),
                size = Size(
                    9.dp.toPx(),
                    8.dp.toPx()
                )
            )

            // Main satellite body.
            drawRect(
                color = Color.White,
                topLeft = Offset(
                    x - 6.dp.toPx(),
                    y - 7.dp.toPx()
                ),
                size = Size(
                    12.dp.toPx(),
                    14.dp.toPx()
                )
            )

            if (selected) {

                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 18.dp.toPx(),
                    center = satellitePosition,
                    style = Stroke(width = 1.dp.toPx())
                )
            }
        }

        // Calculate the independent servicing station position.
        val stationRadians = Math.toRadians(
            servicingStation.orbitAngleDegrees.toDouble()
        )

        val stationX = orbitCenter.x +
                stationRadius * cos(stationRadians).toFloat()

        val stationY = orbitCenter.y +
                stationRadius * sin(stationRadians).toFloat()

        val stationPosition = Offset(
            stationX,
            stationY
        )

        // Highlight the servicing station.
        drawCircle(
            color = stationColor.copy(alpha = 0.15f),
            radius = 21.dp.toPx(),
            center = stationPosition
        )

        drawCircle(
            color = stationColor,
            radius = 18.dp.toPx(),
            center = stationPosition,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Station solar arrays.
        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                stationX - 22.dp.toPx(),
                stationY - 6.dp.toPx()
            ),
            size = Size(
                12.dp.toPx(),
                12.dp.toPx()
            )
        )

        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                stationX + 10.dp.toPx(),
                stationY - 6.dp.toPx()
            ),
            size = Size(
                12.dp.toPx(),
                12.dp.toPx()
            )
        )

        // Station's central service module.
        drawRect(
            color = Color.White,
            topLeft = Offset(
                stationX - 9.dp.toPx(),
                stationY - 7.dp.toPx()
            ),
            size = Size(
                18.dp.toPx(),
                14.dp.toPx()
            )
        )

        // Small docking structures distinguish the
        // servicing station from ordinary satellites.
        drawLine(
            color = stationColor,
            start = Offset(
                stationX,
                stationY - 13.dp.toPx()
            ),
            end = Offset(
                stationX,
                stationY - 7.dp.toPx()
            ),
            strokeWidth = 2.dp.toPx()
        )

        drawLine(
            color = stationColor,
            start = Offset(
                stationX,
                stationY + 7.dp.toPx()
            ),
            end = Offset(
                stationX,
                stationY + 13.dp.toPx()
            ),
            strokeWidth = 2.dp.toPx()
        )
    }
}
