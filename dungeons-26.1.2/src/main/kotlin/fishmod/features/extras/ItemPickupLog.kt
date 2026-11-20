package fishmod.features.extras

import fishmod.compat.curScreen
import fishmod.features.FishHudEditor
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as S

// HUD of recent inventory gains/losses, like "+3 Enchanted Gold"
object ItemPickupLog {
    private class Entry(var delta: Int, var at: Long)

    private var last: Map<String, Int>? = null
    private val log = LinkedHashMap<String, Entry>()
    private var tickN = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (++tickN % 5 == 0) try { tick() } catch (t: Throwable) { FishDiag.fail("ItemPickupLog.1", "pickup log tick failed", t) } }
        FishHudEditor.register("Item Pickup Log", { S.pickupLogX }, { S.pickupLogX = it }, { S.pickupLogY }, { S.pickupLogY = it }, 120, 50,
            { S.pickupLogScale }, { S.pickupLogScale = it })
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "item_pickup_log")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("ItemPickupLog.2", "pickup log render failed", t) }
        }
    }

    private fun tick() {
        val mc = Minecraft.getInstance()
        val p = mc.player
        if (!S.pickupLog || p == null || !SkyblockLocation.inSkyblock()) { last = null; return }
        val now = HashMap<String, Int>()
        for (i in 0 until 36) {
            val st = p.inventory.getItem(i)
            if (st.isEmpty) continue
            val name = TextStrip.plain(st.hoverName.string).trim()
            now.merge(name, st.count, Int::plus)
        }
        val prev = last
        last = now
        // Menus move items around: only rebase while one is open
        if (prev == null || mc.curScreen != null) return
        val t = System.currentTimeMillis()
        for (k in prev.keys + now.keys) {
            val d = (now[k] ?: 0) - (prev[k] ?: 0)
            if (d == 0) continue
            val e = log.remove(k)
            log[k] = if (e != null && t - e.at < S.pickupLogSeconds * 1000L) e.also { it.delta += d; it.at = t } else Entry(d, t)
        }
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.pickupLog || !SkyblockLocation.inSkyblock()) return
        val t = System.currentTimeMillis()
        log.entries.removeIf { t - it.value.at > S.pickupLogSeconds * 1000L || it.value.delta == 0 }
        if (log.isEmpty()) return
        val mc = Minecraft.getInstance()
        val sc = S.pickupLogScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.pickupLogX.toFloat(), S.pickupLogY.toFloat())
            ctx.pose().scale(sc, sc)
            log.entries.toList().takeLast(10).forEachIndexed { i, (name, e) ->
                val s = if (e.delta > 0) "+${e.delta} $name" else "${e.delta} $name"
                ctx.text(mc.font, Component.literal(s), 0, i * 10, if (e.delta > 0) S.pickupLogGainColor else S.pickupLogLossColor, true)
            }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
