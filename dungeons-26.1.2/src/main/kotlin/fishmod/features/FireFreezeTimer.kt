package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.EntityUtil
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

/** Fire Freeze Staff timer: renders a countdown at each frozen mob after use. */
object FireFreezeTimer {

    private const val WAIT_MS = 5000L
    private const val FREEZE_MS = 10000L
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 4.5         // matches the black particle cloud Fire Freeze makes
    private const val CATCH_WINDOW_MS = 2000L // keep scanning briefly after the cast — mobs wander in

    // entityId -> wall-clock ms when the staff was used (cast start)
    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @Volatile private var castAt = 0L

    @JvmStatic
    fun init() {
        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            if (!FishSettings.fireFreezeTimerEnabled || hand != InteractionHand.MAIN_HAND) return@UseItemCallback InteractionResult.PASS
            val mc = Minecraft.getInstance()
            if (mc.player == null || mc.level == null) return@UseItemCallback InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (stack == null || stack.isEmpty) return@UseItemCallback InteractionResult.PASS
            if ("FIRE_FREEZE_STAFF" != ItemUtil.getId(stack)) return@UseItemCallback InteractionResult.PASS

            castAt = System.currentTimeMillis()
            scanFrozen()
            InteractionResult.PASS
        })

        // Re-scan for a short window after the cast — the freeze lands when the projectile arrives,
        // not on the click, and mobs can walk into range in between.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            if (castAt != 0L && System.currentTimeMillis() - castAt <= CATCH_WINDOW_MS) scanFrozen()
        })

        registerRender()
    }

    private fun scanFrozen() {
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        for (e in level.getEntities(p, p.boundingBox.inflate(RADIUS))) {
            if (e is LivingEntity && e !is Player && e !is ArmorStand && e.isAlive &&
                e.distanceToSqr(p) <= RADIUS * RADIUS
            ) {
                frozen.putIfAbsent(e.id, castAt)
            }
        }
    }

    private fun registerRender() {
        // World text renders in the single END_MAIN pass (RenderingEvents) — the node collector that
        // submitText() feeds is already drained by AFTER_TRANSLUCENT_FEATURES. Pose is pre-translated
        // by -camera here, so no manual push/translate.
        RenderingEvents.NO_DEPTH_LINE.register { ctx, matrices, _ ->
            if (!FishSettings.fireFreezeTimerEnabled || frozen.isEmpty()) return@register
            val mc = Minecraft.getInstance()
            if (mc.level == null) return@register

            val now = System.currentTimeMillis()
            val it = frozen.entries.iterator()
            while (it.hasNext()) {
                val en = it.next()
                val elapsed = now - en.value
                val e: Entity? = mc.level!!.getEntity(en.key)
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive) {
                    it.remove()
                    continue
                }

                val t: Component
                if (elapsed < WAIT_MS) {
                    // 5s cooldown/wait countdown with an hourglass.
                    val secs = (WAIT_MS - elapsed) / 1000.0
                    t = Component.literal("§e⌛ " + String.format("%.1fs", secs))
                } else {
                    // 10s freeze countdown with a snowflake.
                    val secs = (TOTAL_MS - elapsed) / 1000.0
                    val color = if (secs <= 2.0) "§c" else if (secs <= 5.0) "§b" else "§3"
                    t = Component.literal(color + "❄ " + String.format("%.1fs", secs))
                }
                // Interpolated position + a fixed offset above the head — the old body-centre spot
                // used the un-lerped tick position, so the label lagged behind a moving mob and
                // read as "stuck"/jittery. Draw it clearly above the mob instead.
                val p = EntityUtil.getLerpedPos(e)
                val y = p.y + e.bbHeight + 0.55
                RenderUtils.renderText(ctx, matrices, t, p.x, y, p.z, 1.35f)
            }
        }
    }
}
