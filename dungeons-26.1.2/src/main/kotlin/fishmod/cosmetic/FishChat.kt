package fishmod.cosmetic

import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.StringArgumentType
import fishmod.utils.FishUtil
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

object FishChat {
    private const val MAX_LEN = 200
    private const val DISCORD_URL = "https://discord.gg/3mSuQUB8kk"
    private const val MIN_GAP_MS = 1500L
    private var lastSent = 0L

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fcoff").executes {
                FishSettings.fishChatEnabled = false
                try { fishmod.utils.config.FishConfig.manager.save() } catch (_: Throwable) {}
                note("FishMod chat disabled. Re-enable it in Chat settings."); 1
            })
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

    private fun send(raw: String) {
        if (!FishSettings.fishChatEnabled) { note("FishMod Chat is off in the General tab."); return }
        val text = raw.replace('§', ' ').trim().take(MAX_LEN)
        if (text.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now - lastSent < MIN_GAP_MS) { note("Slow down a little."); return }
        val player = Minecraft.getInstance().player ?: return
        val json = JsonObject().apply {
            addProperty("t", "chat")
            addProperty("name", player.name.string)
            addProperty("text", text)
        }
        if (!CosmeticLive.sendText(json.toString())) { note("Not connected to FishMod live yet, try again in a moment."); return }
        lastSent = now
    }

    fun receive(root: JsonObject) {
        if (!FishSettings.fishChatEnabled) return
        var name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(16)?.takeIf { it.isNotBlank() } ?: return
        if (root.get("uuid")?.takeIf { !it.isJsonNull }?.asString == "discord") name = name.replace(Regex("^\s*\(D\)\s*"), "").ifBlank { "Discord" }
        val text = root.get("text")?.takeIf { !it.isJsonNull }?.asString?.replace('§', ' ')?.take(MAX_LEN)?.takeIf { it.isNotBlank() } ?: return
        val fromDiscord = root.get("uuid")?.takeIf { !it.isJsonNull }?.asString == "discord"
        val clean = ProfanityFilter.censor(text) ?: text
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
            msg = msg.append(Component.literal("$name: ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(clean).withStyle(ChatFormatting.WHITE))
            FishUtil.addChatMessage(msg)
        }
    }
}
