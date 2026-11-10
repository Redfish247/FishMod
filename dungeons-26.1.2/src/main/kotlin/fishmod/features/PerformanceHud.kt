package fishmod.features
import fishmod.compat.hudHidden

import fishmod.utils.debug.FishDiag
import fishmod.features.dungeon.PartyCommandHandler
import fishmod.utils.PingTracker
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PerformanceHud {

    private const val NAME = "Performance"
    private const val LINE_H = 10
    const val CLOCK_NAME = "Clock"

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.perfHudX }, { v -> FishSettings.perfHudX = v },
            { FishSettings.perfHudY }, { v -> FishSettings.perfHudY = v },
            110, 30,
            { FishSettings.perfHudScale }, { v -> FishSettings.perfHudScale = v },
            { FishSettings.perfHudEnabled }
        )
        FishHudEditor.register(
            CLOCK_NAME,
            { FishSettings.clockHudX }, { v -> FishSettings.clockHudX = v },
            { FishSettings.clockHudY }, { v -> FishSettings.clockHudY = v },
            50, 10,
            { FishSettings.clockHudScale }, { v -> FishSettings.clockHudScale = v },
            { FishSettings.perfHudEnabled && clockDetached() }
        )
        // Example values (fps 144, tps 19.9, ping 42) through the real line builder
        HudPreview.register(NAME) { block(lines(144, 19.9, 42)).takeIf { it.isNotEmpty() }?.let { HudPreview.text(it, LINE_H) } }
        HudPreview.register(CLOCK_NAME) { if (clockDetached()) HudPreview.text(listOf(clockText())) else null }
    }

    private fun lines(mc: Minecraft): List<String> = lines(mc.fps, PartyCommandHandler.currentTps(), ping(mc))

    // Shared by the in-game HUD (live values) and the HUD editor example (fixed values)
    private fun lines(fps: Int, tps: Double, ping: Int): List<String> {
        val label = "§7"
        val out = ArrayList<String>(3)
        if (FishSettings.perfHudFps) {
            val c = when { fps < 30 -> "§c"; fps < 60 -> "§e"; else -> "§a" }
            out.add("${label}FPS: $c$fps")
        }
        if (FishSettings.perfHudTps) {
            val v = if (tps < 0) "§c—" else (when { tps < 15 -> "§c"; tps < 19.5 -> "§e"; else -> "§a" }) + fishmod.utils.Fmt.f1(tps)
            out.add("${label}TPS: $v")
        }
        if (FishSettings.perfHudPing) {
            val v = if (ping < 0) "§c—" else (when { ping > 200 -> "§c"; ping > 100 -> "§e"; else -> "§a" }) + "${ping}ms"
            out.add("${label}Ping: $v")
        }
        if (FishSettings.perfHudClock && !FishSettings.perfHudClockDetached) out.add(clockText())
        return out
    }

    private fun clockDetached() = FishSettings.perfHudClock && FishSettings.perfHudClockDetached

    // Text block as drawn in game: vertical lines or one joined line
    private fun block(ls: List<String>): List<String> = if (FishSettings.perfHudHorizontal && ls.isNotEmpty()) listOf(ls.joinToString("  ")) else ls

    private val FMT_12 = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)
    private val FMT_12_NS = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val FMT_24 = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val FMT_24_NS = DateTimeFormatter.ofPattern("HH:mm")

    @JvmStatic
    fun clockText(): String {
        val sec = FishSettings.perfHudClockSeconds
        val fmt = if (FishSettings.perfHudClock24h) (if (sec) FMT_24 else FMT_24_NS) else (if (sec) FMT_12 else FMT_12_NS)
        return "§f" + LocalTime.now().format(fmt)
    }

    private fun ping(mc: Minecraft): Int {
        val live = PingTracker.latest()
        if (live > 0) return live
        val self = mc.player?.let { mc.connection?.getPlayerInfo(it.uuid) }
        return if (self != null && self.latency > 0) self.latency else -1
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.perfHudEnabled) return
        try {
            renderInner(ctx)
        } catch (t: Throwable) {
            FishDiag.fail("PerformanceHud.1", "performance HUD render failed", t)
        }
    }

    private fun renderInner(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        if (clockDetached()) {
            val sc = FishSettings.clockHudScale.toFloat()
            ctx.pose().pushMatrix()
            ctx.pose().translate(FishSettings.clockHudX.toFloat(), FishSettings.clockHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, clockText(), 0, 0, -1, true)
            ctx.pose().popMatrix()
        }
        val ls = lines(mc)
        if (ls.isEmpty()) return

        val sc = FishSettings.perfHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.perfHudX.toFloat(), FishSettings.perfHudY.toFloat())
        ctx.pose().scale(sc, sc)
        HudPreview.drawText(ctx, block(ls), LINE_H)
        ctx.pose().popMatrix()
    }
}
