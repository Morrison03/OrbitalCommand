package com.example.orbitalcommand.model

/**
 * Converts a physical servicer-facing direction into the operator's
 * procedural Canvas camera convention.
 *
 * SceneView's POV camera orbits the satellite using a real camera
 * position: positive yaw places it on the +X/starboard side.
 *
 * Satellite3DGeometry.project() instead rotates the satellite model
 * around a stationary observer, so positive procedural yaw exposes
 * the -X/port side. Negating yaw makes both views face the same side.
 *
 * Pitch uses the existing convention in both renderers and is not
 * changed here. Docking, navigation, and robot attachment state are
 * not affected by this conversion.
 */
object SatelliteViewAlignment {

    /** Returns the procedural yaw that matches a POV-selected face. */
    fun operatorYawFor(face: SatelliteServiceFace): Float = -face.viewYaw

    /**
     * Creates a procedural camera that initially shows the same
     * physical satellite face as the onboard POV camera.
     */
    fun operatorCameraFor(
        face: SatelliteServiceFace,
        zoom: Float = 1f
    ): SatelliteCamera = SatelliteCamera(
        yawDegrees = operatorYawFor(face),
        pitchDegrees = face.viewPitch,
        zoom = zoom
    )
}
