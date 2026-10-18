package fishmod.features
import fishmod.compat.Fmt
import fishmod.compat.curScreen
import fishmod.compat.hudHidden

import com.google.gson.Gson
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Constants
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.io.File

// Experimentation Table: drops + Enchanting XP from the reward lines Hypixel prints after a claim (SkyHanni patterns).
object ExperimentProfitTracker {

    const val HUD = "Experiment Profit"

    private class Data(
        var experiments: Int = 0,
        var xp: Long = 0,
        val byType: MutableMap<String, Int> = LinkedHashMap(),
        val drops: MutableMap<String, Long> = LinkedHashMap(),
        var colors: MutableMap<String, String>? = LinkedHashMap(),
    )

    private val TABLE_SCREEN = Regex("(?:Superpairs|Chronomatron|Ultrasequencer) ?(?:\\(.+\\)|➜ Stakes|Rewards)|Experiment(?:ation Tabl| [Oo]v)er?")
    private val CLAIM = Regex("^You claimed the (\\S+) rewards!$")
    private val DROP = Regex("^\\+(?:\\[Lvl 1] )?(.+)$")
    private val XP = Regex("^([\\d,.]+)([kMB]?) Enchanting Exp$")
    private val COUNT = Regex("^(.+?) x([\\d,]+)$")
    private val NOT_ITEM = Regex("""(?i)\bclicks?\b""")
    private val ROMAN = Regex("^(.+) (I|II|III|IV|V|VI|VII|VIII|IX|X)$")
    private const val WINDOW_MS = 15_000L
    private val FILE = File("config/fishmod/experiment_profit.json")

    private var data = Data()
    private var lastTableMs = 0L
    private var dirty = false

    @JvmStatic
    fun init() {
        load()
        FishHudEditor.register(
            HUD,
            { FishSettings.expProfitHudX }, { v -> FishSettings.expProfitHudX = v },
            { FishSettings.expProfitHudY }, { v -> FishSettings.expProfitHudY = v },
            160, 14 * 8,
            { FishSettings.expProfitHudScale }, { v -> FishSettings.expProfitHudScale = v },
            { FishSettings.expProfitEnabled },
        )
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val s = mc.curScreen ?: return@register
            if (FishSettings.expProfitEnabled && TABLE_SCREEN.containsMatchIn(fishmod.utils.ScreenTitle.plain(s))) {
                lastTableMs = System.currentTimeMillis()
            }
        }
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.expProfitEnabled) FishDiag.guard("ExperimentProfitTracker.1", "experiment chat parse failed") {
                onChat(text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim(), text)
            }
            false
        }
    }

    private fun onChat(s: String, raw: Component) {
        if (System.currentTimeMillis() - lastTableMs > WINDOW_MS) return
        CLAIM.find(s)?.let { m ->
            data.experiments++
            data.byType.merge(m.groupValues[1], 1, Int::plus)
            changed(); return
        }
        val reward = DROP.find(s)?.groupValues?.get(1)?.trim() ?: return
        XP.find(reward)?.let { m ->
            data.xp += parseShort(m.groupValues[1], m.groupValues[2])
            changed(); return
        }
        if (NOT_ITEM.containsMatchIn(reward)) return
        val cm = COUNT.find(reward)
        val name = cm?.groupValues?.get(1)?.trim() ?: reward
        val n = cm?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull() ?: 1L
        data.drops.merge(name, n, Long::plus)
        rewardColor(raw)?.let { (data.colors ?: LinkedHashMap<String, String>().also { m -> data.colors = m })[name] = it }
        changed()
    }

    // Hypixel colours the reward name by rarity: take the colour of the first text after the "+"
    private fun rewardColor(raw: Component): String? {
        var seenPlus = false
        var out: String? = null
        raw.visit({ style, text ->
            if (!seenPlus) { if (text.contains('+')) { seenPlus = true; if (text.substringAfter('+').isNotBlank()) out = code(style) } }
            else if (out == null && text.isNotBlank()) out = code(style)
            java.util.Optional.empty<Unit>()
        }, net.minecraft.network.chat.Style.EMPTY)
        return out
    }

    private fun code(style: net.minecraft.network.chat.Style): String? {
        val c = style.color ?: return null
        val f = Fmt.codeOfRgb(c.value) ?: return null
        return "§" + f
    }

    private fun colorOf(name: String): String {
        data.colors?.get(name)?.let { return it }
        val tier = ItemsDb.get(ItemsDb.idFor(name))?.get("tier")?.asString
        return when (tier) {
            "UNCOMMON" -> "§a"; "RARE" -> "§9"; "EPIC" -> "§5"; "LEGENDARY" -> "§6"; "MYTHIC" -> "§d"
            "DIVINE" -> "§b"; "SPECIAL", "VERY_SPECIAL" -> "§c"; else -> if (ROMAN.matches(name)) "§9" else "§f"
        }
    }

    private fun parseShort(num: String, suffix: String): Long {
        val v = num.replace(",", "").toDoubleOrNull() ?: return 0
        return (v * when (suffix) { "k" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }).toLong()
    }

    // Books come through as "Protection VII" / "Ultimate Wise V"
    private fun priceOf(name: String): Double {
        ItemsDb.idFor(name)?.let { return CroesusPrices.price(it) }
        val m = ROMAN.find(name) ?: return 0.0
        val lvl = fishmod.utils.data.Roman.toInt(m.groupValues[2])
        val base = m.groupValues[1].uppercase().replace(' ', '_').replace("'", "")
        for (id in listOf("ENCHANTMENT_${base}_$lvl", "ENCHANTMENT_ULTIMATE_${base}_$lvl", "ENCHANTMENT_${base.removePrefix("ULTIMATE_")}_$lvl")) {
            val p = CroesusPrices.price(id)
            if (p > 0 && !p.isNaN()) return p
        }
        return 0.0
    }

    private fun changed() {
        dirty = true
        save()
    }

    @JvmStatic
    fun reset() {
        data = Data(); changed()
        Misc.addChatMessage(Component.literal("§b[FM] §fExperiment profit tracker reset"))
    }

    // ── HUD ──

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.expProfitEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        if (FishSettings.expProfitOnlyIsland && fishmod.utils.Location.getCurrentLocation() != fishmod.utils.Location.PRIVATE_ISLAND) return
        if (data.experiments == 0 && data.drops.isEmpty() && data.xp == 0L) return

        // Same layout as the Slayer/Mining trackers: right-aligned value column, "| item count" after it
        val rows = ArrayList<Pair<String, String>>()
        rows.add("§e§lExperiment Profit" to "")
        val items = data.drops.filterKeys { !NOT_ITEM.containsMatchIn(it) }.map { (name, n) -> Triple(name, n, priceOf(name) * n) }.sortedByDescending { it.third }
        var total = 0.0
        val cap = FishSettings.expProfitLines.coerceIn(1, 30)
        var hiddenN = 0; var hiddenV = 0.0
        for ((i, r) in items.withIndex()) {
            total += r.third
            if (i >= cap) { hiddenN++; hiddenV += r.third; continue }
            rows.add("§7| ${colorOf(r.first)}${r.first} §f${"%,d".format(r.second)}" to (if (r.third > 0) "§6${short(r.third)}" else "§8?"))
        }
        if (hiddenN > 0) rows.add("§7| §8$hiddenN more ${if (hiddenN == 1) "item" else "items"}" to "§6${short(hiddenV)}")

        // Chronomatron/Ultrasequencer are add-ons: shown, but per-experiment rates use Superpairs only
        val sp = data.byType["Superpairs"] ?: 0
        val addons = data.byType.filterKeys { it != "Superpairs" }.entries.joinToString(" ") { "§7${it.key.take(6)} §f${it.value}" }
        rows.add("§7Superpairs §f$sp" + (if (addons.isNotEmpty()) " §8| $addons" else "") to "")
        rows.add("§7Enchanting XP §b${short(data.xp.toDouble())}" + (if (sp > 0) " §7(§b${short(data.xp.toDouble() / sp)}§7/exp)" else "") to "")
        rows.add("§eProfit §6${short(total)}" + (if (sp > 0) " §7(§6${short(total / sp)}§7/exp)" else "") to "")

        val f = mc.font
        val lh = Constants.TEXT_HEIGHT + 2
        val colX = rows.maxOfOrNull { if (it.second.isEmpty()) 0 else f.width(it.second) + 6 } ?: 0
        val sc = FishSettings.expProfitHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.expProfitHudX.toFloat(), FishSettings.expProfitHudY.toFloat())
        ctx.pose().scale(sc, sc)
        for ((i, r) in rows.withIndex()) {
            if (r.second.isEmpty()) { ctx.text(f, r.first, 0, lh * i, 0xFFFFFFFF.toInt(), true); continue }
            ctx.text(f, r.second, colX - 6 - f.width(r.second), lh * i, 0xFFFFFFFF.toInt(), true)
            ctx.text(f, r.first, colX, lh * i, 0xFFFFFFFF.toInt(), true)
        }
        ctx.pose().popMatrix()
    }

    private fun short(v: Double): String = fishmod.features.slayers.SlayerStatsTracker.short(v)

    // ── storage ──

    private fun load() {
        if (!FILE.exists()) return
        try {
            FILE.reader().use { r -> Gson().fromJson(r, Data::class.java)?.let { data = it } }
        } catch (e: Exception) {
            FishDiag.fail("ExperimentProfitTracker.2", "experiment profit file unreadable", e)
            fishmod.utils.SafeFiles.quarantine(FILE, e)
        }
    }

    private fun save() {
        if (!dirty) return
        dirty = false
        val json = Gson().toJson(data)
        fishmod.utils.IoExecutor.execute {
            FishDiag.guard("ExperimentProfitTracker.3", "experiment profit file write failed") { fishmod.utils.SafeFiles.writeAtomic(FILE, json) }
        }
    }
}
