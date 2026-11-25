package edu.purdue.cs536.morse

/** Centre-ROI brightness detector with the specified two-stage calibration. */
class LightSignalDetector {
    enum class Stage { IDLE, OFF, ON, READY, FIXED }

    var stage = Stage.IDLE
        private set
    var threshold = 120.0
        private set
    var offLevel: Double? = null
        private set
    var onLevel: Double? = null
        private set
    var warning: String? = null
        private set

    private var stageStartedMs = 0L
    private var sum = 0.0
    private var samples = 0

    fun begin(nowMs: Long, calibrate: Boolean = true) {
        warning = null
        sum = 0.0
        samples = 0
        stageStartedMs = nowMs
        stage = if (calibrate) Stage.OFF else Stage.FIXED
        if (!calibrate) threshold = 120.0
    }

    fun update(brightness: Double, nowMs: Long): Boolean {
        when (stage) {
            Stage.IDLE -> begin(nowMs)
            Stage.OFF, Stage.ON -> {
                sum += brightness
                samples++
                if (nowMs - stageStartedMs >= 2_000L) advanceCalibration(nowMs)
                return false
            }
            Stage.READY, Stage.FIXED -> return brightness > threshold
        }
        return false
    }

    private fun advanceCalibration(nowMs: Long) {
        val level = if (samples == 0) 0.0 else sum / samples
        if (stage == Stage.OFF) {
            offLevel = level
            stage = Stage.ON
        } else {
            val off = offLevel ?: level
            if (kotlin.math.abs(level - off) >= 20.0) {
                onLevel = level
                threshold = (off + level) / 2.0
                warning = null
            } else {
                warning = "Calibration too close — move the light closer / darken the room"
            }
            stage = Stage.READY
        }
        sum = 0.0
        samples = 0
        stageStartedMs = nowMs
    }

    fun prompt(): String = when (stage) {
        Stage.OFF -> "Calibrating light… keep light OFF"
        Stage.ON -> "Now turn light ON for 2 seconds"
        Stage.FIXED -> "Fixed threshold 120"
        else -> warning.orEmpty()
    }
}
