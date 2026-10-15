package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.data.EntityUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Own Bloodfiend: highlight, STEAK at <=20% HP, Twinclaws warning
object RiftVampire {

    @Volatile private var boss: Player? = null
    @Volatile private var canSteak = false
    private var steakTitled = false
    private var twinUntil = 0L
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 2 == 0) scan() }
        Events.ON_WORLD_CHANGE.register { boss = null; steakTitled = false; false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val me = mc.player ?: return
        if (!S.riftVampire || !Rift.inArea("Stillgore", "Oubliette")) { boss = null; return }
        val stands = Rift.namedStands(20.0)
        val mine = stands.firstOrNull { it.second.contains("Spawned by: ${me.gameProfile.name}") }?.first
        if (mine == null) { boss = null; steakTitled = false; return }
        val level = mc.level ?: return
        val b = level.getEntitiesOfClass(Player::class.java, mine.boundingBox.inflate(1.5, 4.0, 1.5)) {
            it !== me && Rift.strip(it.name.string).trim() == "Bloodfiend"
        }.minByOrNull { it.distanceToSqr(mine) }
        boss = b
        canSteak = b != null && b.maxHealth > 0f && b.health <= b.maxHealth * 0.2f
        if (canSteak && S.riftVampireSteakTitle && !steakTitled) {
            steakTitled = true
            Misc.forceTitle(Component.literal("§c§lSTEAK!"), Component.empty(), 1500)
        }
        if (!canSteak) steakTitled = false
        if (S.riftVampireTwinclaws && stands.any { it.second.contains("TWINCLAWS") && it.first.distanceToSqr(mine) < 64.0 }) {
            val now = System.currentTimeMillis()
            if (now > twinUntil) Misc.forceTitle(Component.literal("§6§lTWINCLAWS"), Component.literal("§bFreeze it with Holy Ice!"), 1500)
            twinUntil = now + 1500
        }
    }

    private fun render() {
        val b = boss ?: return
        if (!S.riftVampireHighlight || !b.isAlive) return
        val c = if (canSteak) S.riftVampireSteakColor else S.riftVampireColor
        val p = EntityUtil.getLerpedPos(b)
        val box = b.boundingBox.move(p.x - b.x, p.y - b.y, p.z - b.z)
        RenderUtils.gizmoBox(box, (c and 0xFFFFFF) or 0x30000000, c, true)
        if (canSteak) RenderUtils.gizmoText(Component.literal("§c§lSTEAK!"), Vec3(p.x, p.y + b.bbHeight + 0.9, p.z), 1.5f, -1, true)
    }
}
