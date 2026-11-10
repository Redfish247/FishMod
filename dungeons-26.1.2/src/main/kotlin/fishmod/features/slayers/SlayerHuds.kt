package fishmod.features.slayers
import fishmod.compat.curScreen
import fishmod.compat.hudHidden

import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import kotlin.math.roundToInt

object SlayerHuds {

    const val SPAWN_HUD = "Slayer Spawn"
    const val STATS_HUD = "Slayer Stats"
    const val TIMER_HUD = "Slayer Boss Timer"
    const val PROFIT_HUD = "Slayer Profit"
    const val RNG_HUD = "Slayer RNG Meter"

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            SPAWN_HUD,
            { FishSettings.slayerSpawnHudX }, { v -> FishSettings.slayerSpawnHudX = v },
            { FishSettings.slayerSpawnHudY }, { v -> FishSettings.slayerSpawnHudY = v },
            120, 22,
            { FishSettings.slayerSpawnHudScale }, { v -> FishSettings.slayerSpawnHudScale = v },
            { FishSettings.slayerSpawnHudEnabled },
        )
        FishHudEditor.register(
            STATS_HUD,
            { FishSettings.slayerStatsHudX }, { v -> FishSettings.slayerStatsHudX = v },
            { FishSettings.slayerStatsHudY }, { v -> FishSettings.slayerStatsHudY = v },
            110, 14 * 3,
            { FishSettings.slayerStatsHudScale }, { v -> FishSettings.slayerStatsHudScale = v },
            { FishSettings.slayerStatsHudEnabled },
        )
        FishHudEditor.register(
            RNG_HUD,
            { FishSettings.slayerRngHudX }, { v -> FishSettings.slayerRngHudX = v },
            { FishSettings.slayerRngHudY }, { v -> FishSettings.slayerRngHudY = v },
            160, 12,
            { FishSettings.slayerRngHudScale }, { v -> FishSettings.slayerRngHudScale = v },
            { FishSettings.slayerRngTitleEnabled },
        )
        FishHudEditor.register(
            TIMER_HUD,
            { FishSettings.slayerTimerHudX }, { v -> FishSettings.slayerTimerHudX = v },
            { FishSettings.slayerTimerHudY }, { v -> FishSettings.slayerTimerHudY = v },
            90, 34,
            { FishSettings.slayerTimerHudScale }, { v -> FishSettings.slayerTimerHudScale = v },
            { FishSettings.slayerTimerEnabled },
        )
        FishHudEditor.register(
            PROFIT_HUD,
            { FishSettings.slayerProfitHudX }, { v -> FishSettings.slayerProfitHudX = v },
            { FishSettings.slayerProfitHudY }, { v -> FishSettings.slayerProfitHudY = v },
            150, 14 * 12,
            { FishSettings.slayerProfitHudScale }, { v -> FishSettings.slayerProfitHudScale = v },
            { FishSettings.slayerProfitEnabled },
        )
        registerExamples()
    }

    private val EX_TYPE get() = SlayerType.values()[0]
    private const val EX_TIER = 4

    private fun registerExamples() {
        HudPreview.register(SPAWN_HUD) {
            blockExample(spawnLines(EX_TYPE, EX_TIER, SlayerManager.State.GRINDING, SlayerManager.SpawnProgress(null, 1250.0, 2000.0, "")))
        }
        HudPreview.register(STATS_HUD) { blockExample(statsLines(EX_TYPE, EX_TIER, 1_250_000.0, 4321, 87.0, true)) }
        // state after a new-PB kill; the Show toggles filter lines like in game
        HudPreview.register(TIMER_HUD) { blockExample(timerLines(TimerData(false, 0.0, true, 23.45, true, 23.45, true, 41.2, true, 12.3))) }
        HudPreview.register(RNG_HUD) {
            val f = Minecraft.getInstance().font
            HudExample(f.width(RNG_LABEL) + f.width("§e$EX_RNG"), FishConstants.TEXT_HEIGHT) { ctx -> drawRng(ctx, EX_RNG) }
        }
        HudPreview.register(PROFIT_HUD) {
            val rows = SlayerProfitTracker.exampleDisplay(EX_TYPE, EX_TIER)
            HudExample(profitWidth(rows), profitHeight(rows)) { ctx -> drawProfit(ctx, rows) }
        }
    }

    private fun blockExample(lines: List<String>): HudExample? {
        if (lines.isEmpty()) return null
        val f = Minecraft.getInstance().font
        val lh = FishConstants.TEXT_HEIGHT + 2
        return HudExample(lines.maxOfOrNull { f.width(it) } ?: 0, HudPreview.textHeight(lines.size, lh)) { ctx -> drawLines(ctx, lines) }
    }

    @JvmStatic
    fun renderSpawn(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        try {
            renderSpawnInner(ctx, tick)
        } catch (e: Exception) {
            FishDiag.fail("SlayerHuds.1", "slayer spawn HUD render failed (type=${SlayerManager.type}, state=${SlayerManager.state})", e)
        }
    }

    private fun renderSpawnInner(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerSpawnHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        if (!SlayerManager.isActiveSlayer() || !SlayerManager.inCorrectArea()) return
        val type = FishDiag.notNull(SlayerManager.type, "SlayerHuds.5") { "active slayer with null type in spawn HUD" } ?: return

        val lines = spawnLines(type, SlayerManager.tier, SlayerManager.state, SlayerManager.progress)
        if (lines.isEmpty()) return
        drawBlock(ctx, FishSettings.slayerSpawnHudX, FishSettings.slayerSpawnHudY,
            FishSettings.slayerSpawnHudScale, lines)
    }

    private fun spawnLines(type: SlayerType, tier: Int, state: SlayerManager.State, p: SlayerManager.SpawnProgress?): List<String> {
        val lines = ArrayList<String>(3)
        lines.add("§5§l${type.displayName} ${roman(tier)}")
        when (state) {
            SlayerManager.State.GRINDING -> {
                when {
                    p == null -> lines.add("§7Spawn: §f…")
                    p.current != null && p.max != null ->
                        lines.add("§7Spawn: §f${fmt(p.current)} §7/ §f${fmt(p.max)}" +
                            (p.percent?.let { " §8(${it.toInt()}%)" } ?: ""))
                    p.percent != null -> lines.add("§7Spawn: §f${p.percent.toInt()}%")
                    else -> lines.add("§7Spawn: §f${p.raw}")
                }
            }
            SlayerManager.State.BOSS_SPAWNED -> lines.add("§c§l☠ BOSS SPAWNED")
            SlayerManager.State.COCOONED -> lines.add("§d§lCOCOONED")
            SlayerManager.State.BOSS_SLAIN -> lines.add("§a§lBOSS SLAIN")
            SlayerManager.State.NONE -> return emptyList()
        }
        return lines
    }

    @JvmStatic
    fun renderStats(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        try {
            renderStatsInner(ctx, tick)
        } catch (e: Exception) {
            FishDiag.fail("SlayerHuds.2", "slayer stats HUD render failed (type=${SlayerManager.type}, state=${SlayerManager.state})", e)
        }
    }

    private fun renderStatsInner(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerStatsHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        if (!SlayerManager.isActiveSlayer() || !SlayerManager.inCorrectArea() || !SlayerStatsTracker.hasData()) return

        val type = SlayerManager.type ?: return
        val lines = statsLines(type, SlayerManager.tier, SlayerStatsTracker.xpGained.toDouble(), SlayerStatsTracker.rank(),
            SlayerStatsTracker.kills.toDouble(), SlayerStatsTracker.fromApi())
        if (lines.isEmpty()) return

        drawBlock(ctx, FishSettings.slayerStatsHudX, FishSettings.slayerStatsHudY,
            FishSettings.slayerStatsHudScale, lines)
    }

    private fun statsLines(type: SlayerType, tier: Int, xp: Double, rank: Int, kills: Double, fromApi: Boolean): List<String> {
        val lines = ArrayList<String>(7)
        lines.add("§5§l${type.displayName} ${roman(tier)}")
        if (FishSettings.slayerStatsShowXp)
            lines.add("§7XP: §f${SlayerStatsTracker.short(xp)}" +
                rank.let { if (it > 0) " §8(§e#${fishmod.utils.Fmt.grouped(it.toLong())} §8on leaderboard)" else "" })
        if (FishSettings.slayerStatsShowKills)
            lines.add("§7Kills: §f${fmt(kills)}")
        if (lines.size == 1) return emptyList()
        if (!fromApi) lines.add("§8(session, API not loaded)")
        return lines
    }

    @JvmStatic
    fun renderRng(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        try {
            if (!FishSettings.slayerRngTitleEnabled) return
            val mc = Minecraft.getInstance()
            if (mc.player == null || mc.hudHidden) return
            if (!SlayerManager.isActiveSlayer() || !SlayerManager.inCorrectArea()) return
            val text = SlayerStatsTracker.rngText() ?: "§8no data yet"
            val x = FishSettings.slayerRngHudX
            val y = FishSettings.slayerRngHudY
            val sc = FishSettings.slayerRngHudScale.toFloat()
            ctx.pose().pushMatrix()
            ctx.pose().translate(x.toFloat(), y.toFloat())
            ctx.pose().scale(sc, sc)
            drawRng(ctx, text)
            ctx.pose().popMatrix()
        } catch (e: Exception) {
            FishDiag.fail("SlayerHuds.10", "slayer RNG HUD render failed (type=${SlayerManager.type})", e)
        }
    }

    private const val RNG_LABEL = "RNG Meter: "
    private const val EX_RNG = "5,000 Stored XP"

    private fun drawRng(ctx: GuiGraphicsExtractor, text: String) {
        val f = Minecraft.getInstance().font
        val col = FishSettings.slayerRngTitleColor or 0xFF000000.toInt()
        ctx.text(f, RNG_LABEL, 0, 0, col, true)
        ctx.text(f, "§e$text", f.width(RNG_LABEL), 0, 0xFFFFFFFF.toInt(), true)
    }

    private var profitFrameMs = 0L
    private var profitLeft = 0.0
    private var profitRight = 0.0
    private val profitRowTop = ArrayList<Double>()
    private val profitRowBot = ArrayList<Double>()
    private val profitRowTag = ArrayList<String>()

    @JvmStatic
    fun renderProfit(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        try {
            renderProfitInner(ctx, false)
        } catch (e: Exception) {
            FishDiag.fail("SlayerHuds.3", "slayer profit HUD render failed (type=${SlayerManager.type}, state=${SlayerManager.state})", e)
        }
    }

    // In the inventory it is redrawn on top of the screen with a reset button under it
    @JvmStatic
    fun initInventory() {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is net.minecraft.client.gui.screens.inventory.InventoryScreen) return@register
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register { _, ctx, mx, my, _ ->
                try {
                    if (!renderProfitInner(ctx, true) || !SlayerProfitTracker.sessionMode()) return@register
                    fishmod.features.other.TrackerResetButton.draw(ctx, "slayer", profitLeft.toInt(), profitRowBot.last().toInt() + 2,
                        FishSettings.slayerProfitHudScale, mx, my) { SlayerProfitTracker.reset() }
                } catch (e: Exception) {
                    FishDiag.fail("SlayerHuds.9", "slayer profit inventory render failed", e)
                }
            }
        }
    }

    private fun renderProfitInner(ctx: GuiGraphicsExtractor, inInv: Boolean): Boolean {
        if (!FishSettings.slayerProfitEnabled) return false
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return false
        // keep drawing while chat is open so it can be clicked (SkyHanni behaviour)
        if (mc.curScreen != null && mc.curScreen !is net.minecraft.client.gui.screens.ChatScreen && !inInv) return false
        if (!SkyblockLocation.inSkyblock() || !SlayerManager.inCorrectArea()) return false
        val type = SlayerManager.type ?: return false
        val tier = SlayerManager.tier
        if (!SlayerProfitTracker.hasData(type, tier)) return false

        val interactive = mc.curScreen is net.minecraft.client.gui.screens.ChatScreen
        val rows = SlayerProfitTracker.display(type, tier, interactive)
        val lh = FishConstants.TEXT_HEIGHT + 2
        val panelW = profitWidth(rows)

        val x = FishSettings.slayerProfitHudX
        val y = FishSettings.slayerProfitHudY
        val sc = FishSettings.slayerProfitHudScale.toFloat()

        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        drawProfit(ctx, rows)
        ctx.pose().popMatrix()

        profitFrameMs = System.currentTimeMillis()
        profitLeft = x.toDouble()
        profitRight = x + panelW.toDouble() * sc
        profitRowTop.clear(); profitRowBot.clear(); profitRowTag.clear()
        for (i in rows.indices) {
            profitRowTop.add(y + lh.toDouble() * i * sc)
            profitRowBot.add(y + lh.toDouble() * (i + 1) * sc)
            profitRowTag.add(rows[i].tag)
        }
        return rows.isNotEmpty()
    }

    // value column right-aligned, item text after it (Mining/Diana style)
    private fun profitColX(rows: List<SlayerProfitTracker.DisplayRow>): Int {
        val f = Minecraft.getInstance().font
        return rows.maxOfOrNull { if (it.value.isEmpty()) 0 else f.width(it.value) + 6 } ?: 0
    }

    private fun profitWidth(rows: List<SlayerProfitTracker.DisplayRow>): Int {
        val f = Minecraft.getInstance().font
        val colX = profitColX(rows)
        var panelW = 0
        for (r in rows) panelW = Math.max(panelW, (if (r.value.isEmpty()) 0 else colX) + f.width(r.label))
        return panelW
    }

    private fun profitHeight(rows: List<SlayerProfitTracker.DisplayRow>) = HudPreview.textHeight(rows.size, FishConstants.TEXT_HEIGHT + 2)

    private fun drawProfit(ctx: GuiGraphicsExtractor, rows: List<SlayerProfitTracker.DisplayRow>) {
        val f = Minecraft.getInstance().font
        val lh = FishConstants.TEXT_HEIGHT + 2
        val colX = profitColX(rows)
        for (i in rows.indices) {
            val r = rows[i]
            if (r.value.isEmpty()) { ctx.text(f, r.label, 0, lh * i, 0xFFFFFFFF.toInt(), true); continue }
            ctx.text(f, r.value, colX - 6 - f.width(r.value), lh * i, 0xFFFFFFFF.toInt(), true)
            ctx.text(f, r.label, colX, lh * i, 0xFFFFFFFF.toInt(), true)
        }
    }

    @JvmStatic
    fun onProfitClick(mx: Double, my: Double, button: Int): Boolean = try {
        onProfitClickInner(mx, my, button)
    } catch (e: Exception) {
        FishDiag.fail("SlayerHuds.7", "slayer profit HUD click failed at $mx,$my button $button", e)
        false
    }

    private fun onProfitClickInner(mx: Double, my: Double, button: Int): Boolean {
        if (!FishSettings.slayerProfitEnabled) return false
        if (System.currentTimeMillis() - profitFrameMs > 500) return false
        if (mx < profitLeft || mx > profitRight) return false
        val type = SlayerManager.type ?: return false
        val tier = SlayerManager.tier
        if (!FishDiag.check(profitRowTop.size == profitRowTag.size && profitRowBot.size == profitRowTag.size, "SlayerHuds.8") { "profit row hitboxes out of sync: ${profitRowTop.size}/${profitRowBot.size}/${profitRowTag.size}" }) return false
        for (i in profitRowTag.indices) {
            if (my < profitRowTop[i] || my > profitRowBot[i]) continue
            val tag = profitRowTag[i]
            when {
                tag == "mode" -> {
                    SlayerProfitTracker.cycleMode()
                    fishmod.utils.config.FishConfig.manager.save()
                    return true
                }
                tag == "title" -> if (button == 1) { SlayerProfitTracker.armOrConfirmReset(); return true }
                tag.startsWith("item:") -> if (button == 0) {
                    SlayerProfitTracker.toggleHidden(type, tier, tag.substring(5)); return true
                }
            }
            return false
        }
        return false
    }

    @JvmStatic
    fun renderTimer(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        try {
            renderTimerInner(ctx, tick)
        } catch (e: Exception) {
            FishDiag.fail("SlayerHuds.4", "slayer timer HUD render failed (type=${SlayerManager.type}, state=${SlayerManager.state})", e)
        }
    }

    private fun renderTimerInner(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerTimerEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        if (!SlayerManager.hasActiveQuest() || !SlayerManager.inCorrectArea()) return

        val type = SlayerManager.type
        val pb = if (type != null) SlayerPersonalBests.get(type, SlayerManager.tier) else 0.0
        val lines = timerLines(TimerData(
            SlayerTimer.running(), SlayerTimer.elapsedSeconds(), SlayerTimer.hasResult(), SlayerTimer.lastResultSeconds(),
            type != null, pb, SlayerTimer.lastWasPb(), SlayerTimer.lastCycleSeconds(), SlayerTimer.hasCycle(), SlayerTimer.cycleElapsedSeconds()))
        if (lines.isEmpty()) return

        drawBlock(ctx, FishSettings.slayerTimerHudX, FishSettings.slayerTimerHudY,
            FishSettings.slayerTimerHudScale, lines)
    }

    private class TimerData(
        val running: Boolean, val elapsed: Double, val hasResult: Boolean, val lastResult: Double,
        val hasType: Boolean, val pb: Double, val wasPb: Boolean, val lastCycle: Double, val hasCycle: Boolean, val since: Double,
    )

    private fun timerLines(d: TimerData): List<String> {
        val lines = ArrayList<String>(4)
        if (d.running) {
            if (FishSettings.slayerTimerShowCurrent)
                lines.add("§6Boss: §f${fishmod.utils.Fmt.f2(d.elapsed)}s")
        } else if (d.hasResult) {
            lines.add("§6Boss: §f${fishmod.utils.Fmt.f2(d.lastResult)}s")
            if (FishSettings.slayerTimerShowPb && d.hasType) {
                lines.add("§7PB: §f" + if (d.pb > 0) fishmod.utils.Fmt.f2(d.pb) + "s" else "—")
            }
            if (FishSettings.slayerTimerShowNewPb && d.wasPb)
                lines.add("§a§lNEW PB!")
        }
        if (FishSettings.slayerTimerShowCycle) {
            if (d.lastCycle >= 0.0)
                lines.add("§7Cycle: §f${fishmod.utils.Fmt.f1(d.lastCycle)}s")
            if (d.hasCycle && d.since in 0.0..1800.0)
                lines.add("§7Since kill: §f${fishmod.utils.Fmt.f1(d.since)}s")
        }
        return lines
    }

    private fun drawLines(ctx: GuiGraphicsExtractor, lines: List<String>) =
        HudPreview.drawText(ctx, lines, FishConstants.TEXT_HEIGHT + 2)

    private fun drawBlock(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, scale: Double,
        lines: List<String>,
    ) {
        val sc = scale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        drawLines(ctx, lines)
        ctx.pose().popMatrix()
    }

    private fun fmt(v: Double): String = fishmod.utils.Fmt.grouped(v.toLong())

    private fun roman(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        else -> { FishDiag.fail("SlayerHuds.6", "slayer tier out of range: $n"); n.toString() }
    }
}
