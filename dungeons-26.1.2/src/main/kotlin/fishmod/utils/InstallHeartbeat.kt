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

/**
 * On every server join, reports this install to the worker's usage tracker (powers the admin
 * installs dashboard) and reads back the operator-controlled broadcast config in the same
 * response — an "update available" notice and a one-time welcome message. The worker side can
 * change the links, text, or version at any time with no mod release required.
 */
object InstallHeartbeat {

    private const val BORDER_WIDTH = 53

    // Fixed for the session — resolve once, not per server hop.
    private val modVersion: String = FabricLoader.getInstance()
        .getModContainer("fishmod-dungeons")
        .map { it.metadata.version.friendlyString }
        .orElse("unknown")

    // JOIN fires on every Hypixel server hop (lobby → skyblock hub → private island), each of which
    // is a real disconnect+reconnect at the network layer — so a plain "once per session" flag never
    // resets and silently swallows every heartbeat after the very first one, including genuine
    // rejoins after actually leaving and coming back (no welcome, no update check, nothing at all).
    // A cooldown collapses the rapid-fire hops into one heartbeat while still letting a real, later
    // rejoin send a fresh one.
    private var lastReportedAt = 0L
    private const val REPORT_COOLDOWN_MS = 2 * 60 * 1000L // 2 min

    // Show the outdated-version box at most once per session, whichever source it comes from.
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
                    // isOutdated compares modVersion's own numbering scheme (latestVersion) — but
                    // show the player Modrinth's version string (latestDisplayVersion), since
                    // that's what actually appears on the page they'd go download from. The two
                    // are unrelated numbering schemes (see LATEST_DISPLAY_VERSION in worker.js).
                    val shown = latestDisplayVersion ?: latestVersion!!
                    if (!updateNoticeShown) { sendUpdateBox(shown, updateLinks); updateNoticeShown = true }
                } else if (latestVersion.isNullOrBlank() && !updateNoticeShown) {
                    // Worker set no broadcast version -> check Modrinth directly.
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

    /**
     * An admin (or the stale-nick sweep) can revoke a nick server-side, but that alone only clears
     * the copy other players see — the owner's own client still has it configured locally and would
     * just re-upload it on the very next join (see RemoteNicks.uploadOwn), silently undoing the
     * revoke. If our current nick predates the revoke, drop it locally too so it actually sticks and
     * disappears in-game for the owner as well, not just for everyone else.
     */
    private fun reconcileNickRevoke(nickClearedAt: Long) {
        if (nickClearedAt <= 0 || !NickState.isActive()) return
        if (NickData.lastSetAtMs() >= nickClearedAt) return // set again after the revoke — leave it
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

    /** Compares dot-separated numeric segments, ignoring any "-loader" suffix on either side. */
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
