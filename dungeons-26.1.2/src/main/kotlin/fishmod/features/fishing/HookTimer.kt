package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3

object HookTimer {
    @JvmField @Volatile var hiddenStandId = -1

    @Volatile private var label: Component? = null
    @Volatile private var pos: Vec3 = Vec3.ZERO
    @Volatile private var biteScale = 1f
    private var lastText = ""
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            val l = label ?: return@register
            FishDiag.guard("HookTimer.1", "hook timer text render failed") {
                FishDraw.renderText(ctx, ps, l, pos, biteScale)
            }
        }
    }

    private fun clear() {
        if (label != null || hiddenStandId != -1) { label = null; hiddenStandId = -1; lastText = "" }
    }

    private fun onTick() {
        if (!FishingWorld.anyHookTimer) { clear(); return }
        if (++tick % 2 != 0) return
        val mc = Minecraft.getInstance()
        val player = mc.player
        val level = mc.level
        val hook = player?.fishing
        if (level == null || hook == null || hook.isRemoved || !Fishing.active()) { clear(); return }
        try {
            var found: ArmorStand? = null
            var text: String? = null
            for (s in level.getEntitiesOfClass(ArmorStand::class.java, hook.boundingBox.inflate(5.0))) {
                val c = s.customName ?: continue
                val p = TextStrip.plain(c.string)
                if (p.isEmpty()) continue
                if (p == "!!!" || isSeconds(p)) { found = s; text = p; break }
            }
            if (found == null || text == null) { clear(); return }
            hiddenStandId = if (W.hookTimerHideStand) found.id else -1
            pos = Vec3(hook.x, hook.y + 0.9, hook.z)
            if (text != lastText) {
                lastText = text
                val bite = text == "!!!"
                label = Component.literal(if (bite) W.hookBiteText else "§e$text")
                biteScale = W.hookTimerScale.toFloat() * if (bite) 1.4f else 1f
            }
        } catch (t: Throwable) {
            FishDiag.fail("HookTimer.2", "hook timer scan failed", t)
        }
    }

    private fun isSeconds(s: String): Boolean {
        var dot = false
        for (i in s.indices) {
            val ch = s[i]
            if (ch == '.') { if (dot || i == 0 || i == s.length - 1) return false; dot = true }
            else if (ch !in '0'..'9') return false
        }
        return true
    }
}
