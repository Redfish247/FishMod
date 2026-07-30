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

    private val splits: Array<Split> = arrayOf(
        Split("S1", "", "", 16755200, 0.0),
        Split("S2", "", "", 16755200, 0.0),
        Split("S3", "", "", 16755200, 0.0),
        Split("S4", "", "", 16755200, 0.0),
        Split("Total", "", "", Split.GREEN, 0.0)
    )
    private val TERMINALS_DONE_PATTERN: Pattern =
        Pattern.compile("^(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$")

    @JvmField
    var SPLIT_LENGTH: Int = 120
    private const val TERM_PHASE_INDEX = 6

    /** Number of per-section splits (S1-S4) at the front of [splits]; the trailing "Total" split is separate. */
    private const val SECTION_COUNT = 4

    private var currentSection = -1
    private var completed = 0
    private var total = 7
    private var gateBlownUp = false

    @ConfigValue @JvmField var enableTerminalSplits: Boolean = false

    @ConfigValue @JvmField var includeTotalTime: Boolean = false

    @ConfigValue @JvmField var displayTerminalSplitsWhen: DisplayTerminalSplitsWhen = DisplayTerminalSplitsWhen.TERMINALS_ONLY

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register(Section::parseMessage)
        Events.ON_LOCATION_CHANGE.register { _ ->
            reset()
            false
        }
        Events.ON_PHASE_CHANGE.register {
            if (Phase.inP2()) {
                currentSection = 0
            }

            if (Phase.inTerminals()) {
                if (Debug.termInfo) {
                    Misc.addChatMessage(Component.literal("Terminals started"))
                }
                currentSection = 1
                splits[0].start()
                splits[SECTION_COUNT].start() // Total: spans the whole terminals phase
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

    private fun incrementSection() {
        resetSection()
        endSplit(currentSection)
        currentSection++

        if (Debug.termInfo) {
            Misc.addChatMessage(Component.literal("section: $currentSection"))
        }

        Events.ON_SECTION_CHANGE.invoke(SectionEvent::onSection)
        startSplit(currentSection)
    }

    private fun endSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= SECTION_COUNT) return
        splits[index].end()
    }

    private fun startSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= SECTION_COUNT) return
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
                //have to do it like this because for some reason they have the color in the
                //Style object and not in the string literal
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
                incrementSection()
                Misc.forceTitle(Component.empty(), message)
            } else {
                total = totalNeeded
                completed = currentCompleted
            }

        } else if (!gateBlownUp) {
            if (string == "The gate has been destroyed!") {
                gateBlownUp = true

                if (completed == total) {
                    incrementSection()
                }

                if (Floor7.terminalTimeStamps) {
                    Misc.addChatMessage(Component.literal("§aThe gate has been destroyed! §8(§7${getSectionTime()}s §8| §7${Phase.getPhaseTime(TERM_PHASE_INDEX)}s§8)"))
                    shouldCancelMessage = true
                }
            }
        } else if (string == "The Core entrance is opening!") {
            //so in "goldor tunnel" can be shown after terms are done
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
        if (index < 0 || index >= SECTION_COUNT) return -1.0
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
        return (recentlyCompleted == total && gateBlownUp) || (recentlyCompleted < completed)
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val x = component.scaledX
        val y = component.scaledY

        val textRenderer: Font = Minecraft.getInstance().font

        val count = if (includeTotalTime) splits.size else SECTION_COUNT
        for (i in 0 until count) {
            splits[i].drawSplit(context, textRenderer, x, y + Constants.TEXT_HEIGHT * i, SPLIT_LENGTH)
        }
    }

    // Rendered explicitly via F7Huds.renderHud (HudRenderCallback in FishModInit) — the proven path
    // every other FishMod HUD uses — so the condition-supplier is forced false to keep
    // practical-config's HudElementRegistry auto-render (unreliable here) from double-drawing it.
    //
    // Default position deliberately not (0,0): that's also Phase.splitTimer's default, and
    // keepOnScreen's off-screen check treats (0,0) as "already on screen" so it never relocates —
    // the two panels would otherwise silently render on top of each other the first time both are
    // visible at once (Terminals/Goldor phase), which is exactly what happened before this fix.
    @ConfigValue @JvmField
    var terminalSplits: HUDComponent = HUDComponent(
        10.0, 154.0, SPLIT_LENGTH, 50, 1f, "Term splits", { false }, Section::render, { enableTerminalSplits }
    )
}
