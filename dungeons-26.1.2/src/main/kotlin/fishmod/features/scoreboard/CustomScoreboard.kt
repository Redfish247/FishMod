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

object CustomScoreboard {

    private const val LINE_H = 9
    private const val TITLE_H = 11
    private val CONTINUATION = java.util.regex.Pattern.compile("^-\\s")

    private class Line(val component: Component, val blank: Boolean)

    private const val CACHE_TTL_MS = 250L
    private var sig = 0
    private var sigAt = 0L
    private var cachedBody: List<Line> = emptyList()
    private var cachedBodyWidth = 0
    private var cachedTitleWidth = 0

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
        if (refreshBody(mc, sb, obj, title)) {
            var w = 0
            for (l in cachedBody) w = max(w, tr.width(l.component))
            cachedBodyWidth = w
            cachedTitleWidth = tr.width(title)
        }

        val extra = extraLines(mc)
        if (cachedBody.isEmpty() && extra.isEmpty() && title.string.isBlank()) return
        val lines = cachedBody + extra

        var width = max(cachedTitleWidth, cachedBodyWidth)
        for (l in extra) width = max(width, tr.width(l.component))
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

    private fun refreshBody(mc: Minecraft, sb: net.minecraft.world.scores.Scoreboard, obj: Objective, title: Component): Boolean {
        val now = System.currentTimeMillis()
        val newSig = buildSig(sb, obj, title)
        if (newSig == sig && now - sigAt < CACHE_TTL_MS) return false
        sig = newSig
        sigAt = now
        cachedBody = buildLines(mc, obj)
        return true
    }

    private fun buildSig(sb: net.minecraft.world.scores.Scoreboard, obj: Objective, title: Component): Int {
        var h = title.string.hashCode()
        h = h * 31 + (if (FishSettings.customScoreboardCompactNumbers) 1 else 0)
        for (s in ScoreboardSection.entries) h = h * 31 + (if (sectionEnabled(s)) 1 else 0)
        for (entry in sb.listPlayerScores(obj)) {
            h = h * 31 + entry.value()
            h = h * 31 + entry.owner().hashCode()
            h = h * 31 + (if (entry.isHidden) 1 else 0)
        }
        return h
    }

    private fun buildLines(mc: Minecraft, obj: Objective): List<Line> {
        val level = mc.level ?: return emptyList()
        val sb = level.scoreboard

        val entries = sb.listPlayerScores(obj)
            .filter { !it.isHidden }
            .sortedWith(compareByDescending<PlayerScoreEntry> { it.value() }.thenBy { it.owner() })
            .take(20)

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

    private fun sectionColor(section: ScoreboardSection): net.minecraft.ChatFormatting = when (section) {
        ScoreboardSection.PURSE -> net.minecraft.ChatFormatting.GOLD
        ScoreboardSection.BANK -> net.minecraft.ChatFormatting.GREEN
        ScoreboardSection.MOTES -> net.minecraft.ChatFormatting.RED
        ScoreboardSection.BITS -> net.minecraft.ChatFormatting.AQUA
        ScoreboardSection.SKILL_AVERAGE -> net.minecraft.ChatFormatting.AQUA
        else -> net.minecraft.ChatFormatting.WHITE
    }

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
