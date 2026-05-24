package moe.fuqiuluo.xposed.hooks.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class StepSimulationStateTest {
    @Test
    fun autoModeUsesSpeedAndStepLength() {
        val state = StepSimulationState(initialStepCount = 100f)

        state.nextCounterValue(
            timestampNanos = 1_000_000_000L,
            speedMetersPerSecond = 1.4,
            stepLengthMeters = 0.7,
            manualStepFrequencySpm = 0.0,
            mode = StepCadenceMode.AUTO,
            enabled = true
        )
        val value = state.nextCounterValue(
            timestampNanos = 2_000_000_000L,
            speedMetersPerSecond = 1.4,
            stepLengthMeters = 0.7,
            manualStepFrequencySpm = 0.0,
            mode = StepCadenceMode.AUTO,
            enabled = true
        )

        assertEquals(102f, value)
    }

    @Test
    fun manualModeUsesConfiguredStepsPerMinute() {
        val state = StepSimulationState()

        state.nextCounterValue(1_000_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)
        val value = state.nextCounterValue(1_500_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)

        assertEquals(1f, value)
    }

    @Test
    fun disabledAndStoppedDoNotIncreaseSteps() {
        val state = StepSimulationState(initialStepCount = 10f)

        state.nextCounterValue(1_000_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.AUTO, true)
        val stopped = state.nextCounterValue(3_000_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.AUTO, true)
        val disabled = state.nextCounterValue(5_000_000_000L, 1.4, 0.7, 120.0, StepCadenceMode.AUTO, false)

        assertEquals(10f, stopped)
        assertEquals(10f, disabled)
    }

    @Test
    fun timestampRegressionDoesNotBreakAccumulation() {
        val state = StepSimulationState()

        state.nextCounterValue(2_000_000_000L, 1.4, 0.7, 0.0, StepCadenceMode.AUTO, true)
        val value = state.nextCounterValue(1_000_000_000L, 1.4, 0.7, 0.0, StepCadenceMode.AUTO, true)

        assertEquals(0f, value)
    }

    @Test
    fun detectorEmitsSingleStepEvents() {
        val state = StepSimulationState()

        state.nextDetectorSteps(1_000_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)
        val first = state.nextDetectorSteps(2_500_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)
        val second = state.nextDetectorSteps(2_600_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)
        val third = state.nextDetectorSteps(2_700_000_000L, 0.0, 0.7, 120.0, StepCadenceMode.MANUAL, true)

        assertEquals(1, first)
        assertEquals(1, second)
        assertEquals(1, third)
    }
}
