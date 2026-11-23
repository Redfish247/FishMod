package fishmod.features.fishing

import fishmod.features.AlertTitles
import fishmod.features.mining.MiningHuds
import fishmod.utils.FishUtil
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import fishmod.features.fishing.FishingWorldSettings as W

object OwnSeaCreatures {
    private class Live(val name: String, val born: Long, var pos: Vec3)
    private val live = LinkedHashMap<Int, Live>()
    private val pending = ArrayList<Pair<SeaCreature, Long>>()
    private var warned = false
    private var tick = 0

    fun init() {
        FishEvents.worldChange.register { live.clear(); pending.clear(); warned = false; false }
        FishingEvents.seaCreature.register { e ->
            if (W.scKillTimer || W.scCap) repeat(e.count) { pending.add(e.creature to e.atMs) }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (++tick % 2 == 0) FishDiag.guard("OwnSeaCreatures.1", "own creature scan threw") { onTick() } }
        MiningHuds.reg("Sea Creature Count", "fishing_sc_count", 90, 10,
            { W.scCapX }, { W.scCapX = it }, { W.scCapY }, { W.scCapY = it }, { W.scCapScale }, { W.scCapScale = it },
            { W.scCap && W.scCapHud && live.isNotEmpty() }, { W.scCap && W.scCapHud },
            example = { listOf("§bCreatures\t§a4/" + W.scCapMax) }, lines = { listOf("§bCreatures\t" + (if (live.size >= W.scCapMax) "§c" else "§a") + live.size + "/" + W.scCapMax) })
    }

    private fun onTick() {
        val mc = Minecraft.getInstance()
        val level = mc.level; val pl = mc.player
        if (level == null || pl == null || !(W.scKillTimer || W.scCap)) { live.clear(); pending.clear(); return }
        val now = System.currentTimeMillis()
        pending.removeIf { now - it.second > 3000 }
        val it = live.entries.iterator()
        while (it.hasNext()) {
            val (id, c) = it.next()
            val e = level.getEntity(id)
            if (e != null && e.isAlive) { c.pos = e.position(); continue }
            it.remove()
            if (W.scKillTimer && pl.position().distanceTo(c.pos) < 24) FishUtil.addChatMessage(Component.literal("§b" + c.name + " §7took §e" + "%.1f".format((now - c.born) / 1000.0) + "s §7to die."))
        }
        if (pending.isNotEmpty()) for (s in level.getEntitiesOfClass(ArmorStand::class.java, pl.boundingBox.inflate(40.0))) {
            if (s.id in live || !s.isCustomNameVisible) continue
            val plain = s.customName?.let { TextStrip.plain(it.string) } ?: continue
            val i = pending.indexOfFirst { plain.contains(it.first.name) }
            if (i < 0) continue
            val p = pending.removeAt(i)
            live[s.id] = Live(p.first.name, p.second, s.position())
            if (pending.isEmpty()) break
        }
        if (W.scCap) {
            if (live.size >= W.scCapMax && !warned) {
                warned = true
                AlertTitles.show(Component.literal("§c§lSEA CREATURE CAP"), Component.literal("§7" + live.size + "/" + W.scCapMax + " alive"), 2000)
                SoundManager.play(SoundManager.preset(W.scCapSound), 1f)
            } else if (live.size < W.scCapMax) warned = false
        }
    }
}
