package fishmod.features.inventory

import fishmod.features.extras.*

import fishmod.features.inventory.StorageCache
import fishmod.features.inventory.StoragePage

import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier
import fishmod.features.extras.ExtrasSettings as X

object BackpackPreview {
    private val CHEST: Identifier = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")

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
        val rows = ((stacks.size + 8) / 9).coerceIn(1, 6)
        val w = 176
        val h = 17 + rows * 18 + 7
        val x = (if (mx + 12 + w <= screen.width) mx + 12 else mx - 12 - w).coerceIn(2, (screen.width - w - 2).coerceAtLeast(2))
        val y = (my - 12).coerceIn(2, (screen.height - h - 2).coerceAtLeast(2))
        val font = Minecraft.getInstance().font
        val tint = X.backpackPreviewTint or 0xFF000000.toInt()
        ctx.pose().pushMatrix()
        try {
            ctx.nextStratum()
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHEST, x, y, 0f, 0f, w, 17 + rows * 18, 256, 256, tint)
            ctx.blit(RenderPipelines.GUI_TEXTURED, CHEST, x, y + 17 + rows * 18, 0f, 215f, w, 7, 256, 256, tint)
            ctx.text(font, StoragePage(idx).name, x + 8, y + 6, 0xFF404040.toInt(), false)
            for (i in 0 until minOf(stacks.size, rows * 9)) {
                val st = stacks[i]
                if (st.isEmpty) continue
                val sx = x + 8 + (i % 9) * 18
                val sy = y + 18 + (i / 9) * 18
                ctx.item(st, sx, sy)
                ctx.itemDecorations(font, st, sx, sy)
            }
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
