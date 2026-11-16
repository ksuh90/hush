package app.hush

/**
 * The setup steps in order. The setup screen shows the first step that is not done, or the ready screen.
 * The battery step is a tip: the user can skip it, and then it does not show again.
 */
enum class SetupStep(val number: Int) {
    MIC(1),
    MODEL(2),
    SERVICE(3),
    BATTERY(4),
    READY(0),
    ;

    companion object {
        fun of(mic: Boolean, model: Boolean, service: Boolean, battery: Boolean, batterySkipped: Boolean = false) = when {
            !mic -> MIC
            !model -> MODEL
            !service -> SERVICE
            !battery && !batterySkipped -> BATTERY
            else -> READY
        }
    }
}
