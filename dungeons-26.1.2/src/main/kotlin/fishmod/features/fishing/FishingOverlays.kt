package fishmod.features.fishing

import fishmod.features.fishing.FishingWorldSettings as W
import fishmod.features.mining.MiningHuds
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

// Bobbin' Time bobber count, Legion player count and Lotus Atoll lilypad size (lilypad logic from RiccioFishingUtils-Modern).
object FishingOverlays {
    private const val BOBBIN_RANGE_SQ = 10.0 * 10.0
    private const val LEGION_RANGE_SQ = 30.0 * 30.0
    @Volatile private var bobbers = 0
    @Volatile private var legion = 0
    private var hasBobbin = false
    private var hasLegion = false
    private var tick = 0

    private class Lily(var pos: Vec3, var scale: Float, var changedAt: Long)
    private val lilies = HashMap<Int, Lily>()
    @Volatile private var lilyLabels: List<Pair<Vec3, String>> = emptyList()

    private fun armorHas(p: Player, word: String): Boolean {
        for (slot in arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val lore = p.getItemBySlot(slot).get(DataComponents.LORE)?.lines() ?: continue
            if (lore.any { it.string.contains(word) }) return true
        }
        return false
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { FishDiag.guard("FishingOverlays.1", "fishing overlay scan failed") { onTick() } }
        MiningHuds.reg("Bobbin Time", "fishing_bobbin", 70, 10,
            { W.bobbinX }, { W.bobbinX = it }, { W.bobbinY }, { W.bobbinY = it }, { W.bobbinScale }, { W.bobbinScale = it },
            { W.bobbinHud && (!W.overlayNeedArmor || hasBobbin) }, { W.bobbinHud },
            example = { listOf("§bBobbers\t§a5") }, lines = { listOf("§bBobbers\t§a" + bobbers) })
        MiningHuds.reg("Legion", "fishing_legion", 70, 10,
            { W.legionX }, { W.legionX = it }, { W.legionY }, { W.legionY = it }, { W.legionScale }, { W.legionScale = it },
            { W.legionHud && (!W.overlayNeedArmor || hasLegion) }, { W.legionHud },
            example = { listOf("§bLegion\t§a12") }, lines = { listOf("§bLegion\t§a" + legion) })
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            val ls = lilyLabels
            if (ls.isEmpty()) return@register
            FishDiag.guard("FishingOverlays.2", "lilypad text render failed") {
                for ((p, t) in ls) FishDraw.renderText(ctx, ps, Component.literal(t), p, 1.2f)
            }
        }
    }

    private fun onTick() {
        val mc = Minecraft.getInstance()
        val level = mc.level; val me = mc.player
        if (level == null || me == null) { lilies.clear(); lilyLabels = emptyList(); return }
        tick++
        if (W.bobbinHud || W.legionHud) {
            if (tick % 20 == 0) { hasBobbin = armorHas(me, "Bobbin"); hasLegion = armorHas(me, "Legion") }
            if (tick % 5 == 0) {
                var b = 0; var l = 0
                for (e in level.entitiesForRendering()) {
                    if (e is FishingHook && e.distanceToSqr(me) <= BOBBIN_RANGE_SQ) b++
                    else if (e is Player && e !== me && e.distanceToSqr(me) <= LEGION_RANGE_SQ && e.uuid.version() == 4) l++
                }
                bobbers = b; legion = l
            }
        }
        if (!W.lilypadSize || !SkyblockLocation.`in`(SkyblockLocation.LOTUS_ATOLL)) { lilies.clear(); lilyLabels = emptyList(); return }
        val now = System.currentTimeMillis()
        val pads = level.entitiesForRendering().filterIsInstance<Display.ItemDisplay>().filter { it.itemStack.item == Items.LILY_PAD }
        lilies.keys.retainAll(pads.map { it.id }.toSet())
        val out = ArrayList<Pair<Vec3, String>>()
        for (e in pads) {
            val scale = e.renderState()?.transformation()?.get(1.0f)?.scale()?.x() ?: continue
            val pos = e.position()
            val st = lilies[e.id]
            if (st == null) lilies[e.id] = Lily(pos, scale, 0L)
            else if (pos.distanceToSqr(st.pos) > 0.0001 || Math.abs(scale - st.scale) > 0.0001f) { st.pos = pos; st.scale = scale; st.changedAt = now }
            val s = lilies[e.id]!!
            if (now - s.changedAt < 5000L) {
                val pct = ((scale - 0.6f) / (8.0f - 0.6f) * 100f).coerceIn(0f, 100f)
                out.add(Vec3(pos.x, pos.y + e.bbHeight + 0.5, pos.z) to "§a" + Math.round(pct) + "%")
            }
        }
        lilyLabels = out
    }
}
