
package com.example.orbitalcommand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orbital Command
 *
 * A Jetpack Compose satellite telemetry simulator.
 * All measurements are simulated locally.
 */

// Application colors
private val Background = Color(0xFF0B1220)
private val Panel = Color(0xFF192436)
private val Accent = Color(0xFF38BDF8)
private val Healthy = Color(0xFF4ADE80)
private val Warning = Color(0xFFF87171)
private val Muted = Color(0xFF94A3B8)

/**
 * Represents the most recently received satellite telemetry.
 */
data class SatelliteTelemetry(
    val sampleSecond: Int,
    val altitudeKm: Double,
    val velocityKmS: Double,
    val batteryPercent: Double,
    val temperatureC: Double,
    val orbitAngleDegrees: Float
)

/**
 * Generates deterministic simulated satellite telemetry.
 *
 * This is an educational model, not a physical orbit simulator.
 * One visual revolution takes 90 simulated seconds.
 */
object SatelliteSimulator {

    fun snapshot(second: Int): SatelliteTelemetry {
        return SatelliteTelemetry(
            sampleSecond = second,
            altitudeKm = 525.0 + 1.8 * sin(second / 15.0),
            velocityKmS = 7.61 + 0.02 * cos(second / 15.0),
            batteryPercent =
                (87.0 - second * 0.015).coerceAtLeast(0.0),
            temperatureC = 21.0 + 3.5 * sin(second / 9.0),
            orbitAngleDegrees = (second * 4 % 360).toFloat()
        )
    }
}

/**
 * Main Android activity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Background,
                    surface = Panel
                )
            ) {
                OrbitalDashboard()
            }
        }
    }
}

/**
 * Main mission-control dashboard.
 *
 * A coroutine advances simulation time once per second.
 * During an outage, received telemetry is frozen.
 */
@Composable
fun OrbitalDashboard() {

    var elapsedSeconds by remember {
        mutableIntStateOf(0)
    }

    var telemetry by remember {
        mutableStateOf(SatelliteSimulator.snapshot(0))
    }

    var linkLost by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)

            elapsedSeconds += 1

            if (!linkLost) {
                telemetry = SatelliteSimulator.snapshot(
                    elapsedSeconds
                )
            }
        }
    }

    val packetAge = elapsedSeconds - telemetry.sampleSecond

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Application heading
        Text(
            text = "ORBITAL COMMAND",
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "SATELLITE OPERATIONS • SIMULATION",
            color = Muted,
            fontSize = 12.sp
        )

        HorizontalDivider(
            color = Color.DarkGray
        )

        // Satellite identity
        Text(
            text = "SAT-001",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = if (linkLost)
                "COMMUNICATION LOST"
            else
                "COMMUNICATION ONLINE",
            color = if (linkLost) Warning else Healthy,
            fontWeight = FontWeight.Bold
        )

        // Orbit graphics
        OrbitVisualization(
            angleDegrees = telemetry.orbitAngleDegrees
        )

        // Current telemetry readings
        Text(
            text = "LIVE TELEMETRY",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        if (linkLost) {
            Text(
                text = "WARNING: Displaying last received telemetry",
                color = Warning,
                fontSize = 12.sp
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                label = "ALTITUDE",
                value = "%.1f km".format(telemetry.altitudeKm),
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                label = "VELOCITY",
                value = "%.2f km/s".format(telemetry.velocityKmS),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                label = "BATTERY",
                value = "%.1f%%".format(telemetry.batteryPercent),
                modifier = Modifier.weight(1f)
            )

            TelemetryCard(
                label = "TEMPERATURE",
                value = "%.1f °C".format(telemetry.temperatureC),
                modifier = Modifier.weight(1f)
            )
        }

        // Subsystem monitoring
        Text(
            text = "SYSTEM HEALTH",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        StatusRow(
            label = "Power subsystem",
            status = "NOMINAL",
            isWarning = false
        )

        StatusRow(
            label = "Thermal subsystem",
            status = "NOMINAL",
            isWarning = false
        )

        StatusRow(
            label = "Communications",
            status = if (linkLost) "OFFLINE" else "ONLINE",
            isWarning = linkLost
        )

        Text(
            text = "Last telemetry received: " +
                    if (packetAge == 0) "Just now" else "$packetAge seconds ago",
            color = if (linkLost) Warning else Muted,
            fontSize = 12.sp
        )

        // Simulation controls
        Text(
            text = "SIMULATION CONTROLS",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = {
                linkLost = !linkLost
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (linkLost)
                    "Restore Connection"
                else
                    "Simulate Communication Failure"
            )
        }

        OutlinedButton(
            onClick = {
                elapsedSeconds = 0
                linkLost = false
                telemetry = SatelliteSimulator.snapshot(0)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Simulation")
        }

        Text(
            text = "Simulation time: $elapsedSeconds seconds",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

/**
 * Draws a simplified orbital view using Compose Canvas.
 */
@Composable
fun OrbitVisualization(angleDegrees: Float) {

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .background(
                Panel,
                RoundedCornerShape(12.dp)
            )
    ) {

        val centerPoint = center
        val orbitRadius = size.minDimension * 0.39f
        val earthRadius = size.minDimension * 0.17f

        // Draw Earth
        drawCircle(
            color = Color(0xFF1D4ED8),
            radius = earthRadius,
            center = centerPoint
        )

        drawCircle(
            color = Color(0xFF60A5FA),
            radius = earthRadius,
            center = centerPoint,
            style = Stroke(width = 3.dp.toPx())
        )

        // Draw satellite orbital path
        drawCircle(
            color = Muted,
            radius = orbitRadius,
            center = centerPoint,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Calculate satellite position
        val radians = Math.toRadians(
            angleDegrees.toDouble()
        )

        val satelliteX = centerPoint.x +
                orbitRadius * cos(radians).toFloat()

        val satelliteY = centerPoint.y +
                orbitRadius * sin(radians).toFloat()

        val satellitePosition = Offset(
            satelliteX,
            satelliteY
        )

        // Draw satellite marker
        drawCircle(
            color = Accent,
            radius = 9.dp.toPx(),
            center = satellitePosition
        )

        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = satellitePosition
        )
    }
}

/**
 * Reusable card for displaying a telemetry measurement.
 */
@Composable
fun TelemetryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Panel
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                color = Muted,
                fontSize = 11.sp
            )

            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Reusable subsystem health indicator.
 */
@Composable
fun StatusRow(
    label: String,
    status: String,
    isWarning: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )

        Text(
            text = status,
            color = if (isWarning) Warning else Healthy,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
