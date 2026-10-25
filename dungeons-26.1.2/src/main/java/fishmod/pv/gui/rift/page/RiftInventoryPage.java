/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift.page;

import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.RiftSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.rift.RiftUi;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import static fishmod.pv.gui.rift.RiftUi.*;

/** Rift inventory: player inventory + ender chest pager. */
public final class RiftInventoryPage {
	private int enderPage;

	public void resetPage() {
		this.enderPage = 0;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, RiftSnapshot snapshot) {
		int total = Math.max(1, snapshot.enderPages().size());
		if (total <= 1 || scrollY == 0) {
			return false;
		}
		int next = this.enderPage - (int) Math.signum(scrollY);
		next = Math.max(0, Math.min(total - 1, next));
		if (next == this.enderPage) {
			return false;
		}
		this.enderPage = next;
		return true;
	}

	public void render(
		RiftSnapshot snapshot, RiftUi ui,
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		int leftW = (w - GAP) / 2;
		int rightW = w - leftW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		int titleY = y + PAD;
		PvDraw.text(g, font, "Inventory", leftX + PAD, titleY, PvDraw.COLOR_TEXT);

		int contentTop = titleY + font.lineHeight + PREVIEW_TOP_PAD();
		drawPlayerInventory(
			ui, g, font,
			leftX + PAD, contentTop,
			leftW - PAD * 2, h - (contentTop - y) - PAD,
			snapshot.inventory().slots(), mx, my
		);

		List<InventorySnapshot.Page> pages = snapshot.enderPages();
		int total = Math.max(1, pages.size());
		this.enderPage = Math.max(0, Math.min(this.enderPage, total - 1));
		InventorySnapshot.Page page = pages.get(this.enderPage);

		PvDraw.text(g, font, "Ender Chest", rightX + PAD, titleY, PvDraw.COLOR_TEXT);
		int gridTop = contentTop;
		int pagerY = y + h - PAD - PAGE_BTN;
		int gridBottom = pagerY - 4;
		drawGrid(
			ui, g, font,
			rightX + PAD, gridTop,
			rightW - PAD * 2, Math.max(SLOT, gridBottom - gridTop),
			page.columns(), page.slots(), mx, my
		);
		drawPager(ui, g, font, rightX + PAD, pagerY, rightW - PAD * 2, this.enderPage, total, mx, my, page.title());
	}

	private static int PREVIEW_TOP_PAD() {
		return 6;
	}

	private void drawPager(
		RiftUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int page, int total,
		int mx, int my, String centerLabel
	) {
		int prevX = x;
		int nextX = x + w - PAGE_BTN;
		drawPageButton(ui, g, font, prevX, y, "<", mx, my, page > 0, () -> this.enderPage = Math.max(0, page - 1));
		drawPageButton(ui, g, font, nextX, y, ">", mx, my, page < total - 1,
			() -> this.enderPage = Math.min(total - 1, page + 1));
		String label = centerLabel == null || centerLabel.isBlank()
			? (page + 1) + " / " + total
			: (page + 1) + " / " + total;
		int labelMax = Math.max(40, nextX - prevX - PAGE_BTN - 12);
		if (font.width(label) > labelMax) {
			label = RiftUi.trim(font, label, labelMax);
		}
		PvDraw.textCentered(g, font, label, x + w / 2, y + (PAGE_BTN - font.lineHeight) / 2, PvDraw.COLOR_MUTED);
	}

	private void drawPageButton(
		RiftUi ui, GuiGraphicsExtractor g, Font font, int x, int y, String label,
		int mx, int my, boolean enabled, Runnable action
	) {
		boolean hovered = enabled && mx >= x && mx < x + PAGE_BTN && my >= y && my < y + PAGE_BTN;
		int bg = !enabled ? PvDraw.CELL : hovered ? 0x50386CC8 : PvDraw.CELL;
		int border = hovered ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		PvDraw.roundedFill(g, x, y, PAGE_BTN, PAGE_BTN, 3, bg);
		PvDraw.roundedRing(g, x, y, PAGE_BTN, PAGE_BTN, 3, border, PvDraw.C_ALL);
		PvDraw.textCentered(g, font, label, x + PAGE_BTN / 2, y + (PAGE_BTN - font.lineHeight) / 2,
			enabled ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
		if (enabled) {
			ui.hits.add(new RiftUi.RunnableHit(x, y, PAGE_BTN, PAGE_BTN, action));
		}
	}

	private void drawPlayerInventory(
		RiftUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
		List<InventorySnapshot.Slot> slots, int mouseX, int mouseY
	) {
		InventorySnapshot.Slot[] equipment = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] armor = new InventorySnapshot.Slot[4];
		InventorySnapshot.Slot[] hotbar = new InventorySnapshot.Slot[9];
		InventorySnapshot.Slot[] main = new InventorySnapshot.Slot[27];
		boolean hasEquipment = slots.size() >= 44;
		int armorOff = hasEquipment ? 4 : 0;
		int invOff = armorOff + 4;
		for (int i = 0; i < 4; i++) {
			equipment[i] = hasEquipment && i < slots.size() ? slots.get(i) : null;
			armor[i] = armorOff + i < slots.size() ? slots.get(armorOff + i) : null;
		}
		for (int i = 0; i < 36; i++) {
			InventorySnapshot.Slot slot = invOff + i < slots.size() ? slots.get(invOff + i) : null;
			if (i < 9) {
				hotbar[i] = slot;
			} else {
				main[i - 9] = slot;
			}
		}

		int armorH = 4 * SLOT + 3 * SLOT_GAP;
		int mainH = 3 * SLOT + 2 * SLOT_GAP;
		int totalH = Math.max(armorH, mainH + HOTBAR_GAP + SLOT);
		int mainW = 9 * SLOT + 8 * SLOT_GAP;
		int gearW = SLOT + COL_GAP + SLOT;
		int totalW = gearW + ARMOR_GAP + mainW;
		int startX = x + Math.max(0, (w - totalW) / 2);
		int startY = y + Math.max(0, (h - totalH) / 2);

		int eqX = startX;
		int armorX = eqX + SLOT + COL_GAP;
		int bodyX = startX + gearW + ARMOR_GAP;
		int bodyY = startY;
		int hotbarY = bodyY + mainH + HOTBAR_GAP;

		for (int i = 0; i < 4; i++) {
			int sy = startY + i * (SLOT + SLOT_GAP);
			drawSlot(ui, g, font, eqX, sy, equipment[i], mouseX, mouseY);
			drawSlot(ui, g, font, armorX, sy, armor[i], mouseX, mouseY);
		}
		for (int i = 0; i < 27; i++) {
			int col = i % 9;
			int row = i / 9;
			drawSlot(ui, g, font, bodyX + col * (SLOT + SLOT_GAP), bodyY + row * (SLOT + SLOT_GAP), main[i], mouseX, mouseY);
		}
		for (int i = 0; i < 9; i++) {
			drawSlot(ui, g, font, bodyX + i * (SLOT + SLOT_GAP), hotbarY, hotbar[i], mouseX, mouseY);
		}

		boolean empty = true;
		for (InventorySnapshot.Slot s : slots) {
			if (s != null && !s.isEmpty()) {
				empty = false;
				break;
			}
		}
		if (empty) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, y + Math.min(h / 2, totalH / 2) - font.lineHeight / 2,
				PvDraw.COLOR_MUTED);
		}
	}

	private void drawGrid(
		RiftUi ui, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
		int columns, List<InventorySnapshot.Slot> slots, int mouseX, int mouseY
	) {
		int cols = Math.max(1, columns);
		int rows = Math.max(1, (slots.size() + cols - 1) / cols);
		int maxRows = Math.max(1, (h + SLOT_GAP) / (SLOT + SLOT_GAP));
		rows = Math.min(rows, maxRows);
		int gridW = cols * SLOT + (cols - 1) * SLOT_GAP;
		int gridH = rows * SLOT + (rows - 1) * SLOT_GAP;
		int startX = x + Math.max(0, (w - gridW) / 2);
		int startY = y + Math.max(0, (h - gridH) / 2);
		int shown = Math.min(slots.size(), cols * rows);
		for (int i = 0; i < shown; i++) {
			int col = i % cols;
			int row = i / cols;
			drawSlot(ui, g, font, startX + col * (SLOT + SLOT_GAP), startY + row * (SLOT + SLOT_GAP),
				slots.get(i), mouseX, mouseY);
		}
		if (slots.isEmpty() || slots.stream().allMatch(s -> s == null || s.isEmpty())) {
			PvDraw.textCentered(g, font, "Empty", x + w / 2, startY + SLOT, PvDraw.COLOR_MUTED);
		}
	}

	private void drawSlot(
		RiftUi ui, GuiGraphicsExtractor g, Font font, int sx, int sy, InventorySnapshot.Slot slot, int mouseX, int mouseY
	) {
		boolean hovered = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
		PvDraw.roundedFill(g, sx, sy, SLOT, SLOT, 3, hovered ? 0x30FFFFFF : ITEM_SLOT_BG);
		PvDraw.roundedRing(g, sx, sy, SLOT, SLOT, 3, hovered ? PvDraw.COLOR_ACCENT : ITEM_SLOT_BORDER, PvDraw.C_ALL);
		if (slot == null || slot.isEmpty()) {
			return;
		}
		ItemStack stack = SkyBlockItemFactory.toStack(slot);
		RiftUi.drawItemIcon(g, stack, slot.id(), sx + 1, sy + 1, 16);
		if (slot.count() > 1 && slot.count() <= 64) {
			PvDraw.textRight(g, font, String.valueOf(slot.count()), sx + SLOT - 1, sy + SLOT - font.lineHeight,
				PvDraw.COLOR_TEXT);
		}
		if (hovered) {
			ui.hoveredSlot = slot;
			ui.hoveredStack = stack;
		}
	}
}
