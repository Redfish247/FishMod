package fishmod.features.dungeon

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.compat.cam
import fishmod.utils.dungeon.BossPhase
import fishmod.features.dungeon.map.Room
import fishmod.features.dungeon.map.Scan
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.AbstractSkullBlock
import net.minecraft.world.phys.AABB
import java.util.concurrent.ConcurrentHashMap

/**
 * Labelled through-walls boxes for every known secret in the dungeon (chest, item, bat, wither essence,
 * redstone key). Positions come from the bundled rooms.json secretDetails, rotated into world space with
 * the same clay/rotation transform the map uses.
 */
object SecretWaypoints {

    private class Entry(val type: String, val pos: BlockPos, val box: AABB, val label: Component, val argb: Int, val room: Room)

    @Volatile private var entries: List<Entry> = emptyList()
    private val collected = ConcurrentHashMap.newKeySet<BlockPos>()
    private var tick = 0

    private val LABELS = mapOf(
        "chest" to "Chest", "item" to "Item", "bat" to "Bat",
        "wither" to "Essence", "redstone_key" to "Redstone Key"
    )

    private fun enabled(type: String): Boolean = when (type) {
        "chest" -> FishSettings.secretWpChest
        "item" -> FishSettings.secretWpItem
        "bat" -> FishSettings.secretWpBat
        "wither" -> FishSettings.secretWpEssence
        "redstone_key" -> FishSettings.secretWpRedstoneKey
        else -> false
    }

    private fun color(type: String): Int = when (type) {
        "chest" -> FishSettings.secretWpColorChest
        "item" -> FishSettings.secretWpColorItem
        "bat" -> FishSettings.secretWpColorBat
        "wither" -> FishSettings.secretWpColorEssence
        "redstone_key" -> FishSettings.secretWpColorRedstoneKey
        else -> -1
    }

    @JvmStatic
    fun markCollected(pos: BlockPos) { collected.add(pos.immutable()) }

    private fun active(): Boolean =
        FishSettings.secretWaypointsEnabled && SkyblockLocation.inDungeon() && !BossPhase.inBoss()

    @JvmStatic
    fun init() {
        fishmod.utils.events.FishEvents.worldChange.register { collected.clear(); entries = emptyList(); false }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            try { onTick(mc) } catch (e: Exception) { FishDiag.fail("SecretWaypoints.1", "secret waypoint rebuild failed", e) }
        })
        RenderHooks.NO_DEPTH_FILLED.register { ctx, matrices, vc ->
            try { render(ctx, matrices, vc) } catch (e: Exception) { FishDiag.fail("SecretWaypoints.2", "secret waypoint render failed", e) }
        }
    }

    private fun onTick(mc: Minecraft) {
        if (!active()) { if (entries.isNotEmpty()) entries = emptyList(); return }
        if (++tick < 10) return
        tick = 0
        val level = mc.level ?: return
        val out = ArrayList<Entry>()
        for (room in Scan.roomsSnapshot()) {
            if (room.rotation == Room.Rotation.NONE || room.clayPos == null) continue
            if (FishSettings.secretWpHideCleared && (room.state == Room.State.CLEARED || room.state == Room.State.GREEN)) continue
            val details = room.data?.secretDetails ?: continue
            for ((type, locals) in details) {
                if (!enabled(type)) continue
                val label = LABELS[type] ?: type.replace('_', ' ').replaceFirstChar { it.uppercase() }
                for (local in locals) {
                    val world = room.offset(local) ?: continue
                    if (world in collected) continue
                    if ((type == "wither" || type == "redstone_key") && level.getBlockState(world).block !is AbstractSkullBlock) continue
                    val argb = color(type)
                    val x = world.x.toDouble(); val y = world.y.toDouble(); val z = world.z.toDouble()
                    val box = if (type == "bat" || type == "item") AABB(x + 0.25, y, z + 0.25, x + 0.75, y + 0.5, z + 0.75)
                    else AABB(x, y, z, x + 1.0, y + 1.0, z + 1.0)
                    out.add(Entry(type, world, box, Component.literal(label).withColor(argb and 0xFFFFFF), argb, room))
                }
            }
        }
        entries = out
    }

    private fun render(ctx: LevelRenderContext, matrices: PoseStack, vc: VertexConsumer) {
        if (!active()) return
        val list = entries
        if (list.isEmpty()) return
        val mc = Minecraft.getInstance()
        val eye = mc.cam.position()
        val look = FishDraw.lookDir()
        val maxDist = FishSettings.secretWpMaxDistance.toDouble()
        val style = FishSettings.secretWpStyle
        val lw = FishSettings.secretWpLineWidth.coerceIn(0.5, 10.0) / 100.0
        for (e in list) {
            if (FishDraw.boxBehindCamera(e.box, eye, look)) continue
            val c = e.box.center
            if (maxDist > 0 && c.distanceTo(eye) > maxDist) continue
            val fill = FishDraw.toFloats((e.argb and 0xFFFFFF) or (0x55 shl 24))
            val line = FishDraw.toFloats(e.argb or (0xFF shl 24))
            if (style != "Outline") FishDraw.renderFilled(matrices, vc, e.box.inflate(0.002), fill)
            if (style != "Filled") FishDraw.renderThickOutline(matrices, vc, e.box, line, lw)
            if (FishSettings.secretWpLabels) FishDraw.renderText(ctx, matrices, e.label, c.x, e.box.maxY + 0.4, c.z, 1.0f)
        }
    }
}
