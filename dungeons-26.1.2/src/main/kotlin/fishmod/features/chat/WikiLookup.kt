package fishmod.features.chat

import fishmod.features.extras.*

import fishmod.utils.FishUtil
import fishmod.utils.TextStrip
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import fishmod.features.extras.ExtrasSettings as S

// /wiki [item name]: clickable Hypixel wiki search link, no arg = held item
object WikiLookup {
    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("wiki")
                .executes { link(null); 1 }
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                    .executes { link(StringArgumentType.getString(it, "item")); 1 }))
        }
    }

    private fun link(arg: String?) {
        if (!S.wikiCommand) { FishUtil.addChatMessage(Component.literal("§7Wiki Lookup is off in the Extras tab.")); return }
        val query = arg?.trim()?.takeIf { it.isNotEmpty() } ?: held()
        if (query == null) { FishUtil.addChatMessage(Component.literal("§cHold an item or use /wiki <item name>.")); return }
        val url = "https://wiki.hypixel.net/index.php?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        val link = Component.literal("§b§n$query").withStyle { it.withClickEvent(ClickEvent.OpenUrl(URI.create(url))) }
        FishUtil.addChatMessage(Component.literal("§aWiki: ").append(link).append(Component.literal(" §7(click to open)")))
    }

    private fun held(): String? {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: return null
        if (stack.isEmpty) return null
        return TextStrip.plain(stack.hoverName.string).trim().takeIf { it.isNotEmpty() }
    }
}
