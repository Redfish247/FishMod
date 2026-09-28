package fishmod.features.diana

import fishmod.features.FishHudEditor
import fishmod.mixin.accessors.GuiAccessor
import fishmod.utils.ChatQueue
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

enum class RareMob(val display: String, val short: String, val code: String, private val glow: () -> Int) {
    INQ("Minos Inquisitor", "Inquisitor", "§d", { DianaSettings.dianaGlowInq }),
    KING("King Minos", "King Minos", "§6", { DianaSettings.dianaGlowKing }),
    MANTI("Manticore", "Manticore", "§2", { DianaSettings.dianaGlowManti }),
    SPHINX("Sphinx", "Sphinx", "§9", { DianaSettings.dianaGlowSphinx });

    val glowColor: Int get() = glow()
    val label: String get() = code + short

    fun spawnText(): String = when (this) {
        INQ -> DianaSettings.dianaInqSpawnText
        KING -> DianaSettings.dianaKingSpawnText
        MANTI -> DianaSettings.dianaMantiSpawnText
        SPHINX -> DianaSettings.dianaSphinxSpawnText
    }

    companion object {
        fun fromName(name: String): RareMob? = entries.firstOrNull { name.contains(it.display, ignoreCase = true) }

        // Short party-chat aliases (inq, king, ...) as well as full names
        fun fromAlias(s: String): RareMob? = when (s.replace("|", "").trim().lowercase()) {
            "inq", "inquisitor", "minos inquisitor" -> INQ
            "king", "king minos" -> KING
            "manticore", "manti" -> MANTI
            "sphinx" -> SPHINX
            else -> null
        }
    }
}

// Rare Diana mobs: name tag tracking, HP/shuriken HUDs, world scan, share/receive, cocoon, glow
object RareMobs {

    private const val MYTHOS_CHAR = ''
    private val PREFIXES = listOf("Empyrean", "Exalted", "Runic", "Venerable", "Stalwart", "Blessed")
    private val MYTHOS_NAMES = listOf(
        "Minos Hunter", "Minos Champion", "Minotaur", "Gaia Construct", "Siamese Lynx",
        "Harpy", "Bagheera", "Cretan Bull", "Stranded Nymph", "Minos Inquisitor", "King Minos", "Manticore", "Sphinx",
    )

    private val HP_FMT = Regex("([0-9.]+[MKmk]?)§f/")
    private val HP_PLAIN = Regex("([0-9.]+[MKmk]?)/[0-9.]+[MKmk]?❤")
    private val KING_HITS = Regex("(\\d+)\\s+Hits")
    private val DUG = Regex("You dug (?:out )?(?:a |an )?(.+?)!")
    private val COCOON = Regex("CAUGHT!.*You cocooned a (.+?)!")
    private val COORDS = Regex("^(?:(Party|Guild|Co-op) > )?(?:\\[[^\\]]+\\] )*(\\w+)(?: [^\\s:]+)?: x: (-?[\\d.]+),? y: (-?[\\d.]+),? z: (-?[\\d.]+)(.*)$")

    private const val HP_HUD = "Diana Mythos HP"
    private const val SHURIKEN_HUD = "Diana No Shuriken"

    // Filled by the tracker: mob display name -> (mobs since last, chance %)
    var sinceProvider: ((String) -> Pair<Int, Double>)? = null
    val deathListeners = mutableListOf<(String, Boolean) -> Unit>()
    @Volatile var lastDianaMobDeathMs = 0L

    private val defeated = HashSet<Int>()
    private val missingTicks = HashMap<Waypoint, Int>()
    private val glow = ConcurrentHashMap<Int, Int>()
    private val pendingSends = ArrayList<Pair<Long, String>>()
    private val lastNotify = HashMap<RareMob, Long>()

    @Volatile private var hpLines: List<Component> = emptyList()
    @Volatile private var noShuriken = false
    private var tick = 0

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            HP_HUD,
            { DianaSettings.dianaHpHudX }, { v -> DianaSettings.dianaHpHudX = v },
            { DianaSettings.dianaHpHudY }, { v -> DianaSettings.dianaHpHudY = v },
            220, 60,
            { DianaSettings.dianaHpHudScale }, { v -> DianaSettings.dianaHpHudScale = v },
            { DianaSettings.dianaMythosHp },
        )
        FishHudEditor.register(
            SHURIKEN_HUD,
            { DianaSettings.dianaShurikenHudX }, { v -> DianaSettings.dianaShurikenHudX = v },
            { DianaSettings.dianaShurikenHudY }, { v -> DianaSettings.dianaShurikenHudY = v },
            80, 10,
            { DianaSettings.dianaShurikenHudScale }, { v -> DianaSettings.dianaShurikenHudScale = v },
            { DianaSettings.dianaNoShuriken },
        )
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "diana_mythos_hp")) { ctx, _ ->
            if (!FishHudEditor.isOpen()) renderHud(ctx)
        }

        ClientTickEvents.END_CLIENT_TICK.register { onTick() }
        Events.ON_GAME_MESSAGE.register { text -> onChat(text.string); false }
        Events.ON_WORLD_CHANGE.register { reset(); false }
    }

    private fun reset() {
        defeated.clear(); missingTicks.clear(); glow.clear()
        hpLines = emptyList(); noShuriken = false
    }

    // ---- name tags ----

    private val CODES = Regex("§.")
    private fun strip(s: String) = s.replace(CODES, "")

    private fun parseHp(raw: String): Double? {
        val s = (HP_FMT.find(raw) ?: HP_PLAIN.find(strip(raw)))?.groupValues?.get(1) ?: return null
        val mult = when (s.last()) { 'M', 'm' -> 1_000_000.0; 'K', 'k' -> 1_000.0; else -> 1.0 }
        val num = if (mult == 1.0) s else s.dropLast(1)
        return num.toDoubleOrNull()?.times(mult)
    }

    private fun isMythos(plain: String): Boolean {
        if (plain.indexOf(MYTHOS_CHAR) >= 0 || plain.contains('✿')) return true
        if (MYTHOS_NAMES.any { plain.contains(it) } || PREFIXES.any { plain.contains("$it ") }) return true
        return Diana.testMode && HP_PLAIN.containsMatchIn(plain)
    }

    private fun onTick() {
        val mc = Minecraft.getInstance()
        val level = mc.level
        val player = mc.player
        if (level == null || player == null || !Diana.inHub()) {
            if (hpLines.isNotEmpty() || noShuriken || glow.isNotEmpty()) { hpLines = emptyList(); noShuriken = false; glow.clear() }
            return
        }
        tick++
        flushPending()

        val eye = player.position()
        val lines = ArrayList<Pair<Double, Component>>()
        val rareStands = ArrayList<Vec3>()
        val alive = HashSet<Int>()
        val newGlow = if (tick % 4 == 0) HashMap<Int, Int>() else null
        var shurikenDist = Double.MAX_VALUE

        for (e in level.entitiesForRendering()) {
            if (e === player) continue
            if (newGlow != null && DianaSettings.dianaHighlightRareMobs && e is LivingEntity && e !is ArmorStand
                && !(e is Player && e.uuid.version() == 4) && e.isAlive && !e.isInvisible) {
                val r = RareMob.fromName(e.name.string)
                if (r != null && player.hasLineOfSight(e)) newGlow[e.id] = r.glowColor or 0xFF000000.toInt()
            }

            val comp = e.customName ?: continue
            val plain = comp.string
            if (plain.isEmpty()) continue
            val kingHits = if (plain.contains("Hits")) KING_HITS.find(plain) else null
            if (kingHits == null && !isMythos(plain)) continue

            alive.add(e.id)
            val dist = e.position().distanceTo(eye)
            if (kingHits != null) {
                lines.add(dist to Component.literal("§6King Minos §7- §5${kingHits.groupValues[1]} Hits"))
                continue
            }
            val hp = parseHp(plain) ?: continue
            val rare = RareMob.fromName(plain)
            if (hp <= 0.0) {
                if (defeated.add(e.id)) onDeath(plain, e.position(), dist, rare)
                continue
            }
            lines.add(dist to comp)
            if (rare == null) continue
            rareStands.add(e.position())
            if (!plain.contains('✯') && dist < shurikenDist) shurikenDist = dist
            if (DianaSettings.dianaScanRareMobs && e is ArmorStand) scanStand(e, rare)
        }

        defeated.retainAll(alive)
        hpLines = lines.sortedBy { it.first }.take(6).map { it.second }
        noShuriken = shurikenDist != Double.MAX_VALUE
        if (newGlow != null) { glow.clear(); glow.putAll(newGlow) }
        pruneStale(eye, rareStands)
    }

    private fun onDeath(name: String, pos: Vec3, dist: Double, rare: RareMob?) {
        lastDianaMobDeathMs = System.currentTimeMillis()
        if (rare != null) DianaWaypoints.removeRareMobsNear(pos, 32.0)
        for (l in deathListeners) runCatching { l(name, dist <= 30) }
    }

    // ---- world scan ----

    private fun scanStand(stand: ArmorStand, rare: RareMob) {
        val pos = stand.position()
        if (DianaWaypoints.rareMobNear(pos, 60.0)) return
        val player = Minecraft.getInstance().player ?: return
        if (!player.hasLineOfSight(stand)) return
        DianaWaypoints.addRareMob(ground(pos), rare.label, 45_000)
        notify(rare, "")
    }

    private fun ground(v: Vec3): BlockPos {
        val level = Minecraft.getInstance().level ?: return BlockPos.containing(v)
        var p = BlockPos.containing(v)
        repeat(12) {
            if (!level.getBlockState(p).isAir) return p
            p = p.below()
        }
        return BlockPos.containing(v).below(3)
    }

    private fun pruneStale(eye: Vec3, stands: List<Vec3>) {
        val rares = DianaWaypoints.list.filter { it.type == WpType.RARE }
        missingTicks.keys.retainAll(rares.toSet())
        for (w in rares) {
            if (w.distTo(eye) > 30 || stands.any { w.distTo(it) <= 30 }) { missingTicks.remove(w); continue }
            val n = (missingTicks[w] ?: 0) + 1
            if (n >= 20) { DianaWaypoints.remove(w); missingTicks.remove(w) } else missingTicks[w] = n
        }
    }

    // ---- chat ----

    private fun onChat(msg: String) {
        if (!Diana.inHub()) return
        val s = strip(msg)

        COCOON.find(s)?.let { m ->
            val rare = RareMob.fromName(m.groupValues[1]) ?: return
            if (DianaSettings.dianaCocoonTitle) {
                title("§6§l<§b§l§kO§6§l> §b§lCOCOON! §6§l<§b§l§kO§6§l>", "§b${rare.display}")
                playSound()
            }
            if (DianaSettings.dianaCocoonParty) ChatQueue.enqueue("pc Cocooned a ${rare.display}!")
            return
        }

        COORDS.find(s)?.let { m ->
            if (m.groupValues[1] == "Guild") return
            onCoords(m.groupValues[2], m.groupValues[3], m.groupValues[4], m.groupValues[5], m.groupValues[6])
            return
        }

        if (s.contains(":")) return
        DUG.find(s)?.let { m ->
            val rare = RareMob.fromName(m.groupValues[1]) ?: return
            onOwnSpawn(rare)
        }
    }

    private fun onOwnSpawn(rare: RareMob) {
        val p = Minecraft.getInstance().player ?: return
        notify(rare, "")
        if (DianaSettings.dianaShareRareMob) {
            ChatQueue.enqueue("pc x: ${p.x.roundToInt()}, y: ${p.y.roundToInt() - 1}, z: ${p.z.roundToInt()} | ${rare.display}")
        }
        val text = rare.spawnText()
        if (text.isNotBlank()) {
            val (since, chance) = sinceProvider?.invoke(rare.display)?.let { it.first.toString() to "%.2f".format(it.second) } ?: ("?" to "?")
            pendingSends.add(System.currentTimeMillis() + 5000 to text.replace("{since}", since).replace("{chance}", chance))
        }
    }

    private fun flushPending() {
        if (pendingSends.isEmpty()) return
        val now = System.currentTimeMillis()
        val due = pendingSends.filter { it.first <= now }
        if (due.isEmpty()) return
        pendingSends.removeAll(due.toSet())
        due.forEach { ChatQueue.enqueue("pc ${it.second}") }
    }

    private fun onCoords(sender: String, xs: String, ys: String, zs: String, trailing: String) {
        val x = xs.toDoubleOrNull() ?: return
        val y = ys.toDoubleOrNull() ?: return
        val z = zs.toDoubleOrNull() ?: return
        val pos = BlockPos.containing(x, y, z)
        val self = sender == Minecraft.getInstance().player?.name?.string
        val rare = RareMob.fromAlias(trailing)
        if (rare != null) {
            if (!DianaSettings.dianaReceiveRareMob) return
            if (DianaWaypoints.rareMobNear(Vec3.atCenterOf(pos), 10.0)) return
            DianaWaypoints.addRareMob(pos, "${rare.label} §7($sender)", 45_000)
            if (!self) notify(rare, sender)
        } else if (!self) {
            DianaWaypoints.addWorld(pos, "§9$sender", 30_000)
        }
    }

    // ---- notify ----

    private fun notify(rare: RareMob, from: String) {
        val now = System.currentTimeMillis()
        if (now - (lastNotify[rare] ?: 0L) < 3000) return
        lastNotify[rare] = now
        title("§6§l<§b§l§kO§6§l> ${rare.code}§l${rare.short.uppercase()}! §6§l<§b§l§kO§6§l>", if (from.isEmpty()) "" else "§7$from")
        playSound()
    }

    private fun playSound() {
        val vol = DianaSettings.dianaRareMobVolume / 100f
        if (vol > 0f) SoundManager.play(SoundManager.preset(DianaSettings.dianaRareMobSound), vol)
    }

    private fun title(t: String, sub: String) {
        val mc = Minecraft.getInstance()
        mc.execute {
            val acc = mc.gui as GuiAccessor
            acc.`fishmod$setTitleFadeInTime`(DianaSettings.dianaTitleFadeIn.coerceAtLeast(0))
            acc.`fishmod$setTitleStayTime`(DianaSettings.dianaTitleStay.coerceAtLeast(1))
            acc.`fishmod$setTitleFadeOutTime`(DianaSettings.dianaTitleFadeOut.coerceAtLeast(0))
            mc.gui.setTitle(Component.literal(t))
            mc.gui.setSubtitle(Component.literal(sub))
        }
    }

    // ---- glow hook (EntityRendererMixin) ----

    @JvmStatic
    fun glowColor(e: Entity): Int {
        if (glow.isEmpty() || !DianaSettings.dianaHighlightRareMobs) return EntityRenderState.NO_OUTLINE
        return glow[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    // ---- HUD ----

    private fun renderHud(ctx: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui || !Diana.inHub()) return
        val font = mc.font
        if (DianaSettings.dianaMythosHp) {
            val lines = hpLines
            if (lines.isNotEmpty()) {
                val sc = DianaSettings.dianaHpHudScale.toFloat()
                ctx.pose().pushMatrix()
                ctx.pose().translate(DianaSettings.dianaHpHudX.toFloat(), DianaSettings.dianaHpHudY.toFloat())
                ctx.pose().scale(sc, sc)
                lines.forEachIndexed { i, c -> ctx.text(font, c, 0, i * 10, -1, true) }
                ctx.pose().popMatrix()
            }
        }
        if (DianaSettings.dianaNoShuriken && noShuriken) {
            val sc = DianaSettings.dianaShurikenHudScale.toFloat()
            ctx.pose().pushMatrix()
            ctx.pose().translate(DianaSettings.dianaShurikenHudX.toFloat(), DianaSettings.dianaShurikenHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(font, "§c§lNO SHURIKEN!", 0, 0, -1, true)
            ctx.pose().popMatrix()
        }
    }
}
