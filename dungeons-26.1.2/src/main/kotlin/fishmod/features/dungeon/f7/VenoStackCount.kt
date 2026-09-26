package fishmod.features.dungeon.f7

import fishmod.features.FishHudEditor
import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Counts own Mage beams that hit Storm while holding a Venomous weapon (stacks cap at 40, expire 5s after last hit).
object VenoStackCount {

    private const val NAME = "Veno Stack Count"
    private const val MAX_STACKS = 40
    private const val DURATION_MS = 5000L

    private class Beam(var last: Vec3, var lastTick: Int, val mine: Boolean, var counted: Boolean = false)

    private val beams = ArrayList<Beam>()
    private var tick = 0
    private var stormBox: AABB? = null
    private var holdingVeno = false
    private var stacks = 0
    private var lastHitMs = 0L

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.venoStackHudX }, { v -> FishSettings.venoStackHudX = v },
            { FishSettings.venoStackHudY }, { v -> FishSettings.venoStackHudY = v },
            40, 20,
            { FishSettings.venoStackScale }, { v -> FishSettings.venoStackScale = v }
        )
        Events.ON_WORLD_CHANGE.register { reset(); false }
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        Events.ON_PARTICLE.register { packet ->
            if (packet.particle.type === ParticleTypes.FIREWORK && active()) onBeamPoint(Vec3(packet.x, packet.y, packet.z))
            false
        }
    }

    private fun reset() { beams.clear(); stormBox = null; stacks = 0; lastHitMs = 0L }

    private fun active(): Boolean =
        FishSettings.venoStackEnabled && holdingVeno && Location.inDungeon() && Phase.inP2() && !Phase.stormDead()

    private fun onTick() {
        tick++
        beams.removeAll { tick - it.lastTick > 2 }
        if (stacks > 0 && System.currentTimeMillis() - lastHitMs > DURATION_MS) stacks = 0
        val mc = Minecraft.getInstance()
        val p = mc.player
        holdingVeno = p != null && isVenomous(p.mainHandItem)
        if (!FishSettings.venoStackEnabled || !Location.inDungeon() || !Phase.inP2() || Phase.stormDead()) {
            stormBox = null
            if (Phase.stormDead() || !Location.inDungeon()) stacks = 0
            return
        }
        run {
            stormBox = mc.level?.entitiesForRendering()?.asSequence()
                ?.filterIsInstance<WitherBoss>()
                ?.filter { !it.isInvisible && it.isAlive && it.invulnerableTicks != 800 && it.boundingBox.ysize >= 2.0 }
                ?.minByOrNull { it.distanceToSqr(p ?: return@minByOrNull 0.0) }
                ?.let { it.boundingBox.inflate(1.0) }
        }
    }

    private fun isVenomous(stack: net.minecraft.world.item.ItemStack): Boolean {
        if (stack.isEmpty) return false
        val tag = stack.fishmodCustomDataTag() ?: return false
        return tag.getCompound("enchantments").map { it.contains("venomous") }.orElse(false)
    }

    private fun onBeamPoint(pt: Vec3) {
        val player = Minecraft.getInstance().player ?: return
        var beam = beams.lastOrNull { tick - it.lastTick < 2 && it.last.distanceToSqr(pt) < 9.0 }
        if (beam == null) {
            beam = Beam(pt, tick, player.getEyePosition().distanceToSqr(pt) < 16.0)
            beams.add(beam)
        } else {
            beam.last = pt; beam.lastTick = tick
        }
        if (!beam.mine || beam.counted) return
        val box = stormBox ?: return
        if (!box.contains(pt)) return
        beam.counted = true
        if (stacks < MAX_STACKS) stacks++
        lastHitMs = System.currentTimeMillis()
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tickCounter: DeltaTracker) {
        if (!FishSettings.venoStackEnabled || stacks <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val left = (DURATION_MS - (System.currentTimeMillis() - lastHitMs)).coerceAtLeast(0L)
        val sc = FishSettings.venoStackScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.venoStackHudX.toFloat(), FishSettings.venoStackHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, "%.1fs".format(left / 1000.0), 0, 0, 0xFFFFFF55.toInt(), true)
        ctx.text(mc.font, "$stacks/$MAX_STACKS", 0, 10, if (stacks >= MAX_STACKS) 0xFFFF5555.toInt() else 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }
}
