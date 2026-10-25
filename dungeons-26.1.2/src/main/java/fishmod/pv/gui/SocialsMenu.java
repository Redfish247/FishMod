/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import fishmod.pv.gui.ItemIcons;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** "Socials" footer label and its external profile links popup. */
final class SocialsMenu {
	static final String LABEL = "Socials";
	static final int LABEL_GAP = 10;
	static final int PAD = 6;
	static final int ICON = 10;
	static final int ICON_GAP = 4;
	static final int ROW_PAD = 4;
	static final int EDGE = 2;
	private static final SocialLinks.Site[] SITES = SocialLinks.Site.values();

	record Layout(int x, int y, int w, int h, int rowH, int nameW) {
		boolean contains(double mx, double my) {
			return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
		}

		int rowY(int index) {
			return this.y + 2 + index * this.rowH;
		}
	}

	private final Supplier<SocialLinks.Target> target;
	private final Consumer<String> opener;
	private final Map<SocialLinks.Site, ItemStack> icons = new EnumMap<>(SocialLinks.Site.class);
	private UUID headUuid;
	private boolean open;
	private int labelX;
	private int labelY;
	private int labelW;
	private int labelH;
	private int viewMinX;
	private int viewMinY;
	private int viewMaxX = Integer.MAX_VALUE / 2;
	private int viewMaxY = Integer.MAX_VALUE / 2;
	private Layout layout;

	SocialsMenu(Supplier<SocialLinks.Target> target, Consumer<String> opener) {
		this.target = target;
		this.opener = opener;
	}

	boolean isOpen() {
		return this.open;
	}

	void close() {
		this.open = false;
	}

	void setLabel(int x, int y, int w, int h) {
		this.labelX = x;
		this.labelY = y;
		this.labelW = w;
		this.labelH = h;
	}

	Layout layout() {
		return this.layout;
	}

	void setViewport(int minX, int minY, int maxX, int maxY) {
		this.viewMinX = minX;
		this.viewMinY = minY;
		this.viewMaxX = maxX;
		this.viewMaxY = maxY;
	}

	void layoutPopup(ToIntFunction<String> width, int lineHeight) {
		this.layout = popupLayout(
			this.labelX, this.labelY, this.labelH, width, lineHeight,
			this.viewMinX, this.viewMinY, this.viewMaxX, this.viewMaxY
		);
	}

	/** Opens under the label, flips above it when there is no room, and stays inside the visible area. */
	static Layout popupLayout(
		int labelX, int labelY, int labelH, ToIntFunction<String> width, int lineHeight,
		int minX, int minY, int maxX, int maxY
	) {
		int nameW = 0;
		for (SocialLinks.Site site : SITES) {
			nameW = Math.max(nameW, width.applyAsInt(site.label()));
		}
		int rowH = lineHeight + ROW_PAD;
		int w = PAD + ICON + ICON_GAP + nameW + PAD;
		int h = SITES.length * rowH + 4;
		int x = Math.max(minX + EDGE, Math.min(labelX, maxX - EDGE - w));
		int y = labelY + labelH + 2;
		if (y + h > maxY - EDGE) {
			y = labelY - 2 - h;
		}
		y = Math.max(minY + EDGE, y);
		return new Layout(x, y, w, h, rowH, nameW);
	}

	boolean labelContains(double mx, double my) {
		return this.labelW > 0
			&& mx >= this.labelX && mx < this.labelX + this.labelW
			&& my >= this.labelY && my < this.labelY + this.labelH;
	}

	/** True while the open popup (or its label) owns the mouse, so nothing underneath reacts. */
	boolean blocksMouse(double mx, double my) {
		return this.open && (labelContains(mx, my) || (this.layout != null && this.layout.contains(mx, my)));
	}

	int rowAt(double mx, double my) {
		if (!this.open || this.layout == null || !this.layout.contains(mx, my)) {
			return -1;
		}
		for (int i = 0; i < SITES.length; i++) {
			int rowY = this.layout.rowY(i);
			if (my >= rowY && my < rowY + this.layout.rowH()) {
				return i;
			}
		}
		return -1;
	}

	/** Consumes every click while open, so outside clicks only close the popup. */
	boolean mouseClicked(double mx, double my, int button) {
		if (labelContains(mx, my)) {
			if (button == 0) {
				this.open = !this.open;
			}
			return true;
		}
		if (!this.open) {
			return false;
		}
		int row = rowAt(mx, my);
		if (row >= 0) {
			if (button != 0) {
				return true;
			}
			String url = SocialLinks.url(SITES[row], this.target.get());
			if (url == null) {
				return true;
			}
			this.open = false;
			this.opener.accept(url);
			return true;
		}
		if (this.layout == null || !this.layout.contains(mx, my)) {
			this.open = false;
		}
		return true;
	}

	boolean mouseScrolled(double mx, double my) {
		return blocksMouse(mx, my);
	}

	boolean keyPressed(int key) {
		if (this.open && key == 256) {
			this.open = false;
			return true;
		}
		return false;
	}

	void renderLabel(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY) {
		String arrow = this.open ? " ▲" : " ▼";
		int textW = font.width(LABEL);
		setLabel(x, y, textW + font.width(arrow), font.lineHeight);
		boolean hover = labelContains(mouseX, mouseY);
		int color = hover || this.open ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED;
		PvDraw.text(g, font, LABEL, x, y, color);
		PvDraw.text(g, font, arrow, x + textW, y, color);
	}

	void renderPopup(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		if (!this.open) {
			return;
		}
		layoutPopup(font::width, font.lineHeight);
		Layout l = this.layout;
		PvDraw.popupSurface(g, l.x(), l.y(), l.w(), l.h());
		SocialLinks.Target viewed = this.target.get();
		int hoverRow = rowAt(mouseX, mouseY);
		int nameX = l.x() + PAD + ICON + ICON_GAP;
		for (int i = 0; i < SITES.length; i++) {
			SocialLinks.Site site = SITES[i];
			int rowY = l.rowY(i);
			boolean available = SocialLinks.url(site, viewed) != null;
			if (available && i == hoverRow) {
				PvDraw.fill(g, l.x() + 1, rowY, l.w() - 2, l.rowH(), 0x33FFFFFF);
			}
			ItemIcons.drawItemIcon(g, icon(site, viewed), l.x() + PAD, rowY + (l.rowH() - ICON) / 2, ICON);
			int nameColor = !available ? PvDraw.COLOR_MUTED : i == hoverRow ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, site.label(), nameX, rowY + ROW_PAD / 2, nameColor);
		}
	}

	private ItemStack icon(SocialLinks.Site site, SocialLinks.Target viewed) {
		UUID uuid = viewed == null ? null : viewed.uuid();
		if (site == SocialLinks.Site.SKYCRYPT && (uuid == null ? this.headUuid != null : !uuid.equals(this.headUuid))) {
			this.icons.remove(site);
			this.headUuid = uuid;
		}
		return this.icons.computeIfAbsent(site, s -> switch (s) {
			case PLANCKE -> new ItemStack(Items.BOOK);
			case SKYCRYPT -> ProfileSelectorController.coopMemberHead(uuid);
			case ELITEBOT -> new ItemStack(Items.WHEAT);
			case COFLNET -> new ItemStack(Items.GOLD_INGOT);
			case NAMEMC -> new ItemStack(Items.NAME_TAG);
		});
	}
}
