package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component

/**
 * Extra Stats (ported from Odin's ExtraStats). Swallows Hypixel's `> EXTRA STATS <` block and
 * reprints a tidy summary with PB markers. Team secret/crypt counts aren't reprinted (no reliable
 * client source); everything else is parsed straight from the same chat lines.
 */
object ExtraStats {

    private val HEADER = Regex(" {29}> EXTRA STATS <")
    private val TITLE = Regex("^\\s*(Master Mode)? ?(?:The )?Catacombs - (Entrance|Floor .{1,3})( Stats)?$")
    private val DEFEATED = Regex("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$")
    private val SCORE = Regex("^\\s*Team Score: (\\d+) \\((.{1,2})\\)\\s?(\\(NEW RECORD!\\))?$")
    private val XP = Regex("^\\s*(\\+[\\d,.]+\\s?\\w+ Experience)\\s?(?:\\(.+\\))?$")
    private val BITS = Regex("^\\s*(\\+[\\d,]+ Bits)$")
    private val DAMAGE = Regex("^\\s*Total Damage as .+: ([\\d,.]+)\\s?(\\(NEW RECORD!\\))?$")
    private val HEAL = Regex("^\\s*Ally Healing: ([\\d,.]+)\\s?(\\(NEW RECORD!\\))?$")
    private val KILLS = Regex("^\\s*Enemies Killed: (\\d+)\\s?(\\(NEW RECORD!\\))?$")
    private val DEATHS = Regex("^\\s*Deaths: (\\d+)$")
    private val SECRETS = Regex("^\\s*Secrets Found: (\\d+)$")
    private val BREAK = Regex("^▬+$")
    private val FAIL = Regex("^\\s*(FAILED|You failed).*$")

    private val cancelIfInDungeon = listOf(HEADER, TITLE, DEFEATED, SCORE, XP, BITS, DAMAGE, HEAL, KILLS, DEATHS, SECRETS, BREAK)

    private var floorTitle = ""
    private var defeated: String? = null
    private var timePB = false
    private var time = ""
    private var score = 0
    private var scoreLetter = ""
    private var scorePB = false
    private val xpLines = mutableListOf<String>()
    private var bits: String? = null
    private var damage = "0"; private var damagePB = false
    private var heal = "0"; private var healPB = false
    private var kills = "0"; private var killsPB = false
    private var deaths = 0
    private var secrets = 0

    private fun reset() {
        floorTitle = ""; defeated = null; timePB = false; time = ""
        score = 0; scoreLetter = ""; scorePB = false
        xpLines.clear(); bits = null
        damage = "0"; damagePB = false; heal = "0"; healPB = false; kills = "0"; killsPB = false
        deaths = 0; secrets = 0
    }

    @JvmStatic
    fun init() {
        Events.ON_WORLD_CHANGE.register { reset(); false }

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.extraStatsEnabled || !Location.inDungeon()) return@register false
            val s = text.string.replace(Regex("§."), "")

            TITLE.find(s)?.let { m ->
                floorTitle = (if (m.groupValues[1].isNotEmpty()) "§cMaster Mode " else "§cThe Catacombs ") + "§r- §e" + m.groupValues[2]
            }
            DEFEATED.find(s)?.let { m -> defeated = m.groupValues[1]; time = m.groupValues[2].trim(); timePB = m.groupValues[3].isNotEmpty() }
            SCORE.find(s)?.let { m -> score = m.groupValues[1].toIntOrNull() ?: 0; scoreLetter = m.groupValues[2]; scorePB = m.groupValues[3].isNotEmpty() }
            XP.find(s)?.let { m -> xpLines.add("§3" + m.groupValues[1].replace("Experience", "EXP").replace("Catacombs", "Cata")) }
            BITS.find(s)?.let { m -> bits = m.groupValues[1] }
            DAMAGE.find(s)?.let { m -> damage = m.groupValues[1]; damagePB = m.groupValues[2].isNotEmpty() }
            HEAL.find(s)?.let { m -> heal = m.groupValues[1]; healPB = m.groupValues[2].isNotEmpty() }
            KILLS.find(s)?.let { m -> kills = m.groupValues[1]; killsPB = m.groupValues[2].isNotEmpty() }
            DEATHS.find(s)?.let { m -> deaths = m.groupValues[1].toIntOrNull() ?: 0 }
            SECRETS.find(s)?.let { m -> secrets = m.groupValues[1].toIntOrNull() ?: 0; print() }

            cancelIfInDungeon.any { it.containsMatchIn(s) } || FAIL.containsMatchIn(s)
        }
    }

    private fun pb(flag: Boolean) = if (flag) " §d§l(PB!)" else ""

    private fun print() {
        val out = mutableListOf<String>()
        out.add("§8§m                                        ")
        if (floorTitle.isNotEmpty()) out.add(floorTitle)
        out.add(
            if (defeated == null) "§c§lFAILED §7- §e$time"
            else "§aDefeated §c${defeated} §7in §e$time${pb(timePB)}"
        )
        val bitsTxt = if (bits != null && FishSettings.extraStatsBits) "   §b$bits" else ""
        out.add("§aScore: §6$score §a(§b$scoreLetter§a)${pb(scorePB)}$bitsTxt")
        if (xpLines.isNotEmpty()) {
            out.add(if (FishSettings.extraStatsClassExp) xpLines.joinToString("  §r") else xpLines.first())
        }
        if (FishSettings.extraStatsCombat) {
            out.add("§eDmg §f${damage}${pb(damagePB)} §7| §bKills §f${kills}${pb(killsPB)} §7| §aHeal §f${heal}${pb(healPB)}")
        }
        out.add("§bSecrets §f$secrets §7| §cDeaths §f$deaths")
        if (FishSettings.extraStatsTeammates) {
            val mates = DungeonClass.getAll().entries.filter { it.key != net.minecraft.client.Minecraft.getInstance().player?.gameProfile?.name }
            out.add(if (mates.isEmpty()) "§3Solo" else mates.joinToString("§r, ") { "${colorOf(it.value)}${it.key}" })
        }
        out.add("§8§m                                        ")
        out.forEach { Misc.addChatMessage(Component.literal(it)) }
        reset()
    }

    private fun colorOf(c: DungeonClass) = when (c) {
        DungeonClass.ARCHER -> "§3"; DungeonClass.BERSERK -> "§c"; DungeonClass.HEALER -> "§d"
        DungeonClass.MAGE -> "§b"; DungeonClass.TANK -> "§a"
    }
}
