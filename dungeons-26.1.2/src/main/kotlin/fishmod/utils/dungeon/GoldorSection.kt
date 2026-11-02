package fishmod.utils.dungeon

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.features.dungeon.PbMessages
import fishmod.utils.config.values.FishSettings
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.debug.FishDebug
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.interfaces.SectionHook
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

object GoldorSection {
    enum class DisplayTerminalSplitsWhen(private val label: String) {
        BOSS("Entire Boss"), TERMINALS_ONLY("Terminals");

        override fun toString(): String = label
    }

    private val splits: Array<RunSplit> = arrayOf(
        RunSplit("1st", "", "", 16755200, 0.0),
        RunSplit("2nd", "", "", 16755200, 0.0),
        RunSplit("3rd", "", "", 16755200, 0.0),
        RunSplit("4th", "", "", 16755200, 0.0),
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
        FishEvents.ON_GAME_MESSAGE.register(GoldorSection::parseMessage)
        FishEvents.ON_LOCATION_CHANGE.register { _ ->
            reset()
            false
        }
        FishEvents.ON_PHASE_CHANGE.register {
            if (FishDebug.termInfo) {
                FishDebug.LOGGER.info("[Section] ON_PHASE_CHANGE phase={} inTerminals={} inGoldorTunnel={} currentSection={}",
                    BossPhase.getPhase(), BossPhase.inTerminals(), BossPhase.inGoldorTunnel(), currentSection)
            }
            if (BossPhase.inP2()) {
                currentSection = 0
            }

            if (BossPhase.inTerminals()) {
                if (FishDebug.termInfo) {
                    FishUtil.addChatMessage(Component.literal("Terminals started"))
                }
                currentSection = 1
                total = totalFor(1)
                splits[0].start()
            } else if (BossPhase.inGoldorTunnel()) {
                if (FishDebug.termInfo) {
                    FishUtil.addChatMessage(Component.literal("Terminals ended"))
                }
                currentSection = 5
                endAllSections()
            }
            false
        }
        FishEvents.ON_SERVER_TICK.register {
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

    @JvmStatic
    fun totalFor(section: Int): Int = if (section == 2) 8 else 7

    private fun incrementSection() {
        if (FishDebug.termInfo) {
            FishDebug.LOGGER.info("[Section] incrementSection: {} -> {} (completed={} total={} gate={})",
                currentSection, currentSection + 1, completed, total, gateBlownUp)
        }
        resetSection()
        endSplit(currentSection)
        currentSection++
        FishDiag.check(currentSection <= 5, "Section.1") { "goldor section advanced past core: $currentSection (completed=$completed total=$total)" }
        total = totalFor(currentSection)

        if (FishDebug.termInfo) {
            FishUtil.addChatMessage(Component.literal("section: $currentSection"))
        }

        FishDiag.guard("Section.2", "ON_SECTION_CHANGE listeners (section $currentSection)") { FishEvents.ON_SECTION_CHANGE.invoke(SectionHook::onSection) }
        startSplit(currentSection)
    }

    private fun endSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= splits.size) return
        finish(index)
    }

    private fun finish(index: Int) {
        val split = splits[index]
        val wasRunning = split.started()
        split.end()
        if (!wasRunning || PracticeMode.active) return
        val floor = BossPhase.getFloor() ?: return
        val time = split.getRealTime()
        FishDiag.check(!time.isNaN() && time >= 0, "Section.3") { "goldor S${index + 1} ended with bad time $time" }
        val r = FishDiag.guard("Section.4", "announce goldor section PB S${index + 1}") {
            PbMessages.announce(FishSettings.pbMessagesGoldor, "goldor:$floor:S${index + 1}",
                Component.literal("§6Goldor S${index + 1}"), time)
        } ?: return
        split.paceColor = BossPhase.paceColor(r, -1.0)
    }

    private fun startSplit(section: Int) {
        val index = section - 1
        if (index < 0 || index >= splits.size) return
        splits[index].start()
    }

    private fun endAllSections() {
        for (i in splits.indices) finish(i)
        if (FishDebug.termInfo) {
            FishUtil.addChatMessage(Component.literal("ending all sections"))
        }
        FishDiag.guard("Section.5", "ON_SECTION_CHANGE listeners (end all)") { FishEvents.ON_SECTION_CHANGE.invoke(SectionHook::onSection) }
    }

    @JvmStatic
    fun parseMessage(message: Component): Boolean {
        if (!BossPhase.inTerminals()) return false
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
                FishDebug.LOGGER.error("Failed to parse terminal message, {}", e.message)
                FishDiag.fail("Section.6", "parse terminal counts in '$string'", e)
                return false
            }

            FishDiag.check(totalNeeded in 1..8 && currentCompleted in 0..totalNeeded, "Section.7") { "odd terminal count $currentCompleted/$totalNeeded section=$currentSection msg='$string'" }

            if (completed == 0 && currentCompleted == totalNeeded) {
                if (FishDebug.termInfo) {
                    FishUtil.addChatMessage(Component.literal(
                        "§cignoring stale terminal echo: section=$currentSection ($currentCompleted/$totalNeeded)"))
                }
                return shouldCancelMessage
            }

            if (FishDebug.termInfo) {
                FishUtil.addChatMessage(Component.literal("name:$name:objective>$objective:($currentCompleted/$totalNeeded)"))
            }

            FishDiag.guard("Section.8", "ON_TERMINAL listeners $name $objective $currentCompleted/$totalNeeded") {
                FishEvents.ON_TERMINAL.invoke { terminalEvent -> terminalEvent.onComplete(name, action, objective, currentCompleted, totalNeeded) }
            }

            if (Floor7Settings.terminalTimeStamps) {
                val texts = message.siblings
                FishDiag.check(texts.isNotEmpty(), "Section.9") { "terminal message has no siblings, can't restyle: '$string'" }
                if (texts.isNotEmpty()) {
                    FishUtil.addChatMessage(
                        Component.literal(name).setStyle(texts.first().style)
                            .append(Component.literal(" §a$action $objective! (§c$currentCompleted§a/$totalNeeded) §8(§7${getSectionTime()}s §8| §7${BossPhase.getPhaseTime(TERM_PHASE_INDEX)}s§8)"))
                    )
                    shouldCancelMessage = true
                }
            }

            if (shouldIncrement(currentCompleted)) {
                if (FishDebug.termInfo) {
                    val why = if (currentCompleted == total && gateBlownUp) "count==total & gate already blown"
                              else "count($currentCompleted) < completed($completed), completed had reached total($total)"
                    FishUtil.addChatMessage(Component.literal("§eincrementSection via device msg §7($why)"))
                }
                incrementSection()
                FishUtil.forceTitle(Component.empty(), message)
            } else {
                total = totalNeeded
                completed = currentCompleted
            }

        } else if (!gateBlownUp) {
            if (string == "The gate has been destroyed!") {
                gateBlownUp = true

                if (FishDebug.termInfo) {
                    FishUtil.addChatMessage(Component.literal(
                        "§egate destroyed msg: section=$currentSection completed=$completed total=$total" +
                            (if (completed == total) " §a-> incrementing now" else " §c-> NOT incrementing (waiting on a device msg)")
                    ))
                }

                if (completed == total) {
                    incrementSection()
                }

                if (Floor7Settings.terminalTimeStamps) {
                    FishUtil.addChatMessage(Component.literal("§aThe gate has been destroyed! §8(§7${getSectionTime()}s §8| §7${BossPhase.getPhaseTime(TERM_PHASE_INDEX)}s§8)"))
                    shouldCancelMessage = true
                }
            }
        } else if (string == "The Core entrance is opening!") {
            currentSection = 5
            endAllSections()
            FishDebug.sendDebugMessage(Component.literal("Core section"))
        }

        return shouldCancelMessage
    }

    @JvmStatic
    fun sectionSplits(): List<RunSplit> = splits.toList()

    @JvmStatic
    fun getSection(): Int = currentSection

    @JvmStatic
    fun isGateBlownUp(): Boolean = gateBlownUp

    @JvmStatic
    fun getSectionTime(): Double {
        val index = currentSection - 1
        if (index < 0 || index >= splits.size) return -1.0
        return splits[index].getRealTime()
    }

    @JvmStatic
    fun display(): Boolean {
        if (enableTerminalSplits && SkyblockLocation.inDungeon()) {
            return when (displayTerminalSplitsWhen) {
                DisplayTerminalSplitsWhen.BOSS -> BossPhase.inBoss()
                DisplayTerminalSplitsWhen.TERMINALS_ONLY -> BossPhase.inTerminals()
            }
        }

        return false
    }

    @JvmStatic
    fun shouldIncrement(recentlyCompleted: Int): Boolean {
        return (recentlyCompleted == total && gateBlownUp) || (recentlyCompleted < completed && completed >= total)
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val x = component.scaledX
        val y = component.scaledY

        val textRenderer: Font = Minecraft.getInstance().font

        try {
            for (i in splits.indices) {
                splits[i].drawSplit(context, textRenderer, x, y + FishConstants.TEXT_HEIGHT * i, SPLIT_LENGTH)
            }
        } catch (t: Throwable) {
            FishDiag.fail("Section.10", "render terminal splits HUD", t)
        }
    }

    @ConfigValue @JvmField
    var terminalSplits: HUDComponent = HUDComponent(
        10.0, 154.0, SPLIT_LENGTH, 50, 1f, "Term splits", { false }, GoldorSection::render, { enableTerminalSplits }
    )
}
