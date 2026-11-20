package fishmod.features.rift

import fishmod.features.mining.MiningHuds
import fishmod.utils.FishConstants
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.decoration.ArmorStand
import fishmod.features.other.BossToolsSettings as S

// Time Chamber Sun Gecko HUD: HP, revival phase, combo hits and modifiers
object SunGeckoHelper {
    // from SkyHanni (SunGeckoHelper patterns, colours stripped)
    private val COMBO = Regex("\\[([^\\]]*?) x(\\d+) ([^\\]]*)]")
    private const val MODS_START = "ACTIVE MODIFIERS!"
    private const val MODS_END = "▬▬▬▬▬▬▬▬▬▬"
    private val MODIFIERS = listOf("Revival", "Combo Manic", "Time Sliced", "Buffantics", "Collective", "Brand New Dance", "Culmination")
    private val HP = Regex("([\\d,.]+)\\s*([kKmM])?\\s*❤")

    private var hp = 0
    private var maxHp = 0
    private var bossId = -1
    private var firstPhase = true
    private var combo = 1
    private var hits = ""
    private var lastHit = 0L
    private var scanning = false
    private val mods = LinkedHashSet<String>()
    private var tick = 0

    private fun inChamber(): Boolean = Rift.inRift() && Rift.area().contains("Time Chamber", true)

    private fun reset() { hp = 0; maxHp = 0; bossId = -1; firstPhase = true; combo = 1; hits = ""; lastHit = 0L; mods.clear() }

    fun init() {
        FishEvents.chat.register { text ->
            if (S.sunGecko && Rift.inRift()) onChat(text.chatPlain.trim())
            false
        }
        ClientReceiveMessageEvents.GAME.register { msg, overlay ->
            if (overlay && S.sunGecko && inChamber()) onActionBar(msg.string.replace(FishConstants.STRIP_COLOR_REGEX, ""))
        }
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { if (S.sunGecko && tick++ % 5 == 0) scan() })
        FishEvents.worldChange.register { reset(); false }
        MiningHuds.reg("Sun Gecko Helper", "rift_sun_gecko", 150, 60,
            { S.sunGeckoX }, { S.sunGeckoX = it }, { S.sunGeckoY }, { S.sunGeckoY = it },
            { S.sunGeckoScale }, { S.sunGeckoScale = it },
            { S.sunGecko && inChamber() && bossId >= 0 }, { S.sunGecko },
            example = { listOf("§eSun Gecko §a180§f/§a250§c❤", "§7Combo\t§a6/10 §e§lx3", "§7Combo Timer\t§a2.4s", "§6Modifiers\t§eRevival") }, lines = ::lines)
    }

    private fun onChat(s: String) {
        if (s.endsWith(MODS_START)) { scanning = true; mods.clear(); return }
        if (s.startsWith(MODS_END)) { scanning = false; return }
        if (scanning) MODIFIERS.filter { s.contains(it) }.forEach { mods.add(it) }
    }

    private fun onActionBar(s: String) {
        val m = COMBO.find(s) ?: return
        combo = m.groupValues[2].toIntOrNull() ?: 1
        var count = m.groupValues[1].count { it == '⬛' } + m.groupValues[3].count { it == '⬛' }
        var total = 10
        if ("Culmination" in mods) total--
        if (count == 9 && total == 8) count = 1
        val h = "$count/$total"
        if (h != hits) lastHit = System.currentTimeMillis()
        hits = h
    }

    private fun scan() {
        if (!inChamber()) { if (bossId >= 0) reset(); return }
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        val st = level.getEntitiesOfClass(ArmorStand::class.java, p.boundingBox.inflate(40.0, 20.0, 40.0)) { it.hasCustomName() }
            .firstOrNull { val n = it.customName?.string ?: ""; n.contains("Sun Gecko") && !n.contains("?") && n.contains("❤") }
        if (st == null) return
        val m = HP.find(st.customName!!.string.replace(FishConstants.STRIP_COLOR_REGEX, "")) ?: return
        val v = ((m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return) * when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; else -> 1.0 }).toInt()
        if (bossId >= 0 && v > hp + 1 && hp > 0) { firstPhase = false; mods.add("Revival") }
        bossId = st.id
        hp = v
        if (v > maxHp) maxHp = v
    }

    private fun lines(): List<String> {
        var shown = hp
        var total = maxHp
        if ("Revival" in mods) { if (firstPhase) shown += maxHp; total *= 2 }
        val c = if (shown <= total / 2) "§e" else "§a"
        // from SkyHanni: combo expires ~4.7s after the last hit, +0.2s per modifier with Collective
        var expiry = 4_700L
        if ("Collective" in mods) expiry += mods.size * 200L
        val left = ((lastHit + expiry - System.currentTimeMillis()).coerceIn(0L, expiry)) / 1000.0
        val out = mutableListOf("§eSun Gecko $c$shown§f/§a$total§c❤", "§7Combo\t§a$hits §e§lx$combo", "§7Combo Timer\t§a${"%.1f".format(left)}s")
        if (S.sunGeckoModifiers) out += "§6Modifiers\t§e" + (if (mods.isEmpty()) "None" else mods.joinToString(", "))
        return out
    }
}
