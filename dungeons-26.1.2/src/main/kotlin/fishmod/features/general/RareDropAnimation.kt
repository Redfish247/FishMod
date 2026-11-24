package fishmod.features.general
import fishmod.compat.showItemActivation

import com.google.gson.JsonParser
import fishmod.pv.gui.inventories.NeuItemResolver
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.neu.NeuRepoCache
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

// Skyblocker-style rare drop effect: the totem-of-undying pop-up shows the dropped item when a
// "RARE DROP!" / "PET DROP!" line arrives in chat and the drop is ticked in the menu.
// The set list lives in resources/data/rareDropAnimation.json (category -> drop names).
object RareDropAnimation {
    private val DROP = Regex("^(?:(?:VERY |CRAZY )?RARE|INSANE|PRAY TO RNGESUS|RNGESUS INCARNATE|PRAY RNGESUS) DROP! +(.+)$")
    private val PET = Regex("^PET DROP! +(?:(?:LEGENDARY|EPIC|RARE|UNCOMMON|COMMON) )?(.+)$")
    private val MF = Regex("""\s*\(\+.*$""")
    private val BOOK = Regex("""^Enchanted Book \((.+)\)$""")
    private val COUNT = Regex("""^\(?[\d,]+x """)
    private val WOW = Regex("""^WOW! (.+?) found (?:a|an) (.+?)(?: #\d+)?!.*$""")
    private val TRAIL_NUM = Regex("""\s(I{1,3}|IV|V|VI{0,3}|IX|X)$""")
    private val ROMAN = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10)

    /** Lower-case name with a trailing Roman numeral turned into digits, so "Smite VII" == "Smite 7". */
    @JvmStatic fun norm(n: String): String {
        val m = TRAIL_NUM.find(n.trim())
        return (if (m != null) n.trim().substring(0, m.range.first) + " " + ROMAN[m.groupValues[1]] else n.trim()).lowercase()
    }

    /** Category name to drop names, in file order. */
    @JvmField val CATEGORIES: List<Pair<String, List<String>>> = load()

    private val BY_NORM: Map<String, List<String>> = CATEGORIES.flatMap { it.second }.distinct().groupBy { norm(it) }

    private fun load(): List<Pair<String, List<String>>> = FishDiag.guard("RareDropAnimation.2", "rareDropAnimation.json failed to load") {
        val text = RareDropAnimation::class.java.getResourceAsStream("/data/rareDropAnimation.json")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        JsonParser.parseString(text).asJsonObject.getAsJsonArray("categories").map { c ->
            val o = c.asJsonObject
            o.get("name").asString to o.getAsJsonArray("drops").map { it.asString }
        }
    } ?: emptyList()

    @JvmStatic fun init() {
        FishEvents.chat.register { c ->
            if (FishSettings.rareDropAnim) onChat(c.chatPlain)
            false
        }
    }

    private fun disabled(): Set<String> = FishSettings.rareDropAnimOff.split(',').toSet()

    @JvmStatic fun isOn(name: String) = name !in disabled()

    @JvmStatic fun setOn(name: String, on: Boolean) {
        val st = FishSettings.rareDropAnimOff.split(',').filter { it.isNotEmpty() }.toMutableSet()
        if (on) st.remove(name) else st.add(name)
        FishSettings.rareDropAnimOff = st.joinToString(",")
    }

    /** Drop name from a chat line, or null when it is not a drop line. */
    @JvmStatic fun dropName(plain: String, own: String? = null): String? {
        val c = plain.firstOrNull() ?: return null
        if (plain.length < 12 || c == '[') return null
        if (c == 'W') {
            val w = WOW.matchEntire(plain) ?: return null
            if (own == null || !w.groupValues[1].trim().substringAfterLast(' ').equals(own, true)) return null
            return w.groupValues[2].trim()
        }
        val raw = (DROP.matchEntire(plain) ?: PET.matchEntire(plain))?.groupValues?.get(1) ?: return null
        val noMf = raw.replace(MF, "").trim()
        val name = BOOK.matchEntire(noMf)?.groupValues?.get(1)
            ?: if (COUNT.containsMatchIn(noMf)) noMf.replace(COUNT, "").removeSuffix(")").trim() else noMf
        return name.trim().let { if (it.length > 2 && it.startsWith("(") && it.endsWith(")") && it.indexOf('(', 1) < 0) it.substring(1, it.length - 1).trim() else it }
            .takeIf { it.isNotEmpty() }
    }

    private fun onChat(plain: String) {
        val name = dropName(plain, Minecraft.getInstance().user.name) ?: return
        val listed = BY_NORM[norm(name)]
        if (listed == null) { fishmod.utils.debug.FishTrace.log("fishing") { "no animation: '$name' not in list (line: $plain)" }; return }
        if (listed.none { isOn(it) }) return
        play(name, plain.startsWith("PET DROP!"))
    }

    private fun play(name: String, pet: Boolean) {
        // item lookup may fetch from the NEU repo, so resolve off the render thread first
        fishmod.utils.IoExecutor.execute {
            val stack = FishDiag.guard("RareDropAnimation.3", "rare drop icon lookup failed for $name") { stackFor(name, pet) } ?: fallback(name)
            val mc = Minecraft.getInstance()
            mc.execute {
                FishDiag.guard("RareDropAnimation.1", "rare drop animation failed for $name") {
                    mc.showItemActivation(stack)
                }
            }
        }
    }

    /** /fmraredrop: runs a fake drop through the same list and toggle checks, ignoring only the master switch. Returns a chat report. */
    @JvmStatic fun test(arg: String?): String {
        val name = arg?.trim()?.takeIf { it.isNotEmpty() } ?: CATEGORIES.firstOrNull()?.second?.firstOrNull() ?: return "§c[RareDrop] drop list is empty"
        val listed = BY_NORM[norm(name)] ?: return "§c[RareDrop] '$name' is not in the list, so it would not animate"
        val master = if (FishSettings.rareDropAnim) "" else " §e(master switch is off, real drops will not animate)"
        if (listed.none { isOn(it) }) return "§c[RareDrop] '$name' is unticked in the menu, so it would not animate$master"
        play(name, CATEGORIES.any { it.first.contains("Pets") && it.second.any { d -> norm(d) == norm(name) } } || norm(name) in PET_NAMES)
        return "§a[RareDrop] playing '$name'$master"
    }

    private val PET_NAMES = setOf("ender dragon", "golem", "enderman", "slug")

    private fun snake(n: String) = n.uppercase().replace("'", "").replace(Regex("[^A-Z0-9]+"), "_").trim('_')

    private fun fallback(name: String) = ItemStack(if (TRAIL_NUM.containsMatchIn(name) || name.matches(Regex(""".*\s\d+$"""))) Items.ENCHANTED_BOOK else Items.NETHER_STAR)

    /** SkyBlock ids to try for a drop name, best first: NEU display name, then derived ids (dye, book, pet, plain). */
    private fun candidates(name: String, pet: Boolean): List<String> {
        val out = LinkedHashSet<String>()
        if (pet) out.add("PET_" + snake(name))
        NeuRepoCache.idsForDisplayName(name).filterNot { it.contains(';') }.forEach { out.add(it) }
        val sn = snake(name)
        if (name.endsWith(" Dye", true)) out.add("DYE_" + snake(name.dropLast(4)))
        val n = norm(name)
        val sp = n.lastIndexOf(' ')
        if (sp > 0 && n.substring(sp + 1).all { it.isDigit() }) {
            val base = snake(n.substring(0, sp)); val lvl = n.substring(sp + 1)
            out.add("ENCHANTMENT_${base}_$lvl"); out.add("ENCHANTMENT_ULTIMATE_${base}_$lvl")
        }
        out.add(sn); out.add("PET_$sn")
        return out.toList()
    }

    private fun stackFor(name: String, pet: Boolean): ItemStack {
        NeuRepoCache.ensureLoadedBlocking()
        for (id in candidates(name, pet)) {
            // fetch synchronously (we are off the render thread) so iconStack finds it already cached; PET_x expands to x;0..5
            if (NeuItemResolver.candidates(id).none { NeuRepoCache.getOrFetch(it) != null }) continue
            val st = SkyBlockItemFactory.iconStack(id)
            if (!st.isEmpty && !st.`is`(Items.PAPER)) return st
        }
        return fallback(name)
    }
}
