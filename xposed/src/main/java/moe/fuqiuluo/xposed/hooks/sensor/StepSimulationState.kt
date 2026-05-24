package moe.fuqiuluo.xposed.hooks.sensor

import kotlin.math.floor
import kotlin.math.max

class StepSimulationState(
    private val initialStepCount: Float = 0f
) {
    private var lastTimestampNanos = 0L
    private var fractionalSteps = 0.0
    private var totalSteps = floor(initialStepCount.toDouble())
    private var pendingDetectorSteps = 0

    fun nextCounterValue(
        timestampNanos: Long,
        speedMetersPerSecond: Double,
        stepLengthMeters: Double,
        manualStepFrequencySpm: Double,
        mode: StepCadenceMode,
        enabled: Boolean
    ): Float {
        accumulate(timestampNanos, speedMetersPerSecond, stepLengthMeters, manualStepFrequencySpm, mode, enabled)
        return totalSteps.toFloat()
    }

    fun nextDetectorSteps(
        timestampNanos: Long,
        speedMetersPerSecond: Double,
        stepLengthMeters: Double,
        manualStepFrequencySpm: Double,
        mode: StepCadenceMode,
        enabled: Boolean
    ): Int {
        if (pendingDetectorSteps > 0) {
            pendingDetectorSteps -= 1
            return 1
        }
        val steps = accumulate(timestampNanos, speedMetersPerSecond, stepLengthMeters, manualStepFrequencySpm, mode, enabled)
        if (steps <= 0) {
            return 0
        }
        pendingDetectorSteps = steps - 1
        return 1
    }

    private fun accumulate(
        timestampNanos: Long,
        speedMetersPerSecond: Double,
        stepLengthMeters: Double,
        manualStepFrequencySpm: Double,
        mode: StepCadenceMode,
        enabled: Boolean
    ): Int {
        val normalizedTimestamp = if (timestampNanos > 0L) timestampNanos else System.nanoTime()
        val previousTimestamp = lastTimestampNanos
        lastTimestampNanos = max(lastTimestampNanos + 1L, normalizedTimestamp)

        if (!enabled || previousTimestamp <= 0L) {
            return 0
        }

        val elapsedSeconds = (lastTimestampNanos - previousTimestamp) / 1_000_000_000.0
        if (elapsedSeconds <= 0.0) {
            return 0
        }

        val stepsPerMinute = when (mode) {
            StepCadenceMode.AUTO -> {
                if (stepLengthMeters <= 0.0 || speedMetersPerSecond <= 0.0) {
                    0.0
                } else {
                    speedMetersPerSecond * 60.0 / stepLengthMeters
                }
            }
            StepCadenceMode.MANUAL -> manualStepFrequencySpm
        }.coerceIn(0.0, 240.0)

        fractionalSteps += stepsPerMinute / 60.0 * elapsedSeconds
        val wholeSteps = floor(fractionalSteps).toInt()
        if (wholeSteps > 0) {
            fractionalSteps -= wholeSteps
            totalSteps += wholeSteps
        }
        return wholeSteps
    }
}
