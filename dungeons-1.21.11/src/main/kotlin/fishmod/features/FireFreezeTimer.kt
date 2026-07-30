package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.rendering.RenderUtils
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import java.util.concurrent.ConcurrentHashMap

/** Fire Freeze Staff timer — when you use the staff, nearby mobs are frozen for 5s. */
object FireFreezeTimer {

    private const val WAIT_MS = 5000L // cooldown/wait countdown before freeze
    private const val FREEZE_MS = 10000L // freeze duration (10s)
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 3.0 // Fire Freeze AOE is small (~2.5-3 blocks)

    // entityId -> wall-clock ms when the staff was used (cast start)
    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @JvmStatic
    fun init() {
        UseItemCallback.EVENT.register(UseItemCallback { player, world, hand ->
            if (!FishSettings.fireFreezeTimerEnabled || hand != Hand.MAIN_HAND) return@UseItemCallback ActionResult.PASS
            val mc = MinecraftClient.getInstance()
            if (mc.player == null || mc.world == null) return@UseItemCallback ActionResult.PASS
            val stack = player.getStackInHand(hand)
            if (stack == null || stack.isEmpty) return@UseItemCallback ActionResult.PASS
            if ("FIRE_FREEZE_STAFF" != ItemUtil.getId(stack)) return@UseItemCallback ActionResult.PASS

            val start = System.currentTimeMillis()
            val area = mc.player!!.boundingBox.expand(RADIUS)
            for (e in mc.world!!.getOtherEntities(mc.player, area)) {
                if (e is LivingEntity && e !is PlayerEntity && e.isAlive
                    && e.squaredDistanceTo(mc.player) <= RADIUS * RADIUS
                ) {
                    frozen[e.id] = start
                }
            }
            ActionResult.PASS
        })

        WorldRenderEvents.AFTER_ENTITIES.register(WorldRenderEvents.AfterEntities { ctx ->
            if (!FishSettings.fireFreezeTimerEnabled || frozen.isEmpty() || ctx.worldState() == null) return@AfterEntities
            val mc = MinecraftClient.getInstance()
            if (mc.world == null) return@AfterEntities
            val matrices = ctx.matrices()
            if (matrices == null) return@AfterEntities

            val now = System.currentTimeMillis()
            val cam = ctx.worldState().cameraRenderState.pos
            matrices.push()
            matrices.translate(-cam.x, -cam.y, -cam.z)

            val it = frozen.entries.iterator()
            while (it.hasNext()) {
                val en = it.next()
                val elapsed = now - en.value
                val e = mc.world!!.getEntityById(en.key)
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive) {
                    it.remove()
                    continue
                }

                val t: Text
                if (elapsed < WAIT_MS) {
                    // 5s cooldown/wait countdown with an hourglass.
                    val secs = (WAIT_MS - elapsed) / 1000.0
                    t = Text.literal("§e⌛ " + String.format("%.1fs", secs))
                } else {
                    // 10s freeze countdown with a snowflake.
                    val secs = (TOTAL_MS - elapsed) / 1000.0
                    val color = if (secs <= 2.0) "§c" else if (secs <= 5.0) "§b" else "§3"
                    t = Text.literal(color + "❄ " + String.format("%.1fs", secs))
                }
                val y = e.y + e.height / 2.0
                RenderUtils.renderText(ctx, matrices, t, e.x, y, e.z, 1.0f)
            }
            matrices.pop()
        })
    }
}
