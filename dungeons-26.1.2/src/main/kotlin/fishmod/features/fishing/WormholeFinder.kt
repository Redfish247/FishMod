package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.utils.FishMsg
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.network.chat.Component
import org.joml.Vector3f

// Wormhole finder (Lotus Atoll, Crimson Isle). Froggles shows arrow text-displays near you; each arrow points at a wormhole.
// We match the arrow bearing against the bundled node list (fishingWormholes.json, SkyHanni-Repo MIT) and draw a waypoint.
// UNVERIFIED in game: that the arrows are Display.TextDisplay within 3 blocks, the arrow's "up" axis as heading, the
// Froggles item ids, and the teleport sound used for the departure alert. Turn on "Debug Raw Arrows" to see what is really there.
object WormholeFinder {
    private val DATA: Map<String, List<WormholeMatch.Node>> = try {
        WormholeFinder::class.java.getResourceAsStream("/fishingWormholes.json")!!.use { WormholeMatch.parse(String(it.readAllBytes(), Charsets.UTF_8)) }
    } catch (t: Throwable) {
        FishDiag.fail("WormholeFinder.1", "fishingWormholes.json failed to load", t)
        emptyMap()
    }

    private const val SCAN_TICKS = 10
    private const val ARROW_RADIUS = 3.0
    private const val KEEP_MS = 60_000L
    private const val REACHED_SQ = 16.0
    private const val DEPARTURE_SOUND = "entity.enderman.teleport"
    private const val DEPARTURE_PITCH = 0.6984127f
    private val FROGGLES = setOf("FROGGLES_DIAMOND", "FROGGLES_GOLD")

    // Swapped whole on every scan, read by the render hook
    @Volatile private var targets: List<Vec3> = emptyList()
    private var area: List<WormholeMatch.Node> = emptyList()
    private var areaName = ""
    private var matchedAt = 0L
    private var lastAlert = 0L
    private var tick = 0
    private val reported = HashSet<Int>()
    private val matched = LinkedHashSet<Int>()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        FishEvents.worldChange.register { reset(); false }
        FishEvents.sound.register { snd, _, pitch -> onSound(snd.location.toString(), pitch); false }
        RenderHooks.GIZMO.register { _ ->
            val ts = targets
            if (ts.isEmpty()) return@register
            FishDiag.guard("WormholeFinder.2", "wormhole waypoint render failed") {
                val color = W.wormholeColor or 0xFF000000.toInt()
                val fill = (W.wormholeColor and 0x00FFFFFF) or 0x66000000
                val p = Minecraft.getInstance().player
                for (t in ts) {
                    FishDraw.gizmoBox(AABB(t.x - 0.5, t.y, t.z - 0.5, t.x + 0.5, t.y + 1.0, t.z + 0.5), fill, color, true)
                    val d = if (p != null) Math.sqrt(p.distanceToSqr(t)).toInt() else 0
                    FishDraw.gizmoText(Component.literal("§dWormhole §7${d}m"), Vec3(t.x, t.y + 1.6, t.z), 1.5f, -0x1, true)
                }
            }
        }
    }

    // Also called by the existing "wormhole gone" chat alert so the waypoint disappears with the wormhole.
    fun onGone() = reset()

    private fun reset() {
        targets = emptyList(); matched.clear(); reported.clear(); area = emptyList(); areaName = ""
    }

    private fun on(): Boolean = W.wormholeFinder && Fishing.enabled() &&
        (Fishing.inArea(SkyblockLocation.LOTUS_ATOLL) || Fishing.inArea(SkyblockLocation.CRIMSON_ISLE))

    private fun wearingFroggles(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return ItemTools.getId(p.getItemBySlot(EquipmentSlot.HEAD)) in FROGGLES
    }

    private fun onTick() {
        if (!W.wormholeFinder) { if (targets.isNotEmpty()) reset(); return }
        if (++tick % SCAN_TICKS != 0) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!on() || !wearingFroggles()) { if (targets.isNotEmpty()) reset(); return }
        try {
            val loc = Fishing.location().name
            if (loc != areaName) { areaName = loc; area = DATA[loc] ?: emptyList(); matched.clear(); targets = emptyList() }
            if (area.isEmpty()) return
            val now = System.currentTimeMillis()
            val arrows = level.getEntitiesOfClass(Display.TextDisplay::class.java, player.boundingBox.inflate(ARROW_RADIUS))
            var changed = false
            for (a in arrows) {
                val f = forward(a) ?: continue
                if (W.wormholeDebug && reported.add(a.id)) debugLine(a, f)
                val i = WormholeMatch.bestNode(a.x, a.z, f[0].toDouble(), f[1].toDouble(), area)
                if (i >= 0 && matched.add(i)) changed = true
            }
            if (arrows.isNotEmpty() && matched.isNotEmpty()) matchedAt = now
            // Drop nodes we walked up to, and everything after a minute without arrows
            if (matched.removeIf { val n = area[it]; val dx = n.x + 0.5 - player.x; val dz = n.z + 0.5 - player.z; dx * dx + dz * dz < REACHED_SQ }) changed = true
            if (matched.isNotEmpty() && now - matchedAt > KEEP_MS) { matched.clear(); changed = true }
            if (changed) {
                targets = matched.map { val n = area[it]; Vec3(n.x + 0.5, n.y.toDouble(), n.z + 0.5) }
            }
        } catch (t: Throwable) {
            FishDiag.fail("WormholeFinder.3", "wormhole scan failed", t)
        }
    }

    // Horizontal heading of the arrow: the display's left rotation applied to local +Y, flattened (UNVERIFIED axis).
    private fun forward(a: Display.TextDisplay): FloatArray? {
        val q = a.renderState()?.transformation?.get(0f)?.leftRotation() ?: return null
        val v = Vector3f(0f, 1f, 0f)
        q.transform(v)
        return floatArrayOf(v.x, v.z)
    }

    private fun debugLine(a: Display.TextDisplay, f: FloatArray) {
        val text = TextStrip.plain(a.text.string).replace('\n', '|')
        FishMsg.send("§7[Wormhole] arrow #${a.id} '$text' at ${a.x.toInt()} ${a.y.toInt()} ${a.z.toInt()} fwd=${"%.2f".format(f[0])},${"%.2f".format(f[1])}")
    }

    private fun onSound(id: String, pitch: Float) {
        if (!W.wormholeFinder || !W.wormholeDeparture) return
        if (!id.endsWith(DEPARTURE_SOUND) || Math.abs(pitch - DEPARTURE_PITCH) > 0.001f) return
        if (!on() || !wearingFroggles()) return
        val now = System.currentTimeMillis()
        if (now - lastAlert < 3000) return
        lastAlert = now
        FishingAlerts.notify(FishingTitles.WORMHOLE, "§c§lWormhole closed!", "", null)
        reset()
    }
}
