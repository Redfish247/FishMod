package fishmod.features.mining

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.FishHudLayer
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import fishmod.features.mining.MiningSettings as S

/**
 * Crystal Hollows key-location waypoints plus a zone minimap (issues #250 / #251).
 *
 * Structure positions are NOT fixed in the Hollows (randomised per lobby), so they are learned: while the
 * sidebar area line names a known structure, the player's positions inside it widen a bounding box and the
 * waypoint sits at that box's centre (it sharpens the more you walk around the structure). The four regional
 * zones ARE fixed quadrants around the Nucleus (centre 513,513, same centre NucleusHighlight uses).
 * Area names, quadrant mapping and Hollows extent (202..823) are public knowledge, UNVERIFIED in game.
 */
object HollowsWaypoints {

    enum class Group { STRUCTURE, ZONE, FAIRY }

    private class Def(val key: String, val label: String, val group: Group, val rgb: Int)

    // order matters: first contains-match wins
    private val DEFS = listOf(
        Def("Lost Precursor City", "Lost Precursor City", Group.STRUCTURE, 0x55FFFF),
        Def("Precursor City", "Precursor City", Group.STRUCTURE, 0x55FFFF),
        Def("Mines of Divan", "Mines of Divan", Group.STRUCTURE, 0x5555FF),
        Def("Khazad", "Khazad-dum", Group.STRUCTURE, 0xFFAA00),
        Def("Jungle Temple", "Jungle Temple", Group.STRUCTURE, 0x55FF55),
        Def("Goblin Queen", "Goblin Queen's Den", Group.STRUCTURE, 0xFF5555),
        Def("Dragon's Lair", "Dragon's Lair", Group.STRUCTURE, 0xFF55FF),
        Def("Crystal Nucleus", "Crystal Nucleus", Group.STRUCTURE, 0xFFFFFF),
        Def("Fairy Grotto", "Fairy Grotto", Group.FAIRY, 0xFF77DD),
    )

    class Zone(val name: String, val rgb: Int, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int)

    const val CENTER = 513
    const val MIN = 202
    const val MAX = 823
    // quadrants match the gem pockets in NucleusHighlight (Amethyst = Jungle, Amber = Goblin, Jade = Mithril, Sapphire = Precursor)
    val ZONES = listOf(
        Zone("Jungle", 0x55FF55, MIN, CENTER, MIN, CENTER),
        Zone("Goblin Holdout", 0xFFAA00, MIN, CENTER, CENTER, MAX),
        Zone("Mithril Deposits", 0x55FFFF, CENTER, MAX, MIN, CENTER),
        Zone("Precursor Remnants", 0xFF55FF, CENTER, MAX, CENTER, MAX),
    )

    private class Found(val def: Def) {
        var minX = 0.0; var maxX = 0.0; var minY = 0.0; var maxY = 0.0; var minZ = 0.0; var maxZ = 0.0
        var n = 0
        fun add(p: Vec3) {
            if (n++ == 0) { minX = p.x; maxX = p.x; minY = p.y; maxY = p.y; minZ = p.z; maxZ = p.z; return }
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
            if (p.z < minZ) minZ = p.z
            if (p.z > maxZ) maxZ = p.z
        }
        fun center() = Vec3((minX + maxX) / 2, (minY + maxY) / 2 + 1.0, (minZ + maxZ) / 2)
    }

    private val found = LinkedHashMap<String, Found>()
    private var curZone: String = ""
    private var tick = 0

    private fun defFor(area: String): Def? {
        val a = area.replace('û', 'u').replace('Û', 'U')
        return DEFS.firstOrNull { a.contains(it.key, ignoreCase = true) }
    }

    private fun zoneAt(x: Double, z: Double): Zone? = ZONES.firstOrNull { x >= it.minX && x <= it.maxX && z >= it.minZ && z <= it.maxZ }

    /** Zone name from the sidebar if it names one, else the fixed quadrant. */
    private fun zoneName(x: Double, z: Double): String {
        val a = Mining.area
        ZONES.firstOrNull { a.contains(it.name, ignoreCase = true) }?.let { return it.name }
        if (a.contains("Magma", ignoreCase = true)) return "Magma Fields"
        return zoneAt(x, z)?.name ?: ""
    }

    private fun enabled(g: Group) = when (g) {
        Group.STRUCTURE -> S.hollowsWpStructures
        Group.FAIRY -> S.hollowsWpFairy
        Group.ZONE -> S.hollowsWpZones
    }

    private const val SIZE = 100

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try { onTick(mc) } catch (t: Throwable) { FishDiag.fail("HollowsWaypoints.1", "hollows waypoint tick failed", t) }
        }
        FishEvents.worldChange.register { found.clear(); curZone = ""; false }
        RenderHooks.GIZMO.register { _ ->
            try { render3d() } catch (t: Throwable) { FishDiag.fail("HollowsWaypoints.2", "hollows waypoint render failed", t) }
        }
        val id = Identifier.fromNamespaceAndPath("fishmod", "hollows_map")
        FishHudEditor.register("Hollows Map",
            { S.hollowsMapX }, { S.hollowsMapX = it }, { S.hollowsMapY }, { S.hollowsMapY = it },
            SIZE, SIZE, { S.hollowsMapScale }, { S.hollowsMapScale = it },
            { S.hollowsMap && Mining.inHollows() }, java.util.function.BooleanSupplier { S.hollowsMap })
        fishmod.features.HudPreview.register("Hollows Map") { fishmod.features.HudExample(SIZE, SIZE) { ctx -> drawMapExample(ctx) } }
        FishHudLayer.addLast(id) { ctx, _ ->
            if (!FishHudEditor.isOpen() && S.hollowsMap && Mining.inHollows()) {
                try { drawMap(ctx) } catch (t: Throwable) { FishDiag.fail("HollowsWaypoints.3", "hollows map draw failed", t) }
            }
        }
    }

    private fun onTick(mc: Minecraft) {
        if (++tick % 5 != 0) return
        val p = mc.player ?: return
        if (!Mining.inHollows() || (!S.hollowsWaypoints && !S.hollowsMap)) return
        val pos = p.position()
        curZone = zoneName(pos.x, pos.z)
        val d = defFor(Mining.area) ?: return
        found.getOrPut(d.label) { Found(d) }.add(pos)
    }

    private fun render3d() {
        if (!S.hollowsWaypoints || !Mining.inHollows()) return
        val me = Minecraft.getInstance().player ?: return
        val k = S.hollowsWpScale / 100f
        for (f in found.values) {
            if (!enabled(f.def.group)) continue
            val c = f.center()
            val col = f.def.rgb or 0xFF000000.toInt()
            FishDraw.gizmoBox(AABB(c.x - 0.5, c.y - 0.5, c.z - 0.5, c.x + 0.5, c.y + 0.5, c.z + 0.5), (0x40 shl 24) or f.def.rgb, col, true)
            val dist = me.position().distanceTo(c)
            val label = if (S.hollowsWpDistance) f.def.label + " §7" + dist.toInt() + "m" else f.def.label
            FishDraw.gizmoText(Component.literal(label), Vec3(c.x, c.y + 1.2, c.z), (0.8f + (dist / 60.0).coerceAtMost(2.5).toFloat()) * k, col, true)
        }
        if (S.hollowsWpZones) {
            val y = me.y + 12
            for (z in ZONES) {
                val cx = (z.minX + z.maxX) / 2.0
                val cz = (z.minZ + z.maxZ) / 2.0
                val dist = me.position().distanceTo(Vec3(cx, y, cz))
                if (dist < 40) continue
                val label = if (S.hollowsWpDistance) z.name + " §7" + dist.toInt() + "m" else z.name
                FishDraw.gizmoText(Component.literal(label), Vec3(cx, y, cz), (1.5f + (dist / 40.0).coerceAtMost(6.0).toFloat()) * k, z.rgb or 0xFF000000.toInt(), true)
            }
        }
    }

    private fun drawMap(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        if (mc.hudHidden) return
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(S.hollowsMapX.toFloat(), S.hollowsMapY.toFloat())
        pose.scale(S.hollowsMapScale.toFloat(), S.hollowsMapScale.toFloat())
        val span = (MAX - MIN).toDouble()
        fun mx(x: Double) = ((x - MIN) / span * SIZE).toInt().coerceIn(0, SIZE)
        fun mz(z: Double) = ((z - MIN) / span * SIZE).toInt().coerceIn(0, SIZE)
        ctx.fill(0, 0, SIZE, SIZE, Mining.alpha(0x000000, S.hollowsMapOpacity))
        if (S.hollowsMapZones) for (z in ZONES) {
            ctx.fill(mx(z.minX.toDouble()), mz(z.minZ.toDouble()), mx(z.maxX.toDouble()), mz(z.maxZ.toDouble()), Mining.alpha(z.rgb, 28))
        }
        ctx.fill(0, 0, SIZE, 1, -0x1)
        ctx.fill(0, SIZE - 1, SIZE, SIZE, -0x1)
        ctx.fill(0, 0, 1, SIZE, -0x1)
        ctx.fill(SIZE - 1, 0, SIZE, SIZE, -0x1)
        val font = mc.font
        if (S.hollowsMapZones) for (z in ZONES) {
            val short = z.name.substringBefore(' ')
            ctx.text(font, short, (mx(z.minX.toDouble()) + mx(z.maxX.toDouble())) / 2 - font.width(short) / 2,
                (mz(z.minZ.toDouble()) + mz(z.maxZ.toDouble())) / 2 - 4, z.rgb or 0xFF000000.toInt(), true)
        }
        for (f in found.values) {
            if (!enabled(f.def.group)) continue
            val c = f.center()
            val x = mx(c.x)
            val y = mz(c.z)
            ctx.fill(x - 2, y - 2, x + 2, y + 2, f.def.rgb or 0xFF000000.toInt())
            ctx.text(font, f.def.label.substringBefore(' '), x + 4, y - 4, f.def.rgb or 0xFF000000.toInt(), true)
        }
        val px = mx(p.x)
        val py = mz(p.z)
        ctx.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFF0000.toInt())
        if (curZone.isNotEmpty()) ctx.text(font, curZone, 2, SIZE + 3, -1, true)
        pose.popMatrix()
    }

    private fun drawMapExample(ctx: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font
        val span = (MAX - MIN).toDouble()
        fun mx(x: Double) = ((x - MIN) / span * SIZE).toInt().coerceIn(0, SIZE)
        fun mz(z: Double) = ((z - MIN) / span * SIZE).toInt().coerceIn(0, SIZE)
        ctx.fill(0, 0, SIZE, SIZE, Mining.alpha(0x000000, S.hollowsMapOpacity))
        for (z in ZONES) {
            ctx.fill(mx(z.minX.toDouble()), mz(z.minZ.toDouble()), mx(z.maxX.toDouble()), mz(z.maxZ.toDouble()), Mining.alpha(z.rgb, 28))
            val short = z.name.substringBefore(' ')
            ctx.text(font, short, (mx(z.minX.toDouble()) + mx(z.maxX.toDouble())) / 2 - font.width(short) / 2,
                (mz(z.minZ.toDouble()) + mz(z.maxZ.toDouble())) / 2 - 4, z.rgb or 0xFF000000.toInt(), true)
        }
        ctx.fill(0, 0, SIZE, 1, -0x1); ctx.fill(0, SIZE - 1, SIZE, SIZE, -0x1)
        ctx.fill(0, 0, 1, SIZE, -0x1); ctx.fill(SIZE - 1, 0, SIZE, SIZE, -0x1)
        ctx.fill(SIZE / 2 - 1, SIZE / 2 - 1, SIZE / 2 + 2, SIZE / 2 + 2, 0xFFFF0000.toInt())
    }
}
