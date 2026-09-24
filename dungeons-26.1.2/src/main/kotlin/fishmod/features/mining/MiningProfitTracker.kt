package fishmod.features.mining

import fishmod.features.FishHudEditor
import fishmod.features.croesus.CroesusPrices
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object MiningProfitTracker {

    // ── Filters: where it runs + what counts ──
    private val MINING_AREAS = setOf(
        Location.DWARVEN_MINES, Location.CRYSTAL_HOLLOWS, Location.MINESHAFT,
        Location.GOLD_MINE, Location.DEEP_CAVERNS,
    )
    private val MINING_ITEMS = setOf(
        "MITHRIL_ORE", "TITANIUM_ORE", "GLACITE", "UMBER", "TUNGSTEN", "HARD_STONE", "COBBLESTONE",
    )
    private val GEM_ID = Regex("(ROUGH|FLAWED|FINE)_[A-Z]+_GEM")
    private val SACK_LINE = Regex("^\\+([\\d,]+) (.+?) \\(")
    private const val IDLE_MS = 90_000L
    private const val DEBUG = true

    // ── State ──
    private class Drop(val id: String, var count: Long)

    private val drops = LinkedHashMap<String, Drop>()
    private var activeMs = 0L
    private var lastGainMs = 0L
    private var lastTickMs = 0L
    private var lastPriceRefresh = 0L

    private fun inMiningArea() = Location.inSkyblock() && Location.getCurrentLocation() in MINING_AREAS
    private fun isMiningItem(id: String) = id in MINING_ITEMS || GEM_ID.matches(id)

    @JvmStatic
    fun init() {
        ItemsDb.initAsync()
        FishHudEditor.register(
            "Mining Profit",
            { FishSettings.miningProfitHudX }, { v -> FishSettings.miningProfitHudX = v },
            { FishSettings.miningProfitHudY }, { v -> FishSettings.miningProfitHudY = v },
            150, 70,
            { FishSettings.miningProfitHudScale }, { v -> FishSettings.miningProfitHudScale = v }
        )
        ClientTickEvents.END_CLIENT_TICK.register { tick() }
        Events.ON_GAME_MESSAGE.register { text ->
            if (FishSettings.miningProfitEnabled) Minecraft.getInstance().execute { onChat(text) }
            false
        }
    }

    // ── Every tick: count active time, keep prices fresh ──
    private fun tick() {
        val now = System.currentTimeMillis()
        val dt = now - lastTickMs
        lastTickMs = now
        if (!FishSettings.miningProfitEnabled || !inMiningArea()) return
        if (now - lastGainMs < IDLE_MS && dt in 1..2000) activeMs += dt
        if (now - lastPriceRefresh > 60_000L) {
            lastPriceRefresh = now
            CroesusPrices.refreshIfStale()
        }
    }

    // ── Chat: read the [Sacks] hover ──
    private fun onChat(text: Component) {
        if (!inMiningArea()) return
        val plain = text.string.replace(Constants.STRIP_COLOR_REGEX, "")
        if (!plain.startsWith("[Sacks]")) return
        val hovers = text.toFlatList().mapNotNull { it.style.hoverEvent as? HoverEvent.ShowText }.distinct()
        for (hover in hovers) {
            for (line in hover.value().string.split("\n")) {
                val m = SACK_LINE.find(line.trim()) ?: continue
                val count = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val name = m.groupValues[2]
                val id = ItemsDb.idFor(name)
                if (id == null || !isMiningItem(id)) {
                    if (DEBUG) Misc.addChatMessage(Component.literal("§8[Mining] skipped $name ($id)"))
                    continue
                }
                drops.getOrPut(name) { Drop(id, 0) }.count += count
                lastGainMs = System.currentTimeMillis()
            }
        }
    }

    // ── Money maths ──
    private fun value(d: Drop) = CroesusPrices.price(d.id) * d.count
    private fun totalProfit() = drops.values.sumOf { value(it) }
    private fun perHour(): Double = if (activeMs < 60_000L) 0.0 else totalProfit() / (activeMs / 3_600_000.0)

    private fun fmt(v: Double): String = when {
        v >= 1e9 -> "%.2fb".format(v / 1e9)
        v >= 1e6 -> "%.2fm".format(v / 1e6)
        v >= 1e3 -> "%.1fk".format(v / 1e3)
        else -> "%.0f".format(v)
    }

    // ── HUD ──
    private fun hudLines(): List<String> {
        val lines = ArrayList<String>()
        lines.add("§b§lMining Profit")
        drops.entries.sortedByDescending { value(it.value) }.take(5).forEach { (name, d) ->
            lines.add("§7${d.count}x §f$name §6${fmt(value(d))}")
        }
        lines.add("§7Total: §6${fmt(totalProfit())}")
        lines.add("§7Per hour: §6${fmt(perHour())}")
        lines.add("§7Time: §f${activeMs / 60_000}m")
        return lines
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.miningProfitEnabled || !inMiningArea()) return
        val mc = Minecraft.getInstance()
        val sc = FishSettings.miningProfitHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.miningProfitHudX.toFloat(), FishSettings.miningProfitHudY.toFloat())
        ctx.pose().scale(sc, sc)
        hudLines().forEachIndexed { i, line -> ctx.text(mc.font, line, 0, i * 10, -1, true) }
        ctx.pose().popMatrix()
    }
}