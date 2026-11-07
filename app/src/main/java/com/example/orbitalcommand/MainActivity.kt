package com.example.orbitalcommand

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.example.orbitalcommand.ui.OrbitalRoute
import com.example.orbitalcommand.ui.theme.OrbitalColors

/**
 * Main entry point for the Orbital Command application.
 *
 * Initializes the Compose interface, configures the
 * application theme, and launches the mission dashboard.
 *
 * Simulation and screen state are managed independently
 * of the Android activity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = OrbitalColors.Accent,
                    background = OrbitalColors.Background,
                    surface = OrbitalColors.Panel
                )
            ) {

                OrbitalRoute()
            }
        }
    }
}
