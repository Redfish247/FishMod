package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.EntityUtil
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

object FireFreezeTimer {

    private const val WAIT_MS = 5000L
    private const val FREEZE_MS = 10000L
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 5.0
    private const val AIM_RANGE = 6.0
    private const val MIN_DIST = 1.6
    private const val CATCH_WINDOW_MS = 2500L
    private const val DRAW_DIST = 28.0

    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @Volatile private var castAt = 0L

    @JvmStatic
    fun init() {
        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand ->
            if (isFireFreezeUse(player, hand)) arm()
            InteractionResult.PASS
        })
        UseBlockCallback.EVENT.register(UseBlockCallback { player, _, hand, _ ->
            if (isFireFreezeUse(player, hand)) arm()
            InteractionResult.PASS
        })
        UseEntityCallback.EVENT.register(UseEntityCallback { player, _, hand, _, _ ->
            if (isFireFreezeUse(player, hand)) arm()
            InteractionResult.PASS
        })

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            if (castAt != 0L && System.currentTimeMillis() - castAt <= CATCH_WINDOW_MS) scanFrozen()
        })

        registerRender()
    }

    private fun isFireFreezeUse(player: Player, hand: InteractionHand): Boolean {
        if (!FishSettings.fireFreezeTimerEnabled || hand != InteractionHand.MAIN_HAND) return false
        if (Minecraft.getInstance().level == null) return false
        val stack = player.getItemInHand(hand)
        return !stack.isEmpty && "FIRE_FREEZE_STAFF" == ItemUtil.getId(stack)
    }

    private fun arm() {
        castAt = System.currentTimeMillis()
        (Minecraft.getInstance().hitResult as? EntityHitResult)?.entity?.let {
            val me = Minecraft.getInstance().player
            if (isFreezable(it) && me != null && it.distanceTo(me) in MIN_DIST..AIM_RANGE) {
                frozen.putIfAbsent(it.id, castAt)
            }
        }
        scanFrozen()
    }

    private fun isFreezable(e: Entity): Boolean =
        e is Monster && e.isAlive && !e.isInvisible && !e.isNoAi && !e.isPassenger

    private fun scanFrozen() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val me = mc.player ?: return
        val box = me.boundingBox.inflate(RADIUS)
        for (e in level.getEntities(null as Entity?, box)) {
            if (isFreezable(e) && e.distanceTo(me) in MIN_DIST..RADIUS) {
                frozen.putIfAbsent(e.id, castAt)
            }
        }
    }

    private fun registerRender() {
        RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!FishSettings.fireFreezeTimerEnabled || frozen.isEmpty()) return@register
            val mc = Minecraft.getInstance()
            val me = mc.player ?: return@register

            val now = System.currentTimeMillis()
            val it = frozen.entries.iterator()
            while (it.hasNext()) {
                val en = it.next()
                val elapsed = now - en.value
                val e: Entity? = mc.level?.getEntity(en.key)
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive || e.distanceTo(me) > 40.0) {
                    it.remove()
                    continue
                }

                val p = EntityUtil.getLerpedPos(e)
                val head = Vec3(p.x, p.y + e.bbHeight + 0.55, p.z)
                val toMob = head.subtract(me.eyePosition)
                if (toMob.length() > DRAW_DIST || toMob.dot(me.lookAngle) <= 0.0) continue

                val t: Component
                if (elapsed < WAIT_MS) {
                    val secs = (WAIT_MS - elapsed) / 1000.0
                    t = Component.literal("§e⌛ " + fishmod.utils.Fmt.f1(secs.toDouble()) + "s")
                } else {
                    val secs = (TOTAL_MS - elapsed) / 1000.0
                    val color = if (secs <= 2.0) "§c" else if (secs <= 5.0) "§b" else "§3"
                    t = Component.literal(color + "❄ " + fishmod.utils.Fmt.f1(secs.toDouble()) + "s")
                }
                RenderUtils.renderText(ctx, matrices, t, head.x, head.y, head.z, 1.35f)
            }
        }
    }
}
