/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.slayer;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.gui.PvDraw;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Centered slayer XP calculator overlay, matching dungeon calc chrome. */
public final class SlayerCalcOverlay {
	private static final int PAD = 8;
	private static final int CLOSE_SIZE = 12;
	private static final int FIELD_H = 14;
	private static final int OPEN_MS = 220;
	private static final float OPEN_SCALE_START = 0.12F;
	private static final int COLOR_LABEL = 0xFF9A9AAC;
	private static final int COLOR_VALUE = 0xFFE8E8F0;
	private static final int COLOR_COIN = 0xFFFFAA00;
	private static final int COLOR_HEADER = 0xFF5B8CFF;

	private boolean open;
	private long openStartMs;
	private ProfileSnapshot.SlayerEntry slayer;
	private SlayerMayorMods mods = SlayerMayorMods.none();
	private String levelText = "";
	private boolean replaceOnType;
	private boolean fieldFocus;
	private int accent;

	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int closeX;
	private int closeY;
	private int fieldX;
	private int fieldY;
	private int fieldW;

	public boolean isOpen() {
		return this.open;
	}

	public void open(ProfileSnapshot.SlayerEntry slayer, SlayerMayorMods mods, int accent) {
		this.slayer = slayer;
		this.mods = mods == null ? SlayerMayorMods.none() : mods;
		this.accent = accent == 0 ? PvDraw.COLOR_ACCENT : accent;
		this.open = slayer != null;
		this.openStartMs = System.currentTimeMillis();
		int max = SlayerXpCalculator.maxLevel(slayer == null ? "" : slayer.id());
		int next = Math.min(max, Math.max(1, (slayer == null ? 0 : slayer.tier()) + 1));
		this.levelText = String.valueOf(next);
		this.replaceOnType = true;
		this.fieldFocus = true;
	}

	public void close() {
		this.open = false;
		this.slayer = null;
		this.fieldFocus = false;
		this.replaceOnType = false;
	}

	public void render(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		if (!this.open || this.slayer == null) {
			return;
		}
		SlayerXpCalculator.Result result = currentResult();
		int lineH = font.lineHeight + 3;
		int bossH = Math.max(16, font.lineHeight) + 6;
		List<String> modLabels = result.mods().labels();
		int modsH = modLabels.isEmpty() ? 0 : modLabels.size() * (font.lineHeight + 2) + 4;
		int contentH = PAD
			+ font.lineHeight + 8
			+ bossH
			+ FIELD_H + 8
			+ lineH
			+ modsH
			+ lineH
			+ result.tiers().size() * lineH
			+ PAD;
		this.panelW = Math.min(300, Math.max(220, screenW / 3));
		this.panelH = Math.min(contentH, screenH - 36);
		this.panelX = (screenW - this.panelW) / 2;
		this.panelY = (screenH - this.panelH) / 2;

		PvDraw.fill(g, 0, 0, screenW, screenH, 0x88000000);

		float scale = openScale();
		float cx = this.panelX + this.panelW / 2F;
		float cy = this.panelY + this.panelH / 2F;
		g.pose().pushMatrix();
		if (scale < 0.999F) {
			g.pose().translate(cx, cy);
			g.pose().scale(scale, scale);
			g.pose().translate(-cx, -cy);
		}

		PvDraw.overlayPanel(g, this.panelX, this.panelY, this.panelW, this.panelH);

		int x = this.panelX + PAD;
		int y = this.panelY + PAD;
		int innerW = this.panelW - PAD * 2;

		PvDraw.textBold(g, font, "Slayer Calculator", x, y, this.accent);
		this.closeX = this.panelX + this.panelW - PAD - CLOSE_SIZE;
		this.closeY = this.panelY + PAD;
		boolean closeHover = mouseX >= this.closeX && mouseX < this.closeX + CLOSE_SIZE
			&& mouseY >= this.closeY && mouseY < this.closeY + CLOSE_SIZE;
		PvDraw.fill(g, this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, closeHover ? 0xFF3A3A4A : 0xFF222230);
		g.outline(this.closeX, this.closeY, CLOSE_SIZE, CLOSE_SIZE, PvDraw.COLOR_BORDER);
		PvDraw.textCentered(g, font, "x", this.closeX + CLOSE_SIZE / 2, this.closeY + 1, PvDraw.COLOR_MUTED);
		y += font.lineHeight + 8;

		int itemY = y + Math.max(0, (Math.max(16, font.lineHeight) - 16) / 2);
		g.item(new ItemStack(result.item()), x, itemY);
		int bossTextY = y + Math.max(0, (Math.max(16, font.lineHeight) - font.lineHeight) / 2);
		PvDraw.text(g, font, "Slayer Boss:", x + 18, bossTextY, COLOR_LABEL);
		int bossX = x + 18 + font.width("Slayer Boss: ");
		PvDraw.text(
			g, font,
			PvDraw.styled(trim(font, result.bossName(), x + innerW - bossX), this.accent, true),
			bossX, bossTextY
		);
		y += bossH;

		PvDraw.text(g, font, "Target level", x, y, COLOR_LABEL);
		this.fieldW = 36;
		this.fieldX = x + innerW - this.fieldW;
		this.fieldY = y - 2;
		boolean fieldHover = mouseX >= this.fieldX && mouseX < this.fieldX + this.fieldW
			&& mouseY >= this.fieldY && mouseY < this.fieldY + FIELD_H;
		PvDraw.fill(g, this.fieldX, this.fieldY, this.fieldW, FIELD_H, 0xFF101018);
		g.outline(this.fieldX, this.fieldY, this.fieldW, FIELD_H,
			this.fieldFocus || fieldHover ? this.accent : PvDraw.COLOR_BORDER);
		String shown = this.levelText.isBlank() ? "-" : this.levelText;
		PvDraw.textCentered(g, font, shown, this.fieldX + this.fieldW / 2, this.fieldY + 2, COLOR_VALUE);
		y += FIELD_H + 8;

		PvDraw.text(g, font, "XP to reach:", x, y, COLOR_LABEL);
		int xpColor = result.xpNeeded() <= 0.5F ? 0xFF6DFF8A : COLOR_COIN;
		PvDraw.textRight(g, font, FormatUtil.commas(Math.round(result.xpNeeded())), x + innerW, y, xpColor);
		y += lineH;

		if (!modLabels.isEmpty()) {
			for (String label : modLabels) {
				PvDraw.text(g, font, PvDraw.styled(trim(font, label, innerW), PvDraw.COLOR_GOLD, false), x, y);
				y += font.lineHeight + 2;
			}
			y += 4;
		}

		PvDraw.text(g, font, "Bosses needed:", x, y, COLOR_HEADER);
		y += lineH;
		for (SlayerXpCalculator.TierLine tier : result.tiers()) {
			String left = "Tier " + roman(tier.tier()) + ": " + FormatUtil.commas(tier.bosses());
			String coins = "(" + FormatUtil.commas(tier.coins()) + " coins)";
			int coinW = font.width(coins);
			PvDraw.text(g, font, PvDraw.styled(trim(font, left, innerW - coinW - 6), tier.color(), true), x, y);
			PvDraw.textRight(g, font, coins, x + innerW, y, COLOR_COIN);
			y += lineH;
		}

		g.pose().popMatrix();
	}

	public boolean mouseClicked(double mx, double my) {
		if (!this.open) {
			return false;
		}
		if (mx >= this.closeX && mx < this.closeX + CLOSE_SIZE
			&& my >= this.closeY && my < this.closeY + CLOSE_SIZE) {
			close();
			return true;
		}
		if (mx >= this.fieldX && mx < this.fieldX + this.fieldW
			&& my >= this.fieldY && my < this.fieldY + FIELD_H) {
			this.fieldFocus = true;
			this.replaceOnType = !this.levelText.isEmpty();
			return true;
		}
		if (mx >= this.panelX && mx < this.panelX + this.panelW
			&& my >= this.panelY && my < this.panelY + this.panelH) {
			this.fieldFocus = false;
			return true;
		}
		close();
		return true;
	}

	public boolean keyPressed(int key) {
		if (!this.open) {
			return false;
		}
		if (key == 256) {
			close();
			return true;
		}
		if (!this.fieldFocus) {
			return false;
		}
		if (key == 259) {
			if (this.replaceOnType) {
				this.levelText = "";
				this.replaceOnType = false;
				return true;
			}
			if (!this.levelText.isEmpty()) {
				this.levelText = this.levelText.substring(0, this.levelText.length() - 1);
			}
			return true;
		}
		return false;
	}

	public boolean charTyped(char ch) {
		if (!this.open || !this.fieldFocus) {
			return false;
		}
		if (ch < '0' || ch > '9') {
			return true;
		}
		if (this.replaceOnType) {
			this.levelText = "";
			this.replaceOnType = false;
		}
		if (this.levelText.length() >= 2) {
			return true;
		}
		this.levelText += ch;
		clampLevelText();
		return true;
	}

	/** Hard-cap the typed target at 9 (slayer max). */
	private void clampLevelText() {
		if (this.levelText == null || this.levelText.isBlank()) {
			return;
		}
		try {
			int value = Integer.parseInt(this.levelText.trim());
			if (value > 9) {
				this.levelText = "9";
			}
		} catch (NumberFormatException ignored) {
			// keep raw text; parseLevel falls back
		}
	}

	private SlayerXpCalculator.Result currentResult() {
		int max = Math.min(9, SlayerXpCalculator.maxLevel(this.slayer.id()));
		int target = parseLevel(this.levelText, Math.min(max, this.slayer.tier() + 1), max);
		return SlayerXpCalculator.calculate(
			this.slayer.id(),
			this.slayer.name(),
			this.slayer.xp(),
			this.slayer.tier(),
			target,
			this.mods
		);
	}

	private static int parseLevel(String text, int fallback, int max) {
		if (text == null || text.isBlank()) {
			return Math.max(1, Math.min(max, fallback));
		}
		try {
			return Math.max(1, Math.min(max, Integer.parseInt(text.trim())));
		} catch (NumberFormatException ignored) {
			return Math.max(1, Math.min(max, fallback));
		}
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openStartMs;
		if (elapsed >= OPEN_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_MS));
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(n);
		};
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (maxW <= 0 || font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "…";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			if (font.width(sb.toString() + text.charAt(i)) > budget) {
				break;
			}
			sb.append(text.charAt(i));
		}
		return sb + ellipsis;
	}
}
