/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home;

import fishmod.pv.BetterPV;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.SkyBlockLevelColors;
import fishmod.pv.networth.InventoryDecoder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** SkyBlock XP strip expand/collapse overlay for Home. */
final class HomeSbXpOverlay {
	private static final Identifier SKYBLOCK_XP_ICON =
		Identifier.fromNamespaceAndPath(BetterPV.MOD_ID, "textures/gui/skyblock_xp.png");
	private static final int SKYBLOCK_XP_TEX_SIZE = 64;
	private static final int ICON_SIZE = 16;
	private static final int BAR_H = 6;
	private static final int PAD = 6;
	private static final int SB_XP_STAGE_MS = 300;
	static final int SB_XP_MASK = 0xB0000000;

	private HomePage.SbXpExpandPhase phase = HomePage.SbXpExpandPhase.CLOSED;
	private long animStartMs;
	private int hitX;
	private int hitY;
	private int hitW;
	private int hitH;
	private int homeBoundsX;
	private int homeBoundsY;
	private int homeBoundsW;
	private int homeBoundsH;
	private int emblemScroll;
	private int emblemMaxScroll;
	private int emblemListX;
	private int emblemListY;
	private int emblemListW;
	private int emblemListH;

	void resetEmblemScroll() {
		this.emblemScroll = 0;
	}

	void setLayoutBounds(int homeX, int homeY, int homeW, int homeH, int stripX, int stripW) {
		this.homeBoundsX = homeX;
		this.homeBoundsY = homeY;
		this.homeBoundsW = homeW;
		this.homeBoundsH = homeH;
		this.hitX = stripX;
		this.hitY = homeY;
		this.hitW = stripW;
		this.hitH = homeH;
	}

	HomePage.SbXpExpandPhase phase() {
		tick();
		return this.phase;
	}

	boolean isActive() {
		tick();
		return this.phase != HomePage.SbXpExpandPhase.CLOSED;
	}

	void forceClose() {
		this.phase = HomePage.SbXpExpandPhase.CLOSED;
		this.animStartMs = 0L;
	}

	boolean click(double mouseX, double mouseY) {
		tick();
		if (this.phase == HomePage.SbXpExpandPhase.EXPANDING_LEFT
			|| this.phase == HomePage.SbXpExpandPhase.EXPANDING_RIGHT
			|| this.phase == HomePage.SbXpExpandPhase.COLLAPSING_RIGHT
			|| this.phase == HomePage.SbXpExpandPhase.COLLAPSING_LEFT) {
			return true;
		}
		if (this.phase == HomePage.SbXpExpandPhase.OPEN) {
			if (mouseX >= this.homeBoundsX && mouseX < this.homeBoundsX + this.homeBoundsW
				&& mouseY >= this.homeBoundsY && mouseY < this.homeBoundsY + this.homeBoundsH) {
				startCollapse();
				return true;
			}
			return false;
		}
		if (mouseX >= this.hitX && mouseX < this.hitX + this.hitW
			&& mouseY >= this.hitY && mouseY < this.hitY + this.hitH
			&& this.hitW > 0) {
			startExpand();
			return true;
		}
		return false;
	}

	boolean requestBack() {
		tick();
		if (this.phase == HomePage.SbXpExpandPhase.CLOSED
			|| this.phase == HomePage.SbXpExpandPhase.COLLAPSING_RIGHT
			|| this.phase == HomePage.SbXpExpandPhase.COLLAPSING_LEFT) {
			return false;
		}
		if (this.phase == HomePage.SbXpExpandPhase.EXPANDING_LEFT) {
			// Reverse mid-expand: collapse left from current progress.
			float p = stageProgress();
			this.phase = HomePage.SbXpExpandPhase.COLLAPSING_LEFT;
			this.animStartMs = System.currentTimeMillis() - Math.round((1F - p) * SB_XP_STAGE_MS);
			return true;
		}
		if (this.phase == HomePage.SbXpExpandPhase.EXPANDING_RIGHT) {
			float p = stageProgress();
			this.phase = HomePage.SbXpExpandPhase.COLLAPSING_RIGHT;
			this.animStartMs = System.currentTimeMillis() - Math.round((1F - p) * SB_XP_STAGE_MS);
			return true;
		}
		startCollapse();
		return true;
	}

	void tick() {
		if (this.phase == HomePage.SbXpExpandPhase.CLOSED || this.phase == HomePage.SbXpExpandPhase.OPEN) {
			return;
		}
		if (stageProgress() < 1F) {
			return;
		}
		this.phase = switch (this.phase) {
			case EXPANDING_LEFT -> HomePage.SbXpExpandPhase.EXPANDING_RIGHT;
			case EXPANDING_RIGHT -> HomePage.SbXpExpandPhase.OPEN;
			case COLLAPSING_RIGHT -> HomePage.SbXpExpandPhase.COLLAPSING_LEFT;
			case COLLAPSING_LEFT -> HomePage.SbXpExpandPhase.CLOSED;
			default -> this.phase;
		};
		if (this.phase == HomePage.SbXpExpandPhase.EXPANDING_RIGHT
			|| this.phase == HomePage.SbXpExpandPhase.COLLAPSING_LEFT) {
			this.animStartMs = System.currentTimeMillis();
		} else if (this.phase == HomePage.SbXpExpandPhase.OPEN || this.phase == HomePage.SbXpExpandPhase.CLOSED) {
			this.animStartMs = 0L;
		}
	}

	float stageProgress() {
		if (this.animStartMs == 0L) {
			return 1F;
		}
		return Math.min(1F, (System.currentTimeMillis() - this.animStartMs) / (float) SB_XP_STAGE_MS);
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.phase == HomePage.SbXpExpandPhase.OPEN && this.emblemMaxScroll > 0
			&& mouseX >= this.emblemListX && mouseX < this.emblemListX + this.emblemListW
			&& mouseY >= this.emblemListY && mouseY < this.emblemListY + this.emblemListH) {
			int next = Math.max(0, Math.min(
				this.emblemMaxScroll,
				this.emblemScroll - (int) Math.round(scrollY * 12)
			));
			if (next != this.emblemScroll) {
				this.emblemScroll = next;
				return true;
			}
		}
		return false;
	}

	void draw(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		ProfileSnapshot snapshot,
		float contentAlpha,
		boolean registerHover,
		List<HomePage.HoverZone> zones
	) {
		PvDraw.innerPanel(g, x, y, w, h);
		boolean emptyPage = this.phase == HomePage.SbXpExpandPhase.OPEN
			|| (this.phase == HomePage.SbXpExpandPhase.EXPANDING_RIGHT && contentAlpha < 0.05F)
			|| (this.phase == HomePage.SbXpExpandPhase.COLLAPSING_RIGHT && contentAlpha < 0.05F);
		if (emptyPage) {
			drawExpanded(g, font, x, y, w, h, snapshot);
			return;
		}
		if (contentAlpha < 0.04F) {
			return;
		}

		int level = snapshot.skyBlockLevel();
		int xp = snapshot.skyBlockXpIntoLevel();
		String levelText = Component.translatable("betterpv.home.sb_level", level).getString();
		int levelColor = fadeColor(SkyBlockLevelColors.colorFor(level), contentAlpha);
		int muted = fadeColor(PvDraw.COLOR_MUTED, contentAlpha);
		int white = fadeColor(PvDraw.COLOR_WHITE, contentAlpha);
		int barW = Math.min(w - 16, 48);
		int cx = x + w / 2;

		int blockH = font.lineHeight + 4 + ICON_SIZE + 4 + font.lineHeight + 2 + BAR_H;
		ProfileSnapshot.EmblemInfo compactEmblems = snapshot.emblems();
		boolean showEmblemCount = compactEmblems != null && compactEmblems.present();
		if (showEmblemCount) {
			blockH += 4 + font.lineHeight;
		}
		int ty = y + Math.max(PAD, (h - blockH) / 2);

		PvDraw.textCentered(g, font, levelText, cx, ty, levelColor);
		ty += font.lineHeight + 4;
		g.blit(
			RenderPipelines.GUI_TEXTURED,
			SKYBLOCK_XP_ICON,
			cx - ICON_SIZE / 2, ty,
			0, 0,
			ICON_SIZE, ICON_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE
		);
		ty += ICON_SIZE + 4;

		Component xpLine = Component.empty()
			.append(PvDraw.styled(String.valueOf(xp), white, false))
			.append(PvDraw.styled("/", muted, false))
			.append(PvDraw.styled("100", levelColor, false));
		PvDraw.textCentered(g, font, xpLine, cx, ty);
		ty += font.lineHeight + 2;

		int barX = cx - barW / 2;
		PvDraw.progressBar(g, barX, ty, barW, BAR_H, snapshot.skyBlockProgress(), levelColor);
		if (showEmblemCount) {
			ty += BAR_H + 4;
			PvDraw.textCentered(
				g, font,
				"Emblems: " + compactEmblems.unlocked().size(),
				cx, ty, muted
			);
		}
		if (registerHover && contentAlpha > 0.85F) {
			double pct = snapshot.skyBlockProgress() * 100.0;
			List<PvTooltip.Line> xpHover = new ArrayList<>();
			xpHover.add(PvTooltip.Line.of("SkyBlock Level " + level, SkyBlockLevelColors.colorFor(level)));
			xpHover.add(PvTooltip.Line.of(xp + "/100 (" + Math.round(pct) + "%)", PvDraw.COLOR_GOLD));
			xpHover.addAll(emblemHoverLines(snapshot.emblems(), true));
			xpHover.add(PvTooltip.Line.of("Click to open", PvDraw.COLOR_MUTED));
			zones.add(new HomePage.HoverZone(x, y, w, h, xpHover));
		}
	}

	private void drawExpanded(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, ProfileSnapshot snapshot
	) {
		int level = snapshot.skyBlockLevel();
		int xp = snapshot.skyBlockXpIntoLevel();
		int levelColor = SkyBlockLevelColors.colorFor(level);
		int pad = PAD + 6;
		int ly = y + pad;
		int barW = Math.min(w - pad * 2, 160);

		PvDraw.textCentered(g, font, Component.translatable("betterpv.home.sb_level", level).getString(),
			x + w / 2, ly, levelColor);
		ly += font.lineHeight + 6;
		Component xpLine = Component.empty()
			.append(PvDraw.styled(String.valueOf(xp), PvDraw.COLOR_WHITE, false))
			.append(PvDraw.styled(" / ", PvDraw.COLOR_MUTED, false))
			.append(PvDraw.styled("100", levelColor, false));
		PvDraw.textCentered(g, font, xpLine, x + w / 2, ly);
		ly += font.lineHeight + 4;
		PvDraw.progressBar(g, x + (w - barW) / 2, ly, barW, BAR_H, snapshot.skyBlockProgress(), levelColor);
		ly += BAR_H + 12;

		this.emblemListH = 0;
		this.emblemMaxScroll = 0;
		PvDraw.textCentered(g, font, "Coming soon", x + w / 2, ly + 20, PvDraw.COLOR_MUTED);
	}

	private void startExpand() {
		this.phase = HomePage.SbXpExpandPhase.EXPANDING_LEFT;
		this.animStartMs = System.currentTimeMillis();
	}

	private void startCollapse() {
		this.phase = HomePage.SbXpExpandPhase.COLLAPSING_RIGHT;
		this.animStartMs = System.currentTimeMillis();
	}

	private static List<PvTooltip.Line> emblemHoverLines(ProfileSnapshot.EmblemInfo emblems, boolean includeHeader) {
		if (emblems == null || !emblems.present()) {
			return List.of();
		}
		List<PvTooltip.Line> lines = new ArrayList<>();
		if (includeHeader) {
			lines.add(PvTooltip.Line.divider());
		}
		lines.add(PvTooltip.Line.row(
			"Emblems", PvDraw.COLOR_MUTED, emblems.unlocked().size() + " unlocked", PvDraw.COLOR_ACCENT
		));
		if (!emblems.selected().isBlank()) {
			lines.add(PvTooltip.Line.row(
				"Selected", PvDraw.COLOR_MUTED, InventoryDecoder.prettyWords(emblems.selected()), PvDraw.COLOR_GOLD
			));
		}
		return lines;
	}

	private static int fadeColor(int argb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * Math.max(0F, Math.min(1F, alpha)))));
		return (a << 24) | (argb & 0x00FFFFFF);
	}
}
