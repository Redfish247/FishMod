package fishmod.features.dungeon

object ChatCommandState {
    @JvmField
    @Volatile
    var lastPartyCommandAt: Long = 0L
}
