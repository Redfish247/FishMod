package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.data.EntityUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Base64
import fishmod.features.rift.RiftSettings as S

// Own Bloodfiend helper, detection modelled on SkyHanni's VampireSlayerFeatures
object RiftVampire {

    // Skin hashes of the Blood Ichor and Killer Spring skull stands
    private const val ICHOR_SKIN = "c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422"
    private const val SPRING_SKIN = "77f7a7bc8ac86f23ca7bf98afeb76960227e1832fe209a3026f6ceb8bde74f54"
    private val TWINCLAWS = Regex("TWINCLAWS \\d[\\d.,]*s")
    private const val RANGE = 15.0

    @Volatile private var boss: Player? = null
    @Volatile private var canSteak = false
    @Volatile private var orbs: List<Pair<ArmorStand, Boolean>> = emptyList()
    private val skinCache = HashMap<Int, String>()
    private var nextTwinclaws = 0L
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 5 == 0) scan() }
        Events.ON_WORLD_CHANGE.register { reset(); skinCache.clear(); false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    private fun reset() { boss = null; canSteak = false; orbs = emptyList() }

    private fun scan() {
        val mc = Minecraft.getInstance()
        val me = mc.player ?: return
        val level = mc.level ?: return
        if (!S.riftVampire || !Rift.inArea("Stillgore", "Oubliette")) { reset(); return }
        val name = me.gameProfile.name
        val area = me.boundingBox.inflate(RANGE)

        // Bloodfiend is an NPC player; its "Spawned by" tag floats ~3 blocks above it
        val b = level.getEntitiesOfClass(Player::class.java, area) {
            it !== me && it.isAlive && it.uuid.version() != 4 && Rift.strip(it.name.string).trim() == "Bloodfiend"
        }.firstOrNull { tagsNear(it, "Spawned by").any { s -> s.contains(name) } }
        boss = b
        canSteak = b != null && b.health <= b.getAttributeBaseValue(Attributes.MAX_HEALTH) * 0.2
        if (b != null && canSteak && S.riftVampireSteakTitle)
            Misc.forceTitle(Component.literal("§c§lSTEAK!"), Component.empty(), 300)
        if (b != null && S.riftVampireTwinclaws && tagsNear(b, "TWINCLAWS").any { TWINCLAWS.containsMatchIn(it) }) {
            val now = System.currentTimeMillis()
            if (now >= nextTwinclaws) {
                Misc.forceTitle(Component.literal("§6§lTWINCLAWS"), Component.literal("§bFreeze it with Holy Ice!"), 1750)
                nextTwinclaws = now + 5000
            }
        }

        orbs = if (!S.riftVampireIchor && !S.riftVampireSpring) emptyList()
        else level.getEntitiesOfClass(ArmorStand::class.java, area) { it.isAlive }.mapNotNull { s ->
            when (skin(s)) {
                ICHOR_SKIN -> if (S.riftVampireIchor) s to true else null
                SPRING_SKIN -> if (S.riftVampireSpring) s to false else null
                else -> null
            }
        }
    }

    // Stand names in a 3-block box centred 3 blocks above the entity, stretched 3 more vertically
    private fun tagsNear(e: Player, contains: String): List<String> {
        val c = e.position().add(0.0, 3.0, 0.0)
        val box = AABB(c.x - 3, c.y - 6, c.z - 3, c.x + 3, c.y + 6, c.z + 3)
        val level = Minecraft.getInstance().level ?: return emptyList()
        return level.getEntitiesOfClass(ArmorStand::class.java, box)
            .mapNotNull { s -> s.customName?.string?.let(Rift::strip) }.filter { it.contains(contains) }
    }

    private fun skin(s: ArmorStand): String = skinCache.getOrPut(s.id) {
        val tex = s.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE)
            ?.partialProfile()?.properties?.get("textures")?.firstOrNull()?.value() ?: return@getOrPut ""
        runCatching { String(Base64.getDecoder().decode(tex)).substringAfter("/texture/").takeWhile { it.isLetterOrDigit() } }
            .getOrDefault("")
    }

    private fun render() {
        boss?.let { b ->
            if (!b.isAlive) return@let
            val p = EntityUtil.getLerpedPos(b)
            if (S.riftVampireHighlight) {
                val c = if (canSteak) S.riftVampireSteakColor else S.riftVampireColor
                val box = b.boundingBox.move(p.x - b.x, p.y - b.y, p.z - b.z)
                RenderUtils.gizmoBox(box, (c and 0xFFFFFF) or 0x30000000, c, true)
                if (canSteak) RenderUtils.gizmoText(Component.literal("§c§lSTEAK!"), Vec3(p.x, p.y + b.bbHeight + 0.9, p.z), 1.5f, -1, true)
            }
            if (S.riftVampireLine) {
                val me = Minecraft.getInstance().player ?: return@let
                val eye = EntityUtil.getLerpedPos(me).add(0.0, me.eyeHeight.toDouble(), 0.0)
                RenderUtils.gizmoLine(eye, p.add(0.0, b.bbHeight / 2.0, 0.0), S.riftVampireLineColor, 2f, true)
            }
        }
        for ((s, ichor) in orbs) {
            if (!s.isAlive) continue
            val c = if (ichor) S.riftVampireIchorColor else S.riftVampireSpringColor
            val bp = s.blockPosition()
            val box = AABB(bp.x.toDouble(), bp.y + 2.0, bp.z.toDouble(), bp.x + 1.0, bp.y + 3.0, bp.z + 1.0)
            RenderUtils.gizmoBox(box, (c and 0xFFFFFF) or 0x40000000, c, true)
            val label = if (ichor) "§4Ichor" else "§4Spring"
            RenderUtils.gizmoText(Component.literal(label), Vec3(bp.x + 0.5, bp.y + 2.5, bp.z + 0.5), 1.5f, -1, false)
        }
    }
}
