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

// /fc <message> (alias /fmchat): chat with every other FishMod user over the live cosmetics socket. Never touches Hypixel chat.
object FishChat {
    private const val MAX_LEN = 200
    private const val MIN_GAP_MS = 1500L
    private var lastSent = 0L

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
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

    // Called on the websocket thread for {t:"chat"} frames
    fun receive(root: JsonObject) {
        if (!FishSettings.fishChatEnabled) return
        val name = root.get("name")?.takeIf { !it.isJsonNull }?.asString?.take(16)?.takeIf { it.isNotBlank() } ?: return
        val text = root.get("text")?.takeIf { !it.isJsonNull }?.asString?.take(MAX_LEN)?.takeIf { it.isNotBlank() } ?: return
        val clean = ProfanityFilter.censor(text) ?: text
        Minecraft.getInstance().execute {
            FishUtil.addChatMessage(
                Component.literal("[FC] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("$name: ").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(clean).withStyle(ChatFormatting.WHITE))
            )
        }
    }
}
