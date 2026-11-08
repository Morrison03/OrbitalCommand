
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.orbitalcommand.model.OrbitalDisplayGeometry
import com.example.orbitalcommand.model.SatelliteState
import com.example.orbitalcommand.model.ServicingStation
import com.example.orbitalcommand.model.ServicingVehiclePhase
import com.example.orbitalcommand.model.ServicingVehicleState
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Displays simulated satellites, an orbital servicing
 * station, and a robotic servicing vehicle.
 *
 * During formation following, the vehicle shares its
 * target satellite's schematic orbital track and
 * maintains a fixed visual separation.
 *
 * The dashed formation indicator is a visual guide,
 * not a physical connection between spacecraft.
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

        // Draw the fixed background starfield.
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
        drawCircle(
            color = stationColor.copy(alpha = 0.55f),
            radius =
                scale * servicingStation.orbitRadiusFraction,
            center = origin,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw the independent satellite orbital tracks.
        satellites.forEachIndexed { index, satellite ->

            val orbitColor = satelliteColors[
                index % satelliteColors.size
            ]

            drawCircle(
                color = orbitColor.copy(alpha = 0.35f),
                radius =
                    scale * satellite.definition.orbitRadiusFraction,
                center = origin,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // Draw a stylized Earth using a radial gradient.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbitalColors.EarthBlue,
                    OrbitalColors.EarthDark
                ),
                center = Offset(
                    origin.x - earthRadius * 0.3f,
                    origin.y - earthRadius * 0.3f
                ),
                radius = earthRadius * 1.8f
            ),
            radius = earthRadius,
            center = origin
        )

        // Draw a simplified land feature.
        drawCircle(
            color = OrbitalColors.EarthLand.copy(alpha = 0.7f),
            radius = earthRadius * 0.38f,
            center = Offset(
                origin.x - earthRadius * 0.24f,
                origin.y - earthRadius * 0.25f
            )
        )

        // Draw Earth's atmospheric outline.
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.6f),
            radius = earthRadius,
            center = origin,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw the monitored satellites at their
        // last successfully received positions.
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
                angleDegrees =
                    satellite.telemetry.orbitAngleDegrees,
                radiusFraction =
                    satellite.definition.orbitRadiusFraction
            )

            val x = origin.x + orbital.x * scale
            val y = origin.y + orbital.y * scale

            val point = Offset(x, y)

            if (selected) {

                drawCircle(
                    color = color.copy(alpha = 0.17f),
                    radius = 23.dp.toPx(),
                    center = point
                )

                drawCircle(
                    color = Color.White.copy(alpha = 0.85f),
                    radius = 18.dp.toPx(),
                    center = point,
                    style = Stroke(width = 1.dp.toPx())
                )

                // Display the selected satellite's
                // recent orbital trail.
                for (i in 1..10) {

                    val trail = OrbitalDisplayGeometry.onOrbit(
                        angleDegrees =
                            satellite.telemetry.orbitAngleDegrees -
                                    i * 4.5f,
                        radiusFraction =
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

            // Draw the satellite's solar panels.
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

            // Draw the satellite body.
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

        // Draw the servicing station at its own
        // independently calculated orbital position.
        val station = OrbitalDisplayGeometry.onOrbit(
            angleDegrees = servicingStation.orbitAngleDegrees,
            radiusFraction = servicingStation.orbitRadiusFraction
        )

        val stationPoint = Offset(
            origin.x + station.x * scale,
            origin.y + station.y * scale
        )

        val sx = stationPoint.x
        val sy = stationPoint.y

        drawCircle(
            color = stationColor.copy(alpha = 0.16f),
            radius = 21.dp.toPx(),
            center = stationPoint
        )

        drawCircle(
            color = stationColor,
            radius = 18.dp.toPx(),
            center = stationPoint,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw the station's solar arrays.
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

        // Draw the central station module.
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

        // Display SERVICER-01 after deployment.
        if (
            servicingVehicle.phase !=
            ServicingVehiclePhase.DOCKED
        ) {

            val vx = origin.x +
                    servicingVehicle.position.x * scale

            val vy = origin.y +
                    servicingVehicle.position.y * scale

            val vehiclePoint = Offset(vx, vy)

            // Show a schematic formation relationship
            // only while tracking current target telemetry.
            if (
                servicingVehicle.phase ==
                ServicingVehiclePhase.FORMATION_FOLLOWING
            ) {

                val target = satellites.firstOrNull {
                    it.definition.id ==
                            servicingVehicle.targetSatelliteId
                }

                if (
                    target != null &&
                    target.isConnected
                ) {

                    val targetPosition =
                        OrbitalDisplayGeometry.onOrbit(
                            angleDegrees =
                                target.telemetry.orbitAngleDegrees,
                            radiusFraction =
                                target.definition.orbitRadiusFraction
                        )

                    val targetPoint = Offset(
                        origin.x + targetPosition.x * scale,
                        origin.y + targetPosition.y * scale
                    )

                    drawLine(
                        color = vehicleColor.copy(alpha = 0.85f),
                        start = vehiclePoint,
                        end = targetPoint,
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(
                                4.dp.toPx(),
                                4.dp.toPx()
                            )
                        )
                    )
                }
            }

            // Highlight the robot's current position.
            drawCircle(
                color = vehicleColor.copy(alpha = 0.17f),
                radius = 17.dp.toPx(),
                center = vehiclePoint
            )

            drawCircle(
                color = if (
                    servicingVehicle.phase ==
                    ServicingVehiclePhase.FORMATION_FOLLOWING
                ) {
                    stationColor
                } else {
                    vehicleColor
                },
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
