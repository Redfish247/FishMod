package fishmod.features.events

import fishmod.features.extras.*

import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import fishmod.features.extras.ExtrasSettings as S

// Carnival Zombie Shootout: outlines zombies by helmet tier (diamond = most points)
object CarnivalShootout {
    // from SkyHanni (CarnivalZombieShootout): start/end lines and helmet -> colour
    private const val START = "[NPC] Carnival Cowboy: Good luck, pal!"
    private const val END = "Zombie Shootout"
    private val HELMETS = mapOf(
        Items.LEATHER_HELMET to 0xFFA52A2A.toInt(), Items.IRON_HELMET to 0xFFC0C0C0.toInt(),
        Items.GOLDEN_HELMET to 0xFFFFD700.toInt(), Items.DIAMOND_HELMET to 0xFF2CD6FA.toInt(),
    )
    private var active = false
    @Volatile private var glow: Map<Int, Int> = emptyMap()
    private var tick = 0

    fun init() {
        FishEvents.chat.register { text ->
            if (S.carnivalShootout) {
                val s = text.chatPlain
                if (s.trim() == START) active = true
                else if (active && s.trim() == END && s.startsWith("    ")) active = false
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (++tick % 2 != 0) return@register
            val level = mc.level
            if (!S.carnivalShootout || !active || level == null) { if (glow.isNotEmpty()) glow = emptyMap(); return@register }
            try {
                val out = HashMap<Int, Int>()
                for (e in level.entitiesForRendering()) {
                    if (e !is LivingEntity || !e.isAlive || net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.type).path != "zombie") continue
                    HELMETS[e.getItemBySlot(EquipmentSlot.HEAD).item]?.let { out[e.id] = it }
                }
                glow = out
            } catch (t: Throwable) {
                FishDiag.fail("CarnivalShootout.1", "shootout scan failed", t)
            }
        }
    }

    @JvmStatic
    fun glowColor(e: Entity): Int = glow[e.id] ?: EntityRenderState.NO_OUTLINE
}
