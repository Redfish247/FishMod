package fishmod.features.dungeon

import fishmod.utils.FishMsg
import fishmod.utils.HypixelApi
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.regex.Pattern
import fishmod.utils.debug.FishDiag

object PartyFinderStats {

    private val lastLookupAt: MutableMap<String, Long> = HashMap()
    private const val COOLDOWN_MS = 15_000L

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private val PF_JOIN: Pattern =
        Pattern.compile("^Party Finder > (\\w{1,16}) joined the dungeon group! \\((\\w+) Level (\\d+)\\)$")

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.pfStatsEnabled) return@register false
            try {
                val plain = text.string.replace(COLOR, "")
                val m = PF_JOIN.matcher(plain)
                if (m.find()) lookup(m.group(1), joinLine = true)
                else if (plain.startsWith("Party Finder > ") && plain.contains("joined the dungeon group")) FishDiag.fail("PartyFinderStats.1", "PF join line didn't match: '$plain'")
            } catch (e: Exception) {
                FishDiag.fail("PartyFinderStats.2", "PF stats chat handler threw", e)
            }
            false
        }
    }

    private val NON_PLAYER_SENDERS = setOf("stash")

    @JvmStatic
    fun onWhisper(sender: String?) {
        if (sender != null && sender.lowercase() in NON_PLAYER_SENDERS) return
        lookup(sender, joinLine = false)
    }

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
        if (lastLookupAt.size > 256) lastLookupAt.values.removeIf { now - it >= COOLDOWN_MS }
        lastLookupAt[sender.lowercase()] = now
        printStats(sender, joinLine)
    }

    private fun printStats(sender: String, joinLine: Boolean) {
        HypixelApi.getByNameSilent(sender) { data ->
            try { printStatsInner(sender, joinLine, data) } catch (e: Exception) { FishDiag.fail("PartyFinderStats.5", "PF stats print threw for $sender", e) }
        }
    }

    private fun printStatsInner(sender: String, joinLine: Boolean, data: HypixelApi.DungeonData) {
        run {
            if (data.failed) {
                FishDiag.fail("PartyFinderStats.3", "PF stats API lookup failed for $sender")
                FishMsg.send("§cCouldn't look up $sender's stats")
                return
            }
            val mp = if (data.magicalPower >= 0) data.magicalPower.toString() else "N/A"
            val pb = if (data.masterPbs != null && data.masterPbs.size > 7 && data.masterPbs[7] != null)
                data.masterPbs[7] else "N/A"
            val cata = HypixelApi.formatLevel(data.cataXp)
            val secrets = if (data.secretAverage != null) " | Sec avg: ${data.secretAverage}" else ""
            val armorStars = data.armorStars
            FishDiag.check(armorStars == null || armorStars.size >= 4, "PartyFinderStats.4") { "armorStars size ${armorStars?.size} < 4 for $sender" }
            val gear = if (armorStars != null && armorStars.size >= 4)
                String.format(
                    "H%d C%d L%d B%d", armorStars[0], armorStars[1],
                    armorStars[2], armorStars[3]
                )
            else "N/A"
            val verb = if (joinLine) "joined" else "wants to join"
            val cls = data.selectedClass?.let { " (${it.replaceFirstChar(Char::uppercase)})" } ?: ""
            FishMsg.send(
                "$sender$cls $verb — MP: $mp | M7 PB: $pb | Cata: $cata$secrets | Gear: $gear"
            )
        }
    }
}
