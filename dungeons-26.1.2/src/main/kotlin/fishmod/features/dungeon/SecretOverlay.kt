package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object SecretOverlay {

    private const val NAME = "Secret Overlay"
    private const val STALE_MS = 2500L

    private var found = 0
    private var total = 0
    private var seenAt = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.secretOverlayX }, { v -> FishSettings.secretOverlayX = v },
            { FishSettings.secretOverlayY }, { v -> FishSettings.secretOverlayY = v },
            70, 10,
            { FishSettings.secretOverlayScale }, { v -> FishSettings.secretOverlayScale = v }
        )
    }

    @JvmStatic
    fun onSecrets(f: Int, t: Int) {
        found = f; total = t; seenAt = System.currentTimeMillis()
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.secretOverlayEnabled || !Location.inDungeon()) return
        if (System.currentTimeMillis() - seenAt > STALE_MS || total <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val c = when { found >= total -> "§a"; found > 0 -> "§e"; else -> "§c" }
        val sc = FishSettings.secretOverlayScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.secretOverlayX.toFloat(), FishSettings.secretOverlayY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "§7Secrets: $c$found§7/$c$total", 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
