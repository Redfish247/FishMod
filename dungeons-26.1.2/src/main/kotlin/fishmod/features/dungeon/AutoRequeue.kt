package fishmod.features.dungeon

import fishmod.features.dungeon.map.DungeonState
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * When the end-of-run "> EXTRA STATS <" header prints, re-queue the **same floor** after a short
 * delay so the party rolls straight into a fresh instance.
 *
 * `/instancerequeue` doesn't re-queue the floor you're currently on, so we resolve the floor from
 * [DungeonState] at header time and send `/joininstance <floor>` instead (falling back to
 * `/instancerequeue` only if the floor can't be read).
 *
 * Two guards:
 *  - [partyChanged] latches on any party breakup / leader change / a member leaving (a requeue would
 *    fail or split the group) and clears on world load.
 *  - [dtSkip] is set by a party member typing "!dt" and cancels the requeue for THIS run only — it
 *    clears itself the moment the run's end screen is handled, so the next run auto-requeues again.
 */
object AutoRequeue {

    private val NUM_WORDS = arrayOf("one", "two", "three", "four", "five", "six", "seven")

    // 29 spaces then the header — Hypixel's exact end-screen divider line.
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

    @Volatile private var partyChanged = false
    @Volatile private var dtSkip = false
    /** Teammate count captured at run start; a shrink by end-of-run means someone left mid-run. */
    @Volatile private var startTeamCount = 0

    @JvmStatic
    fun init() {
        // "!dt" from anyone in party chat = skip the requeue after this run (only this run).
        Events.ON_PARTY_MESSAGE.register { _, message ->
            if (message.trim().equals("!dt", ignoreCase = true)) dtSkip = true
            false
        }

        Events.ON_GAME_MESSAGE.register { text ->
            val raw = text.string
            val s = COLOR.replace(raw, "")
            when {
                // Backup for the ON_PARTY_MESSAGE hook (which can miss in-dungeon party chat).
                DT_LINE.matcher(raw).find() || s.trim().let { it.startsWith("Party > ") && it.endsWith(": !dt") } -> dtSkip = true
                s == MORT_START -> { partyChanged = false; dtSkip = false; startTeamCount = 0 }
                BREAKUP.matcher(s).find() -> partyChanged = true
                EXTRA_STATS.matcher(s).find() -> {
                    val skip = dtSkip
                    dtSkip = false   // "!dt" is per-run — consume it here
                    // Someone left mid-run if the team shrank since the start (or never filled to a party).
                    val teamShrank = startTeamCount >= 2 && fishmod.features.dungeon.map.DungeonPlayers.count() < startTeamCount
                    if (Dungeons.enableAutoRequeue && !partyChanged && !skip && !teamShrank) {
                        val delay = FishSettings.autoRequeueDelayMs.coerceIn(0, 15000).toLong()
                        // Resolve the floor now, while the scoreboard/chat state is still fresh.
                        val cmd = requeueCommand()
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute {
                            Minecraft.getInstance().execute {
                                val mc = Minecraft.getInstance()
                                // Re-check everything — "!dt" or a leave can land during the countdown.
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
        Events.ON_WORLD_CHANGE.register { partyChanged = false; dtSkip = false; startTeamCount = 0; false }
        // Track the peak roster size during the clear; a smaller team at end-of-run == someone left.
        Events.ON_SERVER_TICK.register {
            if (DungeonState.isInDungeon() && !DungeonState.isInBoss()) {
                val n = fishmod.features.dungeon.map.DungeonPlayers.count()
                if (n > startTeamCount) startTeamCount = n
            }
            false
        }
    }

    /** `joininstance` for the current floor, or `instancerequeue` if the floor can't be read. */
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
