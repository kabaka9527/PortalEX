package moe.fuqiuluo.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseLocationHookSpeedTest {
    @Test
    fun zeroSpeedAmplitudeKeepsStableSpeed() {
        repeat(100) {
            val speed = calculateInjectedSpeed(speed = 1.5, speedAmplitude = 0.0)

            assertEquals(1.5f, speed)
        }
    }

    @Test
    fun negativeSpeedAmplitudeIsTreatedAsDisabledJitter() {
        repeat(100) {
            val speed = calculateInjectedSpeed(speed = 1.5, speedAmplitude = -0.5)

            assertEquals(1.5f, speed)
        }
    }

    @Test
    fun positiveSpeedAmplitudeKeepsSpeedNonNegativeAndWithinJitterRange() {
        repeat(1_000) {
            val speed = calculateInjectedSpeed(speed = 1.5, speedAmplitude = 0.5)

            assertTrue("speed should be at least fallback minimum", speed >= 0.1f)
            assertTrue("speed should stay within configured positive jitter range", speed >= 1.0f && speed < 2.0f)
        }
    }

    @Test
    fun positiveSpeedAmplitudeClampsBelowMinimumSpeed() {
        repeat(1_000) {
            val speed = calculateInjectedSpeed(speed = 0.05, speedAmplitude = 1.0)

            assertTrue("speed should never be negative", speed >= 0.1f)
            assertTrue("speed should not exceed base speed plus amplitude", speed < 1.05f)
        }
    }
}
