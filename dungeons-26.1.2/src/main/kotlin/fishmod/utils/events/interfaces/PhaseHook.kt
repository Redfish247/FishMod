package fishmod.utils.events.interfaces

fun interface PhaseHook {
    fun onPhaseChange(): Boolean
}
