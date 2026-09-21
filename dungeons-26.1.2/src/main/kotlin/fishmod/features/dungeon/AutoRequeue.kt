package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AutoRequeue {

    private val NUM_WORDS = arrayOf("one", "two", "three", "four", "five", "six", "seven")

    private val EXTRA_STATS: Pattern = Pattern.compile(" {29}> EXTRA STATS <")
    private val BREAKUP: Pattern = Pattern.compile(
        "^(?:You have been kicked from the party|You left the party|The party was disbanded|" +
            "The party was transferred to |.+ has disbanded the party|.+ has been removed from the party|" +
            ".+ has left the party|.+ has been kicked from the party|.+ was removed from your party because they disconnected|" +
            "Kicked .+ because they were offline|You are not currently in a party)"
    )
    private val DT_LINE: Pattern = Pattern.compile("^(?:§9)?Party §8> .*: !dt$", Pattern.CASE_INSENSITIVE)
    private val MORT_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private val INVISIBLE = Regex("[\\u00A0\\u200B\\u200C\\u200D\\uFEFF\\u00AD]")
    private fun clean(s: String) = INVISIBLE.replace(s, " ").trim()

    @Volatile private var partyChanged = false
    @Volatile private var dtSkip = false
    @Volatile private var startTeamCount = 0
    @Volatile private var extraStatsHandled = false

    @Volatile private var lastLine = ""
    @Volatile private var lastLineMs = 0L

    @JvmStatic
    fun init() {
        Events.ON_PARTY_MESSAGE.register { _, message ->
            if (clean(message).equals("!dt", ignoreCase = true)) dtSkip = true
            false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            val raw = text.string
            val s = COLOR.replace(raw, "")
            val now = System.currentTimeMillis()
            if (s == lastLine && now - lastLineMs < 1_500L) return@register false
            lastLine = s
            lastLineMs = now
            when {
                // Backup for the ON_PARTY_MESSAGE hook (which can miss in-dungeon party chat).
                // Loose match (contains, not startsWith/endsWith) — trailing junk chars Hypixel
                // sometimes appends to chat lines broke the old exact-suffix check.
                DT_LINE.matcher(raw).find() || clean(s).let { it.contains("Party >", ignoreCase = true) && it.contains(": !dt", ignoreCase = true) } -> dtSkip = true
                s == MORT_START -> { partyChanged = false; dtSkip = false; startTeamCount = 0; extraStatsHandled = false }
                BREAKUP.matcher(s).find() -> partyChanged = true
                EXTRA_STATS.matcher(s).find() -> {
                    if (extraStatsHandled) return@register false
                    extraStatsHandled = true
                    val skip = dtSkip
                    dtSkip = false
                    val teamShrank = startTeamCount >= 2 && fishmod.features.dungeon.map.DungeonPlayers.count() < startTeamCount
                    if (Dungeons.enableAutoRequeue && !partyChanged && !skip && !teamShrank) {
                        val delay = FishSettings.autoRequeueDelayMs.coerceIn(0, 15000).toLong()
                        val cmd = requeueCommand()
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute {
                            Minecraft.getInstance().execute {
                                val mc = Minecraft.getInstance()
                                if (Dungeons.enableAutoRequeue && !partyChanged && !dtSkip && mc.connection != null) {
                                    mc.connection!!.sendCommand(cmd)
                                }
                            }
                        }
                    }
                }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { partyChanged = false; dtSkip = false; startTeamCount = 0; extraStatsHandled = false; false }
        Events.ON_SERVER_TICK.register {
            if (DungeonState.isInDungeon() && !DungeonState.isInBoss()) {
                val n = fishmod.features.dungeon.map.DungeonPlayers.count()
                if (n > startTeamCount) startTeamCount = n
            }
            false
        }
    }

    @JvmStatic
    fun onChatLine(raw: String) {
        val s = COLOR.replace(raw, "")
        if (DT_LINE.matcher(raw).find() || clean(s).let { it.contains("Party >", ignoreCase = true) && it.contains(": !dt", ignoreCase = true) }) {
            dtSkip = true
        }
    }

    private fun requeueCommand(): String {
        val floor = DungeonState.floorNumber()
        return when {
            floor == 0 -> "joininstance catacombs_entrance"
            floor in 1..7 -> "joininstance " +
                (if (DungeonState.isMasterMode()) "master_" else "") +
                "catacombs_floor_" + NUM_WORDS[floor - 1]
            else -> "instancerequeue"
        }
    }
}
