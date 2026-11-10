/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GuildStatus
import fishmod.pv.data.MiscStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.home.HomeUi.DISABLED
import fishmod.pv.gui.home.HomeUi.ENABLED
import fishmod.pv.gui.home.HomeUi.FLIP_MS
import fishmod.pv.gui.home.HomeUi.GAP
import fishmod.pv.gui.home.HomeUi.HEADER_COMMUNITY
import fishmod.pv.gui.home.HomeUi.HEADER_DEATHS
import fishmod.pv.gui.home.HomeUi.HEADER_GUILD
import fishmod.pv.gui.home.HomeUi.HEADER_HIGHLIGHTS
import fishmod.pv.gui.home.HomeUi.HEADER_KILLS
import fishmod.pv.gui.home.HomeUi.HEADER_PETS
import fishmod.pv.gui.home.HomeUi.HEADER_PROFILE
import fishmod.pv.gui.home.HomeUi.HEADER_SECTION
import fishmod.pv.gui.home.HomeUi.ITEM_SLOT_BG
import fishmod.pv.gui.home.HomeUi.PAD
import fishmod.pv.gui.home.HomeUi.PANEL_HOVER
import fishmod.pv.gui.home.HomeUi.SEP_GAP
import fishmod.pv.gui.home.HomeUi.STAT_ROW
import fishmod.pv.gui.home.HomeUi.easeInOutCubic
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * Home → Misc: profile extras + guild/cookie (left flip), kills ↔ deaths (right flip).
 */
class MiscStatsPage {
	private var snapshot: MiscStatsSnapshot = MiscStatsSnapshot.empty()
	private var guild: GuildStatus = GuildStatus.idle()
	private var freeCookieMs = 0L
	private var freeCookieKnown = false

	private val zones = ArrayList<HoverZone>()

	private var leftHitX = 0
	private var leftHitY = 0
	private var leftHitW = 0
	private var leftHitH = 0
	private var leftExtrasFace = false
	private var leftFlipStartMs = 0L
	private var leftFlipTarget = false

	private var rightHitX = 0
	private var rightHitY = 0
	private var rightHitW = 0
	private var rightHitH = 0
	private var rightDeathsFace = false
	private var rightFlipStartMs = 0L
	private var rightFlipTarget = false

	private var listScroll = 0
	private var listMaxScroll = 0
	private var guildBtnX = 0
	private var guildBtnY = 0
	private var guildBtnW = 0
	private var guildBtnH = 0
	private var onGuildClick: Runnable? = null

	fun apply(snapshot: MiscStatsSnapshot?) {
		this.snapshot = snapshot ?: MiscStatsSnapshot.empty()
		zones.clear()
		listScroll = 0
		listMaxScroll = 0
		leftFlipStartMs = 0L
		rightFlipStartMs = 0L
		val warm = ArrayList<String>()
		for (u in this.snapshot.communityUpgrades) {
			val id = communitySkyblockId(u.upgrade)
			if (id.isNotBlank()) {
				warm.add(id)
			}
		}
		warm.add("ELIZABETH_NPC")
		SkyBlockItemFactory.prefetchIds(warm)
	}

	fun reset() {
		apply(MiscStatsSnapshot.empty())
		guild = GuildStatus.idle()
		freeCookieMs = 0L
		freeCookieKnown = false
	}

	fun applyGuild(status: GuildStatus?) {
		guild = status ?: GuildStatus.idle()
	}

	fun guild(): GuildStatus = guild

	/** Free cookie claim timestamp from Hypixel player (`skyblock_free_cookie`). */
	fun applyFreeCookie(ms: Long) {
		freeCookieMs = maxOf(0L, ms)
		freeCookieKnown = true
	}

	fun setGuildClickHandler(handler: Runnable?) {
		onGuildClick = handler
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx >= guildBtnX && mx < guildBtnX + guildBtnW && my >= guildBtnY && my < guildBtnY + guildBtnH) {
			if (guild.state != GuildStatus.State.LOADING && guild.state != GuildStatus.State.READY) {
				onGuildClick?.run()
			}
			return true
		}
		if (hit(mx, my, leftHitX, leftHitY, leftHitW, leftHitH)) {
			if (leftFlipStartMs != 0L) {
				return true
			}
			leftFlipTarget = !leftExtrasFace
			leftFlipStartMs = System.currentTimeMillis()
			return true
		}
		if (hit(mx, my, rightHitX, rightHitY, rightHitW, rightHitH)) {
			if (rightFlipStartMs != 0L) {
				return true
			}
			rightFlipTarget = !rightDeathsFace
			rightFlipStartMs = System.currentTimeMillis()
			listScroll = 0
			return true
		}
		return false
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (!hit(mouseX, mouseY, rightHitX, rightHitY, rightHitW, rightHitH)) {
			return false
		}
		if (listMaxScroll <= 0) {
			return false
		}
		listScroll = maxOf(0, minOf(listMaxScroll, listScroll - Math.round(scrollY * 12).toInt()))
		return true
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int
	) {
		zones.clear()

		var leftW = maxOf(100, w / 3)
		val rightW = maxOf(140, w - leftW - GAP)
		leftW = w - rightW - GAP
		val leftX = x
		val rightX = x + leftW + GAP

		drawLeftFlip(g, font, leftX, y, leftW, h, mouseX, mouseY)
		drawRightFlip(g, font, rightX, y, rightW, h, mouseX, mouseY)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	private fun drawLeftFlip(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		leftHitX = x
		leftHitY = y
		leftHitW = w
		leftHitH = h

		val hovering = hit(mx.toDouble(), my.toDouble(), x, y, w, h)
			&& !hit(mx.toDouble(), my.toDouble(), guildBtnX, guildBtnY, guildBtnW, guildBtnH)
		// Only the profile face draws the guild button; a stale hitbox would swallow flip clicks.
		guildBtnW = 0
		var showExtras = leftExtrasFace
		var slideDx = 0F
		if (leftFlipStartMs != 0L) {
			val t = (System.currentTimeMillis() - leftFlipStartMs) / FLIP_MS.toFloat()
			if (t >= 1F) {
				leftExtrasFace = leftFlipTarget
				leftFlipStartMs = 0L
				showExtras = leftExtrasFace
			} else {
				val eased = easeInOutCubic(t.coerceIn(0F, 1F))
				val angle = eased * PI
				slideDx = PvDraw.slideOffset(eased, w)
				showExtras = if (cos(angle) < 0) leftFlipTarget else leftExtrasFace
			}
		}

		PvDraw.innerPanel(g, x, y, w, h)
		g.enableScissor(x, y, x + w, y + h)
		g.pose().pushMatrix()
		g.pose().translate(slideDx, 0F)
		if (hovering && leftFlipStartMs == 0L) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		val lx = x + PAD
		val ly = y + PAD
		val lw = w - PAD * 2
		if (showExtras) {
			drawExtrasFace(g, font, lx, ly, lw, y + h - PAD, mx, my)
		} else {
			drawProfileFace(g, font, lx, ly, lw, y + h - PAD, mx, my)
		}
		g.pose().popMatrix()
		g.disableScissor()
	}

	private fun drawProfileFace(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, bottom: Int, mx: Int, my: Int) {
		val s = snapshot
		var cy = y
		PvDraw.text(g, font, "Profile", x, cy, HEADER_PROFILE)
		cy += font.lineHeight + 4

		cy = stat(
			g, font, "First Join", formatAgo(s.firstJoinMs), x, cy, w, PvDraw.COLOR_TEXT,
			listOf(
				PvTooltip.Line.title("First Join", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(if (s.firstJoinMs > 0L) formatAgo(s.firstJoinMs) else "Unknown")
			)
		)
		cy = stat(
			g, font, "Fairy Souls",
			FormatUtil.commas(s.fairyCollected.toLong()) + " / " + FormatUtil.commas(MAX_FAIRY_SOULS.toLong()),
			x, cy, w, if (s.fairyCollected >= MAX_FAIRY_SOULS) ENABLED else PvDraw.COLOR_ACCENT,
			listOf(
				PvTooltip.Line.title("Fairy Souls", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Collected", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyCollected.toLong()), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row(
					"Missing", PvDraw.COLOR_MUTED,
					FormatUtil.commas(maxOf(0, MAX_FAIRY_SOULS - s.fairyCollected).toLong()), PvDraw.COLOR_TEXT
				),
				PvTooltip.Line.row("Exchanged", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyExchanges.toLong()), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Unspent", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyUnspent.toLong()), PvDraw.COLOR_GOLD)
			)
		)
		cy = stat(g, font, "Soulflow", FormatUtil.commas(s.soulflow), x, cy, w, PvDraw.COLOR_ACCENT, null)
		cy = stat(g, font, "Personal Bank", "Tier " + s.personalBankUpgrade, x, cy, w, PvDraw.COLOR_GOLD, null)
		cy = stat(
			g, font, "Cookie Buff", if (s.cookieBuffActive) "Active" else "Inactive",
			x, cy, w, if (s.cookieBuffActive) ENABLED else PvDraw.COLOR_MUTED, null
		)

		val cookieClaim = if (freeCookieKnown && freeCookieMs > 0L) formatAgo(freeCookieMs) else "-"
		val cookieMeta = when {
			!freeCookieKnown -> "Not loaded yet"
			freeCookieMs > 0L -> "First claim " + formatAgo(freeCookieMs)
			else -> "Never claimed"
		}
		cy = stat(
			g, font, "Free Cookie", cookieClaim, x, cy, w, PvDraw.COLOR_TEXT,
			listOf(
				PvTooltip.Line.title("SkyBlock Free Cookie", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(cookieMeta)
			)
		)

		if (s.refinedJyrreUses > 0) {
			cy = stat(g, font, "Jyrre uses", FormatUtil.commas(s.refinedJyrreUses.toLong()), x, cy, w, PvDraw.COLOR_GOLD, null)
		}
		if (s.unlockedTemples.isNotEmpty()) {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Temples", PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.divider())
			for (temple in s.unlockedTemples) {
				tip.add(PvTooltip.Line.row("Unlocked", PvDraw.COLOR_MUTED, temple, ENABLED))
			}
			cy = stat(g, font, "Temples", s.unlockedTemples.size.toString(), x, cy, w, PvDraw.COLOR_ACCENT, tip)
		}

		cy += 2
		cy = separator(g, x - PAD, cy, w + PAD * 2)

		PvDraw.text(g, font, "Guild", x, cy, HEADER_GUILD)
		cy += font.lineHeight + 4
		drawGuildButton(g, font, x, cy, w, mx, my)
		cy += font.lineHeight + 10

		if (cy + STAT_ROW * 3 < bottom) {
			cy = separator(g, x - PAD, cy, w + PAD * 2)
			PvDraw.text(g, font, "Highlights", x, cy, HEADER_HIGHLIGHTS)
			cy += font.lineHeight + 4
			cy = stat(
				g, font, "K/D", FormatUtil.twoDecimals(s.killDeathRatio()), x, cy, w, HEADER_KILLS,
				listOf(
					PvTooltip.Line.title("Kill / Death Ratio", HEADER_KILLS),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(s.killsTotal), HEADER_KILLS),
					PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(s.deathsTotal), HEADER_DEATHS),
					PvTooltip.Line.row("K/D", PvDraw.COLOR_MUTED, FormatUtil.twoDecimals(s.killDeathRatio()), PvDraw.COLOR_TEXT)
				)
			)
			cy = stat(
				g, font, "Highest Crit", FormatUtil.shortXp(s.highestCriticalDamage.toLong().toDouble()),
				x, cy, w, PvDraw.COLOR_GOLD, null
			)
			cy = stat(
				g, font, "Gifts",
				"→ " + FormatUtil.shortXp(s.giftsGiven.toDouble()) + " / " + FormatUtil.shortXp(s.giftsReceived.toDouble()) + " ←",
				x, cy, w, PvDraw.COLOR_TEXT,
				listOf(
					PvTooltip.Line.title("Gifts", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Given", PvDraw.COLOR_MUTED, FormatUtil.commas(s.giftsGiven), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Received", PvDraw.COLOR_MUTED, FormatUtil.commas(s.giftsReceived), PvDraw.COLOR_TEXT)
				)
			)
		}
	}

	private fun drawExtrasFace(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, bottom: Int, mx: Int, my: Int) {
		val s = snapshot
		var cy = y
		PvDraw.text(g, font, "Community Upgrades", x, cy, HEADER_COMMUNITY)
		cy += font.lineHeight + 4
		cy = if (s.communityUpgrades.isEmpty()) {
			stat(g, font, "Upgrades", "None", x, cy, w, PvDraw.COLOR_MUTED, null)
		} else {
			drawCommunityUpgrades(g, font, s.communityUpgrades, x, cy, w, bottom - font.lineHeight - 4, mx, my)
		}

		cy = separator(g, x - PAD, cy, w + PAD * 2)
		PvDraw.text(g, font, "Pets / Fishing", x, cy, HEADER_PETS)
		cy += font.lineHeight + 4
		cy = stat(g, font, "Pet XP", FormatUtil.shortXp(s.petXpTotal.toDouble()), x, cy, w, PvDraw.COLOR_GOLD, null)
		cy = stat(g, font, "Ores Mined", FormatUtil.commas(s.petOresMined), x, cy, w, PvDraw.COLOR_TEXT, null)
		cy = stat(g, font, "Sea Creatures", FormatUtil.commas(s.petSeaCreatures), x, cy, w, PvDraw.COLOR_TEXT, null)
		cy = stat(g, font, "SC Kills", FormatUtil.commas(s.seaCreatureKills), x, cy, w, PvDraw.COLOR_ACCENT, null)

		val exp = s.experimentation
		if (exp.present() && cy + font.lineHeight + STAT_ROW * 2 < bottom) {
			cy = separator(g, x - PAD, cy, w + PAD * 2)
			PvDraw.text(g, font, "Experimentation", x, cy, HEADER_SECTION)
			cy += font.lineHeight + 4
			for (game in exp.games) {
				if (cy + STAT_ROW > bottom - font.lineHeight) {
					break
				}
				val tip = listOf(
					PvTooltip.Line.title(game.label, PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Claims", PvDraw.COLOR_MUTED, FormatUtil.commas(game.claims), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Attempts", PvDraw.COLOR_MUTED, FormatUtil.commas(game.attempts), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Best score", PvDraw.COLOR_MUTED, FormatUtil.commas(game.bestScore), PvDraw.COLOR_GOLD)
				)
				cy = stat(g, font, game.label, FormatUtil.commas(game.claims), x, cy, w, PvDraw.COLOR_TEXT, tip)
			}
			if (cy + STAT_ROW <= bottom - font.lineHeight) {
				cy = stat(g, font, "Serums", FormatUtil.commas(exp.serumsDrank), x, cy, w, PvDraw.COLOR_ACCENT, null)
			}
			if (cy + STAT_ROW <= bottom - font.lineHeight) {
				cy = stat(g, font, "Resets", FormatUtil.commas(exp.claimsResets), x, cy, w, PvDraw.COLOR_MUTED, null)
			}
		}

		for (section in s.extraSections) {
			if (cy + font.lineHeight + STAT_ROW * 2 > bottom) {
				break
			}
			cy = separator(g, x - PAD, cy, w + PAD * 2)
			PvDraw.text(g, font, section.title, x, cy, HEADER_SECTION)
			cy += font.lineHeight + 4
			var shown = 0
			for (e in section.entries) {
				if (shown >= 6 || cy + STAT_ROW > bottom - font.lineHeight) {
					break
				}
				cy = stat(g, font, trim(font, e.label, w / 2), FormatUtil.commas(e.count), x, cy, w, PvDraw.COLOR_TEXT, null)
				shown++
			}
			if (section.entries.size > shown) {
				PvDraw.text(g, font, "+" + (section.entries.size - shown) + " more", x, cy, PvDraw.COLOR_MUTED)
				cy += STAT_ROW
			}
		}
	}

	/** Two-column essence-shop style rows: icon + name + T#/#. */
	private fun drawCommunityUpgrades(
		g: GuiGraphicsExtractor, font: Font, upgrades: List<MiscStatsSnapshot.CommunityUpgrade>,
		x: Int, y: Int, w: Int, bottom: Int, mx: Int, my: Int
	): Int {
		val iconSize = 16
		val rowH = maxOf(font.lineHeight + 2, iconSize + 2)
		val colGap = 8
		val colW = maxOf(40, (w - colGap) / 2)
		val mid = (upgrades.size + 1) / 2
		val leftBottom = drawCommunityColumn(
			g, font, upgrades.subList(0, minOf(mid, upgrades.size)), x, y, colW, rowH, iconSize, bottom, mx, my
		)
		var rightBottom = y
		if (mid < upgrades.size) {
			rightBottom = drawCommunityColumn(
				g, font, upgrades.subList(mid, upgrades.size), x + colW + colGap, y, colW, rowH, iconSize, bottom, mx, my
			)
		}
		return maxOf(leftBottom, rightBottom) + 2
	}

	private fun drawCommunityColumn(
		g: GuiGraphicsExtractor, font: Font, upgrades: List<MiscStatsSnapshot.CommunityUpgrade>,
		x: Int, y: Int, w: Int, rowH: Int, iconSize: Int, bottom: Int, mx: Int, my: Int
	): Int {
		val labelX = x + iconSize + 3
		var ry = y
		for (u in upgrades) {
			if (ry + font.lineHeight > bottom) {
				break
			}
			val rowAlign = PvDraw.IconTextAlign.of(ry, rowH, iconSize, font.lineHeight)
			drawCommunityIcon(g, u.upgrade, x, rowAlign.iconY, iconSize)
			val max = communityMaxTier(u.upgrade)
			val right = if (max > 0) "${u.tier}/$max" else "T${u.tier}"
			val rightW = font.width(right)
			val left = trim(font, u.label, maxOf(8, w - (labelX - x) - rightW - 4))
			val maxed = max > 0 && u.tier >= max
			PvDraw.text(g, font, left, labelX, rowAlign.textY, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, right, x + w, rowAlign.textY, if (maxed) ENABLED else PvDraw.COLOR_GOLD)
			if (mx >= x && mx < x + w && my >= ry && my < ry + rowH) {
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.title(u.label, HEADER_COMMUNITY))
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.row(
					"Tier", PvDraw.COLOR_MUTED,
					if (max > 0) "${u.tier} / $max" else u.tier.toString(),
					if (maxed) ENABLED else PvDraw.COLOR_GOLD
				))
				if (u.startedMs > 0L) {
					tip.add(PvTooltip.Line.meta("Started " + formatAgo(u.startedMs)))
				}
				zones.add(HoverZone(x, ry, w, rowH, tip))
			}
			ry += rowH
		}
		return ry
	}

	private fun drawGuildButton(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mx: Int, my: Int) {
		val label = guild.buttonLabel()
		val bw = minOf(w, maxOf(72, font.width(label) + 12))
		val bh = font.lineHeight + 6
		guildBtnX = x
		guildBtnY = y
		guildBtnW = bw
		guildBtnH = bh
		val hover = hit(mx.toDouble(), my.toDouble(), x, y, bw, bh)
		PvDraw.fill(g, x, y, bw, bh, ITEM_SLOT_BG)
		val outline = when (guild.state) {
			GuildStatus.State.READY -> PvDraw.COLOR_ACCENT
			GuildStatus.State.NONE -> PvDraw.COLOR_MUTED
			GuildStatus.State.ERROR -> DISABLED
			GuildStatus.State.LOADING -> PvDraw.COLOR_MUTED
			GuildStatus.State.IDLE -> if (hover) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER
		}
		g.outline(x, y, bw, bh, outline)
		val color = when (guild.state) {
			GuildStatus.State.READY -> guild.tagRgb()
			GuildStatus.State.NONE -> PvDraw.COLOR_MUTED
			GuildStatus.State.ERROR -> DISABLED
			GuildStatus.State.LOADING -> PvDraw.COLOR_MUTED
			GuildStatus.State.IDLE -> PvDraw.COLOR_ACCENT
		}
		PvDraw.textCentered(g, font, trim(font, label, bw - 8), x + bw / 2, y + (bh - font.lineHeight) / 2, color)

		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title("Guild", PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.divider())
		when (guild.state) {
			GuildStatus.State.IDLE -> tip.add(PvTooltip.Line.action("Click to load guild"))
			GuildStatus.State.LOADING -> tip.add(PvTooltip.Line.meta("Loading..."))
			GuildStatus.State.NONE -> tip.add(PvTooltip.Line.meta("Not in a guild"))
			GuildStatus.State.ERROR -> tip.add(PvTooltip.Line.meta(guild.error.ifBlank { "Unavailable" }))
			GuildStatus.State.READY -> {
				tip.add(PvTooltip.Line.row("Name", PvDraw.COLOR_MUTED, guild.name, PvDraw.COLOR_TEXT))
				if (guild.tag.isNotBlank()) {
					tip.add(PvTooltip.Line.row("Tag", PvDraw.COLOR_MUTED, "[" + guild.tag + "]", guild.tagRgb()))
				}
				if (guild.rank.isNotBlank()) {
					tip.add(PvTooltip.Line.row("Rank", PvDraw.COLOR_MUTED, guild.rank, PvDraw.COLOR_GOLD))
				}
				tip.add(PvTooltip.Line.row("Members", PvDraw.COLOR_MUTED, FormatUtil.commas(guild.members.toLong()), PvDraw.COLOR_ACCENT))
				tip.add(PvTooltip.Line.row("EXP", PvDraw.COLOR_MUTED, FormatUtil.shortXp(guild.exp.toDouble()), PvDraw.COLOR_TEXT))
				if (guild.joinedMs > 0L) {
					tip.add(PvTooltip.Line.meta("Joined " + formatAgo(guild.joinedMs)))
				}
				if (guild.createdMs > 0L) {
					tip.add(PvTooltip.Line.meta("Created " + formatAgo(guild.createdMs)))
				}
				if (guild.description.isNotBlank()) {
					tip.add(PvTooltip.Line.blank())
					tip.add(PvTooltip.Line.meta(guild.description))
				}
			}
		}
		zones.add(HoverZone(x, y, bw, bh, tip))
	}

	private fun drawRightFlip(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		rightHitX = x
		rightHitY = y
		rightHitW = w
		rightHitH = h

		val hovering = hit(mx.toDouble(), my.toDouble(), x, y, w, h)
		var showDeaths = rightDeathsFace
		var slideDx = 0F
		if (rightFlipStartMs != 0L) {
			val t = (System.currentTimeMillis() - rightFlipStartMs) / FLIP_MS.toFloat()
			if (t >= 1F) {
				rightDeathsFace = rightFlipTarget
				rightFlipStartMs = 0L
				showDeaths = rightDeathsFace
			} else {
				val eased = easeInOutCubic(t.coerceIn(0F, 1F))
				val angle = eased * PI
				slideDx = PvDraw.slideOffset(eased, w)
				showDeaths = if (cos(angle) < 0) rightFlipTarget else rightDeathsFace
			}
		}

		PvDraw.innerPanel(g, x, y, w, h)
		g.enableScissor(x, y, x + w, y + h)
		g.pose().pushMatrix()
		g.pose().translate(slideDx, 0F)
		if (hovering && rightFlipStartMs == 0L) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		val rows = if (showDeaths) snapshot.deaths else snapshot.kills
		val total = if (showDeaths) snapshot.deathsTotal else snapshot.killsTotal
		val title = if (showDeaths) "Deaths" else "Kills"
		val titleColor = if (showDeaths) HEADER_DEATHS else HEADER_KILLS

		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		PvDraw.text(g, font, title, lx, ly, titleColor)
		PvDraw.textRight(g, font, FormatUtil.commas(total), lx + lw, ly, titleColor)
		ly += font.lineHeight + 4

		val listTop = ly
		val listBottom = y + h - PAD
		val listH = maxOf(STAT_ROW, listBottom - listTop)
		// Top entries only, laid out as a two-column grid so nothing scrolls or clips.
		val cols = 2
		val colGap = 10
		val cellW = (lw - colGap) / cols
		val cellH = STAT_ROW + 7
		val fit = cols * maxOf(1, listH / cellH)
		var maxCount = 1L
		for (entry in rows) {
			maxCount = maxOf(maxCount, entry.count)
		}
		listMaxScroll = 0
		listScroll = 0
		val shown = minOf(rows.size, fit)
		val perCol = (shown + cols - 1) / cols
		for (i in 0 until shown) {
			val entry = rows[i]
			val col = i / maxOf(1, perCol)
			val row = i % maxOf(1, perCol)
			val cx = lx + col * (cellW + colGap)
			val cy = listTop + row * cellH
			val value = FormatUtil.shortXp(entry.count.toDouble())
			PvDraw.text(
				g, font, trim(font, entry.label, cellW - font.width(value) - 6), cx, cy,
				if (i < 3) PvDraw.COLOR_TEXT else PvDraw.COLOR_MUTED
			)
			PvDraw.textRight(g, font, value, cx + cellW, cy, titleColor)
			val barY = cy + font.lineHeight
			PvDraw.fill(g, cx, barY, cellW, 2, PvDraw.COLOR_BAR_BG)
			PvDraw.fill(g, cx, barY, maxOf(1L, cellW.toLong() * entry.count / maxCount).toInt(), 2, titleColor)
			if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH) {
				zones.add(HoverZone(
					cx, cy, cellW, cellH, listOf(
						PvTooltip.Line.title(entry.label, titleColor),
						PvTooltip.Line.divider(),
						PvTooltip.Line.row(title, PvDraw.COLOR_MUTED, FormatUtil.commas(entry.count), titleColor)
					)
				))
			}
		}
		if (rows.size > shown) {
			PvDraw.textRight(g, font, "+" + (rows.size - shown) + " more", lx + lw, y + h - PAD - font.lineHeight, PvDraw.COLOR_MUTED)
		}

		if (rows.isEmpty()) {
			PvDraw.textCentered(
				g, font, "No " + title.lowercase(),
				x + w / 2, listTop + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED
			)
		}

		g.pose().popMatrix()
		g.disableScissor()
	}

	private fun stat(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?,
		x: Int, y: Int, w: Int, valueColor: Int, tip: List<PvTooltip.Line>?
	): Int {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, if (value.isNullOrBlank()) "-" else value, x + w, y, valueColor)
		if (!tip.isNullOrEmpty()) {
			zones.add(HoverZone(x, y, w, STAT_ROW, tip))
		}
		return y + STAT_ROW
	}

	private class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	private companion object {
		// Wiki total as of 0.27 (Torrhus Canyon + Safari souls).
		const val MAX_FAIRY_SOULS = 289

		val COMMUNITY_MAX: Map<String, Int> = mapOf(
			"island_size" to 10,
			"minion_slots" to 5,
			"guests_count" to 5,
			"coop_slots" to 3,
			"coins_allowance" to 5,
			"closet_slots" to 9,
			"armor_wardrobe_slots" to 9,
			"armor_closet_slots" to 9,
			"equipment_wardrobe_slots" to 9,
			"equipment_closet_slots" to 9,
			"loadout_slots" to 9,
			"loadouts" to 9,
			"hotm_slots" to 3,
			"hotf_slots" to 3,
			"heart_of_the_mountain" to 3,
			"heart_of_the_forest" to 3,
			"ender_chest_rows" to 8,
			"accessory_slot" to 6,
			"accessory_bag_slots" to 6,
			"sacks_count" to 8,
			"sack_slots" to 8,
			"magic_find" to 5,
			"bazaar_flipper" to 2,
			"garden_fortune" to 10
		)

		/** Prefer SkyBlock / pack texture, then skull stack, then vanilla, never blank paper. */
		fun drawCommunityIcon(g: GuiGraphicsExtractor, upgrade: String?, x: Int, y: Int, size: Int) {
			val sbId = communitySkyblockId(upgrade)
			if (sbId.isNotBlank()) {
				val sky = SkyBlockItemFactory.iconStack(sbId)
				if (SkyBlockIconRenderer.hasKnownIcon(sbId)
					|| (sky != null && !sky.isEmpty && !sky.`is`(Items.PAPER) && !sky.`is`(Items.BARRIER))
				) {
					SkyBlockIconRenderer.draw(g, sky, sbId, x, y, size)
					return
				}
			}
			drawItemIcon(g, communityVanillaIcon(upgrade), x, y, size)
		}

		fun drawItemIcon(g: GuiGraphicsExtractor, icon: ItemStack?, x: Int, y: Int, size: Int) {
			if (icon == null || icon.isEmpty) {
				return
			}
			if (size >= 16) {
				val pad = (size - 16) / 2
				g.item(icon, x + pad, y + pad)
				return
			}
			val scale = size / 16F
			g.pose().pushMatrix()
			g.pose().translate(x.toFloat(), y.toFloat())
			g.pose().scale(scale, scale)
			g.item(icon, 0, 0)
			g.pose().popMatrix()
		}

		fun communityMaxTier(upgrade: String?): Int {
			if (upgrade.isNullOrBlank()) {
				return 0
			}
			return COMMUNITY_MAX[upgrade.lowercase(Locale.ROOT)] ?: 0
		}

		/** NEU / SkyBlock id used for textured icons. */
		fun communitySkyblockId(upgrade: String?): String {
			if (upgrade.isNullOrBlank()) {
				return "ELIZABETH_NPC"
			}
			return when (upgrade.lowercase(Locale.ROOT)) {
				"island_size" -> "GRASS"
				"minion_slots" -> "COBBLESTONE_GENERATOR_1"
				"guests_count" -> "OAK_DOOR"
				"coop_slots" -> "ELIZABETH_NPC"
				"coins_allowance" -> "ENCHANTED_GOLD"
				"closet_slots", "armor_wardrobe_slots", "armor_closet_slots" -> "ARMOR_STAND"
				"equipment_wardrobe_slots", "equipment_closet_slots" -> "GOLDEN_HELMET"
				"loadout_slots", "loadouts" -> "CHEST"
				"hotm_slots", "heart_of_the_mountain" -> "MINING_2_PORTAL"
				"hotf_slots", "heart_of_the_forest" -> "FORAGING_2_PORTAL"
				"ender_chest_rows" -> "ENDER_CHEST"
				"accessory_slot", "accessory_bag_slots" -> "PERSONAL_COMPACTOR_7000"
				"sacks_count", "sack_slots" -> "LARGE_ENCHANTED_MINING_SACK"
				"magic_find" -> "TALISMAN_ENRICHMENT_MAGIC_FIND"
				"bazaar_flipper" -> "ENCHANTED_GOLD_BLOCK"
				"garden_fortune" -> "ENCHANTED_WHEAT"
				else -> "ELIZABETH_NPC"
			}
		}

		fun communityVanillaIcon(upgrade: String?): ItemStack {
			if (upgrade == null) {
				return ItemStack(Items.NETHER_STAR)
			}
			val key = upgrade.lowercase(Locale.ROOT)
			return when (key) {
				"island_size" -> ItemStack(Items.GRASS_BLOCK)
				"minion_slots" -> ItemStack(Items.WOODEN_PICKAXE)
				"guests_count" -> ItemStack(Items.OAK_DOOR)
				"coop_slots" -> ItemStack(Items.PLAYER_HEAD)
				"coins_allowance" -> ItemStack(Items.GOLD_INGOT)
				"closet_slots", "armor_wardrobe_slots", "armor_closet_slots" -> ItemStack(Items.LEATHER_CHESTPLATE)
				"equipment_wardrobe_slots", "equipment_closet_slots" -> ItemStack(Items.GOLDEN_HELMET)
				"loadout_slots", "loadouts" -> ItemStack(Items.CHEST)
				"hotm_slots", "heart_of_the_mountain" -> ItemStack(Items.DIAMOND_PICKAXE)
				"hotf_slots", "heart_of_the_forest" -> ItemStack(Items.OAK_SAPLING)
				"ender_chest_rows" -> ItemStack(Items.ENDER_CHEST)
				"accessory_slot", "accessory_bag_slots" -> ItemStack(Items.EMERALD)
				"sacks_count", "sack_slots" -> ItemStack(Items.CHEST_MINECART)
				"magic_find" -> ItemStack(Items.RABBIT_FOOT)
				"bazaar_flipper" -> ItemStack(Items.GOLD_NUGGET)
				"garden_fortune" -> ItemStack(Items.WHEAT)
				else -> when {
					"minion" in key -> ItemStack(Items.WOODEN_PICKAXE)
					"garden" in key || "farm" in key -> ItemStack(Items.WHEAT)
					"sack" in key -> ItemStack(Items.CHEST_MINECART)
					"wardrobe" in key || "closet" in key || "armor" in key -> ItemStack(Items.LEATHER_CHESTPLATE)
					"bank" in key || "coin" in key || "bazaar" in key -> ItemStack(Items.GOLD_INGOT)
					else -> ItemStack(Items.NETHER_STAR)
				}
			}
		}

		fun separator(g: GuiGraphicsExtractor, panelX: Int, y: Int, panelW: Int): Int {
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			val lineY = y + (SEP_GAP - 1) / 2
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF)
			}
			return y + SEP_GAP
		}

		fun formatAgo(ms: Long): String {
			if (ms <= 0L) {
				return "-"
			}
			return FormatUtil.prettySpan(maxOf(0L, System.currentTimeMillis() - ms)) + " ago"
		}

		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val budget = maxOf(0, maxW - font.width(ellipsis))
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) > budget) {
					break
				}
				sb.append(c)
			}
			return sb.toString() + ellipsis
		}

		fun hit(mx: Double, my: Double, x: Int, y: Int, w: Int, h: Int): Boolean =
			mx >= x && mx < x + w && my >= y && my < y + h
	}
}
