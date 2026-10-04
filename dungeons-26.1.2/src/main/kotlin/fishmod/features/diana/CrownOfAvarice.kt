package fishmod.features.diana

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.Constants
import fishmod.utils.FishMsg
import fishmod.utils.IoExecutor
import fishmod.utils.SafeFiles
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import fishmod.features.FishHudEditor
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.scores.DisplaySlot
import java.nio.file.Files
import java.nio.file.Paths
import java.text.NumberFormat
import java.util.Locale

// Hypixel stops counting Crown of Avarice coins at 1B; keep counting from purse gains while doing Diana
object CrownOfAvarice {

    private const val ID = "CROWN_OF_AVARICE"
    private const val CAP = 1_000_000_000L
    // A maxed crown gives 2x coins instead of 5x, so each purse gain is scaled up by 2.5
    private const val SCALE = 2.5
    // Only purse gains right after a Diana coin source count (mob kill, dug coins, or a burrow dig for Four-Eyed Fish)
    private const val COIN_WINDOW_MS = 3_000L
    private const val MAX_GAIN = 10_000_000L

    private val PATH = Paths.get("config/fishmod/crown_of_avarice.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val PURSE = Regex("""(?:Purse|Piggy):\s*([\d,]+)""")
    private val DUG_COINS = Regex("""^Wow! You dug out [\d,]+ coins!""")
    // Coins from selling, trading or the bank are not crown coins
    private val NOT_CROWN = Regex("""^(?:You sold |\[Bazaar]|\[Auction]|\[NPC]|Sold |You collected |You claimed |Withdrew |Withdrawing |Deposited |Trade completed|You have withdrawn|Claimed )""")
    private val NUM = NumberFormat.getIntegerInstance(Locale.US)

    private var totals: MutableMap<String, Long> = HashMap()
    private var lastPurse = -1L
    private var lastDugCoinsMs = 0L
    private var lastNonCrownMs = 0L
    private var tick = 0
    private const val MILESTONE = 100_000_000L
    private val lastSeen = HashMap<String, Long>()

    // Coins/hour: only time with gains inside the AFK window counts
    // Per crown, so swapping between crowns pauses one session instead of resetting it
    private class Session(var gained: Long = 0L, var activeMs: Long = 0L, var lastTotal: Long = -1L, var lastGainMs: Long = 0L, var lastTickMs: Long = 0L)
    private val sessions = HashMap<String, Session>()

    fun resetSessions() { sessions.clear() }

    fun init() {
        load()
        ClientTickEvents.END_CLIENT_TICK.register { if (tick++ % 10 == 0) onTick(it) }
        Events.ON_GAME_MESSAGE.register { text ->
            val s = text.string.replace(Constants.STRIP_COLOR_REGEX, "").trim()
            val now = System.currentTimeMillis()
            if (NOT_CROWN.containsMatchIn(s)) lastNonCrownMs = now
            else if (Diana.inHub() && DUG_COINS.containsMatchIn(s)) lastDugCoinsMs = now
            false
        }
        Events.ON_WORLD_CHANGE.register { lastPurse = -1L; false }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> editTooltip(stack, lines) })
        FishHudEditor.register("Crown of Avarice", { DianaSettings.dianaCrownHudX }, { DianaSettings.dianaCrownHudX = it },
            { DianaSettings.dianaCrownHudY }, { DianaSettings.dianaCrownHudY = it }, 120, 49,
            { DianaSettings.dianaCrownHudScale }, { DianaSettings.dianaCrownHudScale = it }, { DianaSettings.dianaCrownHud })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "crown_of_avarice")) { ctx, _ -> drawHud(ctx) }
    }

    private fun trackRate(helmet: ItemStack) {
        if (ItemUtil.getId(helmet) != ID) return
        val cur = total(helmet) ?: itemCoins(helmet)
        val now = System.currentTimeMillis()
        val r = sessions.getOrPut(uuidOf(helmet) ?: "crown") { Session(lastTotal = cur, lastTickMs = now) }
        // Gap since this crown was last worn doesn't count
        val worn = now - r.lastTickMs <= 2_000L
        if (worn && r.lastGainMs > 0 && now - r.lastGainMs <= DianaSettings.dianaAfkTimeout * 1000L) r.activeMs += now - r.lastTickMs
        r.lastTickMs = now
        if (worn && cur > r.lastTotal) {
            r.gained += cur - r.lastTotal
            r.lastGainMs = now
        }
        r.lastTotal = cur
    }

    private fun perHour(r: Session?): Long = if (r == null || r.activeMs < 60_000L) 0L else (r.gained * 3_600_000.0 / r.activeMs).toLong()

    private fun short(n: Long): String = when {
        n >= 1_000_000_000L -> "%.2fB".format(Locale.US, n / 1e9)
        n >= 1_000_000L -> "%.1fM".format(Locale.US, n / 1e6)
        n >= 1_000L -> "%.1fK".format(Locale.US, n / 1e3)
        else -> n.toString()
    }

    private fun duration(ms: Long): String {
        val m = ms / 60_000L
        val d = m / 1440; val h = m / 60 % 24; val mm = m % 60
        return when { d > 0 -> "${d}d ${h}h"; h > 0 -> "${h}h ${mm}m"; else -> "${mm}m" }
    }

    private fun hudLines(): List<String> {
        val p = Minecraft.getInstance().player ?: return emptyList()
        val crown = listOf(p.getItemBySlot(EquipmentSlot.HEAD), p.mainHandItem).firstOrNull { ItemUtil.getId(it) == ID } ?: return emptyList()
        val cur = total(crown) ?: itemCoins(crown)
        val session = sessions[uuidOf(crown) ?: "crown"]
        val rate = perHour(session)
        val lines = ArrayList<String>()
        lines += "§dCrown of Avarice"
        lines += if (cur < CAP) "§7Coins: §6${short(cur)}§7/§61B" else "§7Coins: §6${short(cur)} §a(Maxed)"
        val now = System.currentTimeMillis()
        val running = session != null && crown === p.getItemBySlot(EquipmentSlot.HEAD) &&
            session.lastGainMs > 0 && now - session.lastGainMs <= DianaSettings.dianaAfkTimeout * 1000L
        lines += "§7Time: §f${duration(session?.activeMs ?: 0L)}" + if (running) "" else " §c(Paused)"
        lines += "§7Per Hour: §6${if (rate > 0) short(rate) else "-"}"
        if (cur < CAP) lines += "§7Time to Max: §b${if (rate > 0) duration((CAP - cur) * 3_600_000L / rate) else "-"}"
        return lines
    }

    private fun drawHud(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        if (!DianaSettings.dianaCrownHud || FishHudEditor.isOpen() || mc.options.hideGui || mc.player == null) return
        val lines = hudLines()
        if (lines.isEmpty()) return
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(DianaSettings.dianaCrownHudX.toFloat(), DianaSettings.dianaCrownHudY.toFloat())
        val sc = DianaSettings.dianaCrownHudScale.toFloat()
        pose.scale(sc, sc)
        lines.forEachIndexed { i, l -> ctx.text(mc.font, l, 0, i * 10, -1, true) }
        pose.popMatrix()
    }

    private fun uuidOf(stack: ItemStack): String? =
        if (ItemUtil.getId(stack) == ID) ItemUtil.getUuid(stack)?.takeIf { it.isNotEmpty() } else null

    private fun itemCoins(stack: ItemStack): Long = stack.fishmodCustomDataTag()?.getLongOr("collected_coins", 0L) ?: 0L

    fun total(stack: ItemStack): Long? {
        val u = uuidOf(stack) ?: return null
        return maxOf(totals[u] ?: 0L, itemCoins(stack))
    }

    private fun onTick(mc: Minecraft) {
        val p = mc.player ?: return
        checkMilestone(p.getItemBySlot(EquipmentSlot.HEAD))
        trackRate(p.getItemBySlot(EquipmentSlot.HEAD))
        val purse = readPurse(mc)
        val prev = lastPurse
        lastPurse = purse
        if (!DianaSettings.dianaCrownCounter || purse < 0 || prev < 0 || purse <= prev) return
        val gain = purse - prev
        val helmet = p.getItemBySlot(EquipmentSlot.HEAD)
        val u = uuidOf(helmet) ?: return
        // Below 1B Hypixel still counts it on the item itself
        if (itemCoins(helmet) < CAP && (totals[u] ?: 0L) < CAP) return
        val now = System.currentTimeMillis()
        val fromDiana = now - maxOf(lastDugCoinsMs, RareMobs.lastDianaMobDeathMs, BurrowDetector.lastDigMs) <= COIN_WINDOW_MS
        val menuOpen = mc.screen != null && mc.screen !is net.minecraft.client.gui.screens.ChatScreen
        val selling = menuOpen || now - lastNonCrownMs <= COIN_WINDOW_MS
        if (!Diana.active() || !fromDiana || selling || gain > MAX_GAIN) return
        totals[u] = maxOf(totals[u] ?: 0L, itemCoins(helmet)) + (gain * SCALE).toLong()
        save()
    }

    // Chat line each time the worn crown passes another 100M
    private fun checkMilestone(helmet: ItemStack) {
        if (!DianaSettings.dianaCrownMilestones) return
        val u = uuidOf(helmet) ?: return
        val cur = total(helmet) ?: return
        val prev = lastSeen.put(u, cur) ?: return
        if (cur / MILESTONE > prev / MILESTONE && cur - prev < MILESTONE) {
            val m = cur / MILESTONE * 100
            val amt = if (m >= 1000) "%.1fB".format(Locale.US, m / 1000.0).replace(".0B", "B") else "${m}M"
            FishMsg.send("§dCrown of Avarice §7reached §6$amt §7coins!")
        }
    }

    private fun readPurse(mc: Minecraft): Long {
        val sb = mc.level?.scoreboard ?: return -1L
        val obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return -1L
        for (entry in sb.listPlayerScores(obj)) {
            val team = sb.getPlayersTeam(entry.owner())
            val raw = (if (team != null) team.playerPrefix.string + team.playerSuffix.string else entry.owner())
                .replace(Constants.STRIP_COLOR_REGEX, "")
            PURSE.find(raw)?.let { return it.groupValues[1].replace(",", "").toLongOrNull() ?: -1L }
        }
        return -1L
    }

    // Swap the capped "Coins Consumed" value and the per-digit bonuses for the tracked total
    private fun editTooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (!DianaSettings.dianaCrownCounter) return
        val u = uuidOf(stack) ?: return
        val tracked = totals[u] ?: return
        if (tracked <= itemCoins(stack)) return
        val digits = tracked.toString().length
        for (i in lines.indices) {
            val s = lines[i].string
            when {
                s.startsWith("Coins Consumed:") -> lines[i] = Component.literal("§7Coins Consumed: §6${NUM.format(tracked)}")
                s.trim().endsWith("x Damage") && s.trim().startsWith("+") ->
                    lines[i] = Component.literal("  §c+${"%.3f".format(Locale.US, 1 + 0.015 * digits).trimEnd('0').trimEnd('.')}x§c Damage")
                s.trim().endsWith("Magic Find") && s.trim().startsWith("+") && i > 0 && lines.getOrNull(i - 1)?.string?.contains("Damage") == true ->
                    lines[i] = Component.literal("  §b+${"%.1f".format(Locale.US, 2.5 * digits).removeSuffix(".0")} Magic Find §2")
            }
        }
    }

    private fun parseAmount(s: String): Long? {
        val t = s.trim().lowercase().replace(",", "").replace("_", "")
        val mult = when (t.lastOrNull()) { 'k' -> 1e3; 'm' -> 1e6; 'b' -> 1e9; else -> 1.0 }
        val num = (if (mult == 1.0) t else t.dropLast(1)).toDoubleOrNull() ?: return null
        return (num * mult).toLong().takeIf { it >= 0 }
    }

    // Worn crown first, then the held one
    private fun targetCrown(): ItemStack? {
        val p = Minecraft.getInstance().player ?: return null
        return listOf(p.getItemBySlot(EquipmentSlot.HEAD), p.mainHandItem).firstOrNull { uuidOf(it) != null }
    }

    fun command(): LiteralArgumentBuilder<FabricClientCommandSource> =
        ClientCommands.literal("crown")
            .executes {
                val c = targetCrown()
                if (c == null) FishMsg.send("§cWear or hold a Crown of Avarice.")
                else FishMsg.send("§dCrown of Avarice§7: §6${NUM.format(total(c))} §7coins")
                1
            }
            .then(ClientCommands.literal("set").then(ClientCommands.argument("amount", StringArgumentType.word()).executes { ctx ->
                val c = targetCrown()
                val amt = parseAmount(StringArgumentType.getString(ctx, "amount"))
                when {
                    c == null -> FishMsg.send("§cWear or hold a Crown of Avarice.")
                    amt == null -> FishMsg.send("§cBad amount. Examples: 5800000000, 5.8b, 5,800,000,000")
                    else -> {
                        set(c, amt)
                        FishMsg.send("§dCrown of Avarice §7set to §6${NUM.format(amt)} §7coins")
                    }
                }
                1
            }))

    fun set(stack: ItemStack, amount: Long) {
        totals[uuidOf(stack) ?: return] = amount
        save()
    }

    fun wornCrown(): ItemStack? = targetCrown()

    private fun load() {
        if (!Files.exists(PATH)) return
        try {
            val type = object : TypeToken<HashMap<String, Long>>() {}.type
            totals = GSON.fromJson<HashMap<String, Long>>(Files.readString(PATH), type) ?: HashMap()
        } catch (e: Exception) {
            SafeFiles.quarantine(PATH, e)
        }
    }

    private fun save() {
        val json = GSON.toJson(HashMap(totals))
        IoExecutor.execute { SafeFiles.writeAtomic(PATH, json) }
    }
}
