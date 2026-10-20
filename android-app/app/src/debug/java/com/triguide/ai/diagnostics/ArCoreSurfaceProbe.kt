package com.triguide.ai.diagnostics

import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

/** Primitive observations only: orientation is not wall/floor semantics or clearance. */
internal data class SurfaceProbeObservation(val ray: SurfaceProbeRay, val reading: SurfaceProbeReading)

/** Called on the existing ARCore GL thread, with the setDisplayGeometry viewport. */
internal object ArCoreSurfaceProbe {
    /** Current SDK Y offsets, not a ground reference or physical floor classification. */
    fun heights(session: Session, frame: Frame): SurfaceHeightSnapshot {
        val tracking = frame.camera.trackingState == TrackingState.TRACKING
        if (!tracking) return SurfaceHeightProfile.summarize(emptyList(), false, Double.NaN)
        val cameraY = frame.camera.pose.ty().toDouble()
        if (!cameraY.isFinite()) return SurfaceHeightProfile.summarize(emptyList(), true, cameraY)
        val candidates = session.getAllTrackables(Plane::class.java).asSequence().map { plane ->
            val kind = kindOf(plane.type)
            val planeTracking = plane.trackingState == TrackingState.TRACKING
            val subsumed = plane.subsumedBy != null
            SurfaceHeightCandidate(
                kind, if (kind == SurfaceKind.HORIZONTAL_UPWARD && planeTracking && !subsumed) {
                    plane.centerPose.ty().toDouble()
                } else Double.NaN,
                planeTracking, subsumed,
            )
        }.asIterable()
        return SurfaceHeightProfile.summarize(candidates, true, cameraY)
    }

    /**
     * Session-known inventory, NOT current visibility or a current ray intersection.
     * Native getAllTrackables enumeration is SDK-owned; primitive mapping consumes
     * at most MAX_PLANES entries and retains no Session/Plane/Frame references.
     */
    fun inventory(session: Session, frame: Frame): SurfaceInventorySnapshot {
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            return SurfaceInventoryPolicy.summarize(emptyList(), cameraTracking = false)
        }
        val planes = session.getAllTrackables(Plane::class.java)
        val candidates = planes.asSequence().map { plane ->
            SurfacePlaneCandidate(
                kind = kindOf(plane.type),
                tracking = when (plane.trackingState) {
                    TrackingState.TRACKING -> SurfacePlaneTracking.TRACKING
                    TrackingState.PAUSED -> SurfacePlaneTracking.PAUSED
                    else -> SurfacePlaneTracking.STOPPED
                },
                subsumed = plane.subsumedBy != null,
            )
        }.asIterable()
        return SurfaceInventoryPolicy.summarize(candidates, cameraTracking = true)
    }

    private fun kindOf(type: Plane.Type): SurfaceKind = when (type) {
        Plane.Type.VERTICAL -> SurfaceKind.VERTICAL_SURFACE
        Plane.Type.HORIZONTAL_UPWARD_FACING -> SurfaceKind.HORIZONTAL_UPWARD
        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> SurfaceKind.HORIZONTAL_DOWNWARD
        else -> SurfaceKind.UNKNOWN
    }

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
                            kind = kindOf(plane.type),
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
