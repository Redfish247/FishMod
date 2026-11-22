package fishmod.utils.rendering

import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.resources.Identifier

object FishHudLayer {
    @JvmField var inModHud = false
    @JvmField var inTab = false

    // Layers drawn in-world or under the crosshair; never hidden by tab
    private val TAB_EXEMPT = setOf("custom_crosshair","dungeon_waypoints_overlay", "cooldown_overlay_hotbar")

    private class Box { var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = Int.MIN_VALUE; var y1 = Int.MIN_VALUE
        fun reset() { x0 = Int.MAX_VALUE; y0 = Int.MAX_VALUE; x1 = Int.MIN_VALUE; y1 = Int.MIN_VALUE }
        fun add(r: ScreenRectangle) { x0 = minOf(x0, r.left()); y0 = minOf(y0, r.top()); x1 = maxOf(x1, r.right()); y1 = maxOf(y1, r.bottom()) }
        fun empty() = x1 <= x0 || y1 <= y0
        fun hits(o: Box) = !empty() && !o.empty() && x0 < o.x1 && o.x0 < x1 && y0 < o.y1 && o.y0 < y1
        fun copy(o: Box) { x0 = o.x0; y0 = o.y0; x1 = o.x1; y1 = o.y1 }
    }

    private var cur: Box? = null
    private val tabBuild = Box()
    private val tabBox = Box()
    private var tabSeenMs = 0L

    @JvmStatic fun addLast(id: Identifier, el: HudElement) = HudElementRegistry.addLast(id, wrap(id, el))
    @JvmStatic fun addFirst(id: Identifier, el: HudElement) = HudElementRegistry.addFirst(id, wrap(id, el))

    @JvmStatic fun onElement(r: ScreenRectangle?) {
        if (r == null) return
        if (inTab) tabBuild.add(r) else if (inModHud) cur?.add(r)
    }

    @JvmStatic fun tabStart() { inTab = true; tabBuild.reset() }
    @JvmStatic fun tabEnd() {
        inTab = false
        if (!tabBuild.empty()) { tabBox.copy(tabBuild); tabSeenMs = System.currentTimeMillis() }
    }

    private fun tabOpen() = System.currentTimeMillis() - tabSeenMs < 150

    private val panelBox = Box()
    private var panelSeenMs = 0L

    @JvmStatic fun setPanel(x0: Int, y0: Int, x1: Int, y1: Int) {
        panelBox.x0 = x0; panelBox.y0 = y0; panelBox.x1 = x1; panelBox.y1 = y1
        panelSeenMs = System.currentTimeMillis()
    }

    @JvmStatic fun panelSeenMs() = panelSeenMs

    private fun panelOpen() = System.currentTimeMillis() - panelSeenMs < 150

    private fun wrap(id: Identifier, el: HudElement): HudElement {
        val exempt = id.path in TAB_EXEMPT
        val box = Box()
        val frame = Box()
        return HudElement { ctx, tick ->
            if (!exempt && FishSettings.hideHudsUnderTab && tabOpen() && box.hits(tabBox)) return@HudElement
            if (!exempt && panelOpen() && box.hits(panelBox)) return@HudElement
            val prev = inModHud
            val prevBox = cur
            inModHud = true
            frame.reset()
            cur = frame
            try { el.extractRenderState(ctx, tick) } finally {
                inModHud = prev
                cur = prevBox
                box.copy(frame)
            }
        }
    }
}
