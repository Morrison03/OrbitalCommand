
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
import com.example.orbitalcommand.model.OrbitalDisplayGeometry
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.ServicingStation
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.model.ServicingVehicleState
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Draws the satellite fleet, home station, and servicing vehicle.
 *
 * Vehicle coordinates are schematic display units rather
 * than physical distances or validated trajectories.
 */
@Composable
fun OrbitVisualization(
    satellites: List<SatelliteState>,
    selectedSatelliteId: String,
    servicingStation: ServicingStation,
    servicingVehicle: ServicingVehicleState
) {

    val satelliteColors = listOf(
        OrbitalColors.Accent,
        Color(0xFFFBBF24),
        Color(0xFFA78BFA)
    )

    val stationColor = Color(0xFF4ADE80)
    val vehicleColor = Color(0xFF22D3EE)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(
                OrbitalColors.PanelRaised,
                RoundedCornerShape(14.dp)
            )
    ) {

        val origin = center
        val scale = size.minDimension
        val earthRadius = scale * 0.15f

        // Draw a fixed background starfield.
        val stars = listOf(
            Offset(.06f, .12f),
            Offset(.17f, .30f),
            Offset(.31f, .09f),
            Offset(.47f, .20f),
            Offset(.74f, .12f),
            Offset(.91f, .29f),
            Offset(.11f, .77f),
            Offset(.28f, .93f),
            Offset(.66f, .87f),
            Offset(.89f, .79f),
            Offset(.95f, .53f)
        )

        stars.forEach { star ->

            drawCircle(
                color = Color.White.copy(alpha = .45f),
                radius = 1.dp.toPx(),
                center = Offset(
                    star.x * size.width,
                    star.y * size.height
                )
            )
        }

        // Render schematic orbital tracks.
        drawCircle(
            color = stationColor.copy(alpha = .55f),
            radius =
                scale * servicingStation.orbitRadiusFraction,
            center = origin,
            style = Stroke(width = 1.5.dp.toPx())
        )

        satellites.forEachIndexed { index, satellite ->

            drawCircle(
                color = satelliteColors[
                    index % satelliteColors.size
                ].copy(alpha = .35f),
                radius =
                    scale * satellite.definition.orbitRadiusFraction,
                center = origin,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw Earth.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbitalColors.EarthBlue,
                    OrbitalColors.EarthDark
                ),
                center = Offset(
                    origin.x - earthRadius * .3f,
                    origin.y - earthRadius * .3f
                ),
                radius = earthRadius * 1.8f
            ),
            radius = earthRadius,
            center = origin
        )

        drawCircle(
            color = OrbitalColors.EarthLand.copy(alpha = .7f),
            radius = earthRadius * .38f,
            center = Offset(
                origin.x - earthRadius * .24f,
                origin.y - earthRadius * .25f
            )
        )

        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = .6f),
            radius = earthRadius,
            center = origin,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw satellites using their last received positions.
        satellites.forEachIndexed { index, satellite ->

            val selected =
                satellite.definition.id == selectedSatelliteId

            val color = if (
                satellite.hasFault ||
                satellite.isPowerDepleted
            ) {
                OrbitalColors.Warning
            } else {
                satelliteColors[
                    index % satelliteColors.size
                ]
            }

            val orbital = OrbitalDisplayGeometry.onOrbit(
                satellite.telemetry.orbitAngleDegrees,
                satellite.definition.orbitRadiusFraction
            )

            val x = origin.x + orbital.x * scale
            val y = origin.y + orbital.y * scale
            val point = Offset(x, y)

            if (selected) {

                drawCircle(
                    color = color.copy(alpha = .17f),
                    radius = 23.dp.toPx(),
                    center = point
                )

                drawCircle(
                    color = Color.White.copy(alpha = .85f),
                    radius = 18.dp.toPx(),
                    center = point,
                    style = Stroke(width = 1.dp.toPx())
                )

                for (i in 1..10) {

                    val trail = OrbitalDisplayGeometry.onOrbit(
                        satellite.telemetry.orbitAngleDegrees -
                                i * 4.5f,
                        satellite.definition.orbitRadiusFraction
                    )

                    drawCircle(
                        color = color.copy(
                            alpha = (11 - i) / 20f
                        ),
                        radius = 2.dp.toPx(),
                        center = Offset(
                            origin.x + trail.x * scale,
                            origin.y + trail.y * scale
                        )
                    )
                }
            }

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
        }

        // Draw the independent orbital servicing station.
        val station = OrbitalDisplayGeometry.onOrbit(
            servicingStation.orbitAngleDegrees,
            servicingStation.orbitRadiusFraction
        )

        val stationPoint = Offset(
            origin.x + station.x * scale,
            origin.y + station.y * scale
        )

        val sx = stationPoint.x
        val sy = stationPoint.y

        drawCircle(
            color = stationColor.copy(alpha = .16f),
            radius = 21.dp.toPx(),
            center = stationPoint
        )

        drawCircle(
            color = stationColor,
            radius = 18.dp.toPx(),
            center = stationPoint,
            style = Stroke(width = 1.5.dp.toPx())
        )

        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                sx - 22.dp.toPx(),
                sy - 6.dp.toPx()
            ),
            size = Size(
                12.dp.toPx(),
                12.dp.toPx()
            )
        )

        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                sx + 10.dp.toPx(),
                sy - 6.dp.toPx()
            ),
            size = Size(
                12.dp.toPx(),
                12.dp.toPx()
            )
        )

        drawRect(
            color = Color.White,
            topLeft = Offset(
                sx - 9.dp.toPx(),
                sy - 7.dp.toPx()
            ),
            size = Size(
                18.dp.toPx(),
                14.dp.toPx()
            )
        )

        drawLine(
            color = stationColor,
            start = Offset(
                sx,
                sy - 13.dp.toPx()
            ),
            end = Offset(
                sx,
                sy - 7.dp.toPx()
            ),
            strokeWidth = 2.dp.toPx()
        )

        // Display the independent servicing vehicle after deployment.
        if (
            servicingVehicle.phase !=
            ServicingVehiclePhase.DOCKED
        ) {

            val vx = origin.x +
                    servicingVehicle.position.x * scale

            val vy = origin.y +
                    servicingVehicle.position.y * scale

            val vehiclePoint = Offset(vx, vy)

            drawCircle(
                color = vehicleColor.copy(alpha = .17f),
                radius = 17.dp.toPx(),
                center = vehiclePoint
            )

            drawCircle(
                color = vehicleColor,
                radius = 9.dp.toPx(),
                center = vehiclePoint,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = vehiclePoint
            )

            drawLine(
                color = vehicleColor,
                start = Offset(
                    vx - 14.dp.toPx(),
                    vy
                ),
                end = Offset(
                    vx - 6.dp.toPx(),
                    vy
                ),
                strokeWidth = 2.dp.toPx()
            )

            drawLine(
                color = vehicleColor,
                start = Offset(
                    vx + 6.dp.toPx(),
                    vy
                ),
                end = Offset(
                    vx + 14.dp.toPx(),
                    vy
                ),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
