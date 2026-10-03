package fishmod.features.mining

import fishmod.utils.ChatQueue
import fishmod.utils.Location
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import kotlin.math.roundToInt
import fishmod.features.mining.MiningSettings as S

// Mineshaft corpses: party-announce each corpse once when seen; shaft type + corpse summary title/party message
object Corpses {

    enum class Type(val display: String, val letter: String, val helmet: String) {
        LAPIS("Lapis", "L", "LAPIS_ARMOR_HELMET"), UMBER("Umber", "U", "ARMOR_OF_YOG_HELMET"),
        TUNGSTEN("Tungsten", "T", "MINERAL_HELMET"), VANGUARD("Vanguard", "V", "VANGUARD_HELMET");
        fun enabled() = when (this) {
            LAPIS -> S.miningCorpseLapis; UMBER -> S.miningCorpseUmber
            TUNGSTEN -> S.miningCorpseTungsten; VANGUARD -> S.miningCorpseVanguard
        }
    }

    // SkyHanni MineshaftType codes -> names
    private val SHAFTS = linkedMapOf(
        "TOPA1" to "Topaz 1", "TOPA2" to "Topaz 2", "SAPP1" to "Sapphire 1", "SAPP2" to "Sapphire 2",
        "AMET1" to "Amethyst 1", "AMET2" to "Amethyst 2", "AMBE1" to "Amber 1", "AMBE2" to "Amber 2",
        "JADE1" to "Jade 1", "JADE2" to "Jade 2", "TITA1" to "Titanium", "UMBE1" to "Umber", "TUNG1" to "Tungsten",
        "FAIR1" to "Vanguard", "RUBY1" to "Ruby 1", "RUBY2" to "Ruby 2", "RUBYC" to "Ruby Crystal",
        "ONYX1" to "Onyx 1", "ONYX2" to "Onyx 2", "ONYXC" to "Onyx Crystal", "AQUA1" to "Aquamarine 1",
        "AQUA2" to "Aquamarine 2", "AQUAC" to "Aquamarine Crystal", "CITR1" to "Citrine 1", "CITR2" to "Citrine 2",
        "CITRC" to "Citrine Crystal", "PERI1" to "Peridot 1", "PERI2" to "Peridot 2", "PERIC" to "Peridot Crystal",
        "JASP1" to "Jasper", "JASPC" to "Jasper Crystal", "OPAL1" to "Opal", "OPALC" to "Opal Crystal", "LITTL" to "Littlefoot's Den",
    )
    private val TAB_CORPSE = Regex("""^(\w+): (?:NOT )?LOOTED""")

    private val seenTicks = HashMap<java.util.UUID, Int>()
    private val announced = HashSet<java.util.UUID>()
    private var tick = 0
    private var enteredMs = 0L
    private var shaftType: String? = null
    private var typeAtMs = 0L
    private var shaftDone = false

    fun init() {
        Events.ON_WORLD_CHANGE.register { seenTicks.clear(); announced.clear(); shaftType = null; shaftDone = false; false }
        Events.ON_LOCATION_CHANGE.register { loc ->
            if (loc == Location.MINESHAFT) { enteredMs = System.currentTimeMillis(); shaftType = null; shaftDone = false }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null || !Mining.inShaft() || tick++ % 5 != 0) return@register
            if (S.miningCorpseAnnounce) scanCorpses(mc)
            if (!shaftDone && (S.miningShaftTitle || S.miningShaftParty)) checkShaft()
        }
    }

    private fun typeOf(stand: ArmorStand): Type? {
        val id = ItemUtil.getId(stand.getItemBySlot(EquipmentSlot.HEAD)) ?: return null
        return Type.entries.firstOrNull { it.helmet == id }
    }

    private fun scanCorpses(mc: Minecraft) {
        val p = mc.player ?: return
        val level = mc.level ?: return
        val eye = p.eyePosition
        for (e in level.entitiesForRendering()) {
            if (e !is ArmorStand || e.uuid in announced) continue
            val type = typeOf(e) ?: continue
            if (!type.enabled()) continue
            val target = e.position().add(0.0, 1.5, 0.0)
            if (target.distanceToSqr(eye) > 64.0 * 64.0) continue
            val hit = level.clip(ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
            if (hit.type == HitResult.Type.BLOCK && hit.location.distanceToSqr(eye) < target.distanceToSqr(eye) - 1.0) { seenTicks.remove(e.uuid); continue }
            // ~10 ticks of continuous sight, like SkyHanni
            val n = (seenTicks[e.uuid] ?: 0) + 1
            seenTicks[e.uuid] = n
            if (n < 2) continue
            announced += e.uuid
            val msg = S.miningCorpseFormat.replace("{x}", e.x.roundToInt().toString()).replace("{y}", (e.y.roundToInt() + 1).toString())
                .replace("{z}", e.z.roundToInt().toString()).replace("{type}", type.display)
            ChatQueue.enqueue("pc $msg")
        }
    }

    private fun corpseSummary(): String? {
        val counts = LinkedHashMap<Type, Int>()
        for (l in Mining.tabWidget("Frozen Corpses:")) {
            val name = TAB_CORPSE.find(l)?.groupValues?.get(1) ?: continue
            val t = Type.entries.firstOrNull { it.display.equals(name, true) } ?: continue
            counts[t] = (counts[t] ?: 0) + 1
        }
        if (counts.isEmpty()) return null
        return Type.entries.filter { counts.containsKey(it) }.joinToString(" ") { "${counts[it]}${it.letter}" }
    }

    // Type from the sidebar server line; corpses from the tab widget (wait up to 5s for it)
    private fun checkShaft() {
        if (shaftType == null) {
            val joined = Mining.sidebar.joinToString(" ").uppercase().replace("_", "")
            shaftType = SHAFTS.entries.firstOrNull { joined.contains(it.key) }?.value ?: return
            typeAtMs = System.currentTimeMillis()
        }
        val summary = corpseSummary()
        if (summary == null && System.currentTimeMillis() - typeAtMs < 5000) return
        shaftDone = true
        val text = S.miningShaftFormat.replace("{type}", shaftType!!).replace("{corpses}", summary ?: "No corpses")
        if (S.miningShaftTitle) Mining.title("§b§l$shaftType", "§f${summary ?: "No corpses"}", S.miningShaftTitleMs, true)
        if (S.miningShaftParty) ChatQueue.enqueue("pc $text")
    }
}
