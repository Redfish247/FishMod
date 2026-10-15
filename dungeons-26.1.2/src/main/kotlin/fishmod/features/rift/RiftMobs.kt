package fishmod.features.rift

import fishmod.utils.Misc
import fishmod.utils.data.EntityUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import fishmod.features.rift.RiftSettings as S

// Crux hints, Shy warning, Fleespook tracker, Bacte alert + line
object RiftMobs {

    private val CRUX_HINTS = linkedMapOf(
        "Shy" to "§eTurn around!",
        "Shadow" to "§8Run away!",
        "Volt" to "§bCircle out!",
        "Scribe" to "§7Look at the coal!",
        "Frozille" to "§3Break the ice!",
        "Splatter" to "§cPick up the hearts!",
    )
    private fun hintOn(k: String) = when (k) {
        "Shy" -> S.riftCruxShy; "Shadow" -> S.riftCruxShadow; "Volt" -> S.riftCruxVolt
        "Scribe" -> S.riftCruxScribe; "Frozille" -> S.riftCruxFrozille; else -> S.riftCruxSplatter
    }
    // Last scan results for /fm rift debug
    @Volatile var lastCruxNames: List<String> = emptyList(); private set
    private val SHY_LINES = setOf("I'm ugly! :(", "Eek!", "Don't look at me!", "Look away!")
    private val BACTE_NAME = Regex("(?:^|[^A-Za-z])(Bacte|Bact|Bac|Ba)(?![A-Za-z])")
    private val BACTE_CHAT = Regex("^(Bacte|Bact|Bac|Ba) (is rooting|is growing|has summoned|is vulnerable)|^BACTE DOWN")
    // Health tag over the mob, e.g. "B 1,000/1,000❤" (name shrinks to B/Ba/Bac/Bact as it grows)
    private val BACTE_TAG = Regex("^(Bacte|Bact|Bac|Ba|B) [0-9,.]+[kKmM]?/[0-9,.]+[kKmM]?")
    private val BACTE_END = Regex("BACTE DOWN")
    @Volatile private var bacteUntil = 0L
    @Volatile private var bacteStart = 0L
    @Volatile var bacteDebug: List<String> = emptyList(); private set

    private class Mark(val e: Entity, val text: String)
    @Volatile private var marks: List<Mark> = emptyList()
    @Volatile private var fleespook: Entity? = null
    @Volatile private var bacte: List<Entity> = emptyList()
    private var bacteId = -1
    private var tick = 0

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 5 == 0) scan() }
        Events.ON_GAME_MESSAGE.register { t -> onBacteChat(Rift.strip(t.string).trim()); false }
        Events.ON_WORLD_CHANGE.register { bacteUntil = 0L; marks = emptyList(); fleespook = null; bacte = emptyList(); bacteId = -1; false }
        RenderingEvents.GIZMO.register { _ -> render() }
    }

    // Chat drives the fight window; the nametag only finds the mob
    private fun onBacteChat(m: String) {
        if (!S.riftBacte || !Rift.inRift() || !BACTE_CHAT.containsMatchIn(m)) return
        val now = System.currentTimeMillis()
        if (BACTE_END.containsMatchIn(m)) { bacteUntil = 0L; bacte = emptyList(); bacteId = -1; return }
        if (now > bacteUntil) {
            bacteStart = now
            if (S.riftBacteTitle) Misc.forceTitle(Component.literal("§a§lBacte!"), Component.literal("§7Bacte spawned"), 2000)
        }
        bacteUntil = now + 120_000L
    }

    // Bacte (found by its health tag), tentacles; nearest slime only as a fallback while the chat window is open
    private fun findBacte(me: Entity, windowOpen: Boolean): List<Entity> {
        val lv = Minecraft.getInstance().level ?: return emptyList()
        val cand = ArrayList<String>()
        val out = ArrayList<Entity>()
        val slimes = ArrayList<Entity>()
        for (e in lv.getEntities(me, me.boundingBox.inflate(48.0)) { true }) {
            if (!e.isAlive) continue
            val n = (e.customName ?: e.name).string.let { Rift.strip(it) }
            val stand = e is net.minecraft.world.entity.decoration.ArmorStand
            if (stand && e.customName == null) continue
            val hit = BACTE_TAG.containsMatchIn(n) || BACTE_NAME.containsMatchIn(n) || "Bacte" in n || "entacle" in n
            val slime = e is net.minecraft.world.entity.monster.Slime
            if (hit || slime) cand.add("${e.type.descriptionId.substringAfterLast('.')}:'${n.take(50)}'")
            if (slime) slimes.add(e)
            else if (hit) out.add(if (stand) Rift.mobUnder(e) ?: e else e)
        }
        bacteDebug = cand.take(12)
        if (out.isEmpty() && windowOpen) slimes.minByOrNull { it.distanceToSqr(me) }?.let { out.add(it) }
        return out.distinct().sortedBy { it.distanceToSqr(me) }.take(12)
    }

    private fun scan() {
        if (!Rift.inRift() || !(S.riftCrux || S.riftFleespook || S.riftBacte)) {
            bacteDebug = emptyList();
            marks = emptyList(); fleespook = null; bacte = emptyList(); return
        }
        val me = Minecraft.getInstance().player ?: return
        val range = S.riftCruxRange.toDouble()
        val names = ArrayList<String>()
        val out = ArrayList<Mark>()
        var flee: Entity? = null
        for ((stand, name) in Rift.namedStands(48.0)) {
            if (S.riftCrux && stand.isAlive && "Crux" in name && stand.distanceToSqr(me) <= range * range) {
                CRUX_HINTS.entries.firstOrNull { name.contains(it.key) && hintOn(it.key) }?.let { h ->
                    // One label per hint kind: keep only the nearest stand (Volt's ring is many stands)
                    val prev = out.indexOfFirst { it.text == h.value }
                    if (prev < 0) { out.add(Mark(stand, h.value)); names.add(name) }
                    else if (stand.distanceToSqr(me) < out[prev].e.distanceToSqr(me)) out[prev] = Mark(stand, h.value)
                }
            }
            if (S.riftFleespook && "Fleespook" in name) flee = stand
        }
        if (S.riftCrux && S.riftShyTitle && S.riftCruxShy) {
            val lv = Minecraft.getInstance().level
            if (lv != null && lv.getEntities(me, me.boundingBox.inflate(8.0)) { e -> e.customName?.string?.let { Rift.strip(it) in SHY_LINES } == true }.isNotEmpty())
                Misc.forceTitle(Component.literal("§eLook away!"), Component.empty(), 300)
        }
        marks = out; lastCruxNames = names; fleespook = flee
        bacte = if (S.riftBacte) findBacte(me, System.currentTimeMillis() < bacteUntil) else emptyList()
    }

    private fun render() {
        if (!Rift.inRift()) return
        for (m in marks) {
            if (!m.e.isAlive) continue
            val p = EntityUtil.getLerpedPos(m.e)
            RenderUtils.gizmoText(Component.literal(m.text), Vec3(p.x, p.y + m.e.bbHeight + 0.6, p.z), 1.4f, -1, S.riftCruxWalls)
        }
        fleespook?.takeIf { it.isAlive }?.let { line(it, S.riftFleespookColor, "§5Fleespook") }
        if (S.riftBacteLine) for (e in bacte) if (e.isAlive) line(e, S.riftBacteColor, null)
    }

    private fun line(e: Entity, argb: Int, label: String?) {
        val p = EntityUtil.getLerpedPos(e)
        RenderUtils.gizmoLine(RenderUtils.cameraLineStart(1.0), Vec3(p.x, p.y, p.z), argb, 2f, true)
        if (label != null) RenderUtils.gizmoText(Component.literal(label), Vec3(p.x, p.y + 0.5, p.z), 1.2f, -1, true)
    }
}
