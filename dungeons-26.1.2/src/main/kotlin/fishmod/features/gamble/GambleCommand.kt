package fishmod.features.gamble

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.DevOnly
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
                .then(ClientCommands.literal("dev")
                    .executes { ctx -> if (!DevOnly.deny(ctx.source)) devHelp(); 1 }
                    .then(ClientCommands.literal("fake")
                        .executes { ctx -> if (!DevOnly.deny(ctx.source)) devFake(10); 1 }
                        .then(ClientCommands.argument("n", IntegerArgumentType.integer(1, 50)).executes { ctx ->
                            if (!DevOnly.deny(ctx.source)) devFake(IntegerArgumentType.getInteger(ctx, "n")); 1
                        }))
                    .then(ClientCommands.literal("crown")
                        .then(ClientCommands.argument("name", StringArgumentType.word()).executes { ctx ->
                            if (!DevOnly.deny(ctx.source)) devCrown(StringArgumentType.getString(ctx, "name")); 1
                        }))
                    .then(ClientCommands.literal("off").executes { ctx -> if (!DevOnly.deny(ctx.source)) devOff(); 1 })
                    .then(ClientCommands.literal("live").executes { ctx -> if (!DevOnly.deny(ctx.source)) devLive(); 1 }))
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
        if (res == null || o == null) { FishMsg.send("§cGamble is offline." + GambleApi.lastError.takeIf { it.isNotEmpty() }?.let { " §7($it)" }.orEmpty()); return }
        if (o.get("ok")?.asBoolean == true) {
            val won = o.get("won")?.asBoolean == true
            val bet = o.get("bet")?.let { runCatching { it.asLong }.getOrNull() }
            val balance = o.get("balance")?.let { runCatching { it.asLong }.getOrNull() }
            if (balance != null) PlaytimeTracker.lastBalance = balance
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
            "no_points" -> "You have no points to gamble."
            "bad_bet" -> "Invalid bet."
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

    private fun devHelp() {
        FishMsg.send("§6/gamble dev §7- offline leaderboard tests")
        FishMsg.send("§e fake [n] §7fake board of n, you are #1 with the crown")
        FishMsg.send("§e crown <name> §7fake board with <name> as #1 (crown on them)")
        FishMsg.send("§e off §7back to the live worker")
        FishMsg.send("§e live §7raw GET /leaderboard: status, rows, error")
        devSettingsHint()
    }

    private fun devSettingsHint() {
        val off = buildList {
            if (!GambleSettings.gambleEnabled) add("Gamble Points")
            if (!GambleSettings.gambleLeaderboard) add("Leaderboard")
            if (!GambleSettings.gambleCrown) add("Crown")
        }
        if (off.isNotEmpty()) FishMsg.send("§cOff in settings: ${off.joinToString()} §7(turn on to see it)")
    }

    private fun fillers(n: Int, top: Long): List<GambleLeaderboard.Entry> =
        (1..n).map { i -> GambleLeaderboard.Entry("DevFish$i", "", (top - i * 230L).coerceAtLeast(10L)) }

    private fun applyFake(list: List<GambleLeaderboard.Entry>) {
        CrownBadge.clear()
        GambleLeaderboard.fake = list
        GambleLeaderboard.fetch { }
        FishMsg.send("§aFake gamble board on (${list.size} rows, #1 §f${list[0].name}§a). §7/gamble top to view, /gamble dev off to stop.")
        devSettingsHint()
    }

    private fun devFake(n: Int) {
        val p = Minecraft.getInstance().player ?: return
        applyFake(listOf(GambleLeaderboard.Entry(p.name.string, p.uuid.toString(), 12_500L)) + fillers(n - 1, 12_000L))
    }

    private fun devCrown(name: String) {
        val uuid = Minecraft.getInstance().connection?.getPlayerInfo(name)?.profile?.id?.toString() ?: ""
        if (uuid.isEmpty()) FishMsg.send("§7$name is not in tab; crown works by name in chat only.")
        applyFake(listOf(GambleLeaderboard.Entry(name, uuid, 99_999L)) + fillers(9, 12_000L))
    }

    private fun devOff() {
        GambleLeaderboard.fake = null
        CrownBadge.clear()
        FishMsg.send("§7Fake gamble board off; using the live worker again.")
    }

    private fun devLive() {
        FishMsg.send("§7GET ${GambleSettings.API_BASE.trimEnd('/')}/leaderboard?limit=10 ...")
        GambleApi.call("GET", "/leaderboard?limit=10", null) { res ->
            Minecraft.getInstance().execute {
                if (res == null) { FishMsg.send("§cNo response. §7${GambleApi.lastError}"); return@execute }
                FishMsg.send("§7Status §f${res.status}§7, rows §f${res.arr?.size() ?: 0}§7, json ${if (res.json != null) "object" else if (res.arr != null) "array" else "none"}" +
                    GambleApi.lastError.takeIf { it.isNotEmpty() }?.let { " §7err: $it" }.orEmpty())
            }
        }
    }

    private fun top() {
        if (!GambleSettings.gambleLeaderboard) { FishMsg.send("§7Gamble leaderboard is off in settings."); return }
        GambleLeaderboard.fetch { entries ->
            Minecraft.getInstance().execute {
                if (entries == null) { FishMsg.send("§cLeaderboard offline." + GambleApi.lastError.takeIf { it.isNotEmpty() }?.let { " §7($it)" }.orEmpty()); return@execute }
                if (entries.isEmpty()) { FishMsg.send("§7Nobody on the gamble board yet."); return@execute }
                FishMsg.send("§6Gamble Points top ${entries.size}")
                entries.forEachIndexed { i, e ->
                    val line = net.minecraft.network.chat.Component.literal("§7${i + 1}. ")
                    if (i == 0) line.append(CrownBadge.icon()).append(net.minecraft.network.chat.Component.literal(" "))
                    line.append(net.minecraft.network.chat.Component.literal("§f${e.name} §e%,d".format(e.balance)))
                    FishMsg.send(line)
                }
            }
        }
    }
}
