package fishmod.utils

import fishmod.cosmetic.NickData
import fishmod.cosmetic.NickState
import fishmod.utils.config.FishConfig
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import java.net.URI

object InstallHeartbeat {

    private const val BORDER_WIDTH = 53
    private const val TWITCH_URL = "https://www.twitch.tv/redfish2471"
    private const val YOUTUBE_URL = "https://www.youtube.com/@RedFish2471"
    private const val DISCORD_COLOR = 0x5865F2
    private const val TWITCH_COLOR = 0x9146FF
    private const val YOUTUBE_COLOR = 0xFF0000

    private val modVersion: String = FabricLoader.getInstance()
        .getModContainer("fishmod-dungeons")
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    private var lastReportedAt = 0L
    private const val REPORT_COOLDOWN_MS = 2 * 60 * 1000L

    @JvmStatic
    fun init() {
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            try {
                report()
            } catch (e: Throwable) {
                FishDiag.fail("InstallHeartbeat.1", "install heartbeat report failed", e)
            }
        }
    }

    private fun report() {
        val now = System.currentTimeMillis()
        if (now - lastReportedAt < REPORT_COOLDOWN_MS) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val uuid = player.getUUID().toString().replace("-", "")
        val name = player.gameProfile.name() ?: return
        lastReportedAt = now
        FishDiag.check(modVersion != "unknown", "InstallHeartbeat.4") { "mod container fishmod-dungeons not found, version unknown" }

        HypixelApi.reportSeen(uuid, name, modVersion) { _, _, _, welcomeText, discordUrl, nickClearedAt ->
            mc.execute {
                try {
                    reconcileNickRevoke(nickClearedAt)
                    if (!FishSettings.hasSeenWelcomeMessage) {
                        sendWelcomeBox(welcomeText, discordUrl)
                        FishSettings.hasSeenWelcomeMessage = true
                        FishConfig.manager.save()
                    }
                } catch (e: Exception) {
                    FishDiag.fail("InstallHeartbeat.2", "heartbeat response handling failed", e)
                }
            }
        }
    }

    @JvmStatic
    fun applyAdminRevoke(blocked: Boolean, nickClearedAt: Long) {
        if (blocked) {
            if (NickState.isActive()) {
                NickState.reset()
                FishUtil.addChatMessage(Component.literal("Your FishMod access was removed by an admin.").withStyle(ChatFormatting.RED))
            }
            return
        }
        reconcileNickRevoke(nickClearedAt)
    }

    private fun reconcileNickRevoke(nickClearedAt: Long) {
        if (nickClearedAt <= 0 || !NickState.isActive()) return
        if (NickData.lastSetAtMs() >= nickClearedAt) return
        NickState.reset()
        FishUtil.addChatMessage(
            Component.literal("Your FishMod custom name was removed by an admin.").withStyle(ChatFormatting.RED)
        )
    }

    private fun border(): Component =
        Component.literal(" ".repeat(BORDER_WIDTH)).withStyle(ChatFormatting.AQUA, ChatFormatting.STRIKETHROUGH)

    private fun sendWelcomeBox(text: String?, discordUrl: String?) {
        FishUtil.addChatMessage(border())
        FishUtil.addChatMessage(Component.literal("Welcome to FishMod!").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
        if (!text.isNullOrBlank()) FishUtil.addChatMessage(Component.literal(text).withStyle(ChatFormatting.WHITE))
        if (!discordUrl.isNullOrBlank()) {
            FishUtil.addChatMessage(Component.literal(""))
            FishUtil.addChatMessage(Component.literal("Want something added to the mod? ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("/fm suggest [suggestion]").withStyle(ChatFormatting.WHITE)))
            FishUtil.addChatMessage(Component.literal("Find a bug? ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("/fm bug [Explain]").withStyle(ChatFormatting.WHITE)))
            FishUtil.addChatMessage(Component.literal(""))
            FishUtil.addChatMessage(
                linkLine("Discord", discordUrl, DISCORD_COLOR)
                    .append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY))
                    .append(linkLine("Twitch", TWITCH_URL, TWITCH_COLOR))
                    .append(Component.literal("  |  ").withStyle(ChatFormatting.GRAY))
                    .append(linkLine("YouTube", YOUTUBE_URL, YOUTUBE_COLOR))
            )
        }
        FishUtil.addChatMessage(border())
    }

    private fun linkLine(label: String, url: String, color: Int): MutableComponent =
        Component.literal(label).withStyle { style ->
            try {
                style.withColor(color)
                    .withClickEvent(ClickEvent.OpenUrl(URI.create(url)))
                    .withHoverEvent(HoverEvent.ShowText(Component.literal(url)))
            } catch (e: Exception) {
                FishDiag.fail("InstallHeartbeat.3", "bad welcome link url '$url'", e)
                style.withColor(color)
            }
        }
}
