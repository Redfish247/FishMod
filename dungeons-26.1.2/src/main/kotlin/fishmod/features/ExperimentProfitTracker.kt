package fishmod.features

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
    )

    private val TABLE_SCREEN = Regex("(?:Superpairs|Chronomatron|Ultrasequencer) ?(?:\\(.+\\)|➜ Stakes|Rewards)|Experiment(?:ation Tabl| [Oo]v)er?")
    private val CLAIM = Regex("^You claimed the (\\S+) rewards!$")
    private val DROP = Regex("^\\+(?:\\[Lvl 1] )?(.+)$")
    private val XP = Regex("^([\\d,.]+)([kMB]?) Enchanting Exp$")
    private val COUNT = Regex("^(.+?) x([\\d,]+)$")
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
            val s = mc.screen ?: return@register
            if (FishSettings.expProfitEnabled && TABLE_SCREEN.containsMatchIn(fishmod.utils.ScreenTitle.plain(s))) {
                lastTableMs = System.currentTimeMillis()
            }
        }
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.expProfitEnabled) FishDiag.guard("ExperimentProfitTracker.1", "experiment chat parse failed") {
                onChat(text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim())
            }
            false
        }
    }

    private fun onChat(s: String) {
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
        val cm = COUNT.find(reward)
        val name = cm?.groupValues?.get(1)?.trim() ?: reward
        val n = cm?.groupValues?.get(2)?.replace(",", "")?.toLongOrNull() ?: 1L
        data.drops.merge(name, n, Long::plus)
        changed()
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
        if (mc.player == null || mc.options.hideGui) return
        if (FishSettings.expProfitOnlyIsland && fishmod.utils.Location.getCurrentLocation() != fishmod.utils.Location.PRIVATE_ISLAND) return
        if (data.experiments == 0 && data.drops.isEmpty() && data.xp == 0L) return

        val lines = ArrayList<String>()
        lines.add("§d§lEXPERIMENT PROFIT")
        val rows = data.drops.map { (name, n) -> Triple(name, n, priceOf(name) * n) }.sortedByDescending { it.third }
        var total = 0.0
        val maxRows = FishSettings.expProfitLines.coerceIn(1, 30)
        for ((i, r) in rows.withIndex()) {
            total += r.third
            if (i >= maxRows) continue
            val value = if (r.third > 0) "§6${short(r.third)}" else "§8—"
            lines.add("§7${r.second}x §f${r.first}§7: $value")
        }
        if (rows.size > maxRows) lines.add("§8+${rows.size - maxRows} more")
        val exps = data.experiments
        lines.add("§7Experiments: §f$exps" + data.byType.entries.joinToString("§8, ", " §8(", "§8)") { "§7${it.key.take(1)}§f${it.value}" }.takeIf { data.byType.isNotEmpty() }.orEmpty())
        lines.add("§7Enchanting XP: §b${short(data.xp.toDouble())}" + if (exps > 0) " §8(§b${short(data.xp.toDouble() / exps)}§8/exp)" else "")
        lines.add("§7Total value: §6${short(total)}" + if (exps > 0) " §8(§6${short(total / exps)}§8/exp)" else "")

        val lh = Constants.TEXT_HEIGHT + 2
        val sc = FishSettings.expProfitHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.expProfitHudX.toFloat(), FishSettings.expProfitHudY.toFloat())
        ctx.pose().scale(sc, sc)
        for (i in lines.indices) ctx.text(mc.font, lines[i], 0, lh * i, 0xFFFFFFFF.toInt(), true)
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
