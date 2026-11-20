package fishmod.features.slayer

import fishmod.utils.FishConstants
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import fishmod.features.slayer.BossToolsSettings as S

// Boss Phases-style HP label for dungeon floor bosses and other major bosses
object BossDamageIndicator {
    // from SkyHanni (BossType names)
    private val DUNGEON = listOf("Bonzo", "Scarf", "Professor", "Thorn", "Livid", "Sadan",
        "Boulder Tosser", "Sword Thrower", "Bigfoot Jumper", "Laser Shooter")
    private val NETHER = listOf("Bladesoul", "Magma Boss", "Ashfang", "Barbarian Duke", "Mage Outlaw", "Vanquisher")
    private val OTHER = listOf("Endstone Protector", "Headless Horseman", "Minos Inquisitor", "Minos Champion", "Gaia Construct",
        "Minotaur", "Thunder", "Lord Jawbus", "Ragnarok", "Arachne", "Broodmother", "Leech Supreme", "Bacte", "Sun Gecko")
    private val HP = Regex("([\\d,.]+)\\s*([kKmMbB])?\\s*/?\\s*(?:[\\d,.]+\\s*[kKmMbB]?)?\\s*❤")

    private class Boss(val name: String, var hp: Double, var max: Double, val since: Long, var pos: Vec3, var seen: Long)
    private val bosses = HashMap<Int, Boss>()
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            if (S.bossIndicator && tick++ % 4 == 0) try { scan() } catch (e: Exception) { FishDiag.fail("BossDamageIndicator.1", "boss scan failed", e) }
        })
        FishEvents.worldChange.register { bosses.clear(); false }
        RenderHooks.GIZMO.register { _ ->
            if (S.bossIndicator) try { render() } catch (e: Exception) { FishDiag.fail("BossDamageIndicator.2", "boss label render failed", e) }
        }
    }

    private fun names(): List<String> = (if (S.bossIndicatorDungeon) DUNGEON else emptyList()) +
        (if (S.bossIndicatorNether) NETHER else emptyList()) + (if (S.bossIndicatorOther) OTHER else emptyList())

    private fun scan() {
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        val list = names()
        val now = System.currentTimeMillis()
        if (list.isNotEmpty()) for (st in level.getEntitiesOfClass(ArmorStand::class.java, p.boundingBox.inflate(48.0, 32.0, 48.0)) { it.hasCustomName() }) {
            val s = st.customName?.string?.replace(FishConstants.STRIP_COLOR_REGEX, "") ?: continue
            val name = list.firstOrNull { s.contains(it) } ?: continue
            if (name == "Sun Gecko" && s.contains("?")) continue
            val m = HP.find(s) ?: continue
            val hp = (m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue) * mult(m.groupValues[2])
            val b = bosses.getOrPut(st.id) { Boss(name, hp, hp, now, st.position(), now) }
            b.hp = hp; if (hp > b.max) b.max = hp; b.pos = st.position(); b.seen = now
        }
        bosses.values.removeIf { now - it.seen > 1_500L }
    }

    private fun mult(u: String) = when (u.lowercase()) { "k" -> 1e3; "m" -> 1e6; "b" -> 1e9; else -> 1.0 }

    private fun short(v: Double): String = when {
        v >= 1e9 -> "%.2fB".format(v / 1e9)
        v >= 1e6 -> "%.2fM".format(v / 1e6)
        v >= 1e4 -> "%.1fk".format(v / 1e3)
        else -> v.toLong().toString()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        for (b in bosses.values) {
            val f = if (b.max > 0) b.hp / b.max else 1.0
            val c = when { f > 0.75 -> "§a"; f > 0.5 -> "§e"; f > 0.25 -> "§6"; else -> "§c" }
            val y = b.pos.y + 0.6
            FishDraw.gizmoText(Component.literal("$c${short(b.hp)}§7/§a${short(b.max)} §c❤ §7(${(f * 100).toInt()}%)"), Vec3(b.pos.x, y, b.pos.z), 1.6f, -0x1)
            if (S.bossIndicatorTime) FishDraw.gizmoText(Component.literal("§7⏱ §f${"%.1f".format((now - b.since) / 1000.0)}s"), Vec3(b.pos.x, y + 0.45, b.pos.z), 0.9f, -0x1)
        }
    }
}
