package fishmod.features.diana

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

// Keybinds that /warp to the hub warp closest to the current guess or rare mob
object DianaWarp {

    private class Warp(val name: String, val pos: Vec3, val enabled: () -> Boolean, val extra: Int = 0, val preferOver: String? = null)

    private val WARPS = listOf(
        Warp("hub", Vec3(0.5, 77.0, -0.5), { true }),
        Warp("castle", Vec3(-250.0, 130.0, 45.0), { DianaSettings.dianaWarpCastle }, preferOver = "crypt"),
        Warp("wizard", Vec3(44.5, 119.0, 93.5), { DianaSettings.dianaWarpWizard }),
        Warp("crypt", Vec3(-160.5, 62.0, -106.5), { DianaSettings.dianaWarpCrypt }, extra = 10),
        Warp("stonks", Vec3(-36.5, 70.0, -81.5), { DianaSettings.dianaWarpStonks }),
        Warp("da", Vec3(91.5, 75.0, 173.5), { DianaSettings.dianaWarpDa }),
        Warp("taylor", Vec3(29.5, 73.0, -41.5), { DianaSettings.dianaWarpTaylor }),
        Warp("museum", Vec3(29.5, 72.0, 1.5), { DianaSettings.dianaWarpMuseum }),
    )

    @JvmStatic lateinit var guessKey: KeyMapping; private set
    @JvmStatic lateinit var rareMobKey: KeyMapping; private set
    private var lastWarpMs = 0L
    private var lastTitle: String? = null

    fun init() {
        val cat = fishmod.utils.Keybinds.category()
        guessKey = KeyMappingHelper.registerKeyMapping(KeyMapping("Diana Guess Warp", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, cat))
        rareMobKey = KeyMappingHelper.registerKeyMapping(KeyMapping("Diana Rare Mob Warp", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, cat))
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
    }

    private fun tick() {
        while (guessKey.consumeClick()) warp(false)
        while (rareMobKey.consumeClick()) warp(true)
        if (!Diana.active()) return
        val me = Diana.player()?.position() ?: return
        val rare = DianaWaypoints.newestRareMob()
        val target = rare ?: DianaWaypoints.closestTarget(me)
        DianaWaypoints.list.forEach { it.warpHint = null }
        val w = target?.let { finalWarp(it.center, me, fixed = rare != null) }
        target?.warpHint = w?.name
        if (DianaSettings.dianaWarpTitle && w != null && w.name != lastTitle) {
            val col = if (rare != null) "§d" else "§b"
            val line = Component.literal("${col}Warp §e${w.name.replaceFirstChar { it.uppercase() }}")
            if (DianaSettings.dianaWarpTitleSubtitle) Misc.forceTitle(Component.empty(), line, 1500)
            else Misc.forceTitle(line, Component.empty(), 1500)
        }
        lastTitle = w?.name
    }

    internal fun warp(rareMob: Boolean) {
        if (!Diana.active()) return
        val now = System.currentTimeMillis()
        if (now - lastWarpMs < 500) return
        val me = Diana.player()?.position() ?: return
        val rare = DianaWaypoints.newestRareMob()
        val target = if (rareMob) rare ?: return else DianaWaypoints.closestTarget(me) ?: return
        val w = finalWarp(target.center, me, fixed = rareMob) ?: return
        lastWarpMs = now
        Diana.player()?.connection?.sendCommand("warp ${w.name}")
    }

    // Follows up to 10 hops so a warp is only suggested when it's the end of the chain
    private fun finalWarp(target: Vec3, from: Vec3, fixed: Boolean): Warp? {
        var pos = from
        var tgt = target
        var result: Warp? = null
        repeat(10) {
            val w = closestWarp(tgt, pos) ?: return result
            if (w === result) return result
            result = w
            pos = w.pos
            if (!fixed) tgt = DianaWaypoints.closestTarget(pos)?.center ?: return result
        }
        return result
    }

    private fun closestWarp(target: Vec3, me: Vec3): Warp? {
        val sorted = WARPS.filter { it.enabled() }.sortedBy { it.pos.distanceTo(target) }
        var best = sorted.firstOrNull() ?: return null
        val second = sorted.getOrNull(1)
        val bad = DianaSettings.dianaBadWarpDistance
        if (second != null && bad > 0 && second.preferOver == best.name && second.pos.distanceTo(target) - best.pos.distanceTo(target) < bad) best = second
        val warpDist = best.pos.distanceTo(target)
        val meDist = me.distanceTo(target)
        if (meDist <= warpDist + DianaSettings.dianaWarpBlockDiff + best.extra) return null
        if (DianaSettings.dianaDontWarpNearBurrow && (meDist <= 60 || me.distanceTo(best.pos) <= 60)) return null
        return best
    }
}
