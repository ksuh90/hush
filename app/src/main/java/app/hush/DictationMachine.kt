package app.hush

sealed interface State {
    data object Idle : State
    data class Recording(val session: Int) : State
    data class Transcribing(val session: Int) : State
}

sealed interface Event {
    data class MicTap(val modelReady: Boolean) : Event
    data object Cancel : Event
    data object Confirm : Event
    data class Transcribed(val session: Int, val text: String) : Event
    data class Failed(val session: Int, val notice: Notice) : Event
}

sealed interface Effect {
    data class StartRecording(val session: Int) : Effect
    data object DiscardRecording : Effect
    data class FinishRecording(val session: Int) : Effect
    data class Insert(val text: String) : Effect
    data class Notify(val notice: Notice) : Effect
}

enum class Notice { NO_MODEL, NO_SPEECH, MIC_FAILED, MIC_SILENT, ENGINE_FAILED }

class DictationMachine {
    var state: State = State.Idle
        private set
    private var lastSession = 0

    fun on(event: Event): List<Effect> {
        val current = state
        return when (current) {
            State.Idle -> when (event) {
                is Event.MicTap ->
                    if (!event.modelReady) {
                        listOf(Effect.Notify(Notice.NO_MODEL))
                    } else {
                        val session = ++lastSession
                        state = State.Recording(session)
                        listOf(Effect.StartRecording(session))
                    }
                else -> emptyList()
            }

            is State.Recording -> when (event) {
                Event.Cancel -> {
                    state = State.Idle
                    listOf(Effect.DiscardRecording)
                }
                Event.Confirm, is Event.MicTap -> {
                    state = State.Transcribing(current.session)
                    listOf(Effect.FinishRecording(current.session))
                }
                is Event.Failed -> if (event.session != current.session) emptyList() else {
                    state = State.Idle
                    listOf(Effect.DiscardRecording, Effect.Notify(event.notice))
                }
                else -> emptyList()
            }

            is State.Transcribing -> when (event) {
                Event.Cancel -> {
                    state = State.Idle
                    emptyList()
                }
                is Event.Transcribed -> if (event.session != current.session) emptyList() else {
                    state = State.Idle
                    val text = event.text.trim()
                    if (text.isEmpty()) listOf(Effect.Notify(Notice.NO_SPEECH)) else listOf(Effect.Insert(text))
                }
                is Event.Failed -> if (event.session != current.session) emptyList() else {
                    state = State.Idle
                    listOf(Effect.Notify(event.notice))
                }
                else -> emptyList()
            }
        }
    }
}
