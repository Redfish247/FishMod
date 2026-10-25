/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.essence;

import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.dungeons.EssencePerkTips;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Essence shop column: icon, name and balance header, then one row per perk.
 * Packs rows into the given height instead of shrinking the font.
 */
public final class EssenceShopColumn {
	public static final int MAXED_COLOR = 0xFF7CFF9A;

	@FunctionalInterface
	public interface HoverSink {
		void add(int x, int y, int w, int h, List<PvTooltip.Line> lines);
	}

	private EssenceShopColumn() {
	}

	public static void draw(
		GuiGraphicsExtractor g,
		Font font,
		DungeonSnapshot.EssenceShop shop,
		int x,
		int y,
		int w,
		int h,
		int headerColor,
		int rowGap,
		HoverSink hovers
	) {
		if (w <= 0 || h <= 0 || shop == null) {
			return;
		}
		List<DungeonSnapshot.EssencePerk> perks = shop.perks();
		int headerH = Math.max(16, font.lineHeight + 2);
		int n = perks.size();
		int bodyH = Math.max(1, h - headerH - 2);
		int packed = n <= 0 ? font.lineHeight + 2 : bodyH / n;
		int rowH = Math.min(16 + Math.max(0, rowGap), Math.max(font.lineHeight, packed));

		g.enableScissor(x, y, x + w + 3, y + h);

		String name = shop.name() == null ? "" : shop.name();
		String bal = balance(shop.balance());
		int headerLabelX = 16 + 3;
		int avail = Math.max(1, w - headerLabelX);
		int nameW = PvDraw.widthBold(font, name);
		int balW = PvDraw.widthBold(font, bal);
		int gapNameBal = 4;
		float headerScale = 1F;
		float needed = (nameW + gapNameBal + balW) * headerScale;
		if (needed > avail) {
			headerScale = Math.max(0.7F, avail / (float) (nameW + gapNameBal + balW));
		}
		int textH = Math.max(1, Math.round(font.lineHeight * headerScale));
		PvDraw.IconTextAlign headerAlign = PvDraw.IconTextAlign.of(0, headerH, 16, textH);
		g.item(EssenceIcons.shop(shop.iconId()), x, y + headerAlign.iconY());
		int drawBalW = Math.max(1, Math.round(balW * headerScale));
		if (headerScale >= 0.999F) {
			PvDraw.textBold(g, font, name, x + headerLabelX, y + headerAlign.textY(), headerColor);
			PvDraw.textBold(g, font, bal, x + w - balW, y + headerAlign.textY(), headerColor);
		} else {
			PvDraw.textBoldScaled(g, font, name, x + headerLabelX, y + headerAlign.textY(), headerColor, headerScale);
			PvDraw.textBoldScaled(g, font, bal, x + w - drawBalW, y + headerAlign.textY(), headerColor, headerScale);
		}

		int ly = y + headerH + 2;
		int labelX = 16 + 2;
		for (DungeonSnapshot.EssencePerk perk : perks) {
			int iconSize = Math.min(16, rowH);
			int iconY = ly + Math.max(0, (rowH - iconSize) / 2);
			if (iconSize >= 16) {
				g.item(EssenceIcons.perk(perk.id()), x, iconY);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(x, iconY);
				float s = iconSize / 16F;
				g.pose().scale(s, s);
				g.item(EssenceIcons.perk(perk.id()), 0, 0);
				g.pose().popMatrix();
			}
			String right = perk.level() + "/" + perk.maxLevel();
			int rightW = font.width(right);
			String left = perk.name();
			int nameAvail = Math.max(4, w - labelX - rightW - 2);
			if (font.width(left) > nameAvail) {
				left = trimToWidth(font, left, nameAvail);
			}
			int textY = ly + Math.max(0, (rowH - font.lineHeight) / 2);
			int valueColor = perk.maxed() ? MAXED_COLOR : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, left, x + labelX, textY, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, textY, valueColor);
			if (hovers != null) {
				hovers.add(x, ly, w, rowH, EssencePerkTips.tip(perk));
			}
			ly += rowH;
		}

		g.disableScissor();
	}

	public static String balance(long balance) {
		if (balance < 1000L) {
			return FormatUtil.commas(balance);
		}
		String k = FormatUtil.oneDecimal(balance / 1000D);
		if (k.endsWith(".0")) {
			k = k.substring(0, k.length() - 2);
		}
		return k + "k";
	}

	private static String trimToWidth(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}
}
