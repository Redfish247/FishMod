package fishmod.features.fishing

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

// Fishing alert titles drawn as movable HUD slots (one per alert kind) instead of the vanilla title
object FishingTitles {
    class Slot(
        val name: String, val id: String, val w: Int, val h: Int, val centerOffsetY: Int,
        val gx: () -> Int, val sx: (Int) -> Unit, val gy: () -> Int, val sy: (Int) -> Unit,
        val gs: () -> Double, val ss: (Double) -> Unit,
    ) {
        var main = ""; var sub = ""; var start = 0L; var stayMs = 2000
        // -1 means centred until the player moves it
        fun x(): Int = gx().takeIf { it >= 0 } ?: ((Minecraft.getInstance().window.guiScaledWidth - w * gs()) / 2).toInt()
        fun y(): Int = gy().takeIf { it >= 0 } ?: (Minecraft.getInstance().window.guiScaledHeight / 2 + centerOffsetY)
        fun alpha(now: Long): Float {
            if (start == 0L) return 0f
            val t = now - start
            return when {
                t < 250 -> t / 250f
                t < 250 + stayMs -> 1f
                t < 250 + stayMs + 500 -> 1f - (t - 250 - stayMs) / 500f
                else -> 0f
            }
        }
    }

    private val S = FishingAlertSettings

    val CREATURE = Slot("Fishing Creature Alert", "fishing_t_creature", 240, 34, -70, { S.tCreatureX }, { S.tCreatureX = it }, { S.tCreatureY }, { S.tCreatureY = it }, { S.tCreatureScale }, { S.tCreatureScale = it })
    val DROP = Slot("Fishing Rare Drop Alert", "fishing_t_drop", 240, 34, -30, { S.tDropX }, { S.tDropX = it }, { S.tDropY }, { S.tDropY = it }, { S.tDropScale }, { S.tDropScale = it })
    val HOTSPOT = Slot("Fishing Hotspot Alert", "fishing_t_hotspot", 200, 34, 10, { S.tHotspotX }, { S.tHotspotX = it }, { S.tHotspotY }, { S.tHotspotY = it }, { S.tHotspotScale }, { S.tHotspotScale = it })
    val WORMHOLE = Slot("Fishing Wormhole Alert", "fishing_t_wormhole", 200, 34, 50, { S.tWormholeX }, { S.tWormholeX = it }, { S.tWormholeY }, { S.tWormholeY = it }, { S.tWormholeScale }, { S.tWormholeScale = it })
    val TROPHY = Slot("Fishing Trophy Alert", "fishing_t_trophy", 220, 34, 90, { S.tTrophyX }, { S.tTrophyX = it }, { S.tTrophyY }, { S.tTrophyY = it }, { S.tTrophyScale }, { S.tTrophyScale = it })
    val DEPLOYABLE = Slot("Fishing Deployable Alert", "fishing_t_deploy", 220, 34, 130, { S.tDeployX }, { S.tDeployX = it }, { S.tDeployY }, { S.tDeployY = it }, { S.tDeployScale }, { S.tDeployScale = it })
    val BAIT = Slot("Fishing Bait Alert", "fishing_t_bait", 200, 34, 170, { S.tBaitX }, { S.tBaitX = it }, { S.tBaitY }, { S.tBaitY = it }, { S.tBaitScale }, { S.tBaitScale = it })
    private val ALL = listOf(CREATURE, DROP, HOTSPOT, WORMHOLE, TROPHY, DEPLOYABLE, BAIT)

    fun init() {
        for (s in ALL) {
            FishHudEditor.register(s.name, { s.x() }, { v -> s.sx(v) }, { s.y() }, { v -> s.sy(v) }, s.w, s.h,
                { s.gs() }, { v -> s.ss(v) }, { Fishing.enabled() && S.fishingAlerts })
            fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", s.id)) { ctx, _ ->
                if (!FishHudEditor.isOpen()) {
                    val a = s.alpha(System.currentTimeMillis())
                    if (a > 0.02f) draw(ctx, s, a)
                }
            }
        }
    }

    fun show(s: Slot, main: String, sub: String, stayMs: Int) {
        s.main = main; s.sub = sub; s.stayMs = stayMs; s.start = System.currentTimeMillis()
    }

    private fun draw(ctx: GuiGraphicsExtractor, s: Slot, alpha: Float) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden || s.main.isEmpty()) return
        val font = mc.font
        val color = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(s.x().toFloat(), s.y().toFloat())
        val sc = s.gs().toFloat()
        pose.scale(sc, sc)
        val cx = s.w / 2f
        pose.pushMatrix()
        pose.translate(cx, 0f)
        pose.scale(2f, 2f)
        ctx.text(font, s.main, -font.width(s.main) / 2, 0, color, true)
        pose.popMatrix()
        if (s.sub.isNotEmpty()) {
            pose.pushMatrix()
            pose.translate(cx, font.lineHeight * 2f + 2f)
            pose.scale(1.2f, 1.2f)
            ctx.text(font, s.sub, -font.width(s.sub) / 2, 0, color, true)
            pose.popMatrix()
        }
        pose.popMatrix()
    }
}
