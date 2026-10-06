package fishmod.utils.dungeon

// Alpha boss-line aliases; old lines still match exactly.
object DialogueCompat {
    const val NECRON_END_OLD = "[BOSS] Necron: All this, for nothing..."
    const val NECRON_END_NEW = "[BOSS] Necron: ARGH!"
    private const val WATCHER_OPEN_NEW = "[BOSS] The Watcher: Ah, we meet again."
    private val WATCHER_OPEN_OLD = setOf(
        "[BOSS] The Watcher: Congratulations, you made it through the Entrance.",
        "[BOSS] The Watcher: Ah, you've finally arrived.",
        "[BOSS] The Watcher: Ah, we meet again...",
        "[BOSS] The Watcher: So you made it this far... interesting.",
        "[BOSS] The Watcher: You've managed to scratch and claw your way here, eh?",
        "[BOSS] The Watcher: I'm starting to get tired of seeing you around here...",
        "[BOSS] The Watcher: Oh.. hello?",
        "[BOSS] The Watcher: Things feel a little more roomy now, eh?",
    )

    @JvmStatic fun isNecronEnd(s: String): Boolean = s == NECRON_END_OLD || s.startsWith(NECRON_END_NEW)

    @JvmStatic fun isWatcherOpen(s: String): Boolean = s in WATCHER_OPEN_OLD || s.startsWith(WATCHER_OPEN_NEW)

    @JvmStatic
    fun matches(expected: String, actual: String): Boolean = when {
        expected == actual -> true
        expected in WATCHER_OPEN_OLD -> actual.startsWith(WATCHER_OPEN_NEW)
        expected == NECRON_END_OLD -> actual.startsWith(NECRON_END_NEW)
        else -> false
    }
}
