package fishmod.features.dungeon

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

object IceSprayTimer {

    private const val RANGE = 8.0
    private const val CONE_COS = 0.64
    private const val GROUP_RADIUS = 4.0
    private const val FREEZE_MS = 5000L
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    private val frozen = HashMap<LivingEntity, Long>()
    private var lastCastAt = 0L

    @JvmStatic
    fun init() {
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay && FishSettings.iceSprayTimerEnabled && COLOR.replace(msg.string, "").contains("Mana (Ice Spray)")) cast()
        }
        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand ->
            if (FishSettings.iceSprayTimerEnabled && hand == InteractionHand.MAIN_HAND &&
                ItemUtil.getId(player.getItemInHand(hand)) == "ICE_SPRAY_WAND") cast()
            InteractionResult.PASS
        })
        RenderingEvents.GIZMO.register { _ -> render() }
        fishmod.utils.events.Events.ON_WORLD_CHANGE.register { frozen.clear(); false }
    }

    private fun cast() {
        val now = System.currentTimeMillis()
        if (now - lastCastAt < 400) return
        lastCastAt = now
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        val eye = player.eyePosition
        val look = player.lookAngle
        val until = now + FREEZE_MS
        for (e in level.entitiesForRendering()) {
            if (e !is LivingEntity || e is Player || e is ArmorStand || !e.isAlive) continue
            val to = e.boundingBox.center.subtract(eye)
            val dist = to.length()
            if (dist > RANGE || dist < 0.01) continue
            if (to.normalize().dot(look) < CONE_COS) continue
            frozen[e] = until
        }
    }

    private fun render() {
        if (!FishSettings.iceSprayTimerEnabled || frozen.isEmpty()) return
        val now = System.currentTimeMillis()
        frozen.entries.removeIf { (e, until) -> until <= now || !e.isAlive || e.isRemoved }
        val groups = ArrayList<MutableList<Pair<LivingEntity, Long>>>()
        for ((e, until) in frozen) {
            val g = groups.firstOrNull { it[0].first.distanceTo(e) <= GROUP_RADIUS }
            if (g != null) g.add(e to until) else groups.add(mutableListOf(e to until))
        }
        for (g in groups) {
            val x = g.sumOf { it.first.x } / g.size
            val z = g.sumOf { it.first.z } / g.size
            val y = g.maxOf { it.first.boundingBox.maxY } + 0.6
            val secs = (g.minOf { it.second } - now) / 1000.0
            RenderUtils.gizmoText(Component.literal(fishmod.utils.Fmt.f1(secs.toDouble()) + "s"), Vec3(x, y, z),
                FishSettings.iceSprayScale.toFloat(), FishSettings.iceSprayColor or 0xFF000000.toInt())
        }
    }
}
