package fishmod.features.gamble

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.FishMsg
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft

// /gamble <amount|NN%|all> and /gamble top; results print locally only, nothing goes to Hypixel
object GambleCommand {

    @Volatile private var pending = false

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("gamble")
                .executes { FishMsg.send("§7Usage: /gamble <amount|NN%|all> or /gamble top"); 1 }
                .then(ClientCommands.literal("top").executes { top(); 1 })
                .then(ClientCommands.argument("bet", StringArgumentType.greedyString()).executes { ctx ->
                    gamble(StringArgumentType.getString(ctx, "bet")); 1
                }))
        }
    }

    // Returns the normalized bet or null if invalid
    fun parseBet(raw: String): String? {
        val s = raw.trim().lowercase()
        if (s == "all") return "all"
        if (s.endsWith("%")) {
            val p = s.dropLast(1).toIntOrNull() ?: return null
            return if (p in 5..100) "$p%" else null
        }
        val n = s.replace(",", "").toLongOrNull() ?: return null
        return if (n > 0) n.toString() else null
    }

    private fun gamble(raw: String) {
        if (!GambleSettings.gambleEnabled) { FishMsg.send("§7Gamble Points is off in settings."); return }
        val bet = parseBet(raw)
        if (bet == null) { FishMsg.send("§cBet an amount, NN% (minimum 5%) or all."); return }
        if (pending) { FishMsg.send("§7Still waiting for the last result."); return }
        pending = true
        val body = JsonObject().apply { addProperty("bet", bet) }
        GambleApi.call("POST", "/gamble", body) { res ->
            pending = false
            Minecraft.getInstance().execute { handle(res) }
        }
    }

    private fun handle(res: GambleApi.Result?) {
        val o = res?.json
        if (res == null || o == null) { FishMsg.send("§cGamble is offline."); return }
        if (o.get("ok")?.asBoolean == true) {
            val won = o.get("won")?.asBoolean == true
            val bet = o.get("bet")?.let { runCatching { it.asLong }.getOrNull() }
            val balance = o.get("balance")?.let { runCatching { it.asLong }.getOrNull() }
            val amt = if (bet != null) "%,d".format(bet) else "your bet"
            val bal = if (balance != null) " §7Balance: §e%,d".format(balance) else ""
            FishMsg.send(if (won) "§aYou won §e$amt §apoints!$bal" else "§cYou lost §e$amt §cpoints.$bal")
            return
        }
        val err = o.get("error")?.asString ?: "unknown"
        if (err == "cooldown") {
            FishMsg.send("§cYou cannot do this yet. Wait ${waitText(o.get("waitMs")?.asLong ?: 0L)}")
            return
        }
        FishMsg.send("§c" + when (err) {
            "insufficient", "insufficient_balance", "no_balance" -> "Not enough points."
            "bad_bet", "invalid_bet" -> "Invalid bet."
            "unauthorized" -> "Gamble sign-in failed."
            else -> "Gamble failed ($err)."
        })
    }

    // "Xm Ys", seconds only under a minute
    fun waitText(ms: Long): String {
        val total = ((ms.coerceAtLeast(0) + 999) / 1000)
        val m = total / 60
        val s = total % 60
        return if (m == 0L) "${s}s" else "${m}m ${s}s"
    }

    private fun top() {
        if (!GambleSettings.gambleLeaderboard) { FishMsg.send("§7Gamble leaderboard is off in settings."); return }
        GambleLeaderboard.fetch { entries ->
            Minecraft.getInstance().execute {
                if (entries == null) { FishMsg.send("§cLeaderboard offline."); return@execute }
                if (entries.isEmpty()) { FishMsg.send("§7Nobody on the gamble board yet."); return@execute }
                FishMsg.send("§6Gamble Points top ${entries.size}")
                entries.forEachIndexed { i, e ->
                    val crown = if (i == 0) "${CrownBadge.GLYPH} " else ""
                    FishMsg.send("§7${i + 1}. $crown§f${e.name} §e%,d".format(e.balance))
                }
            }
        }
    }
}
