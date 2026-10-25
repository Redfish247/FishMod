/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page;

import static fishmod.pv.gui.crimson.CrimsonUi.DISABLED;
import static fishmod.pv.gui.crimson.CrimsonUi.DOJO_COLOR;
import static fishmod.pv.gui.crimson.CrimsonUi.ENABLED;
import static fishmod.pv.gui.crimson.CrimsonUi.GAP;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.crimson.CrimsonUi.PAD;
import static fishmod.pv.gui.crimson.CrimsonUi.drawHover;
import static fishmod.pv.gui.crimson.CrimsonUi.prettyRingtone;
import static fishmod.pv.gui.crimson.CrimsonUi.sectionSeparator;
import static fishmod.pv.gui.crimson.CrimsonUi.statLine;

import fishmod.pv.data.AbiphoneCosts;
import fishmod.pv.data.AbiphoneNpcs;
import fishmod.pv.data.CrimsonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.price.ItemPricer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Crimson Abiphone subtab: contact grid + stats. */
public final class AbiphonePage {
	private final List<HoverZone> zones = new ArrayList<>();

	private int abiphoneScroll;
	private int abiphoneMaxScroll;
	private int abiphoneX;
	private int abiphoneY;
	private int abiphoneW;
	private int abiphoneH;
	private boolean cheapestFirst;
	private int sortX;
	private int sortY;
	private int sortW;
	private int sortH;

	public void resetScroll() {
		this.abiphoneScroll = 0;
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.sortW > 0 && mx >= this.sortX && mx < this.sortX + this.sortW
			&& my >= this.sortY && my < this.sortY + this.sortH) {
			this.cheapestFirst = !this.cheapestFirst;
			this.abiphoneScroll = 0;
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.abiphoneMaxScroll > 0
			&& mouseX >= this.abiphoneX && mouseX < this.abiphoneX + this.abiphoneW
			&& mouseY >= this.abiphoneY && mouseY < this.abiphoneY + this.abiphoneH) {
			int delta = scrollY > 0 ? -14 : 14;
			int next = Math.max(0, Math.min(this.abiphoneMaxScroll, this.abiphoneScroll + delta));
			if (next != this.abiphoneScroll) {
				this.abiphoneScroll = next;
				return true;
			}
			return false;
		}
		return false;
	}

	public void render(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH
	) {
		this.zones.clear();
		int statsW = Math.max(150, Math.min(220, w * 32 / 100));
		int iconsW = w - statsW - GAP;
		PvDraw.innerPanel(g, x, y, iconsW, h);
		PvDraw.innerPanel(g, x + iconsW + GAP, y, statsW, h);

		drawAbiphoneStats(g, font, snapshot, x + iconsW + GAP, y, statsW, h);
		drawAbiphoneIcons(g, font, snapshot, x, y, iconsW, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		drawHover(g, font, this.zones, mouseX, mouseY, screenW, screenH);
	}

	private void drawAbiphoneStats(GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot, int x, int y, int w, int h) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;

		PvDraw.text(g, font, "Abiphone", lx, ly, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font,
			snapshot.abiphoneActive() + " / " + AbiphoneNpcs.TOTAL_CONTACTS,
			lx + lw, ly, PvDraw.COLOR_ACCENT);
		ly += font.lineHeight + 4;
		ly = sectionSeparator(g, font, x, ly, w);

		if (!snapshot.abiphoneRingtone().isBlank()) {
			ly = statLine(g, font, "Ringtone", prettyRingtone(snapshot.abiphoneRingtone()),
				lx, ly, lw, PvDraw.COLOR_TEXT);
		}
		if (snapshot.trioContactAddons() > 0) {
			ly = statLine(g, font, "Trio addons", String.valueOf(snapshot.trioContactAddons()),
				lx, ly, lw, PvDraw.COLOR_GOLD);
		}
		if (snapshot.operatorChipRepaired() > 0) {
			ly = statLine(g, font, "Operator chip", "Repaired " + snapshot.operatorChipRepaired(),
				lx, ly, lw, PvDraw.COLOR_ACCENT);
		}
		ly = statLine(g, font, "Quests done",
			snapshot.abiphoneQuestsDone() + "/" + AbiphoneNpcs.TOTAL_CONTACTS,
			lx, ly, lw, ENABLED);
		if (snapshot.abiphoneDndCount() > 0) {
			ly = statLine(g, font, "DND contacts", String.valueOf(snapshot.abiphoneDndCount()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		if (snapshot.snakeBestScore() > 0 || snapshot.tttLosses() > 0 || snapshot.tttDraws() > 0) {
			String games = "Snake " + snapshot.snakeBestScore()
				+ " · TTT L" + snapshot.tttLosses()
				+ " D" + snapshot.tttDraws();
			ly = statLine(g, font, "Games", games, lx, ly, lw, DOJO_COLOR);
		}
		if (!snapshot.abiphoneSort().isBlank()) {
			ly = statLine(g, font, "Sort", prettyRingtone(snapshot.abiphoneSort()),
				lx, ly, lw, PvDraw.COLOR_MUTED);
		}
		ly = sectionSeparator(g, font, x, ly, w);
		PvDraw.text(g, font, "Last Called", lx, ly, PvDraw.COLOR_MUTED);
	}

	private void drawAbiphoneIcons(
		GuiGraphicsExtractor g, Font font, CrimsonSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Contacts", lx, ly, PvDraw.COLOR_MUTED);
		List<CrimsonSnapshot.AbiphoneContact> contacts = snapshot.abiphoneContacts();
		List<AbiphoneNpcs.Undiscovered> undiscovered = undiscovered(snapshot);
		this.sortW = 0;
		if (!undiscovered.isEmpty()) {
			String sortLabel = this.cheapestFirst ? "Missing: Cheapest" : "Missing: Name";
			this.sortW = font.width(sortLabel);
			this.sortH = font.lineHeight;
			this.sortX = lx + lw - this.sortW;
			this.sortY = ly;
			boolean sortHovered = mx >= this.sortX && mx < this.sortX + this.sortW
				&& my >= this.sortY && my < this.sortY + this.sortH;
			PvDraw.text(g, font, sortLabel, this.sortX, this.sortY,
				sortHovered || this.cheapestFirst ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED);
			this.zones.add(HoverZone.of(this.sortX, this.sortY, this.sortW, this.sortH, List.of(
				PvTooltip.Line.of("Click to change sort", PvDraw.COLOR_TEXT),
				PvTooltip.Line.of("Name: A to Z", PvDraw.COLOR_MUTED),
				PvTooltip.Line.of("Cheapest: lowest cost first, unpriced last", PvDraw.COLOR_MUTED)
			)));
			if (this.cheapestFirst) {
				undiscovered = this.cheapestCache;
			}
		}
		ly += font.lineHeight + 4;
		if (contacts.isEmpty() && undiscovered.isEmpty()) {
			PvDraw.textCentered(g, font, "No contacts",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			this.abiphoneMaxScroll = 0;
			this.abiphoneW = 0;
			this.abiphoneH = 0;
			return;
		}

		int gridH = Math.max(1, bottom - ly);
		// Largest slot that fits every contact without scrolling; undiscovered start on a fresh row.
		int slot = 18;
		int gap = 2;
		int cols = 1;
		for (int trySlot = 28; trySlot >= 18; trySlot--) {
			int tryGap = trySlot >= 24 ? 4 : trySlot >= 21 ? 3 : 2;
			int tryCols = Math.max(1, (lw + tryGap) / (trySlot + tryGap));
			int needH = gridRows(contacts.size(), undiscovered.size(), tryCols) * (trySlot + tryGap) - tryGap;
			slot = trySlot;
			gap = tryGap;
			cols = tryCols;
			if (needH <= gridH) {
				break;
			}
		}
		// Stretch gaps so the grid uses the full width.
		int usedW = cols * slot;
		int freeW = Math.max(0, lw - usedW);
		int gapX = cols > 1 ? freeW / (cols - 1) : 0;
		gapX = Math.max(gap, gapX);
		int contentH = gridRows(contacts.size(), undiscovered.size(), cols) * (slot + gap) - gap;
		this.abiphoneX = x;
		this.abiphoneY = ly;
		this.abiphoneW = w;
		this.abiphoneH = Math.max(0, bottom - ly);
		this.abiphoneMaxScroll = Math.max(0, contentH - this.abiphoneH);
		this.abiphoneScroll = Math.min(this.abiphoneScroll, this.abiphoneMaxScroll);

		g.enableScissor(lx, this.abiphoneY, lx + lw + 3, this.abiphoneY + this.abiphoneH);
		for (int i = 0; i < contacts.size(); i++) {
			CrimsonSnapshot.AbiphoneContact contact = contacts.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = lx + col * (slot + gapX);
			int by = ly + row * (slot + gap) - this.abiphoneScroll;
			boolean hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot
				&& my >= this.abiphoneY && my < this.abiphoneY + this.abiphoneH;
			PvDraw.fill(g, bx, by, slot, slot, ITEM_SLOT_BG);
			g.outline(bx, by, slot, slot,
				hovered ? PvDraw.COLOR_ACCENT : (contact.active() ? ITEM_SLOT_BORDER : DISABLED));

			int iconPad = Math.max(0, (slot - 16) / 2);
			drawContactIcon(g, contact.id(), bx + iconPad, by + iconPad);
			if (!contact.active()) {
				PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000);
			}

			int y0 = Math.max(by, this.abiphoneY);
			int y1 = Math.min(by + slot, this.abiphoneY + this.abiphoneH);
			if (y1 > y0) {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(contact.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of(contact.active() ? "Active contact" : "Inactive",
					contact.active() ? ENABLED : PvDraw.COLOR_MUTED));
				if (contact.completedQuest()) {
					tip.add(PvTooltip.Line.of("Quest complete", ENABLED));
				} else if (contact.talkedTo()) {
					tip.add(PvTooltip.Line.of("Talked to", PvDraw.COLOR_MUTED));
				}
				if (contact.incomingCalls() > 0) {
					tip.add(PvTooltip.Line.of("Incoming calls: " + FormatUtil.commas(contact.incomingCalls()),
						PvDraw.COLOR_MUTED));
				}
				if (contact.dnd()) {
					tip.add(PvTooltip.Line.of("Do Not Disturb", PvDraw.COLOR_GOLD));
				}
				this.zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip));
			}
		}
		int firstRow = (contacts.size() + cols - 1) / cols;
		for (int i = 0; i < undiscovered.size(); i++) {
			AbiphoneNpcs.Undiscovered contact = undiscovered.get(i);
			int bx = lx + (i % cols) * (slot + gapX);
			int by = ly + (firstRow + i / cols) * (slot + gap) - this.abiphoneScroll;
			boolean hovered = mx >= bx && mx < bx + slot && my >= by && my < by + slot
				&& my >= this.abiphoneY && my < this.abiphoneY + this.abiphoneH;
			PvDraw.roundedFill(g, bx, by, slot, slot, 3, UNDISCOVERED_BG);
			PvDraw.roundedRing(g, bx, by, slot, slot, 3, hovered ? PvDraw.COLOR_ACCENT : UNDISCOVERED_BORDER, PvDraw.C_ALL);
			int iconPad = Math.max(0, (slot - 16) / 2);
			drawNpcIcon(g, contact.neuId(), bx + iconPad, by + iconPad);
			PvDraw.fill(g, bx + 1, by + 1, slot - 2, slot - 2, 0x66000000);

			int y0 = Math.max(by, this.abiphoneY);
			int y1 = Math.min(by + slot, this.abiphoneY + this.abiphoneH);
			if (y1 > y0) {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.of(contact.name(), PvDraw.COLOR_TEXT));
				tip.add(PvTooltip.Line.of("Undiscovered", UNDISCOVERED_TEXT));
				for (String req : contact.requirement()) {
					tip.add(PvTooltip.Line.of(req, PvDraw.COLOR_GOLD));
				}
				AbiphoneCosts.Estimate cost = this.costCache.getOrDefault(contact, AbiphoneCosts.Estimate.UNKNOWN);
				tip.add(PvTooltip.Line.blank());
				if (!cost.known()) {
					tip.add(PvTooltip.Line.meta("Cost unknown"));
				} else if (cost.coins() <= 0) {
					tip.add(PvTooltip.Line.of("No coin cost", ENABLED));
				} else {
					tip.add(PvTooltip.Line.of("Est. cost: " + FormatUtil.compactCoins(cost.coins()), PvDraw.COLOR_GOLD));
				}
				this.zones.add(HoverZone.of(bx, y0, slot, y1 - y0, tip));
			}
		}
		g.disableScissor();
	}

	private static final int UNDISCOVERED_BG = 0xFF3A1010;
	private static final int UNDISCOVERED_BORDER = 0xFFCC3333;
	private static final int UNDISCOVERED_TEXT = 0xFFFF5555;

	private CrimsonSnapshot undiscoveredFor;
	private boolean undiscoveredFinal;
	private long undiscoveredAtMs;
	private List<AbiphoneNpcs.Undiscovered> undiscoveredCache = List.of();
	private List<AbiphoneNpcs.Undiscovered> cheapestCache = List.of();
	private Map<AbiphoneNpcs.Undiscovered, AbiphoneCosts.Estimate> costCache = Map.of();

	private List<AbiphoneNpcs.Undiscovered> undiscovered(CrimsonSnapshot snapshot) {
		// Keep recomputing until the NEU repo and prices are ready; both may still be loading on first draw.
		long now = System.currentTimeMillis();
		boolean retry = !this.undiscoveredFinal && now - this.undiscoveredAtMs >= 1000L;
		if (this.undiscoveredFor != snapshot || retry) {
			this.undiscoveredAtMs = now;
			List<String> ids = new ArrayList<>();
			for (CrimsonSnapshot.AbiphoneContact contact : snapshot.abiphoneContacts()) {
				ids.add(contact.id());
			}
			List<AbiphoneNpcs.Undiscovered> missing = AbiphoneNpcs.undiscovered(ids);
			// NEU lists extra entries; the real count of contacts still missing caps the red slots.
			int allowed = Math.max(0, AbiphoneNpcs.TOTAL_CONTACTS - snapshot.abiphoneActive());
			this.undiscoveredCache = missing.size() > allowed ? List.copyOf(missing.subList(0, allowed)) : missing;
			Map<AbiphoneNpcs.Undiscovered, AbiphoneCosts.Estimate> costs = new IdentityHashMap<>();
			for (AbiphoneNpcs.Undiscovered contact : this.undiscoveredCache) {
				costs.put(contact, AbiphoneCosts.estimate(
					contact.requirement(), NeuRepoCache::idForDisplayName, ItemPricer::knownPrice));
			}
			this.costCache = costs;
			List<AbiphoneNpcs.Undiscovered> cheapest = new ArrayList<>(this.undiscoveredCache);
			cheapest.sort(Comparator
				.comparing((AbiphoneNpcs.Undiscovered c) -> !costs.get(c).known())
				.thenComparingDouble(c -> costs.get(c).known() ? costs.get(c).coins() : 0D)
				.thenComparing(AbiphoneNpcs.Undiscovered::name, String.CASE_INSENSITIVE_ORDER));
			this.cheapestCache = List.copyOf(cheapest);
			this.undiscoveredFinal = NeuRepoCache.isReady() && ItemPricer.isReady();
			this.undiscoveredFor = snapshot;
		}
		return this.undiscoveredCache;
	}

	private static int gridRows(int discovered, int undiscovered, int cols) {
		return (discovered + cols - 1) / cols + (undiscovered + cols - 1) / cols;
	}

	/** Prefer the custom NEU skin texture, then a vanilla stack; never show blank paper. */
	private static void drawContactIcon(GuiGraphicsExtractor g, String contactId, int x, int y) {
		drawNpcIcon(g, AbiphoneNpcs.neuId(contactId), x, y);
	}

	private static void drawNpcIcon(GuiGraphicsExtractor g, String neuId, int x, int y) {
		ItemStack icon = neuId.isBlank() ? ItemStack.EMPTY : SkyBlockItemFactory.iconStack(neuId);
		if (!neuId.isBlank() && SkyBlockIconRenderer.hasKnownIcon(neuId)) {
			SkyBlockIconRenderer.draw(g, icon, neuId, x, y, 16);
			return;
		}
		if (icon.isEmpty() || icon.is(Items.PAPER) || isUntexturedHead(icon)) {
			icon = FALLBACK_ICON;
		}
		g.item(icon, x, y);
	}

	private static final ItemStack FALLBACK_ICON = new ItemStack(Items.VILLAGER_SPAWN_EGG);

	private static boolean isUntexturedHead(ItemStack icon) {
		if (icon == null || icon.isEmpty() || !icon.is(Items.PLAYER_HEAD)) {
			return false;
		}
		var profile = icon.get(DataComponents.PROFILE);
		if (profile == null) {
			return true;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return true;
			}
			for (var prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return false;
				}
			}
		} catch (Exception ignored) {
			return true;
		}
		return true;
	}
}
