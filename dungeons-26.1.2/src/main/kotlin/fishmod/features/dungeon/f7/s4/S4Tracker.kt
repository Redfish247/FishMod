package fishmod.features.dungeon.f7.s4

import fishmod.utils.Misc
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import fishmod.utils.debug.Debug
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.dungeon.Section
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * S4 (the 4th and final terminal section before the Core opens) term/leap failure tracker.
 *
 * Hypixel never tells anyone which terminal a teammate is "assigned" to — that's purely a party
 * convention — so this does not attempt to fabricate assignment data. What it correlates instead,
 * per player, is real evidence: terminal/device/lever completion broadcasts during S4
 * ([Events.ON_TERMINAL]), physical presence in the Core's arrival box (the same [CORE_BOX] region),
 * and death messages.
 * Entering Core while the section is still 4 is unambiguous evidence of an early leap; still not
 * having entered Core a configurable delay after Section 5 (Core open) starts is a late leap;
 * entering Core in S5 having never completed anything in S4 is only ever classified
 * [S4Status.POSSIBLE_MISSED] — plenty of players finish a section without personally completing a
 * terminal, so this is a hint for the HUD, not a hard accusation.
 */
object S4Tracker {

    private val CORE_BOX = AABB(53.5, 114.0, 49.5, 55.5, 116.0, 51.5)

    private val DEATH_PATTERN: Pattern = Pattern.compile(
        "☠ (\\S+) (?:was|were) killed by|☠ (\\S+) (?:died|quit)"
    )

    private val players = ConcurrentHashMap<String, S4PlayerState>()

    private var s4Active = false
    private var coreOpenTime: Long? = null
    private var lateLeapHandled = false

    @JvmStatic
    fun init() {
        Events.ON_SECTION_CHANGE.register {
            onSectionChange()
            false
        }
        Events.ON_TERMINAL.register { name, _, _, _, _ ->
            onContribution(name)
            false
        }
        Events.ON_GAME_MESSAGE.register(S4Tracker::onChat)
        Events.ON_SERVER_TICK.register {
            onTick()
            false
        }
        Events.ON_LOCATION_CHANGE.register {
            reset()
            false
        }
    }

    private fun onSectionChange() {
        if (!Floor7.s4TrackerEnabled) return
        val section = Section.getSection()

        if (section == 4) {
            reset()
            s4Active = true
            for (name in DungeonClass.getAll().keys) {
                players.getOrPut(name) { S4PlayerState(name) }
            }
            if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] tracking started"))
        } else if (section == 5 && s4Active) {
            coreOpenTime = System.currentTimeMillis()
        }
    }

    private fun onContribution(name: String) {
        if (!Floor7.s4TrackerEnabled || !s4Active || Section.getSection() != 4) return
        val state = players.getOrPut(name) { S4PlayerState(name) }
        if (state.died) return
        state.contributionCount++
        state.lastContributionTime = System.currentTimeMillis()
        state.status = S4Status.CONTRIBUTED
        if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Contribution: $name (${state.contributionCount})"))
    }

    private fun onChat(message: Component): Boolean {
        if (!Floor7.s4TrackerEnabled || !s4Active) return false

        val matcher = DEATH_PATTERN.matcher(message.string)
        if (!matcher.find()) return false

        val rawName = matcher.group(1) ?: matcher.group(2) ?: return false
        val name = if (rawName.equals("You", ignoreCase = true)) Minecraft.getInstance().user.name else rawName

        val state = players.getOrPut(name) { S4PlayerState(name) }
        if (state.died) return false
        state.died = true
        state.deathTime = System.currentTimeMillis()
        state.status = S4Status.DEAD
        if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Death: $name"))
        S4Alerts.trigger(S4AlertType.DEATH, name)
        return false
    }

    private fun onTick() {
        if (!Floor7.s4TrackerEnabled || !s4Active) return

        checkCoreEntries()
        checkLateLeaps()

        if (!Phase.inP3()) {
            s4Active = false
        }
    }

    private fun checkCoreEntries() {
        val level = Minecraft.getInstance().level ?: return
        val section = Section.getSection()

        for (entity in level.getEntities(null, CORE_BOX)) {
            if (entity !is Player || !EntityUtil.isARealPlayer(entity)) continue
            val name = entity.name.string
            val state = players.getOrPut(name) { S4PlayerState(name) }
            if (state.coreEntryTime != null || state.died) continue

            state.coreEntryTime = System.currentTimeMillis()
            if (section < 5) {
                state.status = S4Status.CORE_EARLY
                if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Core Entry (EARLY): $name"))
                S4Alerts.trigger(S4AlertType.EARLY_LEAP, name)
            } else if (state.contributionCount > 0) {
                state.status = S4Status.CORE_ON_TIME
                if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Core Entry: $name"))
            } else {
                state.status = S4Status.POSSIBLE_MISSED
                if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Core Entry (no S4 contribution): $name"))
                S4Alerts.trigger(S4AlertType.MISSED_TERM, name)
            }
        }
    }

    private fun checkLateLeaps() {
        if (lateLeapHandled) return
        val opened = coreOpenTime ?: return
        val thresholdMs = Floor7.s4LateLeapThresholdTicks * 50L
        if (System.currentTimeMillis() - opened < thresholdMs) return

        lateLeapHandled = true
        for (state in players.values) {
            if (state.died || state.coreEntryTime != null) continue
            state.status = S4Status.CORE_LATE
            if (Debug.termInfo) Misc.addChatMessage(Component.literal("[S4] Late leap: ${state.name}"))
            S4Alerts.trigger(S4AlertType.LATE_LEAP, state.name)
        }
    }

    private fun reset() {
        players.clear()
        s4Active = false
        coreOpenTime = null
        lateLeapHandled = false
        S4Alerts.reset()
    }

    @JvmStatic
    fun getPlayers(): Collection<S4PlayerState> = players.values

    @JvmStatic
    fun isActive(): Boolean = s4Active
}
