package fishmod.features.slayers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import fishmod.features.FishHudEditor
import fishmod.utils.Constants
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import java.io.File

// /trackcarry: counts slayer bosses spawned by a customer that die near you.
object SlayerCarryTracker {

    const val HUD = "Slayer Carries"

    data class Carry(val ign: String, var done: Int = 0, var total: Int = 0, var boss: String = "")

    private class Watched(val ign: String, val stand: ArmorStand, var pos: Vec3, var boss: String)

    private const val SPAWNED_BY = "Spawned by: "
    private const val COUNT_RANGE_SQ = 32.0 * 32.0
    private val FILE = File("config/fishmod/slayer_carries.json")
    private val TIER_RE = Regex("""\b(I|II|III|IV|V)\b""")

    private val carries = LinkedHashMap<String, Carry>()
    private val watched = HashMap<Int, Watched>()
    private var level: Any? = null
    private var tick = 0

    @JvmStatic
    fun init() {
        load()
        FishHudEditor.register(
            HUD,
            { FishSettings.slayerCarryHudX }, { v -> FishSettings.slayerCarryHudX = v },
            { FishSettings.slayerCarryHudY }, { v -> FishSettings.slayerCarryHudY = v },
            140, 14 * 4,
            { FishSettings.slayerCarryHudScale }, { v -> FishSettings.slayerCarryHudScale = v },
            { FishSettings.slayerCarryHudEnabled },
        )
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("SlayerCarryTracker.1", "carry tracker tick failed") { tick(mc) } }
    }

    private fun tick(mc: Minecraft) {
        val lvl = mc.level
        if (lvl !== level) { level = lvl; watched.clear() }
        val player = mc.player ?: return
        if (lvl == null || carries.isEmpty()) return

        val it = watched.values.iterator()
        while (it.hasNext()) {
            val w = it.next()
            if (!w.stand.isRemoved) { w.pos = w.stand.position(); continue }
            it.remove()
            if (player.position().distanceToSqr(w.pos) <= COUNT_RANGE_SQ) count(w)
        }

        if (tick++ % 5 != 0) return
        for (e in lvl.entitiesForRendering()) {
            if (e !is ArmorStand || watched.containsKey(e.id)) continue
            val name = e.customName?.string ?: continue
            if (!name.startsWith(SPAWNED_BY)) continue
            val ign = name.substring(SPAWNED_BY.length).trim().lowercase()
            if (!carries.containsKey(ign)) continue
            watched[e.id] = Watched(ign, e, e.position(), bossName(lvl, e))
        }
    }

    // The boss nametag ("☠ Voidgloom Seraph IV 12M❤") floats just above the "Spawned by" stand
    private fun bossName(lvl: net.minecraft.client.multiplayer.ClientLevel, stand: ArmorStand): String {
        for (o in lvl.getEntities(stand, stand.boundingBox.inflate(1.0, 3.0, 1.0))) {
            val n = (o as? ArmorStand)?.customName?.string ?: continue
            val type = SlayerType.entries.firstOrNull { t -> t.bossNames.any { n.contains(it) } } ?: continue
            val tier = TIER_RE.find(n.substringAfter(type.bossNames.first { n.contains(it) }))?.value
            return if (tier != null) "${type.bossLabel} $tier" else type.bossLabel
        }
        return ""
    }

    private fun count(w: Watched) {
        val c = carries[w.ign] ?: return
        c.done++
        if (w.boss.isNotEmpty()) c.boss = w.boss
        save()
        val left = if (c.total > 0) " §7(${c.done}/${c.total})" else " §7(${c.done})"
        Misc.addChatMessage(Component.literal("§b[FM] §fCarry boss for §e${c.ign}§f done$left"))
        if (c.total > 0 && c.done >= c.total) {
            Misc.forceTitle(Component.literal("§aCarry done!"), Component.literal("§e${c.ign} §7${c.done}/${c.total}"))
            Scheduler.scheduleSound(SoundEvents.PLAYER_LEVELUP, 1f, 1f)
        }
    }

    // ── commands ──

    @JvmStatic
    fun add(ign: String, total: Int) {
        val key = ign.lowercase()
        val c = carries.getOrPut(key) { Carry(ign) }
        if (total >= 0) c.total = total
        save()
        Misc.addChatMessage(Component.literal("§b[FM] §fTracking carries for §e${c.ign}§f: §a${c.done}§7/§f${if (c.total > 0) c.total else "∞"}"))
    }

    @JvmStatic
    fun remove(ign: String) {
        val removed = carries.remove(ign.lowercase())
        watched.values.removeIf { it.ign == ign.lowercase() }
        save()
        Misc.addChatMessage(Component.literal(if (removed != null) "§b[FM] §fStopped tracking §e${removed.ign}§f (${removed.done} done)" else "§b[FM] §cNot tracking $ign"))
    }

    @JvmStatic
    fun clear() {
        carries.clear(); watched.clear(); save()
        Misc.addChatMessage(Component.literal("§b[FM] §fCleared all carries"))
    }

    @JvmStatic
    fun list() {
        if (carries.isEmpty()) {
            Misc.addChatMessage(Component.literal("§b[FM] §7No carries tracked. §f/trackcarry <ign> [amount]"))
            return
        }
        for (c in carries.values) Misc.addChatMessage(Component.literal("§b[FM] §e${c.ign} §7${c.boss} §a${c.done}§7/§f${if (c.total > 0) c.total else "∞"}"))
    }

    // ── HUD ──

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.slayerCarryHudEnabled || carries.isEmpty()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val lines = ArrayList<String>(carries.size + 1)
        lines.add("§5§lSLAYER CARRIES")
        for (c in carries.values) {
            val col = if (c.total > 0 && c.done >= c.total) "§a" else "§f"
            val boss = if (c.boss.isNotEmpty()) " §7${c.boss}" else ""
            lines.add("§e${c.ign}$boss§7: $col${c.done}§7/§f${if (c.total > 0) c.total else "∞"}")
        }
        val lh = Constants.TEXT_HEIGHT + 2
        val sc = FishSettings.slayerCarryHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.slayerCarryHudX.toFloat(), FishSettings.slayerCarryHudY.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in lines.indices) ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
        ctx.pose().popMatrix()
    }

    // ── storage ──

    private fun load() {
        if (!FILE.exists()) return
        try {
            FILE.reader().use { r ->
                val list: List<Carry>? = Gson().fromJson(r, object : TypeToken<List<Carry>>() {}.type)
                list?.forEach { carries[it.ign.lowercase()] = it }
            }
        } catch (e: Exception) {
            FishDiag.fail("SlayerCarryTracker.2", "carry file unreadable", e)
            fishmod.utils.SafeFiles.quarantine(FILE, e)
        }
    }

    private fun save() {
        val json = Gson().toJson(carries.values.toList())
        fishmod.utils.IoExecutor.execute {
            FishDiag.guard("SlayerCarryTracker.3", "carry file write failed") { fishmod.utils.SafeFiles.writeAtomic(FILE, json) }
        }
    }
}
