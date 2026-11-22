package fishmod.features.slayer

import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.Level
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3

// Shows the stack name and count above dropped items
object GroundItemNames {

    private class Entry(val e: ItemEntity, var count: Int, var label: Component)

    private val cache = HashMap<Int, Entry>()
    @Volatile private var visible: List<Entry> = emptyList()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!S.groundItemNames) {
                if (cache.isNotEmpty()) { cache.clear(); visible = emptyList() }
            } else if (++tick % 5 == 0) FishDiag.guard("GroundItemNames.2", "ground item scan failed") { scan(mc) }
        }
        FishEvents.worldChange.register { cache.clear(); visible = emptyList(); false }
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            if (!S.groundItemNames) return@register
            val list = visible
            if (list.isEmpty()) return@register
            val player = Minecraft.getInstance().player ?: return@register
            val eye = player.eyePosition
            val look = player.getViewVector(1f)
            val scale = S.groundItemScale.toFloat()
            FishDiag.guard("GroundItemNames.1", "ground item label render failed") {
                for (en in list) {
                    val e = en.e
                    if (e.isRemoved) continue
                    // skip labels behind the camera
                    if ((e.x - eye.x) * look.x + (e.y - eye.y) * look.y + (e.z - eye.z) * look.z < 0) continue
                    FishDraw.renderText(ctx, ps, en.label, Vec3(e.x, e.y + 0.8, e.z), scale)
                }
            }
        }
    }

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val r = S.groundItemRange.coerceIn(2, 64).toDouble()
        val seen = HashSet<Int>()
        val out = ArrayList<Entry>()
        for (e in level.getEntitiesOfClass(ItemEntity::class.java, player.boundingBox.inflate(r)) { !it.isRemoved }) {
            if (e.distanceToSqr(player) > r * r) continue
            val st = e.item
            if (st.isEmpty) continue
            val c = cache[e.id]
            if (c != null && c.e === e) {
                if (c.count != st.count) { c.count = st.count; c.label = label(e) }
                seen.add(e.id); out.add(c); continue
            }
            // Cosmetic items (rune eggs, particle items) float, fly or have no Skyblock id
            if (e.isNoGravity || e.isInvisible) continue
            if (!e.onGround() && e.tickCount < 40) continue
            if (ItemTools.getId(st) == null) continue
            if (inGlassCase(level, e)) continue
            val en = Entry(e, st.count, label(e))
            cache[e.id] = en
            seen.add(e.id); out.add(en)
        }
        cache.keys.retainAll(seen)
        visible = out
    }

    // Display-case items: glass directly above or beside the item's block
    private fun inGlassCase(level: Level, e: ItemEntity): Boolean {
        val p = e.blockPosition()
        for (n in arrayOf(p.above(), p.north(), p.south(), p.east(), p.west())) {
            if (BuiltInRegistries.BLOCK.getKey(level.getBlockState(n).block).path.contains("glass")) return true
        }
        return false
    }

    private fun label(e: ItemEntity): Component {
        val st = e.item
        val name = st.hoverName.copy()
        if (st.count > 1) name.append(Component.literal(" §7x" + st.count))
        return name
    }
}
