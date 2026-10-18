/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.events;

import fishmod.pv.data.EventsSnapshot;
import fishmod.pv.gui.events.page.BingoPage;
import fishmod.pv.gui.events.page.ChocolatePage;
import fishmod.pv.gui.nav.PvSubTab;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Events tab: Bingo / Chocolate Factory - layout matches Mining/Rift overviews. */
public final class EventsPage {
	public enum BingoLoadState {
		IDLE,
		LOADING,
		READY,
		ERROR
	}

	private EventsSnapshot snapshot = EventsSnapshot.empty();
	private BingoLoadState bingoState = BingoLoadState.IDLE;
	private String bingoError = "";
	/** True when resources loaded but player history did not. */
	private boolean bingoHistoryMissing;
	private final EventsUi ui = new EventsUi();
	private final BingoPage bingo = new BingoPage();
	private final ChocolatePage chocolate = new ChocolatePage();

	public void apply(EventsSnapshot snapshot) {
		this.snapshot = snapshot == null ? EventsSnapshot.empty() : snapshot;
		this.chocolate.resetScroll();
		this.ui.zones.clear();
		// Fresh profile - bingo resources/history load lazily on Events tab.
		if (this.bingoState == BingoLoadState.READY
			&& (this.snapshot.bingo().currentGoals().isEmpty() && this.snapshot.bingo().history().isEmpty())) {
			this.bingoState = BingoLoadState.IDLE;
		}
	}

	public void applyBingoLoading() {
		this.bingoState = BingoLoadState.LOADING;
		this.bingoError = "";
		this.bingoHistoryMissing = false;
	}

	public void applyBingoReady(EventsSnapshot snapshot) {
		applyBingoReady(snapshot, true);
	}

	public void applyBingoReady(EventsSnapshot snapshot, boolean historyLoaded) {
		this.snapshot = snapshot == null ? EventsSnapshot.empty() : snapshot;
		this.bingoState = BingoLoadState.READY;
		this.bingoError = "";
		this.bingoHistoryMissing = !historyLoaded || this.snapshot.bingo().history().isEmpty();
	}

	public void applyBingoError(String message) {
		this.bingoState = BingoLoadState.ERROR;
		this.bingoError = message == null || message.isBlank() ? "Bingo unavailable" : message;
		this.bingoHistoryMissing = false;
	}

	public BingoLoadState bingoState() {
		return this.bingoState;
	}

	/** Resources present but {@code /skyblock/bingo} history missing - worth retrying. */
	public boolean needsBingoHistory() {
		return this.bingoState == BingoLoadState.READY && this.bingoHistoryMissing;
	}

	public void resetBingoFetch() {
		this.bingoState = BingoLoadState.IDLE;
		this.bingoError = "";
		this.bingoHistoryMissing = false;
	}

	public EventsSnapshot snapshot() {
		return this.snapshot;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, PvSubTab sub) {
		PvSubTab mode = sub == null ? PvSubTab.EVENTS_BINGO : sub;
		if (mode == PvSubTab.EVENTS_CHOCOLATE) {
			return this.chocolate.mouseScrolled(mouseX, mouseY, scrollY, this.ui.contentX, this.ui.contentW);
		}
		return false;
	}

	public boolean mouseClicked(double mouseX, double mouseY, PvSubTab sub) {
		PvSubTab mode = sub == null ? PvSubTab.EVENTS_BINGO : sub;
		return mode == PvSubTab.EVENTS_CHOCOLATE && this.chocolate.mouseClicked(mouseX, mouseY);
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		PvSubTab sub,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.ui.beginFrame(x, y, w, h);
		PvSubTab mode = sub == null ? PvSubTab.EVENTS_BINGO : sub;
		if (mode == PvSubTab.EVENTS_CHOCOLATE) {
			this.chocolate.render(this.snapshot, this.ui, g, font, x, y, w, h, mouseX, mouseY);
		} else {
			this.bingo.render(
				this.snapshot, this.bingoState, this.bingoError, this.bingoHistoryMissing,
				this.ui, g, font, x, y, w, h, mouseX, mouseY
			);
		}
	}

	public void renderTooltip(
		GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH,
		int x, int y, int w, int h
	) {
		this.ui.drawHover(g, font, mouseX, mouseY, screenW, screenH, x, y, w, h);
	}
}
