package com.triguide.ai.diagnostics

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState

/** Primitive observations only: orientation is not wall/floor semantics or clearance. */
internal data class SurfaceProbeObservation(val ray: SurfaceProbeRay, val reading: SurfaceProbeReading)

/** Called on the existing ARCore GL thread, with the setDisplayGeometry viewport. */
internal object ArCoreSurfaceProbe {
    fun sample(frame: Frame, width: Int, height: Int): List<SurfaceProbeObservation> {
        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        return SurfaceProbeLayout.points(width, height).map { ray ->
            if (!tracking) {
                SurfaceProbeObservation(ray, SurfaceProbePolicy.select(emptyList(), tracking = false))
            } else {
                val hits = frame.hitTest(ray.xPx, ray.yPx)
                val reading = if (hits.size > MAX_HITS_PER_RAY) {
                    SurfaceProbeReading(SurfaceKind.UNKNOWN, null, "too_many_hits")
                } else {
                    val candidates = hits.mapNotNull { hit ->
                        val plane = hit.trackable as? Plane ?: return@mapNotNull null
                        val planeTracking = plane.trackingState == TrackingState.TRACKING
                        val subsumed = plane.subsumedBy != null
                        SurfaceHitCandidate(
                            kind = when (plane.type) {
                                Plane.Type.VERTICAL -> SurfaceKind.VERTICAL_SURFACE
                                Plane.Type.HORIZONTAL_UPWARD_FACING -> SurfaceKind.HORIZONTAL_UPWARD
                                Plane.Type.HORIZONTAL_DOWNWARD_FACING -> SurfaceKind.HORIZONTAL_DOWNWARD
                                else -> SurfaceKind.UNKNOWN
                            },
                            distanceMeters = hit.distance,
                            tracking = planeTracking,
                            subsumed = subsumed,
                            // SDK hitTest gives geometric leeway; an unbounded plane hit
                            // must not be treated as an actual observed polygon hit.
                            insidePolygon = planeTracking && !subsumed && plane.isPoseInPolygon(hit.hitPose),
                        )
                    }
                    SurfaceProbePolicy.select(candidates, tracking = true)
                }
                SurfaceProbeObservation(ray, reading)
            }
        }
    }

    private const val MAX_HITS_PER_RAY = 64
}
