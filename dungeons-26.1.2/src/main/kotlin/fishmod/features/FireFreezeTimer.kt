package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

/** Fire Freeze Staff timer: renders a countdown at each frozen mob after use. */
object FireFreezeTimer {

    private const val WAIT_MS = 5000L
    private const val FREEZE_MS = 10000L
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 3.0 // Fire Freeze AOE is small (~2.5-3 blocks)

    // entityId -> wall-clock ms when the staff was used (cast start)
    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @JvmStatic
    fun init() {
        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            if (!FishSettings.fireFreezeTimerEnabled || hand != InteractionHand.MAIN_HAND) return@UseItemCallback InteractionResult.PASS
            val mc = Minecraft.getInstance()
            if (mc.player == null || mc.level == null) return@UseItemCallback InteractionResult.PASS
            val stack = player.getItemInHand(hand)
            if (stack == null || stack.isEmpty) return@UseItemCallback InteractionResult.PASS
            if ("FIRE_FREEZE_STAFF" != ItemUtil.getId(stack)) return@UseItemCallback InteractionResult.PASS

            val mcPlayer = mc.player!!
            val start = System.currentTimeMillis()
            val area = mcPlayer.boundingBox.inflate(RADIUS)
            for (e in mc.level!!.getEntities(mcPlayer, area)) {
                if (e is LivingEntity && e !is Player && e.isAlive
                    && e.distanceToSqr(mcPlayer) <= RADIUS * RADIUS
                ) {
                    frozen[e.id] = start
                }
            }
            InteractionResult.PASS
        })

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
                val y = e.y + e.bbHeight / 2.0
                RenderUtils.renderText(ctx, matrices, t, e.x, y, e.z, 1.0f)
            }
        }
    }
}
