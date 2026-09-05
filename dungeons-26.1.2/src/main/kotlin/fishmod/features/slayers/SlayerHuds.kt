package fishmod.features.slayers

import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * The three movable Slayer HUDs — Spawn Progress, Slayer Stats, Boss Timer.
 *
 * All three follow the mod's standard simple-HUD contract ([fishmod.features.SoulflowHud]): an
 * `object` with a `renderHud(ctx, tick)` that bails early on its toggles/visibility, then draws a
 * scaled, translated block of `ctx.text`. Each is registered with [FishHudEditor] here and gets a
 * `HudElementRegistry` layer + `DEFAULTS`/`COLUMN_HUDS` entry alongside the others, so it's moved
 * and scaled in the exact same editor as every existing HUD.
 *
 * The render methods only read already-computed cached state ([SlayerManager], [SlayerTimer],
 * [SlayerStatsTracker]) — no scoreboard parsing, no entity scans, no allocation beyond the line
 * strings.
 */
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

    // ------------------------------------------------------------------ Spawn Progress

    @JvmStatic
    fun renderSpawn(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerSpawnHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        if (!SlayerManager.isActiveSlayer()) return
        val type = SlayerManager.type ?: return

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
            FishSettings.slayerSpawnHudScale, lines, background = false)
    }

    // ------------------------------------------------------------------ Slayer Stats

    @JvmStatic
    fun renderStats(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerStatsHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        if (!Location.inSkyblock() || !SlayerStatsTracker.hasData()) return

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
            FishSettings.slayerStatsHudScale, lines, background = FishSettings.slayerStatsBackground)
    }

    // ------------------------------------------------------------------ Slayer Profit

    @JvmStatic
    fun renderProfit(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerProfitEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        if (!Location.inSkyblock()) return
        val type = SlayerManager.type ?: return
        if (!SlayerProfitTracker.hasData(type)) return

        val rows = SlayerProfitTracker.rows(type)
        val lines = ArrayList<String>(rows.size + 5)
        lines.add("§6§l${type.displayName} ${roman(SlayerManager.tier)} Profit" +
            (if (SlayerProfitTracker.isPaused()) " §8§l(idle)" else ""))
        for (r in rows.take(FishSettings.slayerProfitLines.coerceIn(1, 20))) {
            val v = if (r.priced) "§7${SlayerStatsTracker.short(r.value)}" else "§8?"
            lines.add("§b${fmt(r.count.toDouble())}x §f${r.name} $v")
        }
        val cost = SlayerProfitTracker.spawnCost(type)
        if (cost > 0) lines.add("§7Spawn Cost: §c-${SlayerStatsTracker.short(cost)}")
        lines.add("§7Bosses killed: §f${SlayerProfitTracker.bosses(type)}")
        lines.add("§6Total Profit: §a${SlayerStatsTracker.short(SlayerProfitTracker.profit(type))}")
        lines.add("§7$/hr: §6${rate(SlayerProfitTracker.profitPerHour(type))}")

        drawBlock(ctx, FishSettings.slayerProfitHudX, FishSettings.slayerProfitHudY,
            FishSettings.slayerProfitHudScale, lines, background = FishSettings.slayerProfitBackground)
    }

    // ------------------------------------------------------------------ Boss Timer

    @JvmStatic
    fun renderTimer(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerTimerEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        if (!SlayerManager.hasActiveQuest()) return

        val lines = ArrayList<String>(3)
        if (SlayerTimer.running()) {
            if (FishSettings.slayerTimerShowCurrent)
                lines.add("§6Boss: §f${String.format("%.2fs", SlayerTimer.elapsedSeconds())}")
        } else if (SlayerTimer.hasResult()) {
            lines.add("§6Boss: §f${String.format("%.2fs", SlayerTimer.lastResultSeconds())}")
            val type = SlayerManager.type
            if (FishSettings.slayerTimerShowPb && type != null) {
                val pb = SlayerPersonalBests.get(type, SlayerManager.tier)
                lines.add("§7PB: §f" + if (pb > 0) String.format("%.2fs", pb) else "—")
            }
            if (FishSettings.slayerTimerShowNewPb && SlayerTimer.lastWasPb())
                lines.add("§a§lNEW PB!")
        } else return
        if (lines.isEmpty()) return

        drawBlock(ctx, FishSettings.slayerTimerHudX, FishSettings.slayerTimerHudY,
            FishSettings.slayerTimerHudScale, lines, background = false)
    }

    // ------------------------------------------------------------------ shared draw

    private fun drawBlock(
        ctx: GuiGraphicsExtractor, x: Int, y: Int, scale: Double,
        lines: List<String>, background: Boolean,
    ) {
        val mc = Minecraft.getInstance()
        val lh = Constants.TEXT_HEIGHT + 2
        val sc = scale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(x.toFloat(), y.toFloat())
        ctx.pose().scale(sc, sc)
        if (background) {
            var w = 0
            for (l in lines) w = Math.max(w, mc.font.width(l))
            ctx.fill(-3, -2, w + 3, lh * lines.size + 1, 0x80000000.toInt())
        }
        for (i in lines.indices) ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    private fun fmt(v: Double): String = String.format("%,d", v.toLong())
    private fun rate(v: Double): String = if (v <= 0.0) "§8—" else SlayerStatsTracker.short(v)
    private fun rateInt(v: Double): String = if (v <= 0.0) "§8—" else String.format("%,d", v.toLong())

    private fun roman(n: Int): String = when (n) {
        1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"; 5 -> "V"; else -> n.toString()
    }
}
