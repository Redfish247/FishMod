package fishmod.features.dungeon

import fishmod.utils.FishMsg
import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.regex.Pattern

/**
 * Party Finder join-request helper: while [FishSettings.pfStatsEnabled] is on, a local-only lookup
 * of a player's MP/PB/Cata/Gear is printed to your own chat so you can vet them. Triggers on both a
 * received whisper (someone asking to join) and the "Party Finder > X joined the dungeon group!"
 * line. Nothing is ever sent back.
 */
object PartyFinderStats {

    private val lastLookupAt: MutableMap<String, Long> = HashMap()
    private const val COOLDOWN_MS = 15_000L

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    // Trigger: "Party Finder > Name joined the dungeon group! (Archer Level 42)"
    private val PF_JOIN: Pattern =
        Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.pfStatsEnabled) return@register false
            val m = PF_JOIN.matcher(text.string.replace(COLOR, ""))
            if (m.find()) lookup(m.group(1), joinLine = true)
            false
        }
    }

    // Hypixel prints "From stash: <item>" for every item you pull with /pickupstash — that matches
    // the generic "From X: …" whisper pattern, so guard against it (and any other reserved sender).
    private val NON_PLAYER_SENDERS = setOf("stash")

    /** Whisper path (kept for direct "From X:" join requests). */
    @JvmStatic
    fun onWhisper(sender: String?) {
        if (sender != null && sender.lowercase() in NON_PLAYER_SENDERS) return
        lookup(sender, joinLine = false)
    }

    /** On-demand `/pfs [name]` — no dungeon-hub gate, no cooldown. */
    @JvmStatic
    fun command(name: String?) {
        val target = name?.takeIf { it.isNotBlank() } ?: Minecraft.getInstance().player?.name?.string ?: return
        printStats(target, joinLine = false)
    }

    private fun lookup(sender: String?, joinLine: Boolean) {
        if (!fishmod.utils.Location.inDungeonHub()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || sender == null) return
        if (sender.equals(mc.player!!.name.string, ignoreCase = true)) return

        val now = System.currentTimeMillis()
        val last = lastLookupAt[sender.lowercase()]
        if (last != null && now - last < COOLDOWN_MS) return
        lastLookupAt[sender.lowercase()] = now
        printStats(sender, joinLine)
    }

    private fun printStats(sender: String, joinLine: Boolean) {
        HypixelApi.getByNameSilent(sender) { data ->
            val mp = if (data.magicalPower >= 0) data.magicalPower.toString() else "N/A"
            val pb = if (data.masterPbs != null && data.masterPbs.size > 7 && data.masterPbs[7] != null)
                data.masterPbs[7] else "N/A"
            val cata = HypixelApi.formatLevel(data.cataXp)
            val secrets = if (data.secretAverage != null) " | Sec avg: ${data.secretAverage}" else ""
            val armorStars = data.armorStars
            val gear = if (armorStars != null)
                String.format(
                    "H%d C%d L%d B%d", armorStars[0], armorStars[1],
                    armorStars[2], armorStars[3]
                )
            else "N/A"
            val verb = if (joinLine) "joined" else "wants to join"
            FishMsg.send(
                "$sender $verb — MP: $mp | M7 PB: $pb | Cata: $cata$secrets | Gear: $gear"
            )
        }
    }
}
