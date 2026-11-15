package app.hush

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupStepTest {
    @Test
    fun showsTheFirstStepThatIsNotDone() {
        assertEquals(SetupStep.MIC, SetupStep.of(mic = false, model = true, service = true, battery = true))
        assertEquals(SetupStep.MODEL, SetupStep.of(mic = true, model = false, service = false, battery = false))
        assertEquals(SetupStep.SERVICE, SetupStep.of(mic = true, model = true, service = false, battery = false))
        assertEquals(SetupStep.BATTERY, SetupStep.of(mic = true, model = true, service = true, battery = false))
        assertEquals(SetupStep.READY, SetupStep.of(mic = true, model = true, service = true, battery = true))
    }

    @Test
    fun skippedBatteryStepGoesToReady() {
        assertEquals(SetupStep.READY, SetupStep.of(mic = true, model = true, service = true, battery = false, batterySkipped = true))
    }

    @Test
    fun skipDoesNotHideOtherSteps() {
        assertEquals(SetupStep.MIC, SetupStep.of(mic = false, model = true, service = true, battery = false, batterySkipped = true))
        assertEquals(SetupStep.MODEL, SetupStep.of(mic = true, model = false, service = true, battery = false, batterySkipped = true))
        assertEquals(SetupStep.SERVICE, SetupStep.of(mic = true, model = true, service = false, battery = false, batterySkipped = true))
    }

    @Test
    fun stepsAreNumberedOneToFour() {
        assertEquals(listOf(1, 2, 3, 4), SetupStep.entries.filter { it != SetupStep.READY }.map { it.number })
    }
}
