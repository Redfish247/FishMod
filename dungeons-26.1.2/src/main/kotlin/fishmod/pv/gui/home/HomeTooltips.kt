/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.PlayerStatus
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.data.UsernameHistory
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

object HomeTooltips {
	const val ENABLED_GREEN = 0xFF55FF55.toInt()
	const val OFFLINE_RED = 0xFFFF5555.toInt()

	@JvmStatic
	fun drawUsernameHistory(
		g: GuiGraphicsExtractor,
		font: Font,
		history: UsernameHistory,
		historyScroll: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int,
		scrollOut: IntArray
	) {
		val lines = usernameHistory(history)
		if (lines.isEmpty()) {
			scrollOut[0] = historyScroll
			scrollOut[1] = 0
			return
		}
		val lineH = font.lineHeight + 3
		val maxBodyH = maxOf(lineH * 4, (screenH * 0.65).toInt() - 16 - lineH)
		val maxScroll = IntArray(1)
		PvTooltip.drawStyled(
			g, font, lines, mouseX, mouseY, screenW, screenH,
			historyScroll, maxBodyH, maxScroll
		)
		scrollOut[0] = minOf(historyScroll, maxScroll[0])
		scrollOut[1] = maxScroll[0]
	}

	@JvmStatic
	fun usernameHistory(history: UsernameHistory): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		when (history.state) {
			UsernameHistory.State.IDLE -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.action("Click to load username history"))
			}
			UsernameHistory.State.LOADING -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.meta("Loading..."))
			}
			UsernameHistory.State.ERROR -> {
				tip.add(PvTooltip.Line.title("Username history", PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.of(history.error.ifBlank { "Unavailable" }, OFFLINE_RED))
			}
			UsernameHistory.State.READY -> {
				val entries = history.entries
				tip.add(PvTooltip.Line.title("Username history (" + entries.size + ")", PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.divider())
				if (entries.isEmpty()) {
					tip.add(PvTooltip.Line.meta("No history"))
				} else {
					for (i in entries.indices.reversed()) {
						val entry = entries[i]
						val current = i == entries.size - 1
						val original = i == 0
						val nameSpans = listOf(
							if (current) PvTooltip.Span.bold(entry.username, PvDraw.COLOR_ACCENT)
							else PvTooltip.Span.of(entry.username, PvDraw.COLOR_TEXT)
						)
						val dateSpans = when {
							entry.changedAt.isNotBlank() -> listOf(PvTooltip.Span.of(shortDate(entry.changedAt), PvDraw.COLOR_MUTED))
							original -> listOf(PvTooltip.Span.bold("original", PvDraw.COLOR_ACCENT))
							else -> listOf()
						}
						tip.add(PvTooltip.Line.row(nameSpans, dateSpans))
					}
				}
			}
		}
		return tip
	}

	@JvmStatic
	fun bank(snapshot: ProfileSnapshot): List<PvTooltip.Line> {
		val lines = ArrayList<PvTooltip.Line>()
		lines.add(PvTooltip.Line.title("Bank", PvDraw.COLOR_TEXT))
		lines.add(PvTooltip.Line.divider())
		lines.add(PvTooltip.Line.row(
			"Balance", PvDraw.COLOR_MUTED,
			FormatUtil.shortCoins(snapshot.bankCoins), PvDraw.COLOR_GOLD
		))
		lines.add(PvTooltip.Line.row(
			"Purse", PvDraw.COLOR_MUTED,
			FormatUtil.shortCoins(snapshot.purseCoins), PvDraw.COLOR_GOLD
		))
		val txs = snapshot.bankTransactions
		if (txs.isNullOrEmpty()) {
			lines.add(PvTooltip.Line.meta("No transactions, or Banking API off"))
			return lines
		}
		lines.add(PvTooltip.Line.blank())
		lines.add(PvTooltip.Line.of("Recent", PvDraw.COLOR_MUTED))
		for (tx in txs.take(6)) {
			val action = formatBankAction(tx.action)
			val amount = FormatUtil.shortCoins(tx.amount)
			val who = tx.initiatorName.ifBlank { "" }
			val whenText = if (tx.timestampMs > 0L) FormatUtil.ago(tx.timestampMs) else ""
			val left = "$action $amount"
			val right = when {
				who.isNotBlank() && whenText.isNotBlank() -> "$who · $whenText"
				who.isNotBlank() -> who
				else -> whenText
			}
			lines.add(PvTooltip.Line.row(left, PvDraw.COLOR_TEXT, right, PvDraw.COLOR_MUTED))
		}
		return lines
	}

	@JvmStatic
	fun status(playerStatus: PlayerStatus): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.of("Hypixel status", PvDraw.COLOR_TEXT))
		when (playerStatus.state) {
			PlayerStatus.State.IDLE -> tip.add(PvTooltip.Line.of("Click to check online status", PvDraw.COLOR_MUTED))
			PlayerStatus.State.LOADING -> tip.add(PvTooltip.Line.of("Loading...", PvDraw.COLOR_MUTED))
			PlayerStatus.State.ONLINE -> {
				tip.add(PvTooltip.Line.of("Online", ENABLED_GREEN))
				tip.add(PvTooltip.Line.of(playerStatus.location(), PvDraw.COLOR_MUTED))
				val game = PlayerStatus.prettyLocation(playerStatus.gameType)
				if (game.isNotBlank() && game != playerStatus.location()) {
					tip.add(PvTooltip.Line.of(game, PvDraw.COLOR_MUTED))
				}
			}
			PlayerStatus.State.OFFLINE -> {
				val lines = offlineLines(playerStatus, System.currentTimeMillis())
				tip.add(PvTooltip.Line.of(lines[0], OFFLINE_RED))
				for (i in 1 until lines.size) {
					tip.add(PvTooltip.Line.of(lines[i], PvDraw.COLOR_MUTED))
				}
			}
			PlayerStatus.State.ERROR -> tip.add(PvTooltip.Line.of(playerStatus.error.ifBlank { "Unavailable" }, OFFLINE_RED))
		}
		return tip
	}

	@JvmStatic
	fun offlineLines(playerStatus: PlayerStatus, nowMs: Long): List<String> {
		val lines = ArrayList<String>(2)
		lines.add(playerStatus.buttonLabel(nowMs))
		if (playerStatus.lastOnlineMs > 0L) {
			val date = FormatUtil.prettyDate(playerStatus.lastOnlineMs)
			if (date.isNotBlank()) {
				lines.add(date)
			}
		} else if (playerStatus.sessionHidden) {
			lines.add("Online status hidden in Hypixel API settings")
		} else {
			lines.add("Hypixel did not share a last online time")
		}
		return lines
	}

	@JvmStatic
	fun shortDate(iso: String?): String {
		if (iso.isNullOrBlank()) {
			return ""
		}
		val t = iso.indexOf('T')
		return if (t > 0) iso.substring(0, t) else iso
	}

	private fun formatBankAction(action: String?): String {
		if (action.isNullOrBlank()) {
			return "Txn"
		}
		return when (action.trim().uppercase(Locale.ROOT)) {
			"DEPOSIT" -> "Deposit"
			"WITHDRAW" -> "Withdraw"
			else -> {
				val lower = action.trim().lowercase(Locale.ROOT)
				lower[0].uppercaseChar() + lower.substring(1)
			}
		}
	}
}
