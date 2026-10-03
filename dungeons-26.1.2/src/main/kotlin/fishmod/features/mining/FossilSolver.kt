package fishmod.features.mining

import fishmod.features.FishHudEditor
import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import fishmod.features.mining.MiningSettings as S

// Fossil Excavator solver (SkyHanni FossilSolver port): highlights the next best slot, never clicks
object FossilSolver {

    private data class Tile(val x: Int, val y: Int) { fun slot() = x + y * 9 }
    private class Shape(val tiles: List<Tile>) {
        fun w() = tiles.maxOf { it.x } - tiles.minOf { it.x }
        fun h() = tiles.maxOf { it.y } - tiles.minOf { it.y }
        fun moveTo(x: Int, y: Int) = Shape(tiles.map { Tile(it.x + x, it.y + y) })
        fun rotate(deg: Int): Shape { val w = w(); val h = h()
            return when (deg) {
                90 -> Shape(tiles.map { Tile(it.y, w - it.x) })
                180 -> Shape(tiles.map { Tile(w - it.x, h - it.y) })
                270 -> Shape(tiles.map { Tile(h - it.y, it.x) })
                else -> this
            } }
        fun flip(): Shape { val h = h(); return Shape(tiles.map { Tile(it.x, h - it.y) }) }
    }

    private val ALL = listOf(0 to false, 90 to false, 180 to false, 270 to false, 0 to true, 90 to true, 180 to true, 270 to true)
    private val ROT = ALL.take(4)

    private class Fossil(val name: String, val pct: String, coords: IntArray, val muts: List<Pair<Int, Boolean>>) {
        val shapes: List<Shape> = Shape(coords.toList().chunked(2) { Tile(it[0], it[1]) }).let { base ->
            muts.map { (r, f) -> base.rotate(r).let { if (f) it.flip() else it } }
        }
    }

    private val FOSSILS = listOf(
        Fossil("Tusk", "12.5%", intArrayOf(0,2, 0,3, 0,4, 1,1, 2,0, 3,1, 3,3, 4,2), ALL),
        Fossil("Webbed", "10%", intArrayOf(0,2, 1,1, 2,0, 3,0, 3,1, 3,2, 3,3, 4,0, 5,1, 6,2), listOf(0 to false, 0 to true)),
        Fossil("Club", "9.1%", intArrayOf(0,2, 0,3, 1,2, 1,3, 2,1, 3,0, 4,0, 5,0, 6,0, 6,2, 7,1),
            listOf(0 to false, 180 to false, 0 to true, 180 to true)),
        Fossil("Spine", "8.3%", intArrayOf(0,2, 1,1, 1,2, 2,0, 2,1, 2,2, 3,0, 3,1, 3,2, 4,1, 4,2, 5,2), ROT),
        Fossil("Claw", "7.7%", intArrayOf(0,3, 1,2, 1,4, 2,1, 2,3, 3,1, 3,2, 3,4, 4,0, 4,1, 4,2, 4,3, 5,1), ALL),
        Fossil("Footprint", "7.7%", intArrayOf(0,2, 1,1, 1,2, 1,3, 2,1, 2,2, 2,3, 3,0, 3,2, 3,4, 4,0, 4,2, 4,4), ROT),
        Fossil("Helix", "7.1%", intArrayOf(0,0, 0,1, 0,2, 0,4, 1,0, 1,2, 1,4, 2,0, 2,4, 3,0, 3,1, 3,2, 3,3, 3,4), ALL),
        Fossil("Ugly", "6.2%", intArrayOf(0,1, 1,0, 1,1, 1,2, 2,0, 2,1, 2,2, 2,3, 3,0, 3,1, 3,2, 3,3, 4,0, 4,1, 4,2, 5,1), ROT),
    )

    // Opening sequences (tile, chance, boards left) used before any fossil tile is found
    private val RISKY = listOf(Tile(4, 2) to 0.515, Tile(5, 3) to 0.393, Tile(3, 2) to 0.513, Tile(7, 2) to 0.345,
        Tile(1, 3) to 0.342, Tile(3, 4) to 0.6, Tile(5, 1) to 0.8, Tile(4, 3) to 1.0)
    private val SAFE = listOf(Tile(4, 2) to 0.515, Tile(5, 4) to 0.413, Tile(3, 3) to 0.461, Tile(5, 2) to 0.387,
        Tile(3, 1) to 0.342, Tile(7, 3) to 0.48, Tile(1, 2) to 0.846, Tile(3, 4) to 1.0)

    private val CHARGES = Regex("""Chisel Charges Remaining: (\d+)""")
    private val PROGRESS = Regex("""Fossil Excavation Progress: ([\d.]+%)""")

    private var sig = 0
    private var bestSlot = -1
    private var bestPct = 0.0
    private var status = ""
    private var remaining = 0
    private var charges = 0
    private var maxCharges = 0
    private var types: List<String> = emptyList()

    private fun screen(): AbstractContainerScreen<*>? =
        (Minecraft.getInstance().screen as? AbstractContainerScreen<*>)?.takeIf { it.title.string == "Fossil Excavator" }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            val scr = screen()
            if (!S.miningFossilSolver || scr == null) { if (sig != 0) reset(); return@register }
            val items = scr.menu.slots.filter { it.index < 54 && it.container !== Minecraft.getInstance().player?.inventory }
            val s = items.joinToString { it.item.hoverName.string }.hashCode()
            if (s != sig) { sig = s; update(items) }
        }
        DrawEvents.INVENTORY_SLOT_BEFORE.register { ctx, _, x, y ->
            if (!S.miningFossilSolver || bestSlot < 0 || screen() == null) return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.index != bestSlot || slot.container === Minecraft.getInstance().player?.inventory) return@register
            ctx.fill(x, y, x + 16, y + 16, Mining.alpha(S.miningFossilColor, S.miningFossilOpacity))
        }
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, _, x, y ->
            if (!S.miningFossilSolver || !S.miningFossilPercent || bestSlot < 0 || screen() == null) return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.index != bestSlot || slot.container === Minecraft.getInstance().player?.inventory) return@register
            val pose = ctx.pose(); pose.pushMatrix(); pose.translate(x.toFloat(), y + 10f); pose.scale(0.5f, 0.5f)
            ctx.text(Minecraft.getInstance().font, "§2${"%.1f".format(bestPct * 100)}%", 0, 0, -1, true)
            pose.popMatrix()
        }
        FishHudEditor.register("Fossil Solver", { S.miningFossilHudX }, { S.miningFossilHudX = it },
            { S.miningFossilHudY }, { S.miningFossilHudY = it }, 150, 60, { S.miningFossilHudScale }, { S.miningFossilHudScale = it },
            { S.miningFossilSolver })
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, scr, _, _ ->
            if (scr !is AbstractContainerScreen<*>) return@AfterInit
            ScreenEvents.afterExtract(scr).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                if (S.miningFossilSolver && screen() != null && status.isNotEmpty())
                    MiningHuds.draw(ctx, panel(), S.miningFossilHudX, S.miningFossilHudY, S.miningFossilHudScale)
            })
        })
    }

    private fun reset() { sig = 0; bestSlot = -1; status = ""; types = emptyList(); maxCharges = 0; charges = 0 }

    private fun panel(): List<String> {
        val out = arrayListOf("§6§lFossil Solver", status, "§eCharges: §a$charges")
        if (types.isNotEmpty()) out += "§eTypes: §7" + types.joinToString(", ")
        return out
    }

    private fun update(items: List<net.minecraft.world.inventory.Slot>) {
        val fossils = HashSet<Int>(); val dirt = HashSet<Int>()
        var pct: String? = null
        for (sl in items) {
            val st = sl.item
            val name = Mining.strip(st.hoverName.string)
            when (name) { "Dirt" -> dirt += sl.index; "Fossil" -> fossils += sl.index; else -> continue }
            val lore = st.get(DataComponents.LORE)?.lines()?.map { Mining.strip(it.string) } ?: continue
            for (l in lore) {
                CHARGES.find(l)?.let { charges = it.groupValues[1].toInt(); if (maxCharges == 0) maxCharges = charges }
                if (name == "Fossil") PROGRESS.find(l)?.let { pct = it.groupValues[1] }
            }
        }
        if (fossils.isEmpty() && dirt.isEmpty()) { bestSlot = -1; status = ""; return }
        solve(fossils, dirt, pct)
    }

    private fun solve(fossilSlots: Set<Int>, dirtSlots: Set<Int>, pct: String?) {
        val invalid = (0..53).filter { it !in fossilSlots && it !in dirtSlots }.map { Tile(it % 9, it / 9) }.toSet()
        val found = fossilSlots.map { Tile(it % 9, it / 9) }.toSet()
        val seq = if (maxCharges in 1..17) RISKY else SAFE
        if (found.isEmpty() && invalid.all { t -> seq.any { it.first == t } }) {
            val move = seq.getOrNull(invalid.size)
            if (move == null) { bestSlot = -1; status = "§cNo possible fossils on board."; return }
            bestSlot = move.first.slot(); bestPct = move.second
            status = "§eOpening move ${invalid.size + 1}"
            return
        }
        val pool = if (pct == null) FOSSILS else FOSSILS.filter { it.pct == pct }
        types = if (pct == null) emptyList() else pool.map { it.name }
        val counts = HashMap<Tile, Int>()
        var total = 0
        for (x in 0..8) for (y in 0..5) for (f in pool) for (shape in f.shapes) {
            val placed = shape.moveTo(x, y)
            if (placed.tiles.any { it in invalid || it.x !in 0..8 || it.y !in 0..5 }) continue
            if (!found.all { it in placed.tiles }) continue
            total++
            for (t in placed.tiles) counts[t] = (counts[t] ?: 0) + 1
        }
        found.forEach { counts.remove(it) }
        val best = counts.maxByOrNull { it.value }
        if (best == null) {
            bestSlot = -1
            status = if (found.isNotEmpty()) "§aFossil found, get all the loot you can." else "§cNo possible fossils on board."
            return
        }
        bestSlot = best.key.slot(); bestPct = best.value.toDouble() / total
        remaining = total
        status = "§ePossible fossils: §a$remaining"
    }
}
