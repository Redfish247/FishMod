/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.pets;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.PetListRules;
import fishmod.pv.data.PetLoreResolver;
import fishmod.pv.data.PetSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.networth.InventoryDecoder;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Left ~56% compact pet inventory + right ~44% selected-pet details. */
public final class PetsPage {
	private static final int GAP = 8;
	private static final int PAD = 8;
	/** Fixed Minecraft-inventory-style cell; 16px icons fill this without upscaling. */
	private static final int SLOT_SIZE = 22;
	private static final int SLOT_GAP = 2;
	private static final int ITEM_ICON = 16;
	private static final int SELECTED_BORDER = PvDraw.COLOR_ACCENT;
	private static final int ACTIVE_BORDER = 0xFF55FF55;

	private static final int MISSING_BG = 0xFF3A1010;
	private static final int MISSING_BORDER = 0xFFCC3333;

	private PetSnapshot snapshot = PetSnapshot.empty();
	private List<PetLoreResolver.PetType> missingPets = List.of();
	private int selected = -1;
	private int scroll;
	private int gridY;
	private int gridH;
	private int maxScroll;
	private int cols = 1;
	private int slotSize = SLOT_SIZE;
	private final List<SlotHit> hits = new ArrayList<>();
	private int xpBarX;
	private int xpBarY;
	private int xpBarW;
	private int xpBarH;
	private String xpHover;
	private int maxXpBarX;
	private int maxXpBarY;
	private int maxXpBarW;
	private int maxXpBarH;
	private String maxXpHover;
	private List<Component> hoverPetTip;
	private List<PvTooltip.Line> autopetTip;

	private enum SortMode {
		XP("XP"),
		LEVEL("Lvl"),
		NAME("Name"),
		NETWORTH("NW"),
		RARITY("Rarity");

		private final String label;

		SortMode(String label) {
			this.label = label;
		}

		SortMode next() {
			SortMode[] values = values();
			return values[(ordinal() + 1) % values.length];
		}
	}

	private SortMode sortMode = SortMode.RARITY;
	private List<PetSnapshot.Entry> sorted = List.of();
	private int sortHitX;
	private int sortHitY;
	private int sortHitW;
	private int sortHitH;

	public void apply(PetSnapshot snapshot) {
		this.snapshot = snapshot == null ? PetSnapshot.empty() : snapshot;
		this.scroll = 0;
		SkyBlockItemFactory.prefetchPets(this.snapshot);
		java.util.Set<String> owned = new java.util.HashSet<>();
		for (PetSnapshot.Entry pet : this.snapshot.pets()) {
			owned.add(pet.type().toUpperCase(Locale.ROOT));
		}
		List<PetLoreResolver.PetType> missing = new ArrayList<>();
		for (PetLoreResolver.PetType type : PetLoreResolver.petTypes()) {
			if (!owned.contains(type.type()) && PetListRules.showAsMissing(type.type(), owned)) {
				missing.add(type);
			}
		}
		this.missingPets = missing;
		this.sorted = sortPets(PetListRules.visibleOwned(this.snapshot.pets()), this.sortMode);
		this.selected = this.sorted.isEmpty() ? -1 : 0;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH
	) {
		this.hits.clear();
		this.xpHover = null;
		this.xpBarW = 0;
		this.maxXpHover = null;
		this.maxXpBarW = 0;
		this.hoverPetTip = null;
		this.autopetTip = null;
		// Compact pets grid (~56%) + wider details inspector (~44%).
		int leftW = Math.max(140, (int) Math.round(w * 0.70));
		int rightW = Math.max(120, w - leftW - GAP);
		leftW = w - rightW - GAP;
		int rightX = x + leftW + GAP;

		PvDraw.innerPanel(g, x, y, leftW, h);
		PvDraw.innerPanel(g, rightX, y, rightW, h);

		drawGrid(g, font, x, y, leftW, h, mouseX, mouseY);
		drawStats(g, font, rightX, y, rightW, h);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.xpHover != null
			&& mouseX >= this.xpBarX && mouseX < this.xpBarX + this.xpBarW
			&& mouseY >= this.xpBarY && mouseY < this.xpBarY + this.xpBarH) {
			PvTooltip.draw(g, font, List.of(this.xpHover), mouseX, mouseY, screenW, screenH);
		} else if (this.maxXpHover != null
			&& mouseX >= this.maxXpBarX && mouseX < this.maxXpBarX + this.maxXpBarW
			&& mouseY >= this.maxXpBarY && mouseY < this.maxXpBarY + this.maxXpBarH) {
			PvTooltip.draw(g, font, List.of(this.maxXpHover), mouseX, mouseY, screenW, screenH);
		} else if (this.autopetTip != null) {
			PvTooltip.drawStyled(g, font, this.autopetTip, mouseX, mouseY, screenW, screenH);
		} else if (this.hoverPetTip != null) {
			PvTooltip.drawComponents(g, font, this.hoverPetTip, mouseX, mouseY, screenW, screenH);
		}
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx >= this.sortHitX && mx < this.sortHitX + this.sortHitW
			&& my >= this.sortHitY && my < this.sortHitY + this.sortHitH) {
			PetSnapshot.Entry current = this.selected >= 0 && this.selected < this.sorted.size()
				? this.sorted.get(this.selected)
				: null;
			this.sortMode = this.sortMode.next();
			this.sorted = sortPets(this.sorted, this.sortMode);
			this.selected = current == null ? this.selected : this.sorted.indexOf(current);
			return true;
		}
		for (SlotHit hit : this.hits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				this.selected = hit.index;
				return true;
			}
		}
		return false;
	}

	public boolean mouseScrolled(double scrollY) {
		if (this.maxScroll <= 0) {
			return false;
		}
		int before = this.scroll;
		int step = this.slotSize + SLOT_GAP;
		if (scrollY > 0) {
			this.scroll = Math.max(0, this.scroll - step);
		} else if (scrollY < 0) {
			this.scroll = Math.min(this.maxScroll, this.scroll + step);
		}
		return this.scroll != before;
	}

	private void drawGrid(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY) {
		PvDraw.text(g, font, "Pets", x + PAD, y + 6, PvDraw.COLOR_TEXT);
		String sortLabel = "Sort: " + this.sortMode.label;
		int sortX = x + w - PAD - font.width(sortLabel);
		boolean sortHover = mouseX >= sortX && mouseX < x + w - PAD
			&& mouseY >= y + 6 && mouseY < y + 6 + font.lineHeight;
		PvDraw.text(g, font, sortLabel, sortX, y + 6, sortHover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED);
		this.sortHitX = sortX;
		this.sortHitY = y + 6;
		this.sortHitW = font.width(sortLabel);
		this.sortHitH = font.lineHeight;
		int summaryY = y + 6 + font.lineHeight + 2;
		boolean showSummary = this.snapshot.highestPetScore() > 0
			|| !this.snapshot.sacrificedTypes().isEmpty()
			|| this.snapshot.autopetRuleCount() > 0
			|| this.snapshot.autopetRulesLimit() > 0;
		if (showSummary) {
			String scorePart = "Score " + FormatUtil.commas(this.snapshot.highestPetScore());
			String autopetPart = "Autopet " + this.snapshot.autopetRuleCount()
				+ "/" + Math.max(this.snapshot.autopetRulesLimit(), this.snapshot.autopetRuleCount());
			int x0 = x + PAD;
			int y0 = summaryY;
			PvDraw.text(g, font, scorePart, x0, y0, PvDraw.COLOR_MUTED);
			int autopetX = x0 + font.width(scorePart + " · ");
			PvDraw.text(g, font, " · " + autopetPart, autopetX - font.width(" · "), y0, PvDraw.COLOR_MUTED);

			if (mouseY >= y0 && mouseY < y0 + font.lineHeight) {
				if (mouseX >= autopetX && mouseX < x + w - PAD) {
					List<PvTooltip.Line> tip = new ArrayList<>();
					tip.add(PvTooltip.Line.title("Autopet", PvDraw.COLOR_TEXT));
					tip.add(PvTooltip.Line.divider());
					tip.add(PvTooltip.Line.row("Rules", PvDraw.COLOR_MUTED,
						this.snapshot.autopetRuleCount() + "/"
							+ Math.max(this.snapshot.autopetRulesLimit(), this.snapshot.autopetRuleCount()),
						PvDraw.COLOR_ACCENT));
					if (this.snapshot.autopetRules().isEmpty()) {
						tip.add(PvTooltip.Line.meta("No rules set"));
					} else {
						int shown = 0;
						for (PetSnapshot.AutopetRule rule : this.snapshot.autopetRules()) {
							if (shown >= 8) {
								tip.add(PvTooltip.Line.blank());
								tip.add(PvTooltip.Line.meta(
									"+" + (this.snapshot.autopetRules().size() - shown) + " more"));
								break;
							}
							tip.add(PvTooltip.Line.blank());
							String pet = rule.petName().isBlank() ? "Unknown pet" : rule.petName();
							int petColor = rule.disabled() ? PvDraw.COLOR_MUTED : PvDraw.COLOR_GOLD;
							tip.add(PvTooltip.Line.title(pet, petColor));
							StringBuilder sub = new StringBuilder(rule.trigger());
							if (!rule.detail().isBlank()) {
								sub.append(" · ").append(rule.detail());
							}
							if (rule.disabled()) {
								sub.append(" · Off");
							}
							tip.add(PvTooltip.Line.meta(sub.toString()));
							shown++;
						}
					}
					this.autopetTip = tip;
				}
			}
		}
		// Fixed top-left origin - never shift up / left / right when slot size changes.
		int gridX = x + PAD;
		this.gridY = showSummary
			? summaryY + font.lineHeight + 4
			: y + 6 + font.lineHeight + 6;
		int gridW = w - PAD * 2;
		this.gridH = Math.max(0, y + h - PAD - this.gridY);

		List<PetSnapshot.Entry> pets = sortedPets();
		if (pets.isEmpty()) {
			PvDraw.textCentered(g, font, "No pets", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			this.maxScroll = 0;
			return;
		}

		this.slotSize = SLOT_SIZE;
		this.cols = Math.max(1, (gridW + SLOT_GAP) / (this.slotSize + SLOT_GAP));
		int total = pets.size() + this.missingPets.size();
		int rows = (total + this.cols - 1) / this.cols;
		int contentH = rows * this.slotSize + Math.max(0, rows - 1) * SLOT_GAP;
		this.maxScroll = Math.max(0, contentH - this.gridH);
		this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll));

		g.enableScissor(gridX, this.gridY, gridX + gridW + 3, this.gridY + this.gridH);
		for (int i = 0; i < total; i++) {
			int col = i % this.cols;
			int row = i / this.cols;
			int sx = gridX + col * (this.slotSize + SLOT_GAP);
			int sy = this.gridY + row * (this.slotSize + SLOT_GAP) - this.scroll;
			if (sy + this.slotSize < this.gridY || sy > this.gridY + this.gridH) {
				continue;
			}
			if (i < pets.size()) {
				drawSlot(g, font, pets.get(i), i, sx, sy, mouseX, mouseY);
			} else {
				drawMissingSlot(g, this.missingPets.get(i - pets.size()), sx, sy, mouseX, mouseY);
			}
		}
		g.disableScissor();
	}

	private List<PetSnapshot.Entry> sortedPets() {
		return this.sorted;
	}

	private static List<PetSnapshot.Entry> sortPets(List<PetSnapshot.Entry> source, SortMode mode) {
		java.util.Comparator<PetSnapshot.Entry> byName = java.util.Comparator.comparing(
			(PetSnapshot.Entry p) -> p.displayName().toLowerCase(java.util.Locale.ROOT));
		java.util.Comparator<PetSnapshot.Entry> byXp = java.util.Comparator
			.comparingDouble(PetSnapshot.Entry::exp).reversed();
		List<PetSnapshot.Entry> pets = new ArrayList<>(source);
		pets.sort(switch (mode) {
			case NAME -> byName;
			case NETWORTH -> java.util.Comparator.comparingDouble(PetSnapshot.Entry::networth).reversed()
				.thenComparing(byName);
			case RARITY -> java.util.Comparator.comparingInt(PetSnapshot.Entry::tierIndex).reversed()
				.thenComparing(byXp)
				.thenComparing(byName);
			case LEVEL -> java.util.Comparator.comparingInt(PetSnapshot.Entry::level).reversed()
				.thenComparing(byName);
			case XP -> byXp.thenComparing(byName);
		});
		return List.copyOf(pets);
	}

	private void drawSlot(
		GuiGraphicsExtractor g,
		Font font,
		PetSnapshot.Entry pet,
		int index,
		int sx,
		int sy,
		int mouseX,
		int mouseY
	) {
		int slot = this.slotSize;
		boolean selected = index == this.selected;
		boolean inGrid = mouseY >= this.gridY && mouseY < this.gridY + this.gridH
			&& mouseX >= sx && mouseX < sx + slot;
		boolean hovered = inGrid && mouseY >= sy && mouseY < sy + slot;
		int rarity = SkyBlockItemFactory.tierArgb(pet.tier());
		int bg = raritySlotBackground(rarity, pet.active(), selected, hovered);
		int border = selected ? SELECTED_BORDER : pet.active() ? ACTIVE_BORDER : hovered ? PvDraw.COLOR_ACCENT : 0x00FFFFFF;
		PvDraw.roundedFill(g, sx, sy, slot, slot, 3, bg);
		PvDraw.roundedRing(g, sx, sy, slot, slot, 3, border, PvDraw.C_ALL);

		ItemStack icon = petIcon(pet);
		// Native 16px only - fractional upscale softens skull / pet textures.
		int draw = ITEM_ICON;
		int ix = sx + (slot - draw) / 2;
		// Nudge icon up 1px so level "100" sits in the bottom band without crowding.
		int iy = sy + Math.max(1, (slot - draw) / 2 - 1);
		SkyBlockIconRenderer.draw(g, icon, pet.neuId(), ix, iy, draw);

		String level = String.valueOf(pet.level());
		float levelScale = 0.6F;
		int levelW = Math.round(font.width(level) * levelScale);
		int levelY = sy + slot - Math.round(font.lineHeight * levelScale);
		int levelRight = sx + slot - 1;
		PvDraw.textScaled(g, font, level, levelRight - levelW + 1, levelY, 0xFFFFFFFF, levelScale);

		int hitTop = Math.max(sy, this.gridY);
		int hitBottom = Math.min(sy + slot, this.gridY + this.gridH);
		if (hitBottom > hitTop) {
			this.hits.add(new SlotHit(sx, hitTop, slot, hitBottom - hitTop, index));
		}
		if (hovered) {
			InventorySnapshot.Slot petSlot = new InventorySnapshot.Slot(
				pet.neuId(),
				1,
				PetLoreResolver.loreFor(pet),
				PetLoreResolver.displayNameFor(pet),
				null,
				null,
				null
			);
			ItemStack petStack = SkyBlockItemFactory.toStack(petSlot);
			this.hoverPetTip = SkyBlockItemFactory.tooltipLines(petSlot, petStack);
		}
	}

	private void drawMissingSlot(GuiGraphicsExtractor g, PetLoreResolver.PetType pet, int sx, int sy, int mouseX, int mouseY) {
		int slot = this.slotSize;
		boolean hovered = mouseY >= this.gridY && mouseY < this.gridY + this.gridH
			&& mouseX >= sx && mouseX < sx + slot && mouseY >= sy && mouseY < sy + slot;
		PvDraw.roundedFill(g, sx, sy, slot, slot, 3, MISSING_BG);
		PvDraw.roundedRing(g, sx, sy, slot, slot, 3, MISSING_BORDER, PvDraw.C_ALL);
		int ix = sx + (slot - ITEM_ICON) / 2;
		int iy = sy + (slot - ITEM_ICON) / 2;
		SkyBlockIconRenderer.draw(g, SkyBlockItemFactory.iconStack(pet.neuId()), pet.neuId(), ix, iy, ITEM_ICON);
		if (hovered) {
			int rarity = SkyBlockItemFactory.tierArgb(pet.tier()) & 0xFFFFFF;
			this.hoverPetTip = List.of(
				Component.literal(pet.name()).withColor(rarity),
				Component.literal("Missing").withColor(0xFF5555)
			);
		}
	}

	/** Dark rarity-tinted slot fill (keeps icons readable). */
	private static int raritySlotBackground(int rarityArgb, boolean active, boolean selected, boolean hovered) {
		int r = (rarityArgb >>> 16) & 0xFF;
		int green = (rarityArgb >>> 8) & 0xFF;
		int b = rarityArgb & 0xFF;
		float tint = hovered ? 0.38f : selected ? 0.32f : 0.26f;
		int outR = Math.round(16 + r * tint);
		int outG = Math.round(16 + green * tint);
		int outB = Math.round(16 + b * tint);
		if (active) {
			outG = Math.min(255, outG + 18);
			outR = Math.max(0, outR - 4);
		}
		return 0xFF000000 | (clampByte(outR) << 16) | (clampByte(outG) << 8) | clampByte(outB);
	}

	private static int clampByte(int value) {
		return Math.max(0, Math.min(255, value));
	}

	private void drawStats(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		PvDraw.text(g, font, "Details", x + PAD, y + 6, PvDraw.COLOR_TEXT);
		int cy = y + 6 + font.lineHeight + 8;
		int contentW = w - PAD * 2;
		int cx = x + PAD;

		if (this.selected < 0 || this.selected >= this.sorted.size()) {
			PvDraw.textCentered(g, font, "Select a pet", x + w / 2, y + h / 2, PvDraw.COLOR_MUTED);
			return;
		}
		PetSnapshot.Entry pet = sortedPets().get(this.selected);

		ItemStack icon = petIcon(pet);
		SkyBlockIconRenderer.draw(g, icon, pet.neuId(), cx, cy, ITEM_ICON);
		PvDraw.text(
			g,
			font,
			trim(font, pet.displayName(), contentW - ITEM_ICON - 6),
			cx + ITEM_ICON + 4,
			cy + (ITEM_ICON - font.lineHeight) / 2,
			SkyBlockItemFactory.tierArgb(pet.tier())
		);
		cy += ITEM_ICON + 8;

		cy += row(g, font, "Tier", InventoryDecoder.prettyWords(pet.tier()), cx, cy, contentW, SkyBlockItemFactory.tierArgb(pet.tier()));
		cy += row(g, font, "Level", pet.level() + " / " + pet.maxLevel(), cx, cy, contentW, PvDraw.COLOR_TEXT);

		boolean maxed = pet.level() >= pet.maxLevel();
		float nextProgress = maxed ? 1f : pet.progressToNext();
		float maxProgress = pet.xpMax() <= 0
			? (maxed ? 1f : 0f)
			: (float) Math.max(0, Math.min(1, pet.exp() / pet.xpMax()));

		PvDraw.text(g, font, "Next level", cx, cy, PvDraw.COLOR_MUTED);
		int nextLabelY = cy;
		cy += font.lineHeight + 2;
		PvDraw.progressBar(g, cx, cy, contentW, PvDraw.BAR_HEIGHT, nextProgress, PvDraw.COLOR_BAR_FILL, maxed);
		this.xpBarX = cx;
		this.xpBarY = nextLabelY;
		this.xpBarW = contentW;
		this.xpBarH = (cy + PvDraw.BAR_HEIGHT) - nextLabelY;
		if (maxed) {
			double overflow = Math.max(0, pet.exp() - pet.xpMax());
			this.xpHover = "Max level · Overflow: " + FormatUtil.shortXp(overflow);
		} else {
			this.xpHover = FormatUtil.shortXp(pet.xpIntoLevel()) + " / " + FormatUtil.shortXp(pet.xpToNext());
		}
		cy += PvDraw.BAR_HEIGHT + 6;

		PvDraw.text(g, font, "To max", cx, cy, PvDraw.COLOR_MUTED);
		int maxLabelY = cy;
		cy += font.lineHeight + 2;
		PvDraw.progressBar(g, cx, cy, contentW, PvDraw.BAR_HEIGHT, maxProgress, PvDraw.COLOR_ACCENT, maxed);
		this.maxXpBarX = cx;
		this.maxXpBarY = maxLabelY;
		this.maxXpBarW = contentW;
		this.maxXpBarH = (cy + PvDraw.BAR_HEIGHT) - maxLabelY;
		this.maxXpHover = FormatUtil.shortXp(Math.min(pet.exp(), pet.xpMax())) + " / " + FormatUtil.shortXp(pet.xpMax());
		cy += PvDraw.BAR_HEIGHT + 8;

		if (pet.candyUsed() > 0) {
			cy += row(g, font, "Candy", String.valueOf(pet.candyUsed()), cx, cy, contentW, PvDraw.COLOR_TEXT);
		}

		PvDraw.text(g, font, "Held item", cx, cy, PvDraw.COLOR_MUTED);
		cy += font.lineHeight + 2;
		if (pet.hasHeldItem()) {
			ItemStack held = SkyBlockItemFactory.iconStack(pet.heldItem());
			SkyBlockIconRenderer.draw(g, held, pet.heldItem(), cx, cy, ITEM_ICON);
			String heldName = SkyBlockItemFactory.plainDisplayName(pet.heldItem());
			int heldColor = SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(pet.heldItem()));
			PvDraw.text(
				g,
				font,
				trim(font, heldName, contentW - ITEM_ICON - 6),
				cx + ITEM_ICON + 4,
				cy + (ITEM_ICON - font.lineHeight) / 2,
				heldColor
			);
			cy += ITEM_ICON + 6;
		} else {
			PvDraw.text(g, font, "None", cx, cy, PvDraw.COLOR_MUTED);
			cy += font.lineHeight + 6;
		}

		if (pet.hasSkin()) {
			cy += row(
				g,
				font,
				"Skin",
				InventoryDecoder.prettyWords(pet.skin()),
				cx,
				cy,
				contentW,
				PvDraw.COLOR_TEXT
			);
		}

		row(
			g,
			font,
			"Networth",
			pet.networth() > 0 ? FormatUtil.shortCoins(pet.networth()) : "-",
			cx,
			cy,
			contentW,
			PvDraw.COLOR_GOLD
		);
	}

	private static ItemStack petIcon(PetSnapshot.Entry pet) {
		if (pet.hasSkin()) {
			ItemStack skin = SkyBlockItemFactory.iconStack("PET_SKIN_" + pet.skin().toUpperCase(Locale.ROOT));
			if (!skin.isEmpty() && !skin.is(Items.PAPER)) {
				return skin;
			}
		}
		return SkyBlockItemFactory.iconStack(pet.neuId());
	}

	private static int row(GuiGraphicsExtractor g, Font font, String label, String value, int x, int y, int w, int valueColor) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, trim(font, value, Math.max(20, w - font.width(label) - 8)), x + w, y, valueColor);
		return font.lineHeight + 4;
	}

	private static String trim(Font font, String value, int maxW) {
		if (value == null) {
			return "";
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value;
		}
		String ellipsis = "...";
		int ellipsisW = font.width(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		StringBuilder sb = new StringBuilder(value);
		while (sb.length() > 0 && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length() - 1);
		}
		return sb + ellipsis;
	}

	private record SlotHit(int x, int y, int w, int h, int index) {
	}
}
