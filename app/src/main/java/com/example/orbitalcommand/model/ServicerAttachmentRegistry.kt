package com.example.orbitalcommand.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Active physical rail attachment to a maintenance target.
 *
 * This is NOT docking at station OS-001. A confirmed gripper
 * remains attached even when the target's downlink is lost.
 */
data class ServicerAttachment(
    val workOrderId: Long,
    val satelliteId: String,
    val docking: ServicerDockingState
) {
    val isRailGripped: Boolean
        get() = docking.grippingRail
}

/**
 * Single application-process source of truth for rail attachment.
 *
 * The repair UI publishes docking transitions here. Navigation
 * and the orbital map observe this exact record. Communication
 * loss must not release it; only explicit return/cancellation,
 * arrival at station, a new assignment, or reset may do so.
 *
 * State is in-memory, so process-death persistence is not yet
 * provided. A future version can place this state in a saved
 * mission repository/ViewModel.
 */
object ServicerAttachmentRegistry {

    private val _current = MutableStateFlow<ServicerAttachment?>(null)

    val current: StateFlow<ServicerAttachment?> =
        _current.asStateFlow()

    /** Establishes a mission record without clearing an existing grip. */
    @Synchronized
    fun prepareMission(workOrderId: Long, satelliteId: String) {
        val previous = _current.value
        if (
            previous?.workOrderId == workOrderId &&
            previous.satelliteId == satelliteId
        ) return

        _current.value = ServicerAttachment(
            workOrderId = workOrderId,
            satelliteId = satelliteId,
            docking = ServicerDockingState(missionOrderId = workOrderId)
        )
    }

    /** Records only transitions for the current mission. */
    @Synchronized
    fun update(docking: ServicerDockingState) {
        val previous = _current.value ?: return
        if (previous.workOrderId != docking.missionOrderId) return
        _current.value = previous.copy(docking = docking)
    }

    /** Detaches the robot when an explicit return is commanded. */
    @Synchronized
    fun release(workOrderId: Long) {
        val previous = _current.value ?: return
        if (previous.workOrderId != workOrderId) return
        _current.value = previous.copy(
            docking = ServicerDockingState(missionOrderId = workOrderId)
        )
    }

    /** Clears mission attachment state after closure or reset. */
    @Synchronized
    fun clear() {
        _current.value = null
    }

    /** True only for a physically gripped target satellite. */
    fun isAttachedTo(satelliteId: String): Boolean {
        val record = current.value
        return record?.satelliteId == satelliteId && record.isRailGripped
    }
}
