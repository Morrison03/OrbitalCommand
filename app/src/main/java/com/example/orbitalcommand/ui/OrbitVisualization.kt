
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.orbitalcommand.ui.theme.OrbitalColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a stylized top-down visualization of a satellite
 * traveling around Earth.
 *
 * The orbital geometry is illustrative and not to scale.
 * Satellite position is supplied by the simulation.
 */
@Composable
fun OrbitVisualization(angleDegrees: Float) {

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                OrbitalColors.PanelRaised,
                RoundedCornerShape(14.dp)
            )
    ) {

        val orbitCenter = center
        val orbitRadius = size.minDimension * 0.37f
        val earthRadius = size.minDimension * 0.18f

        // Draw a fixed starfield.
        val stars = listOf(
            Offset(0.07f, 0.12f),
            Offset(0.21f, 0.19f),
            Offset(0.38f, 0.08f),
            Offset(0.81f, 0.14f),
            Offset(0.93f, 0.33f),
            Offset(0.13f, 0.72f),
            Offset(0.29f, 0.89f),
            Offset(0.71f, 0.83f),
            Offset(0.88f, 0.91f),
            Offset(0.54f, 0.16f),
            Offset(0.08f, 0.43f),
            Offset(0.96f, 0.67f)
        )

        stars.forEach { star ->

            drawCircle(
                color = Color.White.copy(alpha = 0.5f),
                radius = 1.2.dp.toPx(),
                center = Offset(
                    star.x * size.width,
                    star.y * size.height
                )
            )
        }

        // Draw positional reference lines.
        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(orbitCenter.x, 0f),
            end = Offset(orbitCenter.x, size.height),
            strokeWidth = 1.dp.toPx()
        )

        drawLine(
            color = Color.White.copy(alpha = 0.07f),
            start = Offset(0f, orbitCenter.y),
            end = Offset(size.width, orbitCenter.y),
            strokeWidth = 1.dp.toPx()
        )

        // Draw the orbit.
        drawCircle(
            color = OrbitalColors.Accent.copy(alpha = 0.5f),
            radius = orbitRadius,
            center = orbitCenter,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw a trail behind the satellite.
        for (index in 1..10) {

            val trailAngle = Math.toRadians(
                (angleDegrees - index * 4.5f).toDouble()
            )

            val trailPosition = Offset(
                orbitCenter.x +
                        orbitRadius * cos(trailAngle).toFloat(),
                orbitCenter.y +
                        orbitRadius * sin(trailAngle).toFloat()
            )

            drawCircle(
                color = OrbitalColors.Accent.copy(
                    alpha = (11 - index) / 18f
                ),
                radius = 2.5.dp.toPx(),
                center = trailPosition
            )
        }

        // Draw Earth's ocean and shadow gradient.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    OrbitalColors.EarthBlue,
                    OrbitalColors.EarthDark
                ),
                center = Offset(
                    orbitCenter.x - earthRadius * 0.35f,
                    orbitCenter.y - earthRadius * 0.35f
                ),
                radius = earthRadius * 1.8f
            ),
            radius = earthRadius,
            center = orbitCenter
        )

        // Draw a decorative landmass.
        val landmass = Path().apply {

            moveTo(
                orbitCenter.x - earthRadius * 0.65f,
                orbitCenter.y - earthRadius * 0.35f
            )

            lineTo(
                orbitCenter.x - earthRadius * 0.12f,
                orbitCenter.y - earthRadius * 0.65f
            )

            lineTo(
                orbitCenter.x + earthRadius * 0.35f,
                orbitCenter.y - earthRadius * 0.2f
            )

            lineTo(
                orbitCenter.x + earthRadius * 0.13f,
                orbitCenter.y + earthRadius * 0.12f
            )

            lineTo(
                orbitCenter.x + earthRadius * 0.45f,
                orbitCenter.y + earthRadius * 0.55f
            )

            lineTo(
                orbitCenter.x - earthRadius * 0.12f,
                orbitCenter.y + earthRadius * 0.72f
            )

            lineTo(
                orbitCenter.x - earthRadius * 0.55f,
                orbitCenter.y + earthRadius * 0.15f
            )

            close()
        }

        drawPath(
            path = landmass,
            color = OrbitalColors.EarthLand.copy(alpha = 0.75f)
        )

        // Draw Earth's atmospheric boundary.
        drawCircle(
            color = Color(0xFF7DD3FC).copy(alpha = 0.65f),
            radius = earthRadius,
            center = orbitCenter,
            style = Stroke(width = 2.dp.toPx())
        )

        // Calculate satellite position.
        val radians = Math.toRadians(
            angleDegrees.toDouble()
        )

        val satelliteX = orbitCenter.x +
                orbitRadius * cos(radians).toFloat()

        val satelliteY = orbitCenter.y +
                orbitRadius * sin(radians).toFloat()

        val satellitePosition = Offset(
            satelliteX,
            satelliteY
        )

        // Draw a glow around the satellite.
        drawCircle(
            color = OrbitalColors.Accent.copy(alpha = 0.18f),
            radius = 21.dp.toPx(),
            center = satellitePosition
        )

        // Draw the left solar panel.
        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                satelliteX - 21.dp.toPx(),
                satelliteY - 6.dp.toPx()
            ),
            size = Size(
                13.dp.toPx(),
                12.dp.toPx()
            )
        )

        // Draw the right solar panel.
        drawRect(
            color = OrbitalColors.Accent,
            topLeft = Offset(
                satelliteX + 8.dp.toPx(),
                satelliteY - 6.dp.toPx()
            ),
            size = Size(
                13.dp.toPx(),
                12.dp.toPx()
            )
        )

        // Draw the satellite's central body.
        drawRect(
            color = Color.White,
            topLeft = Offset(
                satelliteX - 7.dp.toPx(),
                satelliteY - 8.dp.toPx()
            ),
            size = Size(
                14.dp.toPx(),
                16.dp.toPx()
            )
        )

        // Draw the central satellite sensor detail.
        drawCircle(
            color = OrbitalColors.EarthDark,
            radius = 3.dp.toPx(),
            center = satellitePosition
        )
    }
}

