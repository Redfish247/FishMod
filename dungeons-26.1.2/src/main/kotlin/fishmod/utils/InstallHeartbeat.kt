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

    private var updateNoticeShown = false

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

        HypixelApi.reportSeen(uuid, name, modVersion) { latestVersion, latestDisplayVersion, updateLinks, welcomeText, discordUrl, nickClearedAt ->
            mc.execute {
                reconcileNickRevoke(nickClearedAt)
                if (!FishSettings.hasSeenWelcomeMessage) {
                    sendWelcomeBox(welcomeText, discordUrl)
                    FishSettings.hasSeenWelcomeMessage = true
                    FishConfig.manager.save()
                } else if (isOutdated(modVersion, latestVersion) && updateLinks != null) {
                    val shown = latestDisplayVersion ?: latestVersion!!
                    if (!updateNoticeShown) { sendUpdateBox(shown, updateLinks); updateNoticeShown = true }
                } else if (latestVersion.isNullOrBlank() && !updateNoticeShown) {
                    UpdateChecker.latestVersion { modrinthVersion ->
                        mc.execute {
                            if (!updateNoticeShown && isOutdated(modVersion, modrinthVersion)) {
                                val links = discordUrl?.let { UpdateChecker.links + ("discord" to it) } ?: UpdateChecker.links
                                sendUpdateBox(modrinthVersion!!, links)
                                updateNoticeShown = true
                            }
                        }
                    }
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

    private fun sendUpdateBox(version: String, links: Map<String, String>) {
        Misc.addChatMessage(border())
        Misc.addChatMessage(
            Component.literal("FishMod update available: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(version).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
        )
        Misc.addChatMessage(Component.literal(""))
        links["github"]?.let { Misc.addChatMessage(linkLine("GitHub link", it)) }
        links["modrinth"]?.let { Misc.addChatMessage(linkLine("Modrinth Link", it)) }
        links["discord"]?.let { Misc.addChatMessage(linkLine("Discord link", it)) }
        Misc.addChatMessage(border())
    }

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

    private fun isOutdated(local: String?, remote: String?): Boolean {
        if (local.isNullOrBlank() || remote.isNullOrBlank()) return false
        val localParts = local.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
        val remoteParts = remote.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }
        if (localParts.isEmpty() || remoteParts.isEmpty()) return false
        for (i in 0 until maxOf(localParts.size, remoteParts.size)) {
            val l = localParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (l != r) return l < r
        }
        return false
    }
}
