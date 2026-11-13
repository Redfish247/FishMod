package fishmod.features.extras

import fishmod.features.extras.ExtrasSettings as S
import fishmod.compat.curScreen
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CalendarTimes {
    private val COUNTDOWN = Regex("""\bin:?\s+((?:\d+[dhms]\s*)+)""", RegexOption.IGNORE_CASE)
    private val UNIT = Regex("""(\d+)([dhms])""")
    private val FMT_DATE = DateTimeFormatter.ofPattern("EEE MMM d, HH:mm", Locale.US)

    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { _, _, _, lines ->
            if (!S.calendarRealTimes || !SkyblockLocation.inSkyblock()) return@ItemTooltipCallback
            val title = Minecraft.getInstance().curScreen?.title?.string ?: return@ItemTooltipCallback
            if (!title.contains("Calendar", true)) return@ItemTooltipCallback
            try { annotate(lines) } catch (t: Throwable) { FishDiag.fail("CalendarTimes.1", "calendar tooltip failed", t) }
        })
    }

    private fun annotate(lines: MutableList<Component>) {
        var i = 0
        while (i < lines.size) {
            val plain = fishmod.utils.FishConstants.STRIP_COLOR_REGEX.replace(lines[i].string, "")
            val m = COUNTDOWN.find(plain)
            if (m != null) {
                var secs = 0L
                UNIT.findAll(m.groupValues[1]).forEach {
                    val n = it.groupValues[1].toLong()
                    secs += when (it.groupValues[2].lowercase()) { "d" -> n * 86400; "h" -> n * 3600; "m" -> n * 60; else -> n }
                }
                if (secs > 0) {
                    val at = Instant.now().plusSeconds(secs).atZone(ZoneId.systemDefault())
                    lines.add(i + 1, Component.literal("  ").append(Component.literal("→ " + FMT_DATE.format(at)).withColor(S.calendarTimeColor and 0xFFFFFF)))
                    i++
                }
            }
            i++
        }
    }
}
