package fishmod.features.scoreboard

import fishmod.features.dungeon.PartyCommandHandler
import fishmod.utils.PingTracker
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.PlayerScoreEntry
import net.minecraft.world.scores.PlayerTeam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Custom sidebar scoreboard renderer. Reads the live vanilla scoreboard, buckets each line into
 *  a [ScoreboardSection], drops sections the user disabled, optionally compacts large numbers,
 *  then draws the result in vanilla's own top-right slot -- with the same colors/icons/spacing
 *  vanilla shows, since lines are combined and drawn as real [Component]s (via
 *  [PlayerTeam.formatNameForTeam], vanilla's own prefix+name+suffix combinator) rather than
 *  hand-glued strings, which also sidesteps Hypixel's anti-scrape formatting-code noise inside big numbers.
 *  No drag-to-reorder -- just show/hide and reformat. Vanilla's own draw is cancelled by
 *  `fishmod.mixin.GuiScoreboardMixin`. */
object CustomScoreboard {

    private const val LINE_H = 9
    private const val TITLE_H = 11
    private val CONTINUATION = java.util.regex.Pattern.compile("^-\\s")

    /** One row: either a real vanilla line (kept styling) or a blank spacer. */
    private class Line(val component: Component, val blank: Boolean)

    private fun bgColor(): Int {
        val pct = max(0, min(100, FishSettings.customScoreboardOpacity))
        val a = (pct * 2.55).roundToInt()
        return (a shl 24)
    }

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screenW: Int) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val sb = level.scoreboard
        val obj: Objective = sb.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return
        val tr = mc.font

        val title = obj.displayName
        val lines = buildLines(mc, obj) + extraLines(mc)
        if (lines.isEmpty() && title.string.isBlank()) return

        var width = tr.width(title)
        for (l in lines) width = max(width, tr.width(l.component))
        width += 6

        val x2 = screenW - 3
        val x1 = x2 - width
        val y0 = FishSettings.customScoreboardHudY
        val totalH = TITLE_H + lines.size * LINE_H + 2

        ctx.fill(x1 - 2, y0, x2 + 2, y0 + totalH, bgColor())
        ctx.centeredText(tr, title, (x1 + x2) / 2, y0 + 1, 0xFFFFFFFF.toInt())

        var y = y0 + TITLE_H
        for (l in lines) {
            if (!l.blank) ctx.text(tr, l.component, x2 - tr.width(l.component), y, 0xFFFFFFFF.toInt(), true)
            y += LINE_H
        }
    }

    private fun buildLines(mc: Minecraft, obj: Objective): List<Line> {
        val level = mc.level ?: return emptyList()
        val sb = level.scoreboard

        val entries = sb.listPlayerScores(obj)
            .filter { !it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy { it.owner() })
            .take(20) // Hypixel event boards (e.g. mining/fishing festival) can run past 15 lines

        val raw = ArrayList<Line>()
        var lastSection: ScoreboardSection? = null
        for (entry in entries) {
            val owner = entry.owner()
            val team = sb.getPlayersTeam(owner)
            val nameComponent: Component = if (team != null) PlayerTeam.formatNameForTeam(team, entry.ownerName()) else entry.ownerName()

            val stripped = nameComponent.string.trim()
            if (stripped.isEmpty()) {
                lastSection = null
                raw.add(Line(Component.empty(), true))
                continue
            }

            // Indented "- Voidgloom Seraph III" / "- 12/120 Kills" style sub-lines don't match a
            // header pattern themselves, so they inherit whatever section header preceded them
            // (their own toggle would otherwise misclassify them as OTHER).
            val isContinuation = CONTINUATION.matcher(stripped).find() && lastSection != null
            val section = if (isContinuation) lastSection!! else ScoreboardSection.classify(stripped)
            if (!isContinuation) lastSection = section
            if (!sectionEnabled(section)) continue

            if (FishSettings.customScoreboardCompactNumbers) {
                val compacted = CompactNumbers.apply(stripped)
                if (compacted != stripped) {
                    raw.add(Line(Component.literal(compacted).withStyle(sectionColor(section)), false))
                    continue
                }
            }
            raw.add(Line(nameComponent, false))
        }
        return collapseBlanks(raw)
    }

    /** Drop leading/trailing spacer lines and collapse consecutive ones to one, so hidden sections
     *  don't leave doubled-up gaps but real vanilla grouping gaps are kept. */
    private fun collapseBlanks(lines: List<Line>): List<Line> {
        val out = ArrayList<Line>()
        for (l in lines) {
            if (l.blank) {
                if (out.isEmpty() || out.last().blank) continue
            }
            out.add(l)
        }
        while (out.isNotEmpty() && out.last().blank) out.removeAt(out.size - 1)
        return out
    }

    /** Fallback color for a line whose original styling we discarded because its text changed
     *  (compact numbers rewrites "1,234,567" -> "1.2M", so the original per-character Style no
     *  longer lines up with the new text). Approximates Hypixel's usual per-section palette. */
    private fun sectionColor(section: ScoreboardSection): net.minecraft.ChatFormatting = when (section) {
        ScoreboardSection.PURSE -> net.minecraft.ChatFormatting.GOLD
        ScoreboardSection.BANK -> net.minecraft.ChatFormatting.GREEN
        ScoreboardSection.MOTES -> net.minecraft.ChatFormatting.RED
        ScoreboardSection.BITS -> net.minecraft.ChatFormatting.AQUA
        ScoreboardSection.SKILL_AVERAGE -> net.minecraft.ChatFormatting.AQUA
        else -> net.minecraft.ChatFormatting.WHITE
    }

    /** Synthetic "for funnys" lines appended after the real scoreboard content -- same TPS/ping/FPS
     *  values Compact Tab shows in its header bar, each independently toggleable. */
    private fun extraLines(mc: Minecraft): List<Line> {
        val out = ArrayList<Line>()
        if (FishSettings.sbSectionTps) {
            val tps = PartyCommandHandler.currentTps()
            out.add(Line(Component.literal("§7TPS: " + (if (tps < 0) "§c—" else (if (tps < 19) "§c" else "§a") + String.format("%.2f", tps))), false))
        }
        if (FishSettings.sbSectionPing) {
            val ping = realPing(mc)
            out.add(Line(Component.literal("§7Ping: " + (if (ping < 0) "§c—" else "§a${ping}ms")), false))
        }
        if (FishSettings.sbSectionFps) {
            out.add(Line(Component.literal("§7FPS: §a${mc.fps}"), false))
        }
        if (FishSettings.sbSectionPetExtra) {
            fishmod.features.PetHud.currentPetLine()?.let { out.add(Line(Component.literal(it), false)) }
        }
        if (FishSettings.sbSectionSkills) {
            for (l in SkillLevels.lines()) out.add(Line(Component.literal(l), false))
        }
        if (FishSettings.sbSectionBestiary) {
            for (l in BestiaryProgress.lines()) out.add(Line(Component.literal(l), false))
        }
        if (FishSettings.sbSectionCollections) {
            for (l in CollectionsProgress.lines()) out.add(Line(Component.literal(l), false))
        }
        if (FishSettings.sbSectionElection) {
            for (l in ElectionInfo.lines()) out.add(Line(Component.literal(l), false))
        }
        if (FishSettings.sbSectionFireSales) {
            for (l in FireSaleInfo.lines()) out.add(Line(Component.literal(l), false))
        }
        return out
    }

    /** Same freshest-first fallback chain as [fishmod.features.CompactTab]'s realPing(). */
    private fun realPing(mc: Minecraft): Int {
        val live = PingTracker.latest()
        if (live > 0) return live
        try {
            val self = mc.connection?.getPlayerInfo(mc.player!!.uuid)
            if (self != null && self.latency > 0) return self.latency
        } catch (ignored: Exception) {
        }
        try {
            val si = mc.currentServer
            if (si != null && si.ping > 0) return si.ping.toInt()
        } catch (ignored: Exception) {
        }
        return -1
    }

    private fun sectionEnabled(section: ScoreboardSection): Boolean = when (section) {
        ScoreboardSection.DATE -> FishSettings.sbSectionDate
        ScoreboardSection.TIME -> FishSettings.sbSectionTime
        ScoreboardSection.LOCATION -> FishSettings.sbSectionLocation
        ScoreboardSection.PLAYERS -> FishSettings.sbSectionPlayers
        ScoreboardSection.GAME_MODE -> FishSettings.sbSectionGameMode
        ScoreboardSection.PURSE -> FishSettings.sbSectionPurse
        ScoreboardSection.BANK -> FishSettings.sbSectionBank
        ScoreboardSection.MOTES -> FishSettings.sbSectionMotes
        ScoreboardSection.BITS -> FishSettings.sbSectionBits
        ScoreboardSection.COPPER -> FishSettings.sbSectionCopper
        ScoreboardSection.SOWDUST -> FishSettings.sbSectionSowdust
        ScoreboardSection.GEMS -> FishSettings.sbSectionGems
        ScoreboardSection.HEAT -> FishSettings.sbSectionHeat
        ScoreboardSection.COLD -> FishSettings.sbSectionCold
        ScoreboardSection.NORTH_STARS -> FishSettings.sbSectionNorthStars
        ScoreboardSection.SOULFLOW -> FishSettings.sbSectionSoulflow
        ScoreboardSection.GUILD -> FishSettings.sbSectionGuild
        ScoreboardSection.COOKIE -> FishSettings.sbSectionCookie
        ScoreboardSection.SKILL_AVERAGE -> FishSettings.sbSectionSkillAverage
        ScoreboardSection.OBJECTIVE -> FishSettings.sbSectionObjective
        ScoreboardSection.SLAYER -> FishSettings.sbSectionSlayer
        ScoreboardSection.POWDER -> FishSettings.sbSectionPowder
        ScoreboardSection.DIANA -> FishSettings.sbSectionDiana
        ScoreboardSection.PARTY -> FishSettings.sbSectionParty
        ScoreboardSection.EQUIPMENT -> FishSettings.sbSectionEquipment
        ScoreboardSection.DUNGEON -> FishSettings.sbSectionDungeon
        ScoreboardSection.PET -> FishSettings.sbSectionPet
        ScoreboardSection.OTHER -> FishSettings.sbSectionOther
    }
}
