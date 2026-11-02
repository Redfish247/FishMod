package fishmod.features.dungeon

import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Giant
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

// /fmdev bossDamage: logs damage numbers, boss health and own actions for one run to study Hypixel's data
object BossDamageCapture {

    private val SPLASH = Regex("""^\D{0,3}[\d,.]+[kKmM]?\D{0,3}$""")
    private val seen = HashSet<Int>()
    private val bossHp = HashMap<Int, Float>()
    private val pending = ArrayList<String>()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "FishMod-BossDamageCapture").apply { isDaemon = true } }
    private val file: File by lazy {
        File(Minecraft.getInstance().gameDirectory, "logs/fishmod-bossdamage.log").also { it.parentFile.mkdirs() }
    }
    private var lastActionMs = 0L
    private var ticks = 0

    @JvmField var enabled = false

    fun path(): String = file.path

    fun toggle(): Boolean {
        enabled = !enabled
        if (enabled) {
            seen.clear(); bossHp.clear()
            writer.execute { FishDiag.guard("BossDamageCapture.1", "boss damage log reset failed") { file.writeText("") } }
        } else flush()
        return enabled
    }

    fun init() {
        FishEvents.worldChange.register {
            if (enabled) { log("WORLD_CHANGE"); flush() }
            seen.clear(); bossHp.clear()
            false
        }
        FishEvents.runEnd.register {
            if (enabled) { log("RUN_END"); flush() }
            false
        }
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            if (enabled && level.isClientSide) {
                lastActionMs = System.currentTimeMillis()
                log("ATTACK target=${describe(entity)} item=${player.mainHandItem.hoverName.string}")
            }
            InteractionResult.PASS
        }
        UseItemCallback.EVENT.register(UseItemCallback { player, level, _ ->
            if (enabled && level.isClientSide) {
                lastActionMs = System.currentTimeMillis()
                log("USE item=${player.mainHandItem.hoverName.string}")
            }
            InteractionResult.PASS
        })
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (!enabled || !SkyblockLocation.inDungeon()) return@register
            FishDiag.guard("BossDamageCapture.2", "boss damage capture failed") { scan(mc) }
            if (++ticks % 20 == 0) flush()
        }
    }

    private fun isBoss(e: Entity) = e is WitherBoss || e is EnderDragon || e is Giant

    private fun describe(e: Entity) = "${e.name.string}#${e.id}"

    private fun scan(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        val entities = level.entitiesForRendering().toList()
        val bosses = entities.filter { it is LivingEntity && isBoss(it) }
        for (b in bosses) {
            b as LivingEntity
            if (bossHp.put(b.id, b.health) != b.health)
                log("BOSS ${describe(b)} hp=${fmt(b.health)}/${fmt(b.maxHealth)} pos=${pos(b)} me=${fmt(player.distanceTo(b))}")
        }
        for (e in entities) {
            if (e !is ArmorStand || e.id in seen) continue
            val name = e.customName ?: continue
            val plain = TextStrip.plain(name.string).trim()
            if (!SPLASH.matches(plain)) continue
            seen.add(e.id)
            val colors = name.toFlatList().joinToString("") { "[${it.style.color}]${it.string}" }
            val boss = bosses.minByOrNull { it.distanceTo(e) }
            val other = level.players().filter { it !== player }.minByOrNull { it.distanceTo(e) }
            log("SPLASH id=${e.id} text=\"$plain\" raw=$colors age=${e.tickCount} pos=${pos(e)} me=${fmt(player.distanceTo(e))}" +
                " boss=${boss?.let { "${describe(it)}@${fmt(it.distanceTo(e))}" }}" +
                " nearestPlayer=${other?.let { "${it.name.string}@${fmt(it.distanceTo(e))}" }}" +
                " sinceAction=${System.currentTimeMillis() - lastActionMs}ms")
        }
        if (seen.size > 4096) seen.clear()
    }

    private fun pos(e: Entity) = "${fmt(e.x.toFloat())},${fmt(e.y.toFloat())},${fmt(e.z.toFloat())}"

    private fun fmt(v: Float) = "%.1f".format(Locale.ROOT, v)

    private fun log(line: String) {
        pending.add("${System.currentTimeMillis()} $line")
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val text = pending.joinToString("\n", postfix = "\n")
        pending.clear()
        writer.execute { FishDiag.guard("BossDamageCapture.3", "boss damage log write failed") { file.appendText(text) } }
    }
}
