package fishmod.utils

import fishmod.cosmetic.NickData
import fishmod.cosmetic.NickState
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.net.URI

object InstallHeartbeat {

    private const val BORDER_WIDTH = 53

    private val modVersion: String = FabricLoader.getInstance()
        .getModContainer("fishmod-dungeons")
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    private var lastReportedAt = 0L
    private const val REPORT_COOLDOWN_MS = 2 * 60 * 1000L

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> report() }
    }

    private fun report() {
        val now = System.currentTimeMillis()
        if (now - lastReportedAt < REPORT_COOLDOWN_MS) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val uuid = player.getUUID().toString().replace("-", "")
        val name = player.gameProfile.name() ?: return
        lastReportedAt = now

        HypixelApi.reportSeen(uuid, name, modVersion) { _, _, _, welcomeText, discordUrl, nickClearedAt ->
            mc.execute {
                reconcileNickRevoke(nickClearedAt)
                if (!FishSettings.hasSeenWelcomeMessage) {
                    sendWelcomeBox(welcomeText, discordUrl)
                    FishSettings.hasSeenWelcomeMessage = true
                    FishConfig.manager.save()
                }
            }
        }
    }

    private fun reconcileNickRevoke(nickClearedAt: Long) {
        if (nickClearedAt <= 0 || !NickState.isActive()) return
        if (NickData.lastSetAtMs() >= nickClearedAt) return
        NickState.reset()
        Misc.addChatMessage(
            Component.literal("Your FishMod custom name was removed by an admin.").withStyle(ChatFormatting.RED)
        )
    }

    private fun border(): Component =
        Component.literal(" ".repeat(BORDER_WIDTH)).withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH)

    private fun sendWelcomeBox(text: String?, discordUrl: String?) {
        Misc.addChatMessage(border())
        Misc.addChatMessage(Component.literal("Welcome to FishMod!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        if (!text.isNullOrBlank()) Misc.addChatMessage(Component.literal(text).withStyle(ChatFormatting.WHITE))
        if (!discordUrl.isNullOrBlank()) {
            Misc.addChatMessage(Component.literal(""))
            Misc.addChatMessage(linkLine("Discord link", discordUrl))
        }
        Misc.addChatMessage(border())
    }

    private fun linkLine(label: String, url: String): Component =
        Component.literal(label).withStyle { style ->
            try {
                style.withColor(ChatFormatting.BLUE)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(url)))
            } catch (e: Exception) {
                style.withColor(ChatFormatting.BLUE)
            }
        }
}
