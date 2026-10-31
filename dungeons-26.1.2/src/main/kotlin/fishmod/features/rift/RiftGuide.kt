package fishmod.features.rift

import fishmod.compat.curScreen
import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import fishmod.features.rift.RiftSettings as S

// Rift Guide overlay: caches unfinished tasks of every guide page you open and draws them as a HUD
object RiftGuide {

    private const val NAME = "Rift Guide"
    private const val BACK_SLOT = 40
    private const val LINE_H = 10

    // page title -> unfinished task names, in the order the pages were opened
    private val pages = LinkedHashMap<String, List<String>>()
    @Volatile private var rows: List<String> = emptyList()

    fun init() {
        FishHudEditor.register(NAME,
            { S.riftGuideX }, { S.riftGuideX = it }, { S.riftGuideY }, { S.riftGuideY = it }, 140, 80,
            { S.riftGuideScale }, { S.riftGuideScale = it }, { S.riftGuide })
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("RiftGuide.1", "guide scan failed") { scan(mc) } }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { pages.clear(); rows = emptyList(); false }
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "rift_guide")) { ctx, _ ->
            val mc = Minecraft.getInstance()
            if (!S.riftGuide || mc.player == null || mc.hudHidden || FishHudEditor.isOpen() || mc.curScreen is AbstractContainerScreen<*>) return@addLast
            if (!Rift.inRift() || rows.isEmpty()) return@addLast
            val pose = ctx.pose()
            pose.pushMatrix()
            pose.translate(S.riftGuideX.toFloat(), S.riftGuideY.toFloat())
            pose.scale(S.riftGuideScale.toFloat(), S.riftGuideScale.toFloat())
            val color = S.riftGuideColor and 0xFFFFFF
            rows.take(S.riftGuideMaxLines.coerceAtLeast(1)).forEachIndexed { i, r ->
                val head = r.startsWith("#")
                ctx.text(mc.font, if (head) r.substring(1) else "• $r", 0, i * LINE_H, if (head) -1 else (0xFF000000.toInt() or color), true)
            }
            pose.popMatrix()
        }
    }

    // Guide sub-pages have a "To Rift Guide" back button in slot 40; open tasks end in "✖ Not completed yet!" (SkyHanni HighlightRiftGuide.kt)
    private fun scan(mc: Minecraft) {
        if (!S.riftGuide) return
        val screen = mc.curScreen as? AbstractContainerScreen<*> ?: return
        val slots = screen.menu.slots
        if (slots.size <= BACK_SLOT) return
        val back = lore(slots[BACK_SLOT].item)
        if (back.size != 1 || !back[0].startsWith("To Rift Guide")) return
        val pending = ArrayList<String>()
        for (i in 0 until BACK_SLOT) {
            val st = slots[i].item
            if (st.isEmpty) continue
            if (lore(st).lastOrNull() == "✖ Not completed yet!") pending += Rift.strip(st.hoverName.string)
        }
        val title = fishmod.utils.ScreenTitle.plain(screen)
        if (pages[title] == pending) return
        pages[title] = pending
        rebuild()
    }

    private fun lore(st: net.minecraft.world.item.ItemStack): List<String> =
        st.get(DataComponents.LORE)?.lines()?.map { it.string } ?: emptyList()

    private fun rebuild() {
        val out = ArrayList<String>()
        for ((t, items) in pages) {
            if (items.isEmpty()) continue
            out += "#§e§l$t"
            out += items
        }
        rows = out
    }
}
