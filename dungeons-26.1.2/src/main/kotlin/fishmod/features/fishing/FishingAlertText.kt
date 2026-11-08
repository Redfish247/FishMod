package fishmod.features.fishing

// Pure line parsing for alerts, trophy filter and compact messages. Strings adapted from Feesh (Sleepy-Panda, Apache-2.0); ALL UNVERIFIED IN GAME.
object FishingAlertText {
    enum class TrophyRarity { BRONZE, SILVER, GOLD, DIAMOND }
    class Trophy(val frog: Boolean, val name: String, val rarity: TrophyRarity)

    private val TROPHY_RE = Regex("""^. TROPHY (FISH|FROG)! You caught (?:a|an) (.+?) (BRONZE|SILVER|GOLD|DIAMOND)!$""")
    private val DISCOVERY_RE = Regex("""^NEW DISCOVERY: (.+)$""")
    private val HOTSPOT_RE = Regex("""^HOTSPOT$""")
    private val TIME_RE = Regex("""(\d+)\s*([hms])""")
    private val SC_SPAWN_ARTICLE = Regex("""^[AEIOUaeiou]""")

    const val WORMHOLE = "Your Wormhole closed up..."
    const val DOUBLE_HOOK = "It's a Double Hook!"

    // Cheap prefix test before the regex: line is "<symbol> TROPHY ..."
    @JvmStatic fun isTrophyLine(plain: String): Boolean =
        plain.length > 14 && (plain.startsWith("TROPHY ", 2) || plain.startsWith("TROPHY ", 1))

    @JvmStatic fun trophy(plain: String): Trophy? {
        if (!isTrophyLine(plain)) return null
        val m = TROPHY_RE.matchEntire(plain.trim()) ?: return null
        return Trophy(m.groupValues[1] == "FROG", m.groupValues[2], TrophyRarity.valueOf(m.groupValues[3]))
    }

    // "NEW DISCOVERY: ..." details; fish only count in Crimson Isle unless it is Obfuscated 1 (Feesh rule)
    @JvmStatic fun newDiscovery(plain: String, crimsonIsle: Boolean): String? {
        if (!plain.startsWith("NEW DISCOVERY:")) return null
        val d = DISCOVERY_RE.matchEntire(plain)?.groupValues?.get(1) ?: return null
        if (crimsonIsle || d.contains("Obfuscated-1") || d.contains("Obfuscated 1")) return d
        return null
    }

    @JvmStatic fun isWormholeGone(plain: String): Boolean = plain.startsWith("Your Wormhole closed") && plain == WORMHOLE

    @JvmStatic fun isHotspotName(plain: String): Boolean = plain.length == 7 && HOTSPOT_RE.matches(plain)

    // "1m 02s", "59s", "1h 2m" -> seconds; null when no unit found
    @JvmStatic fun parseSeconds(text: String): Int? {
        var total = 0
        var any = false
        for (m in TIME_RE.findAll(text)) {
            val n = m.groupValues[1].toInt()
            total += when (m.groupValues[2]) { "h" -> n * 3600; "m" -> n * 60; else -> n }
            any = true
        }
        return if (any) total else null
    }

    // Timer-in-name deployables: "<prefix> <n>s". Returns (prefix, seconds) or null
    private val TIMER_PREFIXES = listOf(
        "Umberella", "Dwarven Lantern", "Mithril Lantern", "Titanium Lantern", "Glacite Lantern", "Will-o'-wisp",
        "Overflux", "Plasmaflux", "Mana Flux",
    )
    private val TIMER_RE = Regex("""^(.+?) (\d+)s$""")

    @JvmStatic fun timerDeployable(plain: String): Pair<String, Int>? {
        if (plain.length < 6 || plain.last() != 's') return null
        val m = TIMER_RE.matchEntire(plain) ?: return null
        val name = m.groupValues[1]
        if (TIMER_PREFIXES.none { name.startsWith(it) }) return null
        return name to m.groupValues[2].toInt()
    }

    // Bait lore "Bait Remaining: 1,234" -> 1234
    @JvmStatic fun baitRemaining(lore: String): Int? {
        if (!lore.contains("Bait Remaining:")) return null
        return lore.substringAfter(":").replace(",", "").trim().toIntOrNull()
    }

    // Replacement for the catch line in compact mode, or null to leave it alone
    @JvmStatic fun compactDoubleHook(plain: String): String? = if (plain.startsWith(DOUBLE_HOOK)) "§b§lDOUBLE HOOK!" else null

    @JvmStatic fun compactSpawn(sc: SeaCreature): String {
        val article = if (SC_SPAWN_ARTICLE.containsMatchIn(sc.name)) "An" else "A"
        return "§7$article §${sc.rarity.code}${sc.name} §7has spawned!"
    }

    @JvmStatic fun fmtCoins(v: Double): String = when {
        v >= 1e9 -> String.format(java.util.Locale.ROOT, "%.1fB", v / 1e9)
        v >= 1e6 -> String.format(java.util.Locale.ROOT, "%.1fM", v / 1e6)
        v >= 1e3 -> String.format(java.util.Locale.ROOT, "%.0fk", v / 1e3)
        else -> String.format(java.util.Locale.ROOT, "%.0f", v)
    }
}
