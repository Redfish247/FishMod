package fishmod.cosmetic

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.FishUtil
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object FishChat {
    private var lastDropAt = 0L
    private var lastDropItem = ""
    private const val MAX_LEN = 200
    private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"
    private const val MIN_GAP_MS = 1500L
    private var lastSent = 0L

    fun init() {
        // Chat Tabs: a message typed while the FishMod tab is selected goes to /fc instead of public chat.
        net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_CHAT.register { msg ->
            if (fishmod.features.chat.ChatTabs.fishModTabSend() && msg.isNotBlank()) { send(msg); false } else true
        }
        // Share own rare drops to everyone on FishChat (big message with a gg button).
        fishmod.utils.events.FishEvents.chat.register { c ->
            if (FishSettings.fishChatEnabled && FishSettings.fishChatShareDrops) shareDrop(c.chatPlain)
            false
        }
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fcoff").executes {
                FishSettings.fishChatEnabled = false
                try { fishmod.utils.config.FishConfig.manager.save() } catch (_: Throwable) {}
                note("FishMod chat disabled. Re-enable it in Chat settings."); 1
            })
            // /fcr <name> <message>: reply to that person's latest FishChat line (name click in chat fills this in).
            d.register(
                ClientCommands.literal("fcr").executes { note("Usage: /fcr <name> <message>"); 1 }
                    .then(ClientCommands.argument("name", StringArgumentType.word())
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes { ctx ->
                            sendReply(StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "message")); 1
                        }))
            )
            for (name in arrayOf("fc", "fmchat")) {
                d.register(
                    ClientCommands.literal(name)
                        .executes { note("Usage: /$name <message>"); 1 }
                        .then(ClientCommands.argument("message", StringArgumentType.greedyString()).executes { ctx ->
                            send(StringArgumentType.getString(ctx, "message")); 1
                        })
                )
            }
        }
    }

    private fun note(s: String) = FishUtil.addChatMessage(Component.literal("§7[FishChat] $s"))

    // Only the really rare stuff gets shared: scrolls, Necron's Handle, M6/M7 master stars, top-tier slayer drops, all dyes.
    private val SHARE_EXACT = setOf(
        "necron's handle", "fourth master star", "fifth master star",
        // Slayer drops
        "warden heart", "overflux capacitor", "judgement core", "primordial eye", "high class archfiend dice",
        // Fishing (Lord Jawbus / Thunder) and a few other big ones
        "radioactive vial", "burnt texts", "titanoboa shed", "tiki mask", "divan alloy", "shimmering wool", "tier boost core", "ender dragon", "quick claw", "dark claymore",
    )

    /** True for drops worth broadcasting. [item] is the name from RareDropAnimation.dropName. */
    private fun isVeryRare(item: String): Boolean {
        val n = item.trim().lowercase()
        // every tier-7 enchant book (Smite VII, Sharpness VII ...) except serums
        if (!n.contains("serum") && n.endsWith(" vii")) return true
        return n in SHARE_EXACT || n.endsWith(" dye") || n.endsWith(" scroll") || n.endsWith("locket") || n.startsWith("scatha") || n.contains("fatal tempo") || n.contains("fetal tempo") || n.contains("master star")
    }

    private fun shareDrop(plain: String) {
        val own = Minecraft.getInstance().user.name
        val item = fishmod.features.general.RareDropAnimation.dropName(plain, own)?.take(48) ?: return
        if (!isVeryRare(item)) return
        val now = System.currentTimeMillis()
        if (item == lastDropItem && now - lastDropAt < 10_000) return
        lastDropItem = item; lastDropAt = now
        val json = JsonObject().apply {
            addProperty("t", "drop")
            addProperty("name", own)
            addProperty("item", item)
        }
        CosmeticLive.sendText(json.toString())
    }

    /** Dev info (/fmdev fcinfo): settings state plus every rule and exact name the very-rare share filter matches. */
    fun shareInfo() {
        note("Dev: FishChat ${if (FishSettings.fishChatEnabled) "§aON" else "§cOFF"}§7, share drops ${if (FishSettings.fishChatShareDrops) "§aON" else "§cOFF"}§7, live connected: see send result of /fmdev fcdrop")
        note("Dev: rules: any '... Dye', '... Scroll', '...Locket', 'Scatha...', 'master star', 'Fatal Tempo', '... VII' book (not serum)")
        note("Dev: exact (${SHARE_EXACT.size}): " + SHARE_EXACT.sorted().joinToString(", "))
        note("Dev: test one with /fmdev fcdrop <chat line>, e.g. /fmdev fcdrop RARE DROP! Smite VII")
    }

    /** Dev test (/fmdev fcdrop): runs a fake drop line through the real parse + send path, bypassing the 10s dedup. */
    fun testDrop(line: String) {
        val own = Minecraft.getInstance().user.name
        val item = fishmod.features.general.RareDropAnimation.dropName(line, own)?.take(48)
        if (item == null) { note("Dev: line did not parse as a drop: $line"); return }
        if (!isVeryRare(item)) { note("Dev: '$item' parsed but is NOT on the very-rare list, real drop would not send."); return }
        if (!FishSettings.fishChatEnabled || !FishSettings.fishChatShareDrops) note("Dev: FishChat or share-drops setting is OFF, real drops would not send.")
        lastDropItem = ""; lastDropAt = 0L
        val json = JsonObject().apply {
            addProperty("t", "drop")
            addProperty("name", own)
            addProperty("item", item)
        }
        val ok = CosmeticLive.sendText(json.toString())
        note("Dev: parsed '$item', send ${if (ok) "OK" else "FAILED (not connected)"}")
    }

    /** A {t:drop} frame: big highlighted line with a clickable gg that sends a gold "gg" through /fc. */
    fun receiveDrop(root: JsonObject) {
        if (!FishSettings.fishChatEnabled || !FishSettings.fishChatShareDrops) return
        val name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(16)?.takeIf { it.isNotBlank() } ?: return
        val item = root.get("item")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(48)?.takeIf { it.isNotBlank() } ?: return
        val clean = ProfanityFilter.censor(item) ?: item
        Minecraft.getInstance().execute {
            val msg = Component.literal("[FC] ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("★ RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal("$name ").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal("got ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(clean).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal("  "))
                .append(Component.literal("[gg]").withStyle {
                    it.withColor(ChatFormatting.GOLD).withBold(true).withUnderlined(true)
                        .withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("/fc gg"))
                        .withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§6Click to send gg")))
                })
            FishUtil.addChatMessage(msg)
        }
    }

    /** Latest line per sender (lowercase name -> display name to text), so /fcr can quote what is being replied to. */
    private val lastLines = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    private fun sendReply(name: String, raw: String) {
        val last = lastLines[name.lowercase()]
        if (last == null) { note("No recent FishChat message from $name to reply to."); return }
        send(raw, last.first, last.second)
    }

    private fun send(raw: String, replyName: String? = null, replyText: String? = null) {
        if (!FishSettings.fishChatEnabled) { note("FishMod Chat is off in the General tab."); return }
        val text = raw.replace('§', ' ').trim().take(MAX_LEN)
        if (text.isEmpty()) return
        if (text.equals(".fotd", true)) { factOfTheDay(); return }
        val now = System.currentTimeMillis()
        if (now - lastSent < MIN_GAP_MS) { note("Slow down a little."); return }
        val player = Minecraft.getInstance().player ?: return
        val json = JsonObject().apply {
            addProperty("t", "chat")
            addProperty("name", player.name.string)
            addProperty("text", text)
            if (replyName != null) add("reply", JsonObject().apply { addProperty("name", replyName); addProperty("text", (replyText ?: "").take(60)) })
        }
        if (!CosmeticLive.sendText(json.toString())) { note("Not connected to FishMod live yet, try again in a moment."); return }
        lastSent = now
    }

    private val fotdHttp: java.net.http.HttpClient = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(8)).build()
    @Volatile private var fotdBusy = false
    @Volatile private var fotdCache: Pair<String, String>? = null // utc day -> fact

    /** .fotd in FishChat: shows today's fact (worker caches one api-ninjas call per UTC day) to the sender only. */
    private fun factOfTheDay() {
        val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        fotdCache?.takeIf { it.first == day }?.let { showFact(it.second); return }
        if (fotdBusy) return
        fotdBusy = true
        Thread({
            try {
                val req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://fishmod.dev/fotd"))
                    .header("X-FishMod-Token", fishmod.utils.HypixelApi.MOD_TOKEN).header("User-Agent", "Mozilla/5.0")
                    .timeout(java.time.Duration.ofSeconds(10)).GET().build()
                val res = fotdHttp.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
                val root = com.google.gson.JsonParser.parseString(res.body()).asJsonObject
                val fact = root.get("fact")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.trim()
                if (res.statusCode() != 200 || fact.isNullOrBlank()) throw java.io.IOException("HTTP ${res.statusCode()}")
                fotdCache = day to fact
                showFact(fact)
            } catch (_: Throwable) {
                Minecraft.getInstance().execute { note("Could not get the fact of the day right now, try again later.") }
            } finally { fotdBusy = false }
        }, "FishMod-fotd").apply { isDaemon = true }.start()
    }

    private fun showFact(fact: String) {
        val clean = ProfanityFilter.censor(fact) ?: fact
        Minecraft.getInstance().execute {
            FishUtil.addChatMessage(
                Component.literal("[FC] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("Fact of the day: ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal(clean).withStyle(ChatFormatting.WHITE))
            )
        }
    }

    fun receive(root: JsonObject) {
        if (!FishSettings.fishChatEnabled) return
        var name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(16)?.takeIf { it.isNotBlank() } ?: return
        val fromBridge = root.get("uuid")?.takeIf { !it.isJsonNull }?.asString == "bridge"
        if (root.get("uuid")?.takeIf { !it.isJsonNull }?.asString == "discord") name = name.replace(Regex("^\\s*\\(D\\)\\s*"), "").ifBlank { "Discord" }
        val fromDiscord = root.get("uuid")?.takeIf { !it.isJsonNull }?.asString == "discord"
        // Discord messages can carry long signed attachment URLs after the text; don't cut them off.
        val text = root.get("text")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(if (fromDiscord) 1000 else MAX_LEN)?.takeIf { it.isNotBlank() } ?: return
        val clean = ProfanityFilter.censorIncoming(text) ?: text
        if (!fromBridge) lastLines[name.lowercase()] = name to text.take(60)
        val badgeParts = (root.get("badges")?.takeIf { it.isJsonArray }?.asJsonArray ?: com.google.gson.JsonArray()).mapNotNull { el ->
            val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val sym = o.get("s")?.takeIf { !it.isJsonNull }?.asString?.replace(Regex("[\\uFE0E\\uFE0F\\u200D]"), "")?.take(4)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rgb = o.get("c")?.takeIf { !it.isJsonNull }?.asString?.removePrefix("#")?.toIntOrNull(16) ?: 0xFFFFFF
            Component.literal(sym).withStyle { it.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)) }
        }
        val replyObj = root.get("reply")?.takeIf { it.isJsonObject }?.asJsonObject
        val replyName = replyObj?.get("name")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(16)?.takeIf { it.isNotBlank() }
        val replyText = (replyObj?.get("text")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(60) ?: "").let { ProfanityFilter.censorIncoming(it) ?: it }
        Minecraft.getInstance().execute {
            if (!FishSettings.fishChatNoticeShown) {
                FishSettings.fishChatNoticeShown = true
                try { fishmod.utils.config.FishConfig.manager.save() } catch (_: Throwable) {}
                FishUtil.addChatMessage(
                    Component.literal("[FishMod] FishMod chat is on. ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("Click this message to disable it.").withStyle {
                            it.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                                .withClickEvent(net.minecraft.network.chat.ClickEvent.RunCommand("/fcoff"))
                        })
                )
            }
            if (fromDiscord && !FishSettings.fishChatDiscordNoticeShown) {
                FishSettings.fishChatDiscordNoticeShown = true
                try { fishmod.utils.config.FishConfig.manager.save() } catch (_: Throwable) {}
                FishUtil.addChatMessage(
                    Component.literal("[FishMod] That message came from the FishMod Discord. FishMod chat links Minecraft and the Discord server, so anyone using /fc here can talk with people there. Join to chat back: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(DISCORD_URL).withStyle {
                            it.withColor(ChatFormatting.YELLOW).withUnderlined(true)
                                .withClickEvent(net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(DISCORD_URL)))
                        })
                )
            }
            var msg = Component.literal("[FC] ").withStyle(ChatFormatting.AQUA)
            if (fromDiscord) msg = msg.append(Component.literal("(Discord) ").withStyle(ChatFormatting.LIGHT_PURPLE))
            // Bridge bot lines read "[FC] (Bridge): message" (no FishBridge name).
            if (fromBridge) msg = msg.append(Component.literal("(Bridge): ").withStyle(ChatFormatting.LIGHT_PURPLE))
            else {
                for (b in badgeParts) msg = msg.append(b)
                if (badgeParts.isNotEmpty()) msg = msg.append(Component.literal(" "))
                msg = msg.append(Component.literal(name).withStyle(ChatFormatting.GOLD))
                // Reply line reads "[FC] person1 -> person2: response".
                if (replyName != null) msg = msg.append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(replyName).withStyle(ChatFormatting.GOLD))
                msg = msg.append(Component.literal(": ").withStyle(ChatFormatting.GOLD))
            }
            msg = msg.append(if (clean.trim().equals("gg", true)) Component.literal(clean).withStyle(ChatFormatting.GOLD) else linkify(clean))
            // Click anywhere on the line to reply to its sender (links and image previews keep their own click/hover).
            if (!fromBridge) msg.withStyle {
                it.withClickEvent(net.minecraft.network.chat.ClickEvent.SuggestCommand("/fcr $name "))
                    .withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7Click to reply to $name")))
            }
            FishUtil.addChatMessage(msg)
        }
    }

    private val URL_RE = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    /** Message text with every http(s) link made clickable; image/GIF links from trusted hosts show as [Image] and preview on hover. */
    private fun linkify(text: String): Component {
        val out = Component.empty()
        var last = 0
        for (m in URL_RE.findAll(text)) {
            val url = m.value.trimEnd('.', ',', ')', '!', '?', ';', ':', '\'', '"')
            if (url.length <= "https://".length) continue
            val uri = try { java.net.URI.create(url) } catch (_: Throwable) { continue }
            if (m.range.first > last) out.append(Component.literal(text.substring(last, m.range.first)).withStyle(ChatFormatting.WHITE))
            val image = ChatImagePreview.isPreviewable(url)
            val label = if (image) "[Image]" else url
            out.append(Component.literal(label).withStyle {
                var s = it.withColor(if (image) ChatFormatting.GREEN else ChatFormatting.AQUA).withUnderlined(true)
                    .withClickEvent(net.minecraft.network.chat.ClickEvent.OpenUrl(uri))
                // Image links show the picture on hover (ChatImagePreview) instead of a text tooltip.
                if (!image) s = s.withHoverEvent(net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7Click to open\n§f$url")))
                s
            })
            last = m.range.first + url.length
        }
        if (last < text.length) out.append(Component.literal(text.substring(last)).withStyle(ChatFormatting.WHITE))
        return out
    }
}
