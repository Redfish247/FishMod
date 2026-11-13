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

object GoldorSection {
    enum class SplitsShownIn(private val label: String) {
        BOSS("Whole Boss Fight"), TERMINALS_ONLY("Terminals");

        override fun toString(): String = label
    }

    private const val SPLIT_GOLD = 0xFFAA00
    private val splits: Array<RunSplit> = Array(4) { RunSplit("S${it + 1}", "", "", SPLIT_GOLD, 0.0) }

    private val PROGRESS_LINE = Regex("^(\\w+) (activated|completed) a (terminal|device|lever)! \\((\\d)/(\\d)\\)$")

    @JvmField
    var SPLIT_LENGTH: Int = 120
    private const val P3_PHASE = 6

    private var currentSection = -1
    private var completed = 0
    private var total = 7
    private var gateBlownUp = false

    @ConfigValue @JvmField var goldorSplitsEnabled: Boolean = false

    @ConfigValue @JvmField var goldorSplitsShownIn: SplitsShownIn = SplitsShownIn.TERMINALS_ONLY

    @JvmStatic
    fun init() {
        FishEvents.chat.register(GoldorSection::parseMessage)
        FishEvents.locationChange.register { reset(); false }
        FishEvents.phaseChange.register { onPhase(); false }
        FishEvents.serverTick.register {
            if (currentSection in 1..5) splits.forEach(RunSplit::tick)
            false
        }
    }

    private fun debug(msg: String) {
        if (FishDebug.termInfo) FishUtil.addChatMessage(Component.literal("§7[P3] §f$msg"))
    }

    private fun onPhase() {
        if (FishDebug.termInfo) {
            FishDebug.LOGGER.info("[GoldorSection] phase={} terms={} tunnel={} section={}",
                BossPhase.getPhase(), BossPhase.inTerminals(), BossPhase.inGoldorTunnel(), currentSection)
        }
        if (BossPhase.inP2()) currentSection = 0
        when {
            BossPhase.inTerminals() -> {
                debug("section 1 open")
                currentSection = 1
                total = totalFor(1)
                splits[0].start()
            }
            BossPhase.inGoldorTunnel() -> {
                debug("all sections closed")
                currentSection = 5
                endAllSections()
            }
        }
    }

    private fun reset() {
        currentSection = -1
        completed = 0
        gateBlownUp = false
        splits.forEach(RunSplit::reset)
    }

    @JvmStatic
    fun totalFor(section: Int): Int = if (section == 2) 8 else 7

    private fun nextSection() {
        val from = currentSection
        splits.getOrNull(from - 1)?.let { finish(from - 1) }
        completed = 0
        gateBlownUp = false
        currentSection = from + 1
        total = totalFor(currentSection)
        FishDiag.check(currentSection <= 5, "GoldorSection.1") { "goldor section advanced past core: $currentSection" }
        debug("section $from -> $currentSection")
        FishDiag.guard("GoldorSection.2", "section change listeners (section $currentSection)") { FishEvents.sectionChange.invoke(SectionHook::onSection) }
        splits.getOrNull(currentSection - 1)?.start()
    }

    private fun finish(index: Int) {
        val split = splits[index]
        val wasRunning = split.started()
        split.end()
        if (!wasRunning || PracticeMode.active) return
        val floor = BossPhase.getFloor() ?: return
        val time = split.getRealTime()
        FishDiag.check(!time.isNaN() && time >= 0, "GoldorSection.3") { "goldor S${index + 1} ended with bad time $time" }
        val r = FishDiag.guard("GoldorSection.4", "announce goldor section PB S${index + 1}") {
            PbMessages.announce(FishSettings.pbMessagesGoldor, "goldor:$floor:S${index + 1}",
                Component.literal("§6Goldor S${index + 1}"), time)
        } ?: return
        split.paceColor = BossPhase.paceColor(r, -1.0)
    }

    private fun endAllSections() {
        splits.indices.forEach(::finish)
        debug("closing all splits")
        FishDiag.guard("GoldorSection.5", "section change listeners (end all)") { FishEvents.sectionChange.invoke(SectionHook::onSection) }
    }

    private fun stamp(): String = " §8[§7${getSectionTime()}s §8/ §7${BossPhase.getPhaseTime(P3_PHASE)}s§8]"

    // returns true when the original chat line should be hidden (replaced by a timestamped copy)
    private fun onProgress(message: Component, m: MatchResult): Boolean {
        val (who, verb, kind, nowRaw, ofRaw) = m.destructured
        val now = nowRaw.toInt()
        val of = ofRaw.toInt()
        FishDiag.check(of in 1..8 && now in 0..of, "GoldorSection.7") { "odd terminal count $now/$of section=$currentSection msg='${message.string}'" }

        if (completed == 0 && now == of) {
            debug("§cskipped stale echo $now/$of (section $currentSection)")
            return false
        }
        debug("$who $kind $now/$of")
        FishDiag.guard("GoldorSection.8", "terminal listeners $who $kind $now/$of") {
            FishEvents.terminalStep.invoke { it.onComplete(who, verb, kind, now, of) }
        }

        var hide = false
        if (Floor7Settings.termSplitTimestamps) {
            val first = message.siblings.firstOrNull()
            FishDiag.check(first != null, "GoldorSection.9") { "terminal message has no siblings, can't restyle: '${message.string}'" }
            if (first != null) {
                FishUtil.addChatMessage(Component.literal(who).setStyle(first.style)
                    .append(Component.literal(" §a$verb $kind! (§c$now§a/$of)" + stamp())))
                hide = true
            }
        }

        if (shouldIncrement(now)) {
            debug("§eadvancing on progress line (gate=$gateBlownUp, $now/$total, was $completed)")
            nextSection()
            FishUtil.forceTitle(Component.empty(), message)
        } else {
            total = of
            completed = now
        }
        return hide
    }

    private fun onGate(): Boolean {
        gateBlownUp = true
        debug("§egate down, section $currentSection at $completed/$total" + if (completed == total) " §a(advancing)" else " §c(waiting on progress line)")
        if (completed == total) nextSection()
        if (!Floor7Settings.termSplitTimestamps) return false
        FishUtil.addChatMessage(Component.literal("§aThe gate has been destroyed!" + stamp()))
        return true
    }

    @JvmStatic
    fun parseMessage(message: Component): Boolean {
        if (!BossPhase.inTerminals()) return false
        var shouldCancelMessage = false
        val string = message.string
        val progress = PROGRESS_LINE.find(string)
        if (progress != null) {
            shouldCancelMessage = onProgress(message, progress)
        } else if (!gateBlownUp) {
            if (string == "The gate has been destroyed!") shouldCancelMessage = onGate()
        } else if (string == "The Core entrance is opening!") {
            currentSection = 5
            endAllSections()
            FishDebug.sendDebugMessage(Component.literal("§7[P3] §fcore reached"))
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
    fun getSectionTime(): Double = splits.getOrNull(currentSection - 1)?.getRealTime() ?: -1.0

    @JvmStatic
    fun display(): Boolean = goldorSplitsEnabled && SkyblockLocation.inDungeon() && when (goldorSplitsShownIn) {
        SplitsShownIn.BOSS -> BossPhase.inBoss()
        SplitsShownIn.TERMINALS_ONLY -> BossPhase.inTerminals()
    }

    @JvmStatic
    fun shouldIncrement(latest: Int): Boolean =
        (gateBlownUp && latest == total) || (completed >= total && latest < completed)

    // HUD editor example: S1-S3 done, S4 running
    private val EXAMPLE_SPLITS: Array<RunSplit> by lazy {
        val times = doubleArrayOf(12.34, 13.10, 11.92, 6.40)
        Array(4) { i -> RunSplit("S${i + 1}", "", "", SPLIT_GOLD, 0.0).also { it.setExample(times[i], i == 3) } }
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        val font: Font = Minecraft.getInstance().font
        try {
            (if (fishmod.features.HudPreview.active) EXAMPLE_SPLITS else splits).forEachIndexed { i, s -> s.drawSplit(context, font, component.scaledX, component.scaledY + FishConstants.TEXT_HEIGHT * i, SPLIT_LENGTH) }
        } catch (t: Throwable) {
            FishDiag.fail("GoldorSection.10", "render goldor splits HUD", t)
        }
    }

    @ConfigValue @JvmField
    var goldorSplitsBox: HUDComponent = HUDComponent(
        10.0, 154.0, SPLIT_LENGTH, 50, 1f, "Goldor Splits", { false }, GoldorSection::render, { goldorSplitsEnabled }
    )
}
