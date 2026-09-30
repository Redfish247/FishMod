package fishmod.cosmetic

import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.player.RemotePlayer
import java.lang.reflect.Method

// EntityCulling drops culled players' nametags (or draws a bare vanilla one), so keep nearby players un-culled.
object NametagCullingCompat {
    private const val RANGE_SQ = 64.0 * 64.0

    @JvmStatic
    fun init() {
        if (!FabricLoader.getInstance().isModLoaded("entityculling")) return
        val setTimeout: Method = try {
            Class.forName("dev.tr7zw.entityculling.access.Cullable").getMethod("setTimeout")
        } catch (t: Throwable) { FishDiag.fail("NametagCullingCompat.1", "EntityCulling Cullable.setTimeout not found", t); return }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val self = mc.player ?: return@register
            val level = mc.level ?: return@register
            for (p in level.players()) {
                if (p !is RemotePlayer || p.isInvisible || p.distanceToSqr(self) > RANGE_SQ) continue
                try { setTimeout.invoke(p) } catch (t: Throwable) { FishDiag.fail("NametagCullingCompat.2", "Cullable.setTimeout invoke failed", t) }
            }
        }
    }
}
