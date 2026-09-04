package fishmod.utils.dungeon

import config.practical.hud.HUDComponent
import config.practical.manager.ConfigValue
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.Floor7
import fishmod.utils.debug.Debug
import fishmod.utils.events.Events
import fishmod.utils.events.interfaces.SectionEvent
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object Section {
    enum class DisplayTerminalSplitsWhen(private val label: String) {
        BOSS("Entire Boss"), TERMINALS_ONLY("Terminals");

        override fun toString(): String = label
    }

    // 1:1 with blade-addons' Section.java: four per-gate splits, no synthetic "Total" row.
    private val splits: Array<Split> = arrayOf(
        Split("1st", "", "", 16755200, 0.0),
        Split("2nd", "", "", 16755200, 0.0),
        Split("3rd", "", "", 16755200, 0.0),
        Split("4th", "", "", 16755200, 0.0),
    )
    private val TERMINALS_DONE_PATTERN: Pattern =
        Pattern.compile("^(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$")

    @JvmField
    var SPLIT_LENGTH: Int = 120
    private const val TERM_PHASE_INDEX = 6

    private var currentSection = -1
    private var completed = 0
    private var total = 7
    private var gateBlownUp = false

    @ConfigValue @JvmField var enableTerminalSplits: Boolean = false

    @ConfigValue @JvmField var displayTerminalSplitsWhen: DisplayTerminalSplitsWhen = DisplayTerminalSplitsWhen.TERMINALS_ONLY

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register(Section::parseMessage)
        Events.ON_LOCATION_CHANGE.register { _ ->
            reset()
            false
        }
        Events.ON_PHASE_CHANGE.register {
            Debug.LOGGER.info("[Section] ON_PHASE_CHANGE phase={} inTerminals={} inGoldorTunnel={} currentSection={}",
                Phase.getPhase(), Phase.inTerminals(), Phase.inGoldorTunnel(), currentSection)
            if (Phase.inP2()) {
                currentSection = 0
            }

            if (Phase.inTerminals()) {
                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal("Terminals started"))
                }
                currentSection = 1
                total = totalFor(1)
                splits[0].start()
            } else if (Phase.inGoldorTunnel()) {
                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal("Terminals ended"))
                }
                currentSection = 5
                endAllSections()
            }
            false
        }
        Events.ON_SERVER_TICK.register {
            if (currentSection < 1 || currentSection > 5) return@register false
            for (split in splits) {
                split.tick()
            }
            false
        }
    }

    private fun reset() {
        currentSection = -1
        resetSection()
        for (split in splits) {
            split.reset()
        }
    }

    private fun resetSection() {
        completed = 0
        gateBlownUp = false
    }

    /** Devices needed to open the given section's gate — every section is 7 except S2, which is 8. */
    @JvmStatic
    fun totalFor(section: Int): Int = if (section == 2) 8 else 7

    private fun incrementSection() {
        Debug.LOGGER.info("[Section] incrementSection: {} -> {} (completed={} total={} gate={})",
            currentSection, currentSection + 1, completed, total, gateBlownUp)
        resetSection()
        endSplit(currentSection)
        currentSection++
        // Seed the new section's expected count up front — waiting on its first device message left
        // `total` at the previous section's count, so a gate blown before that first message came in
        // (or that message racing the "gate destroyed" line) compared `completed == total` wrong and
        // mistimed the split.
        total = totalFor(currentSection)

        if (Debug.termInfo) {
            Misc.addChatMessage(Component.literal("section: $currentSection"))
        }

        Events.ON_SECTION_CHANGE.invoke(SectionEvent::onSection)
        startSplit(currentSection)
    }

    private fun endSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= splits.size) return
        splits[index].end()
    }

    private fun startSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= splits.size) return
        splits[index].start()
    }

    private fun endAllSections() {
        for (split in splits) {
            split.end()
        }
        if (Debug.termInfo) {
            Misc.addChatMessage(Component.literal("ending all sections"))
        }
        Events.ON_SECTION_CHANGE.invoke(SectionEvent::onSection)
    }

    @JvmStatic
    fun parseMessage(message: Component): Boolean {
        if (!Phase.inTerminals()) return false
        var shouldCancelMessage = false

        val string = message.string
        val matcher = TERMINALS_DONE_PATTERN.matcher(string)
        if (matcher.find()) {
            val name = matcher.group(1)
            val action = matcher.group(2)
            val objective = matcher.group(3)
            val currentCompleted: Int
            val totalNeeded: Int

            try {
                currentCompleted = matcher.group(4).toInt()
                totalNeeded = matcher.group(5).toInt()
            } catch (e: NumberFormatException) {
                Debug.LOGGER.error("Failed to parse terminal message, {}", e.message)
                return false
            }

            if (Debug.termInfo) {
                Misc.addChatMessage(Component.literal("name:$name:objective>$objective:($currentCompleted/$totalNeeded)"))
            }

            Events.ON_TERMINAL.invoke { terminalEvent -> terminalEvent.onComplete(name, action, objective, currentCompleted, totalNeeded) }

            if (Floor7.terminalTimeStamps) {
                // Color lives in the sibling's Style, not the string literal itself.
                val texts = message.siblings
                if (texts.isNotEmpty()) {
                    Misc.addChatMessage(
                        Component.literal(name).setStyle(texts.first().style)
                            .append(Component.literal(" §a$action $objective! (§c$currentCompleted§a/$totalNeeded) §8(§7${getSectionTime()}s §8| §7${Phase.getPhaseTime(TERM_PHASE_INDEX)}s§8)"))
                    )
                    shouldCancelMessage = true
                }
            }

            if (shouldIncrement(currentCompleted)) {
                if (Debug.termInfo) {
                    val why = if (currentCompleted == total && gateBlownUp) "count==total & gate already blown"
                              else "count($currentCompleted) < completed($completed), completed had reached total($total)"
                    Misc.addChatMessage(Component.literal("§eincrementSection via device msg §7($why)"))
                }
                incrementSection()
                Misc.forceTitle(Component.empty(), message)
            } else {
                total = totalNeeded
                completed = currentCompleted
            }

        } else if (!gateBlownUp) {
            if (string == "The gate has been destroyed!") {
                gateBlownUp = true

                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal(
                        "§egate destroyed msg: section=$currentSection completed=$completed total=$total" +
                            (if (completed == total) " §a-> incrementing now" else " §c-> NOT incrementing (waiting on a device msg)")
                    ))
                }

                if (completed == total) {
                    incrementSection()
                }

                if (Floor7.terminalTimeStamps) {
                    Misc.addChatMessage(Component.literal("§aThe gate has been destroyed! §8(§7${getSectionTime()}s §8| §7${Phase.getPhaseTime(TERM_PHASE_INDEX)}s§8)"))
                    shouldCancelMessage = true
                }
            }
        } else if (string == "The Core entrance is opening!") {
            // so "goldor tunnel" can be shown after terms are done
            currentSection = 5
            endAllSections()
            Debug.sendDebugMessage(Component.literal("Core section"))
        }

        return shouldCancelMessage
    }

    @JvmStatic
    fun getSection(): Int = currentSection

    @JvmStatic
    fun isGateBlownUp(): Boolean = gateBlownUp

    @JvmStatic
    fun inSection(section: Int): Boolean {
        if (section == 0 && (Phase.inP2() || Phase.inP3())) return true
        return currentSection == section && Phase.inP3()
    }

    @JvmStatic
    fun getSectionTime(): Double {
        val index = currentSection - 1
        if (index < 0 || index >= splits.size) return -1.0
        return splits[index].getRealTime()
    }

    @JvmStatic
    fun display(): Boolean {
        if (enableTerminalSplits && Location.inDungeon()) {
            return when (displayTerminalSplitsWhen) {
                DisplayTerminalSplitsWhen.BOSS -> Phase.inBoss()
                DisplayTerminalSplitsWhen.TERMINALS_ONLY -> Phase.inTerminals()
            }
        }

        return false
    }

    @JvmStatic
    fun shouldIncrement(recentlyCompleted: Int): Boolean {
        // The second clause is a fallback for a missed "gate has been destroyed!" line: a low count on
        // a fresh section reads as "went backward" from the previous section's count. With multiple
        // players finishing devices in parallel, two of THIS section's own progress messages can also
        // land out of order (e.g. "3/8" then a race-delayed "2/8") — that's not a new section, and
        // firing on it jumped straight to the next section (and its gate marker) mid-way through this
        // one. Only trust "went backward" once this section had actually reached its full count.
        return (recentlyCompleted == total && gateBlownUp) || (recentlyCompleted < completed && completed >= total)
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val x = component.scaledX
        val y = component.scaledY

        val textRenderer: Font = Minecraft.getInstance().font

        for (i in splits.indices) {
            splits[i].drawSplit(context, textRenderer, x, y + Constants.TEXT_HEIGHT * i, SPLIT_LENGTH)
        }
    }

    // condition forced false: rendered explicitly via F7Huds.renderHud. Default pos isn't (0,0) because
    // keepOnScreen treats (0,0) as on-screen and never relocates it, leaving it atop Phase.splitTimer.
    @ConfigValue @JvmField
    var terminalSplits: HUDComponent = HUDComponent(
        10.0, 154.0, SPLIT_LENGTH, 50, 1f, "Term splits", { false }, Section::render, { enableTerminalSplits }
    )
}
