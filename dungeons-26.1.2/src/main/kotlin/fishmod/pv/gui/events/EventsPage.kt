/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events

import fishmod.pv.data.EventsSnapshot
import fishmod.pv.gui.events.page.BingoPage
import fishmod.pv.gui.events.page.ChocolatePage
import fishmod.pv.gui.nav.PvSubTab
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class EventsPage {
	enum class BingoLoadState {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	private var snapshot: EventsSnapshot = EventsSnapshot.empty()
	private var bingoState = BingoLoadState.IDLE
	private var bingoError = ""
	private var bingoHistoryMissing = false
	private val ui = EventsUi()
	private val bingo = BingoPage()
	private val chocolate = ChocolatePage()

	fun apply(snapshot: EventsSnapshot?) {
		this.snapshot = snapshot ?: EventsSnapshot.empty()
		chocolate.resetScroll()
		ui.zones.clear()
		if (bingoState == BingoLoadState.READY &&
			this.snapshot.bingo.currentGoals.isEmpty() && this.snapshot.bingo.history.isEmpty()
		) {
			bingoState = BingoLoadState.IDLE
		}
	}

	fun applyBingoLoading() {
		bingoState = BingoLoadState.LOADING
		bingoError = ""
		bingoHistoryMissing = false
	}

	@JvmOverloads
	fun applyBingoReady(snapshot: EventsSnapshot?, historyLoaded: Boolean = true) {
		this.snapshot = snapshot ?: EventsSnapshot.empty()
		bingoState = BingoLoadState.READY
		bingoError = ""
		bingoHistoryMissing = !historyLoaded || this.snapshot.bingo.history.isEmpty()
	}

	fun applyBingoError(message: String?) {
		bingoState = BingoLoadState.ERROR
		bingoError = if (message.isNullOrBlank()) "Bingo unavailable" else message
		bingoHistoryMissing = false
	}

	fun bingoState(): BingoLoadState = bingoState

	fun needsBingoHistory(): Boolean = bingoState == BingoLoadState.READY && bingoHistoryMissing

	fun resetBingoFetch() {
		bingoState = BingoLoadState.IDLE
		bingoError = ""
		bingoHistoryMissing = false
	}

	fun snapshot(): EventsSnapshot = snapshot

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double, sub: PvSubTab?): Boolean {
		val mode = sub ?: PvSubTab.EVENTS_BINGO
		if (mode == PvSubTab.EVENTS_CHOCOLATE) {
			return chocolate.mouseScrolled(mouseX, mouseY, scrollY, ui.contentX, ui.contentW)
		}
		return false
	}

	fun mouseClicked(mouseX: Double, mouseY: Double, sub: PvSubTab?): Boolean {
		val mode = sub ?: PvSubTab.EVENTS_BINGO
		return mode == PvSubTab.EVENTS_CHOCOLATE && chocolate.mouseClicked(mouseX, mouseY)
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		sub: PvSubTab?,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int
	) {
		ui.beginFrame(x, y, w, h)
		val mode = sub ?: PvSubTab.EVENTS_BINGO
		if (mode == PvSubTab.EVENTS_CHOCOLATE) {
			chocolate.render(snapshot, ui, g, font, x, y, w, h, mouseX, mouseY)
		} else {
			bingo.render(snapshot, bingoState, bingoError, bingoHistoryMissing, ui, g, font, x, y, w, h, mouseX, mouseY)
		}
	}

	fun renderTooltip(
		g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int,
		x: Int, y: Int, w: Int, h: Int
	) {
		ui.drawHover(g, font, mouseX, mouseY, screenW, screenH, x, y, w, h)
	}
}
