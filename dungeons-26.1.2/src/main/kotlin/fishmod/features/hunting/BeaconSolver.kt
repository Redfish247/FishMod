package fishmod.features.hunting

import fishmod.features.extras.*

import fishmod.compat.curScreen
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.DrawHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import fishmod.features.extras.ExtrasSettings as S

object BeaconSolver {
    // from SkyHanni (ForagingBeaconSolver): titles, lore patterns, colour order, speeds, pitches, slots
    private val TITLE = Regex("(Upgrade Signal Strength)|Tune Frequency")
    private val COLOR = Regex("Current color: ([\\w ]+)")
    private val SPEED = Regex("Current speed: (\\d+)")
    private val PITCH = Regex("Current pitch: (\\w+)")
    private val COLORS = listOf("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "cyan", "purple", "blue", "brown", "green", "red")
    private val SPEED_TICKS = intArrayOf(52, 42, 32, 22, 12)
    private val PITCHES = listOf("Low" to 0.0952381f, "Normal" to 0.7936508f, "High" to 1.4920635f)
    private val MATCH = 10..16
    private const val COLOR_SLOT = 46
    private const val SPEED_SLOT = 48
    private const val PITCH_SLOT = 50

    private var active = false
    private var offset = 0
    private var refColor = -1; private var ourColor = -1
    private var refSpeed = -1; private var ourSpeed = -1
    private var refPitch = -1; private var ourPitch = -1
    private var refSlot = -1
    private var ticks = 0; private var lastMoveTick = 0; private var moveTick = -100
    private val gaps = ArrayList<Int>()
    private val pitchBuf = ArrayList<Int>()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("BeaconSolver.1", "beacon tick failed") { onTick(mc) } }
        FishEvents.sound.register { snd, _, pitch ->
            if (active && snd.location.toString() == "minecraft:block.note_block.bass") onPitch(pitch)
            false
        }
        DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, _, x, y ->
            if (!active) return@register
            val slot = DrawHooks.currentSlot ?: return@register
            if (slot.container is Inventory) return@register
            val off = when (slot.index) {
                COLOR_SLOT + offset -> diff(refColor, ourColor, COLORS.size)
                SPEED_SLOT + offset -> diff(refSpeed, ourSpeed, SPEED_TICKS.size)
                PITCH_SLOT + offset -> diff(refPitch, ourPitch, PITCHES.size)
                else -> null
            } ?: return@register
            if (off == 0) ctx.fill(x, y, x + 16, y + 16, S.beaconSolverColor)
            else ctx.text(Minecraft.getInstance().font, "§a$off", x + 1, y + 1, 0xFFFFFFFF.toInt(), true)
        }
    }

    // same wrap-around offset as SkyHanni's internalGetOffset
    private fun diff(ref: Int, ours: Int, size: Int): Int? {
        if (ref < 0 || ours < 0) return null
        val o = (ref - ours + size) % size
        return if (o > size / 2) o - size else o
    }

    private fun onTick(mc: Minecraft) {
        val screen = mc.curScreen as? AbstractContainerScreen<*>
        val m = if (S.beaconSolver && screen != null) TITLE.find(TextStrip.plain(screen.title.string)) else null
        if (m == null || screen == null) { if (active) reset(); return }
        if (!active) { reset(); active = true }
        ticks++
        offset = if (m.groupValues[1].isNotEmpty()) -9 else 0
        for (slot in screen.menu.slots) {
            if (slot.container is Inventory) continue
            val st = slot.item
            if (st.isEmpty || st.hasFoil()) continue
            val i = slot.index
            if (i in MATCH) {
                val c = paneColor(st) ?: continue
                refColor = c
                if (i != refSlot) { if (refSlot >= 0) onMove(); refSlot = i }
            }
            when (i) {
                COLOR_SLOT + offset -> lore(st).firstNotNullOfOrNull { COLOR.find(it) }?.let { ourColor = COLORS.indexOf(it.groupValues[1].trim().lowercase().replace(' ', '_')) }
                SPEED_SLOT + offset -> lore(st).firstNotNullOfOrNull { SPEED.find(it) }?.let { ourSpeed = (it.groupValues[1].toIntOrNull() ?: 0) - 1 }
                PITCH_SLOT + offset -> lore(st).firstNotNullOfOrNull { PITCH.find(it) }?.let { ourPitch = PITCHES.indexOfFirst { p -> p.first.equals(it.groupValues[1], true) } }
            }
        }
    }

    private fun onMove() {
        val gap = ticks - lastMoveTick
        lastMoveTick = ticks; moveTick = ticks
        if (gap <= 0 || gap > 80) return
        gaps.add(gap); if (gaps.size > 10) gaps.removeAt(0)
        val med = gaps.sorted()[gaps.size / 2]
        val avg = gaps.filter { it.toDouble() in (med * 0.8)..(med * 1.2) }.average()
        refSpeed = SPEED_TICKS.indices.minBy { kotlin.math.abs(SPEED_TICKS[it] - avg) }
    }

    private fun onPitch(pitch: Float) {
        if (ticks - moveTick > 3) return
        val p = PITCHES.indexOfFirst { it.second == pitch }.takeIf { it >= 0 } ?: return
        pitchBuf.add(p); if (pitchBuf.size > 6) pitchBuf.removeAt(0)
        if (pitchBuf.distinct().size > 2) { pitchBuf.clear(); return }
        if (pitchBuf.size >= 3) refPitch = pitchBuf.groupingBy { it }.eachCount().maxBy { it.value }.key
    }

    private fun paneColor(st: ItemStack): Int? {
        val id = st.item.toString().substringAfter(':')
        if (!id.endsWith("_stained_glass_pane")) return null
        return COLORS.indexOf(id.removeSuffix("_stained_glass_pane")).takeIf { it >= 0 }
    }

    private fun lore(st: ItemStack): List<String> =
        st.get(DataComponents.LORE)?.lines()?.map { TextStrip.plain(it.string).trim() } ?: emptyList()

    private fun reset() {
        active = false; refColor = -1; ourColor = -1; refSpeed = -1; ourSpeed = -1; refPitch = -1; ourPitch = -1
        refSlot = -1; ticks = 0; lastMoveTick = 0; moveTick = -100; gaps.clear(); pitchBuf.clear()
    }
}
