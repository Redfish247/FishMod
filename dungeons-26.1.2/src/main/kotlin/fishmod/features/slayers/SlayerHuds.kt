package fishmod.features.slayers

import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Location
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
            110, 14 * 6,
            { FishSettings.slayerStatsHudScale }, { v -> FishSettings.slayerStatsHudScale = v },
            { FishSettings.slayerStatsHudEnabled },
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
        if (mc.player == null || mc.options.hideGui) return
        if (!SlayerManager.isActiveSlayer() || !SlayerManager.inCorrectArea()) return
        val type = FishDiag.notNull(SlayerManager.type, "SlayerHuds.5") { "active slayer with null type in spawn HUD" } ?: return

        val lines = ArrayList<String>(3)
        lines.add("§5§l${type.displayName} ${roman(SlayerManager.tier)}")
        when (SlayerManager.state) {
            SlayerManager.State.GRINDING -> {
                val p = SlayerManager.progress
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
            SlayerManager.State.NONE -> return
        }
        drawBlock(ctx, FishSettings.slayerSpawnHudX, FishSettings.slayerSpawnHudY,
            FishSettings.slayerSpawnHudScale, lines)
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
        if (mc.player == null || mc.options.hideGui) return
        if (!SlayerManager.isActiveSlayer() || !SlayerManager.inCorrectArea() || !SlayerStatsTracker.hasData()) return

        val lines = ArrayList<String>(7)
        lines.add("§5§lSLAYER STATS")
        if (FishSettings.slayerStatsShowXp)
            lines.add("§7XP: §f${SlayerStatsTracker.short(SlayerStatsTracker.xpGained.toDouble())}")
        if (FishSettings.slayerStatsShowKills)
            lines.add("§7Kills: §f${SlayerStatsTracker.kills}")
        if (FishSettings.slayerStatsShowXpHr)
            lines.add("§7XP/hr: §e${rate(SlayerStatsTracker.xpPerHour())}")
        if (FishSettings.slayerStatsShowKillsHr)
            lines.add("§7Kills/hr: §e${rateInt(SlayerStatsTracker.killsPerHour())}")
        if (lines.size == 1) return

        drawBlock(ctx, FishSettings.slayerStatsHudX, FishSettings.slayerStatsHudY,
            FishSettings.slayerStatsHudScale, lines)
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
        if (mc.player == null || mc.options.hideGui) return false
        // keep drawing while chat is open so it can be clicked (SkyHanni behaviour)
        if (mc.screen != null && mc.screen !is net.minecraft.client.gui.screens.ChatScreen && !inInv) return false
        if (!Location.inSkyblock() || !SlayerManager.inCorrectArea()) return false
        val type = SlayerManager.type ?: return false
        val tier = SlayerManager.tier
        if (!SlayerProfitTracker.hasData(type, tier)) return false

        val interactive = mc.screen is net.minecraft.client.gui.screens.ChatScreen
        val rows = SlayerProfitTracker.display(type, tier, interactive)
        val f = mc.font
        val lh = Constants.TEXT_HEIGHT + 2
        // value column right-aligned, item text after it (Mining/Diana style)
        val colX = rows.maxOfOrNull { if (it.value.isEmpty()) 0 else f.width(it.value) + 6 } ?: 0
        var panelW = 0
        for (r in rows) panelW = Math.max(panelW, (if (r.value.isEmpty()) 0 else colX) + f.width(r.label))

        val x = FishSettings.slayerProfitHudX
        val y = FishSettings.slayerProfitHudY
        val sc = FishSettings.slayerProfitHudScale.toFloat()

        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in rows.indices) {
            val r = rows[i]
            if (r.value.isEmpty()) { ctx.text(f, r.label, 0, lh * i, 0xFFFFFFFF.toInt(), true); continue }
            ctx.text(f, r.value, colX - 6 - f.width(r.value), lh * i, 0xFFFFFFFF.toInt(), true)
            ctx.text(f, r.label, colX, lh * i, 0xFFFFFFFF.toInt(), true)
        }
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
        if (mc.player == null || mc.options.hideGui) return
        if (!SlayerManager.hasActiveQuest() || !SlayerManager.inCorrectArea()) return

        val lines = ArrayList<String>(4)
        if (SlayerTimer.running()) {
            if (FishSettings.slayerTimerShowCurrent)
                lines.add("§6Boss: §f${fishmod.utils.Fmt.f2(SlayerTimer.elapsedSeconds())}s")
        } else if (SlayerTimer.hasResult()) {
            lines.add("§6Boss: §f${fishmod.utils.Fmt.f2(SlayerTimer.lastResultSeconds())}s")
            val type = SlayerManager.type
            if (FishSettings.slayerTimerShowPb && type != null) {
                val pb = SlayerPersonalBests.get(type, SlayerManager.tier)
                lines.add("§7PB: §f" + if (pb > 0) fishmod.utils.Fmt.f2(pb) + "s" else "—")
            }
            if (FishSettings.slayerTimerShowNewPb && SlayerTimer.lastWasPb())
                lines.add("§a§lNEW PB!")
        }
        if (FishSettings.slayerTimerShowCycle) {
            if (SlayerTimer.lastCycleSeconds() >= 0.0)
                lines.add("§7Cycle: §f${fishmod.utils.Fmt.f1(SlayerTimer.lastCycleSeconds())}s")
            val since = SlayerTimer.cycleElapsedSeconds()
            if (SlayerTimer.hasCycle() && since in 0.0..1800.0)
                lines.add("§7Since kill: §f${fishmod.utils.Fmt.f1(since)}s")
        }
        if (lines.isEmpty()) return

        drawBlock(ctx, FishSettings.slayerTimerHudX, FishSettings.slayerTimerHudY,
            FishSettings.slayerTimerHudScale, lines)
    }

    private fun drawBlock(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, scale: Double,
        lines: List<String>,
    ) {
        val mc = Minecraft.getInstance()
        val lh = Constants.TEXT_HEIGHT + 2
        val sc = scale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in lines.indices) ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    private fun fmt(v: Double): String = fishmod.utils.Fmt.grouped(v.toLong())
    private fun rate(v: Double): String = if (v <= 0.0) "§8—" else SlayerStatsTracker.short(v)
    private fun rateInt(v: Double): String = if (v <= 0.0) "§8—" else fishmod.utils.Fmt.grouped(v.toLong())

    private fun roman(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"
        else -> { FishDiag.fail("SlayerHuds.6", "slayer tier out of range: $n"); n.toString() }
    }
}
