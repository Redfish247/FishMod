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

/**
 * Fire Freeze Staff timer: renders a countdown over each mob caught in the freeze.
 *
 * The staff's freeze is a self-centred AoE burst — everything within ~5 blocks of the caster the
 * moment it goes off. So detection is simply: on a staff right-click, snapshot the nearby mobs (plus
 * whatever the crosshair is directly on) and keep re-checking for a beat as the cloud lingers.
 */
object FireFreezeTimer {

    private const val WAIT_MS = 5000L
    private const val FREEZE_MS = 10000L
    private const val TOTAL_MS = WAIT_MS + FREEZE_MS
    private const val RADIUS = 5.0           // freeze burst reach around the caster
    private const val AIM_RANGE = 6.0        // a mob you're looking straight at, slightly past the burst
    private const val MIN_DIST = 1.6         // closer than this = the pet on your face, not a target
    private const val CATCH_WINDOW_MS = 2500L // keep scanning briefly after the cast — the cloud lingers
    private const val DRAW_DIST = 28.0       // don't clutter the screen with timers for far-off mobs

    /** entityId -> wall-clock ms the staff was used (cast start). */
    private val frozen: MutableMap<Int, Long> = ConcurrentHashMap()

    @Volatile private var castAt = 0L

    @JvmStatic
    fun init() {
        // Any right-click route with the staff in hand — air, block, or entity.
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
        // A cast aimed straight at a mob freezes it even if it's a hair outside the burst.
        (Minecraft.getInstance().hitResult as? EntityHitResult)?.entity?.let {
            val me = Minecraft.getInstance().player
            if (isFreezable(it) && me != null && it.distanceTo(me) in MIN_DIST..AIM_RANGE) {
                frozen.putIfAbsent(it.id, castAt)
            }
        }
        scanFrozen()
    }

    /**
     * Hostile, AI-driven mobs only. `Monster` drops nametag armour-stands / players; `!isNoAi` drops
     * the player's pet (Hypixel spawns pets — including a Blaze pet — with AI off, and they sit right
     * on the camera, which is what made the timer look "stuck to the screen").
     */
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
        // world text renders in the END_MAIN pass; pose is pre-translated by -camera, so no manual push/translate
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
                // Drop finished timers, dead/despawned mobs, and anything absurdly far (mistag guard).
                if (elapsed >= TOTAL_MS || e == null || !e.isAlive || e.distanceTo(me) > 40.0) {
                    it.remove()
                    continue
                }

                val p = EntityUtil.getLerpedPos(e)
                val head = Vec3(p.x, p.y + e.bbHeight + 0.55, p.z)
                // only draw mobs in front of the camera — one you've walked past shouldn't smear a number across the screen corner
                val toMob = head.subtract(me.eyePosition)
                if (toMob.length() > DRAW_DIST || toMob.dot(me.lookAngle) <= 0.0) continue

                val t: Component
                if (elapsed < WAIT_MS) {
                    val secs = (WAIT_MS - elapsed) / 1000.0
                    t = Component.literal("§e⌛ " + String.format("%.1fs", secs))
                } else {
                    val secs = (TOTAL_MS - elapsed) / 1000.0
                    val color = if (secs <= 2.0) "§c" else if (secs <= 5.0) "§b" else "§3"
                    t = Component.literal(color + "❄ " + String.format("%.1fs", secs))
                }
                RenderUtils.renderText(ctx, matrices, t, head.x, head.y, head.z, 1.35f)
            }
        }
    }
}
