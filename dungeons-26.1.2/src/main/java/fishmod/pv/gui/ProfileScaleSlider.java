/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.api.BetterPVConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ProfileScaleSlider {
	static final int WIDTH = 76;
	static final int HEIGHT = 5;
	static final int HIT_PAD = 5;
	static final int SCREEN_MARGIN = 14;
	private static final int KNOB_OVERHANG = 2;
	private static final float IDLE_OPACITY = 0.25F;
	private static final float ACTIVE_OPACITY = 0.85F;
	private static final long FADE_NANOS = 140_000_000L;

	private int x;
	private int y;
	private boolean hovered;
	private boolean dragging;
	private float opacity = IDLE_OPACITY;
	private long lastFrameNanos;

	void layoutViewport(int screenWidth, int screenHeight) {
		Position position = position(screenWidth, screenHeight);
		this.x = position.x();
		this.y = position.y();
	}

	static Position position(int screenWidth, int screenHeight) {
		int x = Math.max(KNOB_OVERHANG, screenWidth - WIDTH - SCREEN_MARGIN);
		int y = Math.max(1, screenHeight - HEIGHT - SCREEN_MARGIN);
		if (screenWidth >= WIDTH + KNOB_OVERHANG * 2) {
			x = Math.min(x, screenWidth - WIDTH - KNOB_OVERHANG);
		}
		if (screenHeight >= HEIGHT + 2) {
			y = Math.min(y, screenHeight - HEIGHT - 1);
		}
		return new Position(x, y);
	}

	void render(GuiGraphicsExtractor graphics, Font font, double mouseX, double mouseY) {
		this.hovered = contains(mouseX, mouseY);
		long now = System.nanoTime();
		long elapsed = this.lastFrameNanos == 0L ? 0L : Math.max(0L, now - this.lastFrameNanos);
		this.lastFrameNanos = now;
		float target = this.hovered || this.dragging ? ACTIVE_OPACITY : IDLE_OPACITY;
		float blend = elapsed <= 0L ? 1.0F : Math.min(1.0F, elapsed / (float) FADE_NANOS);
		this.opacity += (target - this.opacity) * blend;

		int track = withAlpha(0xFF363646, this.opacity);
		int fill = withAlpha(PvDraw.COLOR_ACCENT, this.opacity);
		int knob = withAlpha(0xFFE8E8F0, this.opacity);
		int centerY = this.y + HEIGHT / 2;
		PvDraw.fill(graphics, this.x, centerY - 1, WIDTH, 3, track);
		int knobCenter = this.x + Math.round((WIDTH - 1) * progress());
		PvDraw.fill(graphics, this.x, centerY - 1, Math.max(1, knobCenter - this.x), 3, fill);
		PvDraw.fill(graphics, knobCenter - 2, this.y - 1, 5, HEIGHT + 2, knob);
		if (this.hovered || this.dragging) {
			String label = BetterPVConfig.profileViewerScalePercent() + "%  RMB reset";
			PvDraw.textRight(graphics, font, label, this.x + WIDTH, this.y - font.lineHeight - 3, withAlpha(PvDraw.COLOR_TEXT, this.opacity));
		}
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		if (button == 1) {
			BetterPVConfig.setProfileViewerScalePercent(100);
			this.dragging = false;
			return true;
		}
		if (button != 0) {
			return false;
		}
		this.dragging = true;
		setFromMouse(mouseX);
		return true;
	}

	boolean mouseDragged(double mouseX) {
		if (!this.dragging) {
			return false;
		}
		setFromMouse(mouseX);
		return true;
	}

	boolean mouseReleased(int button) {
		if (button != 0 || !this.dragging) {
			return false;
		}
		this.dragging = false;
		return true;
	}

	boolean dragging() {
		return this.dragging;
	}

	private boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.x - HIT_PAD && mouseX <= this.x + WIDTH + HIT_PAD
			&& mouseY >= this.y - HIT_PAD && mouseY <= this.y + HEIGHT + HIT_PAD;
	}

	private void setFromMouse(double mouseX) {
		BetterPVConfig.setProfileViewerScalePercent(percentAt(mouseX, this.x));
	}

	static int percentAt(double mouseX, int trackX) {
		float raw = (float) ((mouseX - trackX) / WIDTH);
		return 75 + Math.round(Math.max(0.0F, Math.min(1.0F, raw)) * 15.0F) * 5;
	}

	private float progress() {
		return (BetterPVConfig.profileViewerScalePercent() - 75) / 75.0F;
	}

	private static int withAlpha(int argb, float opacity) {
		int sourceAlpha = (argb >>> 24) & 0xFF;
		int alpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * opacity)));
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	record Position(int x, int y) {
	}
}
