package fishmod.features.hud

import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import fishmod.utils.config.values.FishSettings
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Item list NPC click: waypoint when on the NPC's island, otherwise a chat message with a click-to-warp link.
object NpcTravel {

    private const val COLOR = 0xFF55FFFF.toInt()
    private const val TTL_MS = 10 * 60 * 1000L

    private var target: Vec3? = null
    private var label = ""
    private var mode = ""
    private var setAt = 0L
    private var fromWarp = false
    private const val PENDING_MS = 2 * 60 * 1000L
    private var pending: Vec3? = null
    private var pendingLabel = ""
    private var pendingMode = ""
    private var pendingBase = false
    private var pendingAt = 0L

    private val ISLANDS: Map<String, Pair<String, String?>> = mapOf(
        "hub" to ("Hub" to "hub"),
        "dynamic" to ("Private Island" to "is"),
        "dungeon_hub" to ("Dungeon Hub" to "warp dhub"),
        "farming_1" to ("The Farming Islands" to "warp barn"),
        "foraging_1" to ("The Park" to "warp park"),
        "foraging_2" to ("Galatea" to "warp galatea"),
        "combat_1" to ("Spider's Den" to "warp spider"),
        "combat_3" to ("The End" to "warp end"),
        "mining_1" to ("Gold Mine" to "warp gold"),
        "mining_2" to ("Deep Caverns" to "warp deep"),
        "mining_3" to ("Dwarven Mines" to "warp dwarves"),
        "crystal_hollows" to ("Crystal Hollows" to "warp ch"),
        "crimson_isle" to ("Crimson Isle" to "warp isle"),
        "garden" to ("The Garden" to "warp garden"),
        "winter" to ("Jerry's Workshop" to "warp jerry"),
        "rift" to ("The Rift" to null),
        "fishing_1" to ("Backwater Bayou" to null),
        "lotus_atoll" to ("Lotus Atoll" to null),
        "safari" to ("Safari" to null),
        "foraging_3" to ("Foraging Island" to null),
    )

    fun init() {
        RenderHooks.GIZMO.register { _ ->
            try {
                promotePending()
                val t = target ?: return@register
                val p = Minecraft.getInstance().player
                if (p == null || SkyblockLocation.currentMode != mode || System.currentTimeMillis() - setAt > TTL_MS || p.position().distanceTo(t) < 3.0) {
                    target = null
                    return@register
                }
                val rgb = (if (fromWarp) FishSettings.npcWarpHighlightColor else COLOR) and 0xFFFFFF
                val npc = if (fromWarp) findNpc(t) else null
                if (npc != null) FishDraw.gizmoBox(npc.boundingBox.inflate(0.1), 0x40000000 or rgb, 0xFF000000.toInt() or rgb, true)
                else FishDraw.gizmoBox(AABB(t.x - 0.5, t.y, t.z - 0.5, t.x + 0.5, t.y + 1.0, t.z + 0.5), 0x40000000 or rgb, 0, true)
                if (!fromWarp || FishSettings.npcWarpHighlightBeam) FishDraw.gizmoBox(AABB(t.x - 0.15, t.y, t.z - 0.15, t.x + 0.15, t.y + 24.0, t.z + 0.15), 0x40000000 or rgb, 0, true)
                FishDraw.gizmoText(Component.literal("$label §7${p.position().distanceTo(t).toInt()}m"), Vec3(t.x, t.y + 2.0, t.z), 1.2f, -0x1, true)
            } catch (e: Exception) { FishDiag.fail("NpcTravel.1", "npc waypoint render threw", e) }
        }
    }

    private fun isBaseCamp(y: Double, z: Double) = y <= 140.0 && z >= 100.0

    // Arms a highlight that activates once the player lands on the NPC's island (no command sent here).
    private fun arm(pos: Vec3, name: String, island: String, base: Boolean) {
        if (!FishSettings.npcWarpHighlight) return
        pending = pos; pendingLabel = "§b$name"; pendingMode = island; pendingBase = base; pendingAt = System.currentTimeMillis()
    }

    private fun promotePending() {
        val pos = pending ?: return
        if (System.currentTimeMillis() - pendingAt > PENDING_MS) { pending = null; return }
        val p = Minecraft.getInstance().player ?: return
        if (SkyblockLocation.currentMode != pendingMode) return
        if (pendingMode == "mining_3" && isBaseCamp(p.y, p.z) != pendingBase) return
        target = pos; label = pendingLabel; mode = pendingMode; setAt = System.currentTimeMillis(); fromWarp = true
        pending = null
    }

    private fun findNpc(t: Vec3): Entity? {
        val lvl = Minecraft.getInstance().level ?: return null
        val me = Minecraft.getInstance().player
        return lvl.getEntitiesOfClass(LivingEntity::class.java, AABB(t.x - 2.5, t.y - 2.0, t.z - 2.5, t.x + 2.5, t.y + 3.0, t.z + 2.5)) { it !== me && it !is ArmorStand && !it.isInvisible }
            .minByOrNull { it.position().distanceToSqr(t) }
    }

    fun go(id: String) {
        val o = NeuRepo.get(id) ?: return
        val name = fishmod.utils.TextStrip.plain(o.get("displayname")?.asString ?: id).replace(Regex("\\s*\\((Rift )?NPC\\)$"), "").trim()
        fun num(k: String) = o.get(k)?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()
        val x = num("x"); val y = num("y"); val z = num("z")
        val island = o.get("island")?.takeIf { !it.isJsonNull }?.asString
        if (x == null || y == null || z == null) { FishUtil.addChatMessage(Component.literal("§b[Item List] §7No known location for §f$name§7.")); return }
        val here = SkyblockLocation.currentMode
        val pl = Minecraft.getInstance().player
        val baseTarget = island == "mining_3" && isBaseCamp(y, z)
        if (island == "mining_3" && here == "mining_3" && pl != null && baseTarget != isBaseCamp(pl.y, pl.z)) {
            val msg = Component.literal("§b[Item List] §f$name §7is in §f${if (baseTarget) "Dwarven Base Camp" else "Dwarven Mines"}§7.")
            msg.append(Component.literal(" §a[Click to warp]").withStyle { it.withClickEvent(ClickEvent.RunCommand(if (baseTarget) "/warp base" else "/warp dwarves")) })
            FishUtil.addChatMessage(msg)
            arm(Vec3(x + 0.5, y, z + 0.5), name, "mining_3", baseTarget)
            return
        }
        if (island == null || island == here) {
            target = Vec3(x + 0.5, y, z + 0.5)
            label = "§b$name"
            mode = here
            setAt = System.currentTimeMillis()
            fromWarp = false
            FishUtil.addChatMessage(Component.literal("§b[Item List] §7Waypoint set to §f$name §7(${x.toInt()}, ${y.toInt()}, ${z.toInt()})."))
            return
        }
        val info = ISLANDS[island]
        val islandName = if (baseTarget) "Dwarven Base Camp" else info?.first ?: island
        val msg = Component.literal("§b[Item List] §f$name §7is on §f$islandName§7.")
        (if (baseTarget) "warp base" else info?.second)?.let { cmd ->
            msg.append(Component.literal(" §a[Click to warp]").withStyle { it.withClickEvent(ClickEvent.RunCommand("/$cmd")) })
            arm(Vec3(x + 0.5, y, z + 0.5), name, island, baseTarget)
        }
        FishUtil.addChatMessage(msg)
    }
}
