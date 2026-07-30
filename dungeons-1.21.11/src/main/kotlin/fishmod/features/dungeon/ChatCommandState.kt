package fishmod.features.dungeon

/** Shared state for suppressing Hypixel's error-reply chat lines shortly after a mod-issued party command. */
object ChatCommandState {
    @JvmField
    @Volatile
    var lastPartyCommandAt: Long = 0L
}
