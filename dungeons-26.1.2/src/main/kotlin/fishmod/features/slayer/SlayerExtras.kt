package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks

object SlayerExtras {

    // from SkyHanni (SlayerApi: cocoons burst after 5s)
    private const val COCOON_MS = 5_000L
    // from SkyHanni (SummoningMobManager)
    private val RECALL = Regex("^The Seraph recalled your (\\d+) summoned allies!$")

    private var cocoonAt = 0L

    fun init() {
        FishEvents.chat.register { text ->
            if (S.summonsRecallAlert) {
                RECALL.find(text.chatPlain.trim())?.let { m -> title("§c§lSUMMONS RECALLED", "§7Seraph took §f${m.groupValues[1]}§7 allies", 2000) }
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        RenderHooks.GIZMO.register { _ ->
            try { renderCocoon() } catch (e: Exception) { FishDiag.fail("SlayerExtras.2", "cocoon timer render failed", e) }
        }
    }

    private fun onTick() {
        if (!S.cocoonTimer || SlayerManager.state != SlayerManager.State.COCOONED) { cocoonAt = 0L; return }
        if (cocoonAt == 0L) cocoonAt = System.currentTimeMillis()
    }

    private fun renderCocoon() {
        if (!S.cocoonTimer || cocoonAt == 0L) return
        val boss = SlayerManager.bossEntity ?: return
        val left = (COCOON_MS - (System.currentTimeMillis() - cocoonAt)) / 1000.0
        if (left < 0) return
        val name = SlayerManager.type?.displayName ?: "Boss"
        FishDraw.gizmoText(Component.literal("§d$name §f" + String.format("%.1f", left) + "s"),
            Vec3(boss.x, boss.y + boss.bbHeight + 0.6, boss.z), 1.4f, -0x1)
    }

    private fun title(main: String, sub: String, ms: Int) {
        try {
            FishUtil.forceTitle(Component.literal(main), Component.literal(sub), ms)
        } catch (e: Exception) {
            FishDiag.fail("SlayerExtras.1", "slayer extras title failed", e)
        }
    }
}
