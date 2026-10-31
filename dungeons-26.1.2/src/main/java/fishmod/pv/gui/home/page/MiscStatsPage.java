/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page;

import static fishmod.pv.gui.home.HomeUi.DISABLED;
import static fishmod.pv.gui.home.HomeUi.ENABLED;
import static fishmod.pv.gui.home.HomeUi.FLIP_MS;
import static fishmod.pv.gui.home.HomeUi.GAP;
import static fishmod.pv.gui.home.HomeUi.HEADER_COMMUNITY;
import static fishmod.pv.gui.home.HomeUi.HEADER_DEATHS;
import static fishmod.pv.gui.home.HomeUi.HEADER_GUILD;
import static fishmod.pv.gui.home.HomeUi.HEADER_HIGHLIGHTS;
import static fishmod.pv.gui.home.HomeUi.HEADER_KILLS;
import static fishmod.pv.gui.home.HomeUi.HEADER_PETS;
import static fishmod.pv.gui.home.HomeUi.HEADER_PROFILE;
import static fishmod.pv.gui.home.HomeUi.HEADER_SECTION;
import static fishmod.pv.gui.home.HomeUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.home.HomeUi.PAD;
import static fishmod.pv.gui.home.HomeUi.PANEL_HOVER;
import static fishmod.pv.gui.home.HomeUi.SEP_GAP;
import static fishmod.pv.gui.home.HomeUi.STAT_ROW;
import static fishmod.pv.gui.home.HomeUi.easeInOutCubic;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.GuildStatus;
import fishmod.pv.data.MiscStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Home → Misc: profile extras + guild/cookie (left flip), kills ↔ deaths (right flip).
 */
public final class MiscStatsPage {
	private MiscStatsSnapshot snapshot = MiscStatsSnapshot.empty();
	private GuildStatus guild = GuildStatus.idle();
	private long freeCookieMs;
	private boolean freeCookieKnown;

	private final List<HoverZone> zones = new ArrayList<>();

	private int leftHitX;
	private int leftHitY;
	private int leftHitW;
	private int leftHitH;
	private boolean leftExtrasFace;
	private long leftFlipStartMs;
	private boolean leftFlipTarget;

	private int rightHitX;
	private int rightHitY;
	private int rightHitW;
	private int rightHitH;
	private boolean rightDeathsFace;
	private long rightFlipStartMs;
	private boolean rightFlipTarget;

	private int listScroll;
	private int listMaxScroll;
	private int guildBtnX;
	private int guildBtnY;
	private int guildBtnW;
	private int guildBtnH;
	private Runnable onGuildClick;

	public void apply(MiscStatsSnapshot snapshot) {
		this.snapshot = snapshot == null ? MiscStatsSnapshot.empty() : snapshot;
		this.zones.clear();
		this.listScroll = 0;
		this.listMaxScroll = 0;
		this.leftFlipStartMs = 0L;
		this.rightFlipStartMs = 0L;
		List<String> warm = new ArrayList<>();
		for (MiscStatsSnapshot.CommunityUpgrade u : this.snapshot.communityUpgrades()) {
			String id = communitySkyblockId(u.upgrade());
			if (id != null && !id.isBlank()) {
				warm.add(id);
			}
		}
		warm.add("ELIZABETH_NPC");
		SkyBlockItemFactory.prefetchIds(warm);
	}

	public void reset() {
		apply(MiscStatsSnapshot.empty());
		this.guild = GuildStatus.idle();
		this.freeCookieMs = 0L;
		this.freeCookieKnown = false;
	}

	public void applyGuild(GuildStatus status) {
		this.guild = status == null ? GuildStatus.idle() : status;
	}

	public GuildStatus guild() {
		return this.guild;
	}

	/** Free cookie claim timestamp from Hypixel player ({@code skyblock_free_cookie}). */
	public void applyFreeCookie(long ms) {
		this.freeCookieMs = Math.max(0L, ms);
		this.freeCookieKnown = true;
	}

	public void setGuildClickHandler(Runnable handler) {
		this.onGuildClick = handler;
	}

	public boolean mouseClicked(double mx, double my) {
		if (mx >= this.guildBtnX && mx < this.guildBtnX + this.guildBtnW
			&& my >= this.guildBtnY && my < this.guildBtnY + this.guildBtnH) {
			if (this.onGuildClick != null
				&& this.guild.state() != GuildStatus.State.LOADING
				&& this.guild.state() != GuildStatus.State.READY) {
				this.onGuildClick.run();
			}
			return true;
		}
		if (hit(mx, my, this.leftHitX, this.leftHitY, this.leftHitW, this.leftHitH)) {
			if (this.leftFlipStartMs != 0L) {
				return true;
			}
			this.leftFlipTarget = !this.leftExtrasFace;
			this.leftFlipStartMs = System.currentTimeMillis();
			return true;
		}
		if (hit(mx, my, this.rightHitX, this.rightHitY, this.rightHitW, this.rightHitH)) {
			if (this.rightFlipStartMs != 0L) {
				return true;
			}
			this.rightFlipTarget = !this.rightDeathsFace;
			this.rightFlipStartMs = System.currentTimeMillis();
			this.listScroll = 0;
			return true;
		}
		return false;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!hit(mouseX, mouseY, this.rightHitX, this.rightHitY, this.rightHitW, this.rightHitH)) {
			return false;
		}
		if (this.listMaxScroll <= 0) {
			return false;
		}
		this.listScroll = Math.max(0, Math.min(this.listMaxScroll,
			this.listScroll - (int) Math.round(scrollY * 12)));
		return true;
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
		this.zones.clear();

		int leftW = Math.max(100, w / 3);
		int rightW = Math.max(140, w - leftW - GAP);
		leftW = w - rightW - GAP;
		int leftX = x;
		int rightX = x + leftW + GAP;

		drawLeftFlip(g, font, leftX, y, leftW, h, mouseX, mouseY);
		drawRightFlip(g, font, rightX, y, rightW, h, mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				break;
			}
		}
	}

	private void drawLeftFlip(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.leftHitX = x;
		this.leftHitY = y;
		this.leftHitW = w;
		this.leftHitH = h;

		boolean hovering = hit(mx, my, x, y, w, h)
			&& !hit(mx, my, this.guildBtnX, this.guildBtnY, this.guildBtnW, this.guildBtnH);
		// Only the profile face draws the guild button; a stale hitbox would swallow flip clicks.
		this.guildBtnW = 0;
		boolean showExtras = this.leftExtrasFace;
		float scaleX = 1F;
		if (this.leftFlipStartMs != 0L) {
			float t = (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS;
			if (t >= 1F) {
				this.leftExtrasFace = this.leftFlipTarget;
				this.leftFlipStartMs = 0L;
				showExtras = this.leftExtrasFace;
			} else {
				float eased = easeInOutCubic(Math.max(0F, Math.min(1F, t)));
				double angle = eased * Math.PI;
				scaleX = (float) Math.max(0.04, Math.abs(Math.cos(angle)));
				showExtras = Math.cos(angle) < 0 ? this.leftFlipTarget : this.leftExtrasFace;
			}
		}

		g.pose().pushMatrix();
		g.pose().translate(x + w / 2F, y + h / 2F);
		g.pose().scale(scaleX, 1F);
		g.pose().translate(-(x + w / 2F), -(y + h / 2F));

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovering && this.leftFlipStartMs == 0L) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		if (showExtras) {
			drawExtrasFace(g, font, lx, ly, lw, y + h - PAD, mx, my);
		} else {
			drawProfileFace(g, font, lx, ly, lw, y + h - PAD, mx, my);
		}
		g.pose().popMatrix();
	}

	private void drawProfileFace(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int bottom, int mx, int my
	) {
		MiscStatsSnapshot s = this.snapshot;
		int cy = y;
		PvDraw.text(g, font, "Profile", x, cy, HEADER_PROFILE);
		cy += font.lineHeight + 4;

		cy = stat(g, font, "First Join", formatAgo(s.firstJoinMs()), x, cy, w, PvDraw.COLOR_TEXT,
			List.of(
				PvTooltip.Line.title("First Join", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(s.firstJoinMs() > 0L ? formatAgo(s.firstJoinMs()) : "Unknown")
			));
		cy = stat(g, font, "Fairy Souls",
			FormatUtil.commas(s.fairyCollected()) + " / " + FormatUtil.commas(MAX_FAIRY_SOULS),
			x, cy, w, s.fairyCollected() >= MAX_FAIRY_SOULS ? ENABLED : PvDraw.COLOR_ACCENT,
			List.of(
				PvTooltip.Line.title("Fairy Souls", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Collected", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyCollected()), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Missing", PvDraw.COLOR_MUTED,
					FormatUtil.commas(Math.max(0, MAX_FAIRY_SOULS - s.fairyCollected())), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Exchanged", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyExchanges()), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Unspent", PvDraw.COLOR_MUTED, FormatUtil.commas(s.fairyUnspent()), PvDraw.COLOR_GOLD)
			));
		cy = stat(g, font, "Soulflow", FormatUtil.commas(s.soulflow()), x, cy, w, PvDraw.COLOR_ACCENT, null);
		cy = stat(g, font, "Personal Bank", "Tier " + s.personalBankUpgrade(), x, cy, w, PvDraw.COLOR_GOLD, null);
		cy = stat(g, font, "Cookie Buff",
			s.cookieBuffActive() ? "Active" : "Inactive",
			x, cy, w, s.cookieBuffActive() ? ENABLED : PvDraw.COLOR_MUTED, null);

		String cookieClaim = !this.freeCookieKnown
			? "-"
			: (this.freeCookieMs > 0L ? formatAgo(this.freeCookieMs) : "-");
		cy = stat(g, font, "Free Cookie", cookieClaim, x, cy, w, PvDraw.COLOR_TEXT,
			List.of(
				PvTooltip.Line.title("SkyBlock Free Cookie", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.meta(this.freeCookieKnown
					? (this.freeCookieMs > 0L ? "First claim " + formatAgo(this.freeCookieMs) : "Never claimed")
					: "Not loaded yet")
			));

		if (s.refinedJyrreUses() > 0) {
			cy = stat(g, font, "Jyrre uses", FormatUtil.commas(s.refinedJyrreUses()),
				x, cy, w, PvDraw.COLOR_GOLD, null);
		}
		if (!s.unlockedTemples().isEmpty()) {
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title("Temples", PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.divider());
			for (String temple : s.unlockedTemples()) {
				tip.add(PvTooltip.Line.row("Unlocked", PvDraw.COLOR_MUTED, temple, ENABLED));
			}
			cy = stat(g, font, "Temples", String.valueOf(s.unlockedTemples().size()),
				x, cy, w, PvDraw.COLOR_ACCENT, tip);
		}

		cy += 2;
		cy = separator(g, x - PAD, cy, w + PAD * 2);

		PvDraw.text(g, font, "Guild", x, cy, HEADER_GUILD);
		cy += font.lineHeight + 4;
		drawGuildButton(g, font, x, cy, w, mx, my);
		cy += font.lineHeight + 10;

		if (cy + STAT_ROW * 3 < bottom) {
			cy = separator(g, x - PAD, cy, w + PAD * 2);
			PvDraw.text(g, font, "Highlights", x, cy, HEADER_HIGHLIGHTS);
			cy += font.lineHeight + 4;
			cy = stat(g, font, "K/D", FormatUtil.twoDecimals(s.killDeathRatio()), x, cy, w, HEADER_KILLS,
				List.of(
					PvTooltip.Line.title("Kill / Death Ratio", HEADER_KILLS),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(s.killsTotal()), HEADER_KILLS),
					PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(s.deathsTotal()), HEADER_DEATHS),
					PvTooltip.Line.row("K/D", PvDraw.COLOR_MUTED, FormatUtil.twoDecimals(s.killDeathRatio()), PvDraw.COLOR_TEXT)
				));
			cy = stat(g, font, "Highest Crit", FormatUtil.shortXp((long) s.highestCriticalDamage()), x, cy, w, PvDraw.COLOR_GOLD, null);
			cy = stat(g, font, "Gifts",
				"→ " + FormatUtil.shortXp(s.giftsGiven()) + " / " + FormatUtil.shortXp(s.giftsReceived()) + " ←",
				x, cy, w, PvDraw.COLOR_TEXT,
				List.of(
					PvTooltip.Line.title("Gifts", PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Given", PvDraw.COLOR_MUTED, FormatUtil.commas(s.giftsGiven()), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Received", PvDraw.COLOR_MUTED, FormatUtil.commas(s.giftsReceived()), PvDraw.COLOR_TEXT)
				));
		}

	}

	private void drawExtrasFace(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int bottom, int mx, int my
	) {
		MiscStatsSnapshot s = this.snapshot;
		int cy = y;
		PvDraw.text(g, font, "Community Upgrades", x, cy, HEADER_COMMUNITY);
		cy += font.lineHeight + 4;
		if (s.communityUpgrades().isEmpty()) {
			cy = stat(g, font, "Upgrades", "None", x, cy, w, PvDraw.COLOR_MUTED, null);
		} else {
			cy = drawCommunityUpgrades(g, font, s.communityUpgrades(), x, cy, w, bottom - font.lineHeight - 4, mx, my);
		}

		cy = separator(g, x - PAD, cy, w + PAD * 2);
		PvDraw.text(g, font, "Pets / Fishing", x, cy, HEADER_PETS);
		cy += font.lineHeight + 4;
		cy = stat(g, font, "Pet XP", FormatUtil.shortXp(s.petXpTotal()), x, cy, w, PvDraw.COLOR_GOLD, null);
		cy = stat(g, font, "Ores Mined", FormatUtil.commas(s.petOresMined()), x, cy, w, PvDraw.COLOR_TEXT, null);
		cy = stat(g, font, "Sea Creatures", FormatUtil.commas(s.petSeaCreatures()), x, cy, w, PvDraw.COLOR_TEXT, null);
		cy = stat(g, font, "SC Kills", FormatUtil.commas(s.seaCreatureKills()), x, cy, w, PvDraw.COLOR_ACCENT, null);

		MiscStatsSnapshot.ExperimentationStats exp = s.experimentation();
		if (exp.present() && cy + font.lineHeight + STAT_ROW * 2 < bottom) {
			cy = separator(g, x - PAD, cy, w + PAD * 2);
			PvDraw.text(g, font, "Experimentation", x, cy, HEADER_SECTION);
			cy += font.lineHeight + 4;
			for (MiscStatsSnapshot.ExperimentGame game : exp.games()) {
				if (cy + STAT_ROW > bottom - font.lineHeight) {
					break;
				}
				List<PvTooltip.Line> tip = List.of(
					PvTooltip.Line.title(game.label(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Claims", PvDraw.COLOR_MUTED, FormatUtil.commas(game.claims()), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Attempts", PvDraw.COLOR_MUTED, FormatUtil.commas(game.attempts()), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Best score", PvDraw.COLOR_MUTED, FormatUtil.commas(game.bestScore()), PvDraw.COLOR_GOLD)
				);
				cy = stat(g, font, game.label(), FormatUtil.commas(game.claims()),
					x, cy, w, PvDraw.COLOR_TEXT, tip);
			}
			if (cy + STAT_ROW <= bottom - font.lineHeight) {
				cy = stat(g, font, "Serums", FormatUtil.commas(exp.serumsDrank()),
					x, cy, w, PvDraw.COLOR_ACCENT, null);
			}
			if (cy + STAT_ROW <= bottom - font.lineHeight) {
				cy = stat(g, font, "Resets", FormatUtil.commas(exp.claimsResets()),
					x, cy, w, PvDraw.COLOR_MUTED, null);
			}
		}

		for (MiscStatsSnapshot.Section section : s.extraSections()) {
			if (cy + font.lineHeight + STAT_ROW * 2 > bottom) {
				break;
			}
			cy = separator(g, x - PAD, cy, w + PAD * 2);
			PvDraw.text(g, font, section.title(), x, cy, HEADER_SECTION);
			cy += font.lineHeight + 4;
			int shown = 0;
			for (MiscStatsSnapshot.CountEntry e : section.entries()) {
				if (shown >= 6 || cy + STAT_ROW > bottom - font.lineHeight) {
					break;
				}
				cy = stat(g, font, trim(font, e.label(), w / 2), FormatUtil.commas(e.count()),
					x, cy, w, PvDraw.COLOR_TEXT, null);
				shown++;
			}
			if (section.entries().size() > shown) {
				PvDraw.text(g, font, "+" + (section.entries().size() - shown) + " more", x, cy, PvDraw.COLOR_MUTED);
				cy += STAT_ROW;
			}
		}

	}

	/** Two-column essence-shop style rows: icon + name + T#/#. */
	private int drawCommunityUpgrades(
		GuiGraphicsExtractor g, Font font, List<MiscStatsSnapshot.CommunityUpgrade> upgrades,
		int x, int y, int w, int bottom, int mx, int my
	) {
		int iconSize = 16;
		int rowH = Math.max(font.lineHeight + 2, iconSize + 2);
		int colGap = 8;
		int colW = Math.max(40, (w - colGap) / 2);
		int mid = (upgrades.size() + 1) / 2;
		int leftBottom = drawCommunityColumn(g, font, upgrades.subList(0, Math.min(mid, upgrades.size())),
			x, y, colW, rowH, iconSize, bottom, mx, my);
		int rightBottom = y;
		if (mid < upgrades.size()) {
			rightBottom = drawCommunityColumn(g, font, upgrades.subList(mid, upgrades.size()),
				x + colW + colGap, y, colW, rowH, iconSize, bottom, mx, my);
		}
		return Math.max(leftBottom, rightBottom) + 2;
	}

	private int drawCommunityColumn(
		GuiGraphicsExtractor g, Font font, List<MiscStatsSnapshot.CommunityUpgrade> upgrades,
		int x, int y, int w, int rowH, int iconSize, int bottom, int mx, int my
	) {
		int labelX = x + iconSize + 3;
		int ry = y;
		for (MiscStatsSnapshot.CommunityUpgrade u : upgrades) {
			if (ry + font.lineHeight > bottom) {
				break;
			}
			PvDraw.IconTextAlign rowAlign = PvDraw.IconTextAlign.of(ry, rowH, iconSize, font.lineHeight);
			drawCommunityIcon(g, u.upgrade(), x, rowAlign.iconY(), iconSize);
			int max = communityMaxTier(u.upgrade());
			String right = max > 0 ? u.tier() + "/" + max : "T" + u.tier();
			int rightW = font.width(right);
			String left = trim(font, u.label(), Math.max(8, w - (labelX - x) - rightW - 4));
			boolean maxed = max > 0 && u.tier() >= max;
			PvDraw.text(g, font, left, labelX, rowAlign.textY(), PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, right, x + w, rowAlign.textY(), maxed ? ENABLED : PvDraw.COLOR_GOLD);
			if (mx >= x && mx < x + w && my >= ry && my < ry + rowH) {
				List<PvTooltip.Line> tip = new ArrayList<>();
				tip.add(PvTooltip.Line.title(u.label(), HEADER_COMMUNITY));
				tip.add(PvTooltip.Line.divider());
				tip.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED,
					max > 0 ? u.tier() + " / " + max : String.valueOf(u.tier()),
					maxed ? ENABLED : PvDraw.COLOR_GOLD));
				if (u.startedMs() > 0L) {
					tip.add(PvTooltip.Line.meta("Started " + formatAgo(u.startedMs())));
				}
				this.zones.add(new HoverZone(x, ry, w, rowH, tip));
			}
			ry += rowH;
		}
		return ry;
	}

	/** Prefer SkyBlock / pack texture, then skull stack, then vanilla, never blank paper. */
	private static void drawCommunityIcon(GuiGraphicsExtractor g, String upgrade, int x, int y, int size) {
		String sbId = communitySkyblockId(upgrade);
		if (sbId != null && !sbId.isBlank()) {
			ItemStack sky = SkyBlockItemFactory.iconStack(sbId);
			if (SkyBlockIconRenderer.hasKnownIcon(sbId)
				|| (sky != null && !sky.isEmpty() && !sky.is(Items.PAPER) && !sky.is(Items.BARRIER))) {
				SkyBlockIconRenderer.draw(g, sky, sbId, x, y, size);
				return;
			}
		}
		drawItemIcon(g, communityVanillaIcon(upgrade), x, y, size);
	}

	private static void drawItemIcon(GuiGraphicsExtractor g, ItemStack icon, int x, int y, int size) {
		if (icon == null || icon.isEmpty()) {
			return;
		}
		if (size >= 16) {
			int pad = (size - 16) / 2;
			g.item(icon, x + pad, y + pad);
			return;
		}
		float scale = size / 16F;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(icon, 0, 0);
		g.pose().popMatrix();
	}

	// Wiki total as of 0.27 (Torrhus Canyon + Safari souls).
	private static final int MAX_FAIRY_SOULS = 289;

	private static final Map<String, Integer> COMMUNITY_MAX = Map.ofEntries(
		Map.entry("island_size", 10),
		Map.entry("minion_slots", 5),
		Map.entry("guests_count", 5),
		Map.entry("coop_slots", 3),
		Map.entry("coins_allowance", 5),
		Map.entry("closet_slots", 9),
		Map.entry("armor_wardrobe_slots", 9),
		Map.entry("armor_closet_slots", 9),
		Map.entry("equipment_wardrobe_slots", 9),
		Map.entry("equipment_closet_slots", 9),
		Map.entry("loadout_slots", 9),
		Map.entry("loadouts", 9),
		Map.entry("hotm_slots", 3),
		Map.entry("hotf_slots", 3),
		Map.entry("heart_of_the_mountain", 3),
		Map.entry("heart_of_the_forest", 3),
		Map.entry("ender_chest_rows", 8),
		Map.entry("accessory_slot", 6),
		Map.entry("accessory_bag_slots", 6),
		Map.entry("sacks_count", 8),
		Map.entry("sack_slots", 8),
		Map.entry("magic_find", 5),
		Map.entry("bazaar_flipper", 2),
		Map.entry("garden_fortune", 10)
	);

	private static int communityMaxTier(String upgrade) {
		if (upgrade == null || upgrade.isBlank()) {
			return 0;
		}
		Integer max = COMMUNITY_MAX.get(upgrade.toLowerCase(Locale.ROOT));
		return max == null ? 0 : max;
	}

	/** NEU / SkyBlock id used for textured icons (null → vanilla only). */
	private static String communitySkyblockId(String upgrade) {
		if (upgrade == null || upgrade.isBlank()) {
			return "ELIZABETH_NPC";
		}
		return switch (upgrade.toLowerCase(Locale.ROOT)) {
			case "island_size" -> "GRASS";
			case "minion_slots" -> "COBBLESTONE_GENERATOR_1";
			case "guests_count" -> "OAK_DOOR";
			case "coop_slots" -> "ELIZABETH_NPC";
			case "coins_allowance" -> "ENCHANTED_GOLD";
			case "closet_slots", "armor_wardrobe_slots", "armor_closet_slots" -> "ARMOR_STAND";
			case "equipment_wardrobe_slots", "equipment_closet_slots" -> "GOLDEN_HELMET";
			case "loadout_slots", "loadouts" -> "CHEST";
			case "hotm_slots", "heart_of_the_mountain" -> "MINING_2_PORTAL";
			case "hotf_slots", "heart_of_the_forest" -> "FORAGING_2_PORTAL";
			case "ender_chest_rows" -> "ENDER_CHEST";
			case "accessory_slot", "accessory_bag_slots" -> "PERSONAL_COMPACTOR_7000";
			case "sacks_count", "sack_slots" -> "LARGE_ENCHANTED_MINING_SACK";
			case "magic_find" -> "TALISMAN_ENRICHMENT_MAGIC_FIND";
			case "bazaar_flipper" -> "ENCHANTED_GOLD_BLOCK";
			case "garden_fortune" -> "ENCHANTED_WHEAT";
			default -> "ELIZABETH_NPC";
		};
	}

	private static ItemStack communityVanillaIcon(String upgrade) {
		if (upgrade == null) {
			return new ItemStack(Items.NETHER_STAR);
		}
		String key = upgrade.toLowerCase(Locale.ROOT);
		return switch (key) {
			case "island_size" -> new ItemStack(Items.GRASS_BLOCK);
			case "minion_slots" -> new ItemStack(Items.WOODEN_PICKAXE);
			case "guests_count" -> new ItemStack(Items.OAK_DOOR);
			case "coop_slots" -> new ItemStack(Items.PLAYER_HEAD);
			case "coins_allowance" -> new ItemStack(Items.GOLD_INGOT);
			case "closet_slots", "armor_wardrobe_slots", "armor_closet_slots" -> new ItemStack(Items.LEATHER_CHESTPLATE);
			case "equipment_wardrobe_slots", "equipment_closet_slots" -> new ItemStack(Items.GOLDEN_HELMET);
			case "loadout_slots", "loadouts" -> new ItemStack(Items.CHEST);
			case "hotm_slots", "heart_of_the_mountain" -> new ItemStack(Items.DIAMOND_PICKAXE);
			case "hotf_slots", "heart_of_the_forest" -> new ItemStack(Items.OAK_SAPLING);
			case "ender_chest_rows" -> new ItemStack(Items.ENDER_CHEST);
			case "accessory_slot", "accessory_bag_slots" -> new ItemStack(Items.EMERALD);
			case "sacks_count", "sack_slots" -> new ItemStack(Items.CHEST_MINECART);
			case "magic_find" -> new ItemStack(Items.RABBIT_FOOT);
			case "bazaar_flipper" -> new ItemStack(Items.GOLD_NUGGET);
			case "garden_fortune" -> new ItemStack(Items.WHEAT);
			default -> {
				if (key.contains("minion")) {
					yield new ItemStack(Items.WOODEN_PICKAXE);
				}
				if (key.contains("garden") || key.contains("farm")) {
					yield new ItemStack(Items.WHEAT);
				}
				if (key.contains("sack")) {
					yield new ItemStack(Items.CHEST_MINECART);
				}
				if (key.contains("wardrobe") || key.contains("closet") || key.contains("armor")) {
					yield new ItemStack(Items.LEATHER_CHESTPLATE);
				}
				if (key.contains("bank") || key.contains("coin") || key.contains("bazaar")) {
					yield new ItemStack(Items.GOLD_INGOT);
				}
				yield new ItemStack(Items.NETHER_STAR);
			}
		};
	}

	private void drawGuildButton(GuiGraphicsExtractor g, Font font, int x, int y, int w, int mx, int my) {
		String label = this.guild.buttonLabel();
		int bw = Math.min(w, Math.max(72, font.width(label) + 12));
		int bh = font.lineHeight + 6;
		this.guildBtnX = x;
		this.guildBtnY = y;
		this.guildBtnW = bw;
		this.guildBtnH = bh;
		boolean hover = hit(mx, my, x, y, bw, bh);
		PvDraw.fill(g, x, y, bw, bh, ITEM_SLOT_BG);
		int outline = switch (this.guild.state()) {
			case READY -> PvDraw.COLOR_ACCENT;
			case NONE -> PvDraw.COLOR_MUTED;
			case ERROR -> DISABLED;
			case LOADING -> PvDraw.COLOR_MUTED;
			case IDLE -> hover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER;
		};
		g.outline(x, y, bw, bh, outline);
		int color = switch (this.guild.state()) {
			case READY -> this.guild.tagRgb();
			case NONE -> PvDraw.COLOR_MUTED;
			case ERROR -> DISABLED;
			case LOADING -> PvDraw.COLOR_MUTED;
			case IDLE -> PvDraw.COLOR_ACCENT;
		};
		PvDraw.textCentered(g, font, trim(font, label, bw - 8), x + bw / 2, y + (bh - font.lineHeight) / 2, color);

		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Guild", PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.divider());
		switch (this.guild.state()) {
			case IDLE -> tip.add(PvTooltip.Line.action("Click to load guild"));
			case LOADING -> tip.add(PvTooltip.Line.meta("Loading..."));
			case NONE -> tip.add(PvTooltip.Line.meta("Not in a guild"));
			case ERROR -> tip.add(PvTooltip.Line.meta(this.guild.error().isBlank() ? "Unavailable" : this.guild.error()));
			case READY -> {
				tip.add(PvTooltip.Line.row("Name", PvDraw.COLOR_MUTED, this.guild.name(), PvDraw.COLOR_TEXT));
				if (!this.guild.tag().isBlank()) {
					tip.add(PvTooltip.Line.row("Tag", PvDraw.COLOR_MUTED, "[" + this.guild.tag() + "]", this.guild.tagRgb()));
				}
				if (!this.guild.rank().isBlank()) {
					tip.add(PvTooltip.Line.row("Rank", PvDraw.COLOR_MUTED, this.guild.rank(), PvDraw.COLOR_GOLD));
				}
				tip.add(PvTooltip.Line.row("Members", PvDraw.COLOR_MUTED, FormatUtil.commas(this.guild.members()), PvDraw.COLOR_ACCENT));
				tip.add(PvTooltip.Line.row("EXP", PvDraw.COLOR_MUTED, FormatUtil.shortXp(this.guild.exp()), PvDraw.COLOR_TEXT));
				if (this.guild.joinedMs() > 0L) {
					tip.add(PvTooltip.Line.meta("Joined " + formatAgo(this.guild.joinedMs())));
				}
				if (this.guild.createdMs() > 0L) {
					tip.add(PvTooltip.Line.meta("Created " + formatAgo(this.guild.createdMs())));
				}
				if (!this.guild.description().isBlank()) {
					tip.add(PvTooltip.Line.blank());
					tip.add(PvTooltip.Line.meta(this.guild.description()));
				}
			}
		}
		this.zones.add(new HoverZone(x, y, bw, bh, tip));
	}

	private void drawRightFlip(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mx, int my
	) {
		this.rightHitX = x;
		this.rightHitY = y;
		this.rightHitW = w;
		this.rightHitH = h;

		boolean hovering = hit(mx, my, x, y, w, h);
		boolean showDeaths = this.rightDeathsFace;
		float scaleX = 1F;
		if (this.rightFlipStartMs != 0L) {
			float t = (System.currentTimeMillis() - this.rightFlipStartMs) / (float) FLIP_MS;
			if (t >= 1F) {
				this.rightDeathsFace = this.rightFlipTarget;
				this.rightFlipStartMs = 0L;
				showDeaths = this.rightDeathsFace;
			} else {
				float eased = easeInOutCubic(Math.max(0F, Math.min(1F, t)));
				double angle = eased * Math.PI;
				scaleX = (float) Math.max(0.04, Math.abs(Math.cos(angle)));
				showDeaths = Math.cos(angle) < 0 ? this.rightFlipTarget : this.rightDeathsFace;
			}
		}

		g.pose().pushMatrix();
		g.pose().translate(x + w / 2F, y + h / 2F);
		g.pose().scale(scaleX, 1F);
		g.pose().translate(-(x + w / 2F), -(y + h / 2F));

		PvDraw.innerPanel(g, x, y, w, h);
		if (hovering && this.rightFlipStartMs == 0L) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER);
		}

		List<MiscStatsSnapshot.CountEntry> rows = showDeaths ? this.snapshot.deaths() : this.snapshot.kills();
		long total = showDeaths ? this.snapshot.deathsTotal() : this.snapshot.killsTotal();
		String title = showDeaths ? "Deaths" : "Kills";
		int titleColor = showDeaths ? HEADER_DEATHS : HEADER_KILLS;

		int lx = x + PAD;
		int ly = y + PAD;
		int lw = w - PAD * 2;
		PvDraw.text(g, font, title, lx, ly, titleColor);
		PvDraw.textRight(g, font, FormatUtil.commas(total), lx + lw, ly, titleColor);
		ly += font.lineHeight + 4;

		int listTop = ly;
		int listBottom = y + h - PAD;
		int listH = Math.max(STAT_ROW, listBottom - listTop);
		// Top entries only, laid out as a two-column grid so nothing scrolls or clips.
		int cols = 2;
		int colGap = 10;
		int cellW = (lw - colGap) / cols;
		int cellH = STAT_ROW + 7;
		int fit = cols * Math.max(1, listH / cellH);
		long maxCount = 1L;
		for (MiscStatsSnapshot.CountEntry entry : rows) {
			maxCount = Math.max(maxCount, entry.count());
		}
		this.listMaxScroll = 0;
		this.listScroll = 0;
		int shown = Math.min(rows.size(), fit);
		int perCol = (shown + cols - 1) / cols;
		for (int i = 0; i < shown; i++) {
			MiscStatsSnapshot.CountEntry entry = rows.get(i);
			int col = i / Math.max(1, perCol);
			int row = i % Math.max(1, perCol);
			int cx = lx + col * (cellW + colGap);
			int cy = listTop + row * cellH;
			String value = FormatUtil.shortXp(entry.count());
			PvDraw.text(g, font, trim(font, entry.label(), cellW - font.width(value) - 6), cx, cy,
				i < 3 ? PvDraw.COLOR_TEXT : PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, value, cx + cellW, cy, titleColor);
			int barY = cy + font.lineHeight;
			PvDraw.fill(g, cx, barY, cellW, 2, PvDraw.COLOR_BAR_BG);
			PvDraw.fill(g, cx, barY, (int) Math.max(1L, (long) cellW * entry.count() / maxCount), 2, titleColor);
			if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH) {
				this.zones.add(new HoverZone(cx, cy, cellW, cellH, List.of(
					PvTooltip.Line.title(entry.label(), titleColor),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row(title, PvDraw.COLOR_MUTED, FormatUtil.commas(entry.count()), titleColor)
				)));
			}
		}
		if (rows.size() > shown) {
			PvDraw.textRight(g, font, "+" + (rows.size() - shown) + " more", lx + lw, y + h - PAD - font.lineHeight, PvDraw.COLOR_MUTED);
		}

		if (rows.isEmpty()) {
			PvDraw.textCentered(g, font, "No " + title.toLowerCase(),
				x + w / 2, listTop + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
		}

		g.pose().popMatrix();
	}

	private int stat(
		GuiGraphicsExtractor g, Font font, String label, String value,
		int x, int y, int w, int valueColor, List<PvTooltip.Line> tip
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value == null || value.isBlank() ? "-" : value, x + w, y, valueColor);
		if (tip != null && !tip.isEmpty()) {
			this.zones.add(new HoverZone(x, y, w, STAT_ROW, tip));
		}
		return y + STAT_ROW;
	}

	private static int separator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static String formatAgo(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		return FormatUtil.prettySpan(Math.max(0L, System.currentTimeMillis() - ms)) + " ago";
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = Math.max(0, maxW - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private static boolean hit(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}
