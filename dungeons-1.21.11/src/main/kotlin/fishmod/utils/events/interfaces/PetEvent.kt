package fishmod.utils.events.interfaces

fun interface PetEvent {
    fun onPet(name: String): Boolean
}
