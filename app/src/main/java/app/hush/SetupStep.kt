package app.hush

/** The setup steps in order. The setup screen shows the first step that is not done, or the ready screen. */
enum class SetupStep(val number: Int) {
    MIC(1),
    MODEL(2),
    SERVICE(3),
    BATTERY(4),
    READY(0),
    ;

    companion object {
        fun of(mic: Boolean, model: Boolean, service: Boolean, battery: Boolean) = when {
            !mic -> MIC
            !model -> MODEL
            !service -> SERVICE
            !battery -> BATTERY
            else -> READY
        }
    }
}
