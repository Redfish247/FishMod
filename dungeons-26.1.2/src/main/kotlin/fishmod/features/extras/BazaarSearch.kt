package fishmod.features.extras

import com.google.gson.JsonParser
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import fishmod.features.extras.ExtrasSettings as S

// /fmbz <item>: bazaar search with buy/sell prices and a 7-day chat sparkline (Coflnet history)
object BazaarSearch {
    private const val BARS = "▁▂▃▄▅▆▇█"

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fmbz")
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                    .executes { search(StringArgumentType.getString(it, "item")); 1 }))
        }
    }

    private fun msg(s: String) = FishUtil.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + s))

    private fun search(query: String) {
        if (!S.bazaarSearch) { msg("§7Bazaar Search is off in the Extras tab."); return }
        CroesusPrices.refreshIfStale()
        val q = query.trim().lowercase()
        val hits = ItemsDb.names().asSequence()
            .filter { it.lowercase().contains(q) }
            .mapNotNull { n -> ItemsDb.idFor(n)?.let { id -> n to id } }
            .filter { CroesusPrices.bazaarPrice(it.second, true) != null }
            .distinctBy { it.second }
            .sortedBy { it.first.length }
            .take(8).toList()
        if (hits.isEmpty()) { msg("§cNo bazaar product matches \"$query\" (prices may still be loading)."); return }
        if (hits.size > 1 && hits[0].first.lowercase() != q) {
            msg("§eMatches §7(click one):")
            for ((name, _) in hits) {
                FishUtil.addChatMessage(Component.literal(" §b$name").withStyle { it.withClickEvent(ClickEvent.RunCommand("/fmbz $name")) })
            }
            return
        }
        val (name, id) = hits[0]
        val buy = CroesusPrices.bazaarPrice(id, true) ?: 0.0
        val sell = CroesusPrices.bazaarPrice(id, false) ?: 0.0
        msg("§6$name §7Insta-buy §a${fmt(buy)} §7Insta-sell §c${fmt(sell)} §7Spread §e${fmt(buy - sell)}")
        history(id)
    }

    private fun history(id: String) {
        val req = HttpRequest.newBuilder(URI.create("https://sky.coflnet.com/api/bazaar/$id/history/week"))
            .timeout(Duration.ofSeconds(12)).GET().build()
        fishmod.utils.Http.CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept { r ->
            try {
                if (r.statusCode() != 200) { post("§7No price history (HTTP ${r.statusCode()})."); return@thenAccept }
                val arr = JsonParser.parseString(r.body()).asJsonArray
                val buys = arr.mapNotNull { e -> e.asJsonObject.get("buy")?.takeIf { !it.isJsonNull }?.asDouble }.filter { it > 0 }
                if (buys.size < 2) { post("§7No price history for this item."); return@thenAccept }
                post("§77d §f${spark(buys)} §7low §a${fmt(buys.min())} §7high §c${fmt(buys.max())}")
            } catch (t: Throwable) {
                FishDiag.fail("BazaarSearch.1", "history parse failed for $id", t)
                post("§7Price history unavailable.")
            }
        }
    }

    private fun post(s: String) = Minecraft.getInstance().execute { msg(s) }

    // Downsample to 40 bars
    private fun spark(v: List<Double>): String {
        val n = 40.coerceAtMost(v.size)
        val pts = (0 until n).map { i -> v[(i * v.size) / n] }
        val lo = pts.min(); val hi = pts.max()
        return pts.joinToString("") { p -> BARS[if (hi <= lo) 3 else ((p - lo) / (hi - lo) * (BARS.length - 1)).toInt()].toString() }
    }

    private fun fmt(d: Double): String = when {
        d >= 1e9 -> "%.2fB".format(d / 1e9)
        d >= 1e6 -> "%.2fM".format(d / 1e6)
        d >= 1e3 -> "%.1fk".format(d / 1e3)
        else -> "%.1f".format(d)
    }
}
