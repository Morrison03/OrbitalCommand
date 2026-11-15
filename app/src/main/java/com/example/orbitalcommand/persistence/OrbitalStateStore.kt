package com.example.orbitalcommand.persistence

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.example.orbitalcommand.model.OrbitalUiState
import com.example.orbitalcommand.model.SatelliteCatalog
import com.example.orbitalcommand.model.ServicerAttachment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * A consistent checkpoint of both mission operations and physical rail attachment.
 *
 * Telemetry, work orders, fault status, servicing progress, selected satellite,
 * and mission event history are contained in [uiState]. Rail attachment is held
 * separately by the app's attachment registry and must be saved alongside it.
 */
data class OrbitalSavedState(
    val schemaVersion: Int = OrbitalStateStore.SCHEMA_VERSION,
    val uiState: OrbitalUiState,
    val attachment: ServicerAttachment?,
    /** The wall-clock instant when this exact checkpoint was written. */
    val savedAtEpochMillis: Long = 0L,
    /** Full-restart-safe pause state; older snapshots read this as false. */
    val isSimulationPaused: Boolean = false
)

/**
 * Saves complete Orbital Command simulation checkpoints in app-private storage.
 *
 * Unlike ViewModel state or rememberSaveable, the checkpoint remains available
 * after the user swipes the app away or the OS terminates its process. AtomicFile
 * protects the previous good checkpoint if the process dies during a write.
 *
 * Reads and writes are intentionally synchronous so callers can restore the
 * checkpoint before starting the one-second simulation loop. Ongoing writes
 * must be dispatched to Dispatchers.IO by the ViewModel.
 */
class OrbitalStateStore(context: Context) {

    companion object {
        const val SCHEMA_VERSION = 1
        private const val TAG = "OrbitalStateStore"
        private const val FILE_NAME = "orbital_mission_state_v1.json"
        private const val MAX_SAVED_BYTES = 20L * 1024L * 1024L
        private const val PAUSE_PREFS = "orbital_simulation_pause"
        private const val KEY_PAUSED = "paused"
        private const val KEY_LAST_RESUME_EPOCH = "last_resumed_at_epoch_millis"
    }

    private val file = File(context.applicationContext.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(file)
    private val gson: Gson = GsonBuilder().serializeNulls().create()
    private val lock = Any()

    /**
     * A small synchronous preference records Pause/Resume immediately.
     * This remains authoritative if Android kills the app before its next
     * larger JSON checkpoint is finished writing.
     */
    private val pausePreferences = context.applicationContext.getSharedPreferences(
        PAUSE_PREFS,
        Context.MODE_PRIVATE
    )

    fun isSimulationPaused(): Boolean =
        pausePreferences.getBoolean(KEY_PAUSED, false)

    /**
     * Remember the operator's choice before returning to the screen.
     * On Resume, anchor the next offline calculation to *now*, so time
     * spent paused can never be included by a stale JSON checkpoint.
     */
    fun persistSimulationPaused(paused: Boolean): Boolean = synchronized(lock) {
        val editor = pausePreferences.edit().putBoolean(KEY_PAUSED, paused)
        if (!paused) {
            editor.putLong(KEY_LAST_RESUME_EPOCH, System.currentTimeMillis())
        }
        val saved = editor.commit()
        if (!saved) {
            Log.e(TAG, "Could not persist simulation Pause/Resume setting")
        }
        saved
    }

    /**
     * Loads a saved checkpoint or returns null when no usable save exists.
     * A corrupt or incompatible checkpoint is retained for diagnosis rather
     * than being deleted by this method.
     */
    fun load(): OrbitalSavedState? = synchronized(lock) {
        if (!file.exists() && !File("${file.path}.bak").exists()) {
            return@synchronized null
        }

        try {
            if (file.length() > MAX_SAVED_BYTES) {
                Log.w(TAG, "Saved mission exceeds the safe size limit")
                return@synchronized null
            }

            val json = atomicFile.openRead().use { stream ->
                String(stream.readBytes(), StandardCharsets.UTF_8)
            }
            val restored = gson.fromJson(json, OrbitalSavedState::class.java)
                ?: return@synchronized null

            if (restored.schemaVersion != SCHEMA_VERSION) {
                Log.w(TAG, "Unsupported saved mission version: ${restored.schemaVersion}")
                return@synchronized null
            }

            val state = restored.uiState
            val expectedIds = SatelliteCatalog.satellites.map { it.id }.toSet()
            val restoredIds = state.satellites.map { it.definition.id }.toSet()

            if (restoredIds != expectedIds ||
                state.selectedSatelliteId !in expectedIds ||
                state.elapsedSeconds < 0
            ) {
                Log.w(TAG, "Saved mission failed satellite/state validation")
                return@synchronized null
            }

            val attachment = restored.attachment
            if (attachment != null &&
                (attachment.satelliteId !in expectedIds ||
                    attachment.workOrderId != attachment.docking.missionOrderId)
            ) {
                Log.w(TAG, "Saved rail attachment failed validation")
                return@synchronized null
            }

            // The old persistence package deliberately froze mission time at
            // shutdown. Reconstruct the real elapsed interval at relaunch.
            // Older v1 checkpoints do not contain a timestamp, so the actual
            // atomic file modification time is the migration fallback.
            // The synchronous pause preference wins over any JSON write that
            // was still in flight when the process was closed.
            val paused = pausePreferences.getBoolean(
                KEY_PAUSED,
                restored.isSimulationPaused
            )
            val checkpointTime = restored.savedAtEpochMillis
                .takeIf { it > 0L }
                ?: file.lastModified().takeIf { it > 0L }

            // A Resume starts a new live interval. Never count a previously
            // paused interval even if the larger JSON snapshot is older.
            val resumedAt = pausePreferences.getLong(
                KEY_LAST_RESUME_EPOCH,
                0L
            )
            val liveAnchor = maxOf(checkpointTime ?: 0L, resumedAt)
            val closedSeconds = if (paused || liveAnchor <= 0L) {
                0L
            } else {
                ((System.currentTimeMillis() - liveAnchor)
                    .coerceAtLeast(0L) / 1000L)
            }

            // OrbitalUiState uses Int seconds. Clamp without overflow on long
            // gaps and ignore negative gaps from wall-clock adjustments.
            val currentElapsed = state.elapsedSeconds.toLong()
            val updatedElapsed = (currentElapsed + closedSeconds)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()

            if (closedSeconds > 0L) {
                Log.i(TAG, "Advanced mission clock by $closedSeconds seconds since checkpoint")
            }

            restored.copy(
                uiState = state.copy(elapsedSeconds = updatedElapsed),
                isSimulationPaused = paused
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to restore saved mission", error)
            null
        }
    }

    /**
     * Atomically writes the newest immutable state snapshot.
     * Exceptions are contained so a temporary disk error never crashes
     * mission control or interrupts the simulator.
     */
    fun save(snapshot: OrbitalSavedState) = synchronized(lock) {
        var stream: java.io.FileOutputStream? = null
        try {
            // Stamp the saved state only when writing. The age of this
            // checkpoint is calculated from this instant after process death.
            val stamped = snapshot.copy(
                savedAtEpochMillis = System.currentTimeMillis()
            )
            val bytes = gson.toJson(stamped).toByteArray(StandardCharsets.UTF_8)
            if (bytes.size > MAX_SAVED_BYTES) {
                Log.w(TAG, "Mission checkpoint exceeds the safe size limit")
                return@synchronized
            }

            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
            stream = null
        } catch (error: Exception) {
            stream?.let { atomicFile.failWrite(it) }
            Log.e(TAG, "Unable to save mission checkpoint", error)
        }
    }
}
