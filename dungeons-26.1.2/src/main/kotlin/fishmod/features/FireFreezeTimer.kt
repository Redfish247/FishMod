package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.EntityUtil
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

/** Fire Freeze Staff timer: renders a countdown at each mob caught in the freeze cloud after use. */
object FireFreezeTimer {

    private const val WAIT_MS = 5000L
    private const val FREEZE_MS = 10000L
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 4.5          // the black particle cloud Fire Freeze makes is ~4.5 blocks
    private const val ARM_WINDOW_MS = 1500L // how long after a right-click we treat a smoke burst as the cast
    private const val CATCH_WINDOW_MS = 2500L // keep scanning briefly after the cast — mobs wander in

    // entityId -> wall-clock ms when the staff was used (cast start)
    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @Volatile private var armedAt = 0L                 // last Fire Freeze Staff right-click
    @Volatile private var castAt = 0L                  // when the freeze actually registered
    @Volatile private var center: Vec3 = Vec3.ZERO     // freeze cloud centre (particle burst, or the player)

    @JvmStatic
    fun init() {
        // Any right-click route with the staff in hand — air, block, or entity — arms detection.
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

        // The freeze's black smoke burst is the real "it happened" signal (and gives its centre,
        // so a ranged cast is handled too). Only trust it right after a staff right-click.
        Events.ON_PARTICLE.register { packet ->
            if (FishSettings.fireFreezeTimerEnabled &&
                System.currentTimeMillis() - armedAt <= ARM_WINDOW_MS &&
                packet.count >= 4 &&
                (packet.particle.type === ParticleTypes.LARGE_SMOKE || packet.particle.type === ParticleTypes.SMOKE)
            ) {
                val mc = Minecraft.getInstance()
                val p = mc.player
                val pos = Vec3(packet.x, packet.y, packet.z)
                if (p != null && p.position().closerThan(pos, 40.0)) {
                    center = pos
                    castAt = System.currentTimeMillis()
                    scanFrozen()
                }
            }
            false
        }

        // Re-scan for a short window after the cast — the cloud lingers and mobs walk into it.
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            val now = System.currentTimeMillis()
            if (castAt != 0L && now - castAt <= CATCH_WINDOW_MS) {
                scanFrozen()
            } else if (armedAt != 0L && castAt < armedAt && now - armedAt in 250..ARM_WINDOW_MS) {
                // No smoke burst matched (wrong particle id / not sent) — fall back to a
                // player-centred cast so the timer still shows.
                Minecraft.getInstance().player?.let {
                    center = it.position()
                    castAt = now
                    scanFrozen()
                }
            }
        })

        registerRender()
    }

    private fun isFireFreezeUse(player: Player, hand: InteractionHand): Boolean {
        if (!FishSettings.fireFreezeTimerEnabled || hand != InteractionHand.MAIN_HAND) return false
        if (Minecraft.getInstance().level == null) return false
        val stack = player.getItemInHand(hand)
        return stack != null && !stack.isEmpty && "FIRE_FREEZE_STAFF" == ItemUtil.getId(stack)
    }

    private fun arm() {
        armedAt = System.currentTimeMillis()
    }

    private fun scanFrozen() {
        val level = Minecraft.getInstance().level ?: return
        val box = net.minecraft.world.phys.AABB.ofSize(center, RADIUS * 2, RADIUS * 2, RADIUS * 2)
        for (e in level.getEntities(null as Entity?, box)) {
            if (e is LivingEntity && e !is Player && e !is ArmorStand && e.isAlive &&
                e.position().closerThan(center, RADIUS)
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
                    val secs = (WAIT_MS - elapsed) / 1000.0
                    t = Component.literal("§e⌛ " + String.format("%.1fs", secs))
                } else {
                    val secs = (TOTAL_MS - elapsed) / 1000.0
                    val color = if (secs <= 2.0) "§c" else if (secs <= 5.0) "§b" else "§3"
                    t = Component.literal(color + "❄ " + String.format("%.1fs", secs))
                }
                // Interpolated position, anchored above the head so it's easy to read.
                val p = EntityUtil.getLerpedPos(e)
                val y = p.y + e.bbHeight + 0.55
                RenderUtils.renderText(ctx, matrices, t, p.x, y, p.z, 1.35f)
            }
        }
    }
}
