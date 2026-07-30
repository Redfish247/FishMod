package fishmod.utils.events.interfaces

fun interface ScoreBoardEvent {
    fun onTeam(text: String): Boolean
}
