package app.hush

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationMachineTest {
    private val machine = DictationMachine()

    private fun record(): Int {
        machine.on(Event.MicTap(modelReady = true))
        return (machine.state as State.Recording).session
    }

    @Test
    fun micTapWithoutModelStaysIdle() {
        assertEquals(listOf(Effect.Notify(Notice.NO_MODEL)), machine.on(Event.MicTap(modelReady = false)))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun micTapStartsRecording() {
        assertEquals(listOf(Effect.StartRecording(1)), machine.on(Event.MicTap(modelReady = true)))
        assertEquals(State.Recording(1), machine.state)
    }

    @Test
    fun cancelWhileRecordingDiscards() {
        record()
        assertEquals(listOf(Effect.DiscardRecording), machine.on(Event.Cancel))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun confirmTranscribesAndInserts() {
        val session = record()
        assertEquals(listOf(Effect.FinishRecording(session)), machine.on(Event.Confirm))
        assertEquals(State.Transcribing(session), machine.state)
        assertEquals(listOf(Effect.Insert("hello world")), machine.on(Event.Transcribed(session, "  hello world ")))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun secondMicTapActsAsConfirm() {
        val session = record()
        assertEquals(listOf(Effect.FinishRecording(session)), machine.on(Event.MicTap(modelReady = true)))
    }

    @Test
    fun blankTranscriptNotifiesNoSpeech() {
        val session = record()
        machine.on(Event.Confirm)
        assertEquals(listOf(Effect.Notify(Notice.NO_SPEECH)), machine.on(Event.Transcribed(session, " ")))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun cancelWhileTranscribingDropsTheResult() {
        val session = record()
        machine.on(Event.Confirm)
        assertEquals(emptyList<Effect>(), machine.on(Event.Cancel))
        assertEquals(State.Idle, machine.state)
        assertEquals(emptyList<Effect>(), machine.on(Event.Transcribed(session, "late text")))
    }

    @Test
    fun resultOfAnOldSessionDoesNotInsert() {
        val first = record()
        machine.on(Event.Confirm)
        machine.on(Event.Cancel)
        val second = record()
        machine.on(Event.Confirm)
        assertEquals(emptyList<Effect>(), machine.on(Event.Transcribed(first, "old")))
        assertEquals(State.Transcribing(second), machine.state)
        assertEquals(listOf(Effect.Insert("new")), machine.on(Event.Transcribed(second, "new")))
    }

    @Test
    fun recordFailureDiscardsAndNotifies() {
        val session = record()
        assertEquals(
            listOf(Effect.DiscardRecording, Effect.Notify(Notice.MIC_FAILED)),
            machine.on(Event.Failed(session, Notice.MIC_FAILED)),
        )
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun failureOfAnOldSessionIsIgnored() {
        val first = record()
        machine.on(Event.Cancel)
        record()
        assertEquals(emptyList<Effect>(), machine.on(Event.Failed(first, Notice.MIC_FAILED)))
    }

    @Test
    fun engineFailureNotifies() {
        val session = record()
        machine.on(Event.Confirm)
        assertEquals(listOf(Effect.Notify(Notice.ENGINE_FAILED)), machine.on(Event.Failed(session, Notice.ENGINE_FAILED)))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun buttonsDoNothingWhenIdle() {
        assertEquals(emptyList<Effect>(), machine.on(Event.Cancel))
        assertEquals(emptyList<Effect>(), machine.on(Event.Confirm))
        assertEquals(State.Idle, machine.state)
    }

    @Test
    fun confirmWhileTranscribingDoesNothing() {
        val session = record()
        machine.on(Event.Confirm)
        assertEquals(emptyList<Effect>(), machine.on(Event.Confirm))
        assertEquals(State.Transcribing(session), machine.state)
    }
}
