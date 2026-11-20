package fishmod.features.storage

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import fishmod.features.extras.ExtrasSettings as X

// Hover a page in the Storage menu to see its cached contents (only when the storage overlay is off)
object BackpackPreview {
    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is AbstractContainerScreen<*>) return@register
            ScreenEvents.afterExtract(screen).register { s, ctx, mx, my, _ ->
                try { render(s as AbstractContainerScreen<*>, ctx, mx, my) } catch (t: Throwable) { FishDiag.fail("BackpackPreview.1", "backpack preview render failed", t) }
            }
        }
    }

    private fun render(screen: AbstractContainerScreen<*>, ctx: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (!X.backpackPreview || FishSettings.storageOverlayEnabled || StoragePage.inRift()) return
        if (fishmod.utils.ScreenTitle.plain(screen) != "Storage") return
        val slot = (screen as HandledScreenAccessor).`fishmod$getHoveredSlot`() ?: return
        val idx = StoragePage.overviewIndex(slot.index) ?: return
        val inv = StorageCache.view()[idx] ?: return
        val stacks = inv.stacks
        if (stacks.isEmpty()) return
        val rows = (stacks.size + 8) / 9
        val w = 9 * 18 + 6
        val h = rows * 18 + 6
        val x = (mx - w - 12).coerceAtLeast(2)
        val y = (my - h / 2).coerceIn(2, (screen.height - h - 2).coerceAtLeast(2))
        val font = Minecraft.getInstance().font
        ctx.pose().pushMatrix()
        try {
            ctx.nextStratum()
            ctx.fill(x, y, x + w, y + h, X.backpackPreviewBg)
            stacks.forEachIndexed { i, st ->
                if (st.isEmpty) return@forEachIndexed
                val sx = x + 3 + (i % 9) * 18 + 1
                val sy = y + 3 + (i / 9) * 18 + 1
                ctx.item(st, sx, sy)
                ctx.itemDecorations(font, st, sx, sy)
            }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
