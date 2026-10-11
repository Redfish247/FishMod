package fishmod.features.dungeon.f7

import fishmod.features.dungeon.f7.dragons.WitherDragon
import fishmod.utils.Misc
import fishmod.utils.Scheduler
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand

// F7/M7: sums damage numbers near withers/dragons, yours = numbers that pop right after your swing/shot
object BossDamage {

    private const val MELEE_MS = 700L
    private const val RANGED_MS = 1500L
    private val SPLASH = Regex("""^\D{0,3}([\d,]+(?:\.\d+)?)([kKmM]?)\D{0,3}$""")

    private class Tally { var mine = 0.0; var total = 0.0 }

    private val tallies = LinkedHashMap<String, Tally>()
    private val seen = HashSet<Int>()
    private var lastMeleeMs = 0L
    private var lastUseMs = 0L

    @JvmStatic
    fun init() {
        AttackEntityCallback.EVENT.register { _, level, _, _, _ ->
            if (level.isClientSide) lastMeleeMs = System.currentTimeMillis()
            InteractionResult.PASS
        }
        UseItemCallback.EVENT.register { _, level, _ ->
            if (level.isClientSide) lastUseMs = System.currentTimeMillis()
            InteractionResult.PASS
        }
        Events.ON_WORLD_CHANGE.register { tallies.clear(); seen.clear(); false }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (FishSettings.bossDamageEnabled && Phase.isInFloor7() && Phase.inBoss())
                FishDiag.guard("BossDamage.1", "boss damage scan") { scan(mc) }
        }
        Events.ON_RUN_END.register {
            if (FishSettings.bossDamageEnabled && tallies.isNotEmpty())
                Scheduler.scheduleTask(Runnable { FishDiag.guard("BossDamage.2", "boss damage print") { print() } }, 20)
            false
        }
    }

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val bosses = level.getEntitiesOfClass(Entity::class.java, player.boundingBox.inflate(96.0)) { it is WitherBoss || it is EnderDragon }
        if (bosses.isEmpty()) return
        val now = System.currentTimeMillis()
        val mineWindow = now - lastMeleeMs < MELEE_MS || now - lastUseMs < RANGED_MS
        for (boss in bosses) {
            for (stand in level.getEntitiesOfClass(ArmorStand::class.java, boss.boundingBox.inflate(4.0))) {
                if (stand.tickCount > 3 || stand.id in seen) continue
                val raw = stand.customName?.string?.replace(Regex("§."), "")?.trim() ?: continue
                val dmg = parse(raw) ?: continue
                seen.add(stand.id)
                val t = tallies.getOrPut(bossName(boss)) { Tally() }
                t.total += dmg
                if (mineWindow) t.mine += dmg
            }
        }
        if (seen.size > 4096) seen.clear()
    }

    private fun parse(s: String): Double? {
        val m = SPLASH.matchEntire(s) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        return n * when (m.groupValues[2].lowercase()) { "k" -> 1e3; "m" -> 1e6; else -> 1.0 }
    }

    private fun bossName(e: Entity): String {
        if (e is EnderDragon) return WitherDragon.byEntityId(e.id)?.displayName ?: "Dragon"
        return when (Phase.getPhase()) {
            4 -> "Maxor"
            5 -> "Storm"
            6, 7 -> "Goldor"
            8 -> "Necron"
            else -> "Wither King"
        }
    }

    // /bossdamagetest: fake M7 tallies, printed after the same 20-tick delay
    @JvmStatic
    fun test() {
        tallies.clear()
        val fake = listOf("Maxor" to (210e6 to 800e6), "Storm" to (260e6 to 1e9), "Goldor" to (300e6 to 1.2e9),
            "Necron" to (350e6 to 1.4e9), "Power Dragon" to (180e6 to 600e6), "Flame Dragon" to (120e6 to 450e6),
            "Apex Dragon" to (150e6 to 500e6), "Ice Dragon" to (95e6 to 400e6), "Soul Dragon" to (140e6 to 520e6), "Wither King" to (90e6 to 300e6))
        for ((n, v) in fake) tallies[n] = Tally().apply { mine = v.first; total = v.second }
        Scheduler.scheduleTask(Runnable { FishDiag.guard("BossDamage.3", "boss damage test") { print() } }, 20)
    }

    private fun print() {
        Misc.addChatMessage(Component.literal("§3§lBoss Damage"))
        var mineSum = 0.0; var totalSum = 0.0
        val order = listOf("Maxor", "Storm", "Goldor", "Necron") + WitherDragon.entries.filter { it != WitherDragon.NONE }.map { it.displayName }
        for ((name, t) in tallies.entries.sortedBy { e -> order.indexOf(e.key).let { if (it < 0) order.size else it } }.map { it.key to it.value }) {
            mineSum += t.mine; totalSum += t.total
            Misc.addChatMessage(Component.literal(" §7$name: §f${Misc.abbr(t.mine)} §8(§b${pct(t.mine, t.total)}§8)"))
        }
        Misc.addChatMessage(Component.literal(" §7Total: §f${Misc.abbr(mineSum)} §8(§b${pct(mineSum, totalSum)}§8)"))
        tallies.clear()
    }

    private fun pct(a: Double, b: Double) = if (b <= 0.0) "0%" else String.format("%.1f%%", a * 100 / b)
}
