package fishmod.features.extras

import fishmod.compat.hud
import fishmod.features.FishHudEditor
import fishmod.mixin.accessors.PlayerTabOverlayAccessor
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import fishmod.features.extras.ExtrasSettings as S

// Booster Cookie warning, kick cooldown timer and SkyBlock date HUD
object MiscReminders {
    private var cookieCheckAt = 0L
    private var cookieWarnedAt = 0L
    private var kickedAt = 0L
    private var tickN = 0
    private val MONTHS = arrayOf("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
        "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter")
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

    fun init() {
        FishEvents.worldChange.register { cookieCheckAt = System.currentTimeMillis() + 5000; false }
        FishEvents.chat.register { text ->
            if (S.kickTimer) {
                val s = text.chatPlain.trim()
                // both lines seen in Eli's logs
                if (s == "You were kicked while joining that server!" || s.startsWith("You were spawned in Limbo")) kickedAt = System.currentTimeMillis()
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (++tickN % 20 == 0) try { tick() } catch (t: Throwable) { FishDiag.fail("MiscReminders.1", "tick failed", t) } }
        FishHudEditor.register("SkyBlock Date", { S.sbDateHudX }, { S.sbDateHudX = it }, { S.sbDateHudY }, { S.sbDateHudY = it }, 130, 10,
            { S.sbDateHudScale }, { S.sbDateHudScale = it })
        fishmod.features.HudPreview.register("SkyBlock Date") { fishmod.features.HudPreview.text(listOf(dateText())) }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "sb_date_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("MiscReminders.2", "date hud render failed", t) }
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (S.kickTimer && kickedAt > 0 && now - kickedAt >= 60_000) {
            kickedAt = 0
            if (!SkyblockLocation.inSkyblock()) FishUtil.forceTitle(Component.literal("§aKick cooldown over"), Component.literal("§7You can rejoin SkyBlock"), 2500)
        }
        if (S.cookieWarning && cookieCheckAt in 1..now) {
            val inSb = SkyblockLocation.inSkyblock()
            // Slow join: keep retrying for 25 s until SkyBlock is detected
            if (inSb || now - cookieCheckAt > 25_000) cookieCheckAt = 0
            if (inSb && now - cookieWarnedAt > 30 * 60_000 && cookieInactive()) {
                cookieWarnedAt = now
                FishUtil.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§cYour Booster Cookie buff is not active!"))
            }
        }
    }

    // Tab footer "Cookie Buff" then "Not active" (same parse as CompactTab)
    private fun cookieInactive(): Boolean {
        val acc = Minecraft.getInstance().hud.tabList as? PlayerTabOverlayAccessor ?: return false
        val lines = (acc.`fishmod$getFooter`()?.string ?: return false).split("\n").map { TextStrip.plain(it).trim() }.filter { it.isNotEmpty() }
        val i = lines.indexOfFirst { it.contains("Cookie Buff", true) }
        if (i < 0) return false
        val v = lines[i].substringAfter(":", "").trim().ifEmpty { lines.getOrNull(i + 1) ?: "" }
        return v.contains("Not active", true)
    }

    fun dateText(): String {
        val elapsed = System.currentTimeMillis() / 1000 - 1560275700L
        val year = elapsed / 446400 + 1
        val inYear = elapsed % 446400
        val month = (inYear / 37200).toInt()
        val day = (inYear % 37200) / 1200 + 1
        val hour = ((inYear % 1200) / 50).toInt()
        val suffix = if (day in 11..13) "th" else when (day % 10) { 1L -> "st"; 2L -> "nd"; 3L -> "rd"; else -> "th" }
        val base = "§e${MONTHS[month]} $day$suffix§7, Year $year §8(${"%02d".format(hour)}:00)"
        return if (S.sbDateHudRealTime) "$base §7${LocalTime.now().format(TIME_FMT)}" else base
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.sbDateHud || !SkyblockLocation.inSkyblock()) return
        val mc = Minecraft.getInstance()
        val sc = S.sbDateHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.sbDateHudX.toFloat(), S.sbDateHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, Component.literal(dateText()), 0, 0, -1, true)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
