/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fishmod.pv.BetterPV;
import fishmod.pv.api.BetterPVConfig;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.api.ProfileFetcher;
import fishmod.pv.data.MiscStatsSnapshot;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.gui.bestiary.BestiaryPage;
import fishmod.pv.gui.events.EventsPage;
import fishmod.pv.gui.foraging.ForagingPage;
import fishmod.pv.gui.mining.MiningPage;
import fishmod.pv.gui.museum.MuseumPage;
import fishmod.pv.gui.nav.MuseumSort;
import fishmod.pv.gui.collections.CollectionsPage;
import fishmod.pv.gui.crimson.CrimsonPage;
import fishmod.pv.gui.dungeons.DungeonChestsPage;
import fishmod.pv.gui.dungeons.DungeonPage;
import fishmod.pv.gui.fishing.FishingPage;
import fishmod.pv.gui.fishing.FishingTrophyTrialPage;
import fishmod.pv.gui.garden.GardenPage;
import fishmod.pv.gui.home.HomePage;
import fishmod.pv.gui.home.page.EssencePage;
import fishmod.pv.gui.home.page.MiscStatsPage;
import fishmod.pv.gui.home.page.RecordsPage;
import fishmod.pv.gui.inventories.InventoryPage;
import fishmod.pv.gui.nav.IconButtonBar;
import fishmod.pv.gui.nav.InventoryPane;
import fishmod.pv.gui.nav.PvSubTab;
import fishmod.pv.gui.nav.PvTab;
import fishmod.pv.gui.pets.PetsPage;
import fishmod.pv.gui.rift.RiftPage;
import java.net.URI;
import java.net.URISyntaxException;
import fishmod.pv.price.HypixelCollectionsCache;
import fishmod.pv.gui.collections.CollectionCategoryIcons;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ProfileViewerScreen extends Screen {
	private static final int PAD = 8;
	private static final int TARGET_GUI_SCALE = 2;
	private static final int BOTTOM_FIT_ROOM = 39;
	private static final int TIP_SLIDER_GAP = 8;
	private static final long OPEN_ANIM_MS = 260L;
	private static final float OPEN_SCALE_START = 0.12F;

	private final String requestedName;
	private final HomePage homePage;
	private final MiscStatsPage homeMiscPage = new MiscStatsPage();
	private final RecordsPage homeRecordsPage = new RecordsPage();
	private final EssencePage homeEssencePage = new EssencePage();
	private final DungeonPage dungeonPage = new DungeonPage();
	private final DungeonChestsPage dungeonChestsPage = new DungeonChestsPage();
	private final InventoryPage inventoryPage = new InventoryPage();
	private final PetsPage petsPage = new PetsPage();
	private final CollectionsPage collectionsPage = new CollectionsPage();
	private final GardenPage gardenPage = new GardenPage();
	private final FishingPage fishingPage = new FishingPage();
	private final FishingTrophyTrialPage fishingTrophyTrialPage = new FishingTrophyTrialPage();
	private final CrimsonPage crimsonPage = new CrimsonPage();
	private final RiftPage riftPage = new RiftPage();
	private final IconButtonBar topBar = new IconButtonBar();
	private final IconButtonBar sideBar = new IconButtonBar();
	private final IconButtonBar inventoryBar = new IconButtonBar();
	private final Map<PvTab, PvSubTab> subSelection = new EnumMap<>(PvTab.class);
	private final long openAnimStartMs = System.currentTimeMillis();
	private final ProfileSelectorController profileSelector = new ProfileSelectorController(this);
	private final ProfileScreenDataLoader dataLoader = new ProfileScreenDataLoader(this);
	private final InventorySplitLayout inventoryLayout = new InventorySplitLayout();
	private final BestiaryPage bestiaryPage = new BestiaryPage();
	private final MiningPage miningPage = new MiningPage();
	private final ForagingPage foragingPage = new ForagingPage();
	private final MuseumPage museumPage = new MuseumPage();
	private final EventsPage eventsPage = new EventsPage();
	private MuseumSort museumSort = MuseumSort.ALL;
	private final BestiarySplitLayout bestiaryLayout = new BestiarySplitLayout();
	private float openPivotX;
	private float openPivotY;
	private final ProfileScaleSlider scaleSlider = new ProfileScaleSlider();
	private final SocialsMenu socialsMenu = new SocialsMenu(this::socialTarget, this::openExternalLink);
	private final SearchHistoryMenu searchHistory = new SearchHistoryMenu(BetterPVConfig::searchHistory, this::openSearchedPlayer);
	private boolean recordSearch;
	private ProfileViewerScale.Viewport scaleViewport;
	private ProfileViewerScale.Viewport sliderViewport;

	private PvTab tab = PvTab.HOME;
	private boolean fetchStarted;
	private boolean dataReady;
	private String loadCorrelationId;
	private long loadStartedNanos;
	private boolean firstVisibleLogged;
	/** Set when fetch fails (e.g. unknown IGN). Shown instead of infinite loading. */
	private String loadError;
	/** Bumps on each fetch/switch so stale async results cannot overwrite newer state. */
	private int loadGeneration;
	private final Consumer<ProfileFetcher.LoadedProfile> networthListener = this::onDeferredNetworth;
	private boolean breakScheduled;
	private String profileId;
	private String preferredProfileId;
	private UUID playerUuid;
	private EditBox inventorySearch;
	private String inventorySearchQuery = "";
	private List<IconButtonBar.Entry> topTabEntries;
	private PvTab cachedSideTab;
	private List<IconButtonBar.Entry> sideTabEntries = List.of();

	private JsonObject profilesRoot;
	private int panelXCache;
	private int panelYCache;
	private int panelWCache;
	private int panelHCache;
	private EditBox playerSearch;
	private String playerSearchError = "";
	private long playerSearchErrorUntilMs;

	public ProfileViewerScreen(String playerName) {
		this(playerName, PvTab.HOME);
	}

	public ProfileViewerScreen(String playerName, PvTab initialTab) {
		this(playerName, initialTab, null);
	}

	public ProfileViewerScreen(String playerName, PvTab initialTab, PvSubTab initialSub) {
		super(Component.translatable("betterpv.screen.title"));
		this.requestedName = playerName == null || playerName.isBlank() ? "?" : playerName.trim();
		this.homePage = new HomePage(ProfileSnapshot.loading(this.requestedName));
		this.tab = initialTab == null ? PvTab.HOME : initialTab;
		for (PvTab t : PvTab.values()) {
			PvSubTab[] subs = t.subTabs();
			if (subs.length > 0) {
				this.subSelection.put(t, subs[0]);
			}
		}
		if (initialSub != null && new PvTab.Route(this.tab, initialSub).valid()) {
			this.subSelection.put(this.tab, initialSub);
		}
	}

	/** Same SkyBlock profile and page, different co-op member. */
	ProfileViewerScreen forCoopMember(String playerName, String coopProfileId) {
		ProfileViewerScreen next = new ProfileViewerScreen(playerName, this.tab);
		next.subSelection.putAll(this.subSelection);
		next.homeEssencePage.select(this.homeEssencePage.selectedEntry().def().id());
		next.museumSort = this.museumSort;
		next.preferredProfileId = coopProfileId;
		return next;
	}

	@Override
	protected void init() {
		super.init();
		PvTheme.apply(fishmod.utils.config.values.FishSettings.pvTheme);
		try {
			java.lang.reflect.Field fontField = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("font");
			fontField.setAccessible(true);
			fontField.set(this, PvFont.get(Minecraft.getInstance().font));
		} catch (ReflectiveOperationException | RuntimeException exception) {
			BetterPV.LOGGER.warn("Could not swap the viewer font", exception);
		}
		this.inventorySearch = new EditBox(
			this.font,
			0,
			0,
			100,
			InventorySplitLayout.SEARCH_H,
			Component.translatable("betterpv.inv.search")
		);
		this.inventorySearch.setMaxLength(64);
		this.inventorySearch.setBordered(false);
		// EditBox colours are ARGB - stripping alpha makes typed text invisible.
		this.inventorySearch.setTextColor(PvDraw.COLOR_TEXT);
		this.inventorySearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.inventorySearch.setValue(this.inventorySearchQuery);
		this.inventorySearch.setResponder(value -> {
			this.inventorySearchQuery = value;
			this.inventoryPage.setSearchQuery(value);
			this.bestiaryPage.setSearchQuery(value);
		});
		this.inventorySearch.setVisible(false);
		this.addWidget(this.inventorySearch);

		this.playerSearch = new EditBox(
			this.font,
			0,
			0,
			120,
			InventorySplitLayout.SEARCH_H,
			Component.translatable("betterpv.home.search_hint")
		);
		this.playerSearch.setMaxLength(16);
		this.playerSearch.setBordered(false);
		this.playerSearch.setTextColor(PvDraw.COLOR_TEXT);
		this.playerSearch.setTextColorUneditable(PvDraw.COLOR_MUTED);
		this.playerSearch.setHint(Component.translatable("betterpv.home.search_hint"));
		this.playerSearch.setVisible(false);
		this.addWidget(this.playerSearch);

		if (this.fetchStarted) {
			// removed() drops this while a link confirm screen is up; late networth still needs to land.
			ProfileFetcher.addNetworthListener(this.networthListener);
			return;
		}
		this.fetchStarted = true;
		this.loadCorrelationId = UUID.randomUUID().toString();
		this.loadStartedNanos = System.nanoTime();
		this.firstVisibleLogged = false;
		int generation = ++this.loadGeneration;
		ProfileFetcher.prioritizeTab(this.tab);
		ProfileFetcher.addNetworthListener(this.networthListener);
		ProfileFetcher.fetch(this.requestedName, this.preferredProfileId, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (fishmod.compat.McCompatKt.getCurScreen(client) != this || generation != this.loadGeneration) {
					return;
				}
				if (updated == null || !updated.ok()) {
					return;
				}
				applyLoadedProfile(updated);
			});
		}, this.loadCorrelationId, this.loadStartedNanos).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (fishmod.compat.McCompatKt.getCurScreen(client) != this || generation != this.loadGeneration) {
					return;
				}
				ProfileFetcher.LoadedProfile displayed = loaded;
				if (error != null || loaded == null) {
					BetterPV.LOGGER.warn(
						"Profile fetch failed for {}",
						this.requestedName,
						error
					);
					String message = error != null && error.getMessage() != null
						? error.getMessage()
						: "Couldn't load profile";
					displayed = ProfileFetcher.failed(this.requestedName, message);
				}
				if (!displayed.ok()) {
					BetterPV.LOGGER.warn("Profile fetch failed for {}: {}", this.requestedName, displayed.error());
					this.loadError = friendlyLoadError(displayed.error());
					return;
				}
				this.loadError = null;
				applyLoadedProfile(displayed);
				this.dataReady = true;
				if (this.recordSearch && displayed.snapshot() != null) {
					this.recordSearch = false;
					BetterPVConfig.recordSearch(displayed.snapshot().playerUuid(), displayed.snapshot().playerName());
				}
				BetterPV.LOGGER.info(
					"[PV timing] event=render_thread_applied correlation_id={} player={} elapsed_ms={}",
					this.loadCorrelationId,
					this.requestedName,
					(System.nanoTime() - this.loadStartedNanos) / 1_000_000L
				);
			});
		});
	}

	private void onDeferredNetworth(ProfileFetcher.LoadedProfile updated) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || updated == null || !updated.ok()) {
			return;
		}
		client.execute(() -> {
			if (fishmod.compat.McCompatKt.getCurScreen(client) != this || !this.dataReady) {
				return;
			}
			if (this.playerUuid != null && updated.snapshot() != null
				&& this.playerUuid.equals(updated.snapshot().playerUuid())
				&& (this.profileId == null || this.profileId.equals(updated.profileId()))) {
				applyLoadedProfile(updated);
			}
		});
	}

	@Override
	public void removed() {
		this.socialsMenu.close();
		this.searchHistory.close();
		ProfileFetcher.removeNetworthListener(this.networthListener);
		super.removed();
	}

	void applyLoadedProfile(ProfileFetcher.LoadedProfile loaded) {
		if (loaded == null) {
			return;
		}
		UUID prevUuid = this.playerUuid;
		String prevProfileId = this.profileId;
		UUID nextUuid = loaded.snapshot() == null ? null : loaded.snapshot().playerUuid();
		String nextProfileId = loaded.profileId();
		boolean sameIdentity = prevUuid != null
			&& prevUuid.equals(nextUuid)
			&& prevProfileId != null
			&& prevProfileId.equals(nextProfileId);

		this.profileSelector.clearFooterNameCache();
		this.homePage.applyLoaded(
			loaded.snapshot(),
			loaded.senither(),
			loaded.lily(),
			loaded.networthNormal(),
			loaded.networthNonCosmetic(),
			loaded.networthUnsoulbound(),
			loaded.networthUnsoulboundNonCosmetic(),
			loaded.armor(),
			loaded.playerStats(),
			loaded.error()
		);
		this.homeMiscPage.reset();
		this.homeMiscPage.apply(loaded.misc() == null ? MiscStatsSnapshot.empty() : loaded.misc());
		this.homeMiscPage.setGuildClickHandler(this.dataLoader::ensureGuild);
		this.homeRecordsPage.apply(loaded.misc());
		this.homeEssencePage.apply(loaded.dungeons(), loaded.mining(), loaded.foraging(), loaded.crimson());
		this.dungeonPage.apply(loaded.dungeons());
		this.dungeonChestsPage.apply(loaded.dungeons());
		this.inventoryPage.apply(loaded.inventories());
		this.petsPage.apply(loaded.pets());
		this.collectionsPage.apply(loaded.collections());
		this.gardenPage.apply(loaded.garden());
		this.gardenPage.setPlayerStats(loaded.playerStats());
		this.miningPage.apply(loaded.mining());
		this.miningPage.setPlayerStats(loaded.playerStats());
		this.foragingPage.apply(loaded.foraging());
		this.fishingPage.apply(loaded.fishing(), loaded.bestiary());
		this.crimsonPage.apply(loaded.crimson());
		this.riftPage.apply(loaded.rift());
		this.bestiaryPage.apply(loaded.bestiary());
		this.eventsPage.apply(loaded.events());
		if (loaded.museumMember() != null) {
			this.museumPage.applyMuseum(loaded.museumMember());
		} else if (!sameIdentity) {
			this.museumPage.reset();
		}
		this.profileId = loaded.profileId();
		this.playerUuid = nextUuid;
		if (loaded.profilesRoot() != null) {
			this.profilesRoot = loaded.profilesRoot();
		}
		String viewed = this.playerUuid == null ? "" : HypixelApiClient.undashed(this.playerUuid);
		this.profileSelector.applyChoices(loaded.profiles(), viewed, this.homePage.playerName());
		if (!sameIdentity) {
			this.dataLoader.resetForNewLoad();
			if (loaded.museumMember() != null) {
				this.dataLoader.markMuseumLoaded();
			}
			this.eventsPage.resetBingoFetch();
			this.dataLoader.ensurePlayerRank();
		} else if (loaded.museumMember() != null) {
			this.dataLoader.markMuseumLoaded();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int screenWidth = this.width;
		int screenHeight = this.height;
		Minecraft client = Minecraft.getInstance();
		int framebufferWidth = client != null && client.getWindow() != null
			? client.getWindow().getWidth()
			: screenWidth * TARGET_GUI_SCALE;
		int framebufferHeight = client != null && client.getWindow() != null
			? client.getWindow().getHeight()
			: screenHeight * TARGET_GUI_SCALE;
		int guiScale = client != null && client.getWindow() != null
			? client.getWindow().getGuiScale()
			: TARGET_GUI_SCALE;
		this.sliderViewport = ProfileViewerScale.viewport(
			screenWidth, screenHeight, framebufferWidth, framebufferHeight, guiScale, 100, null
		);
		int virtualWidth = this.sliderViewport.virtualWidth();
		int virtualHeight = this.sliderViewport.virtualHeight();
		this.scaleSlider.layoutViewport(virtualWidth, virtualHeight);

		int topRoom = IconButtonBar.TAB + 4;
		int leftRoom = IconButtonBar.TAB + 4;
		int tabsW = 8 + PvTab.values().length * (IconButtonBar.TAB + IconButtonBar.GAP);
		int panelW = Math.min(Math.max(520, tabsW), Math.max(Math.max(420, tabsW), virtualWidth - 80 - leftRoom));
		int contentH = this.homePage.preferredHeight(this.font, panelW - PAD * 2);
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) != PvSubTab.HOME_OVERVIEW) {
			contentH = Math.max(contentH, 220);
		}
		int maxPanelH = Math.max(200, virtualHeight - topRoom - 24);
		int panelH = Math.min(maxPanelH, Math.max(200, contentH + PAD * 2));
		int panelX = (virtualWidth - panelW) / 2 + leftRoom / 2;
		int panelY = (virtualHeight - panelH - topRoom) / 2 + topRoom;

		ProfileViewerScale.Bounds bounds = new ProfileViewerScale.Bounds(
			panelX - leftRoom - 2,
			panelY - topRoom - 2,
			panelX + panelW + 2,
			panelY + panelH + BOTTOM_FIT_ROOM
		);
		int selectedPercent = BetterPVConfig.profileViewerScalePercent();
		this.scaleViewport = ProfileViewerScale.viewport(
			screenWidth, screenHeight, framebufferWidth, framebufferHeight, guiScale, selectedPercent, bounds
		);
		IconButtonBar.setVirtualScreen(virtualW(), virtualH());
		int virtualMouseX = (int) Math.round(this.scaleViewport.toVirtualX(mouseX));
		int virtualMouseY = (int) Math.round(this.scaleViewport.toVirtualY(mouseY));

		graphics.pose().pushMatrix();
		graphics.pose().translate(this.scaleViewport.offsetX(), this.scaleViewport.offsetY());
		graphics.pose().scale(this.scaleViewport.renderScale(), this.scaleViewport.renderScale());
		int previousWidth = this.width;
		int previousHeight = this.height;
		this.width = virtualWidth;
		this.height = virtualHeight;
		try {
			super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
			int dimLeft = (int) Math.floor(this.scaleViewport.toVirtualX(0));
			int dimTop = (int) Math.floor(this.scaleViewport.toVirtualY(0));
			int dimRight = (int) Math.ceil(this.scaleViewport.toVirtualX(screenWidth));
			int dimBottom = (int) Math.ceil(this.scaleViewport.toVirtualY(screenHeight));
			PvDraw.fill(graphics, dimLeft, dimTop, dimRight - dimLeft, dimBottom - dimTop, 0x99000000);
			this.socialsMenu.setViewport(dimLeft, dimTop, dimRight, dimBottom);
			this.searchHistory.setViewport(dimLeft, dimTop, dimRight, dimBottom);
			extractProfileRenderState(
				graphics, virtualMouseX, virtualMouseY, delta,
				panelX, panelY, panelW, panelH
			);
		} finally {
			this.width = previousWidth;
			this.height = previousHeight;
			graphics.pose().popMatrix();
		}

		int sliderMouseX = (int) Math.round(this.sliderViewport.toVirtualX(mouseX));
		int sliderMouseY = (int) Math.round(this.sliderViewport.toVirtualY(mouseY));
		graphics.nextStratum();
		graphics.pose().pushMatrix();
		graphics.pose().translate(this.sliderViewport.offsetX(), this.sliderViewport.offsetY());
		graphics.pose().scale(this.sliderViewport.renderScale(), this.sliderViewport.renderScale());
		try {
			// scale slider hidden
		} finally {
			graphics.pose().popMatrix();
		}

		if (this.dataReady && (this.socialsMenu.isOpen() || this.searchHistory.isVisible())) {
			// Last stratum so the tip and scale slider can't draw over the popup.
			graphics.nextStratum();
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.scaleViewport.offsetX(), this.scaleViewport.offsetY());
			graphics.pose().scale(this.scaleViewport.renderScale(), this.scaleViewport.renderScale());
			try {
				this.socialsMenu.renderPopup(graphics, this.font, virtualMouseX, virtualMouseY);
				this.searchHistory.render(graphics, this.font, virtualMouseX, virtualMouseY);
			} finally {
				graphics.pose().popMatrix();
			}
		}
	}

	private void extractProfileRenderState(
		GuiGraphicsExtractor graphics,
		int mouseX,
		int mouseY,
		float delta,
		int panelX,
		int panelY,
		int panelW,
		int panelH
	) {
		this.topBar.clearHits();
		this.sideBar.clearHits();
		this.inventoryBar.clearHits();
		this.panelXCache = panelX;
		this.panelYCache = panelY;
		this.panelWCache = panelW;
		this.panelHCache = panelH;
		float scale = openScale();
		this.openPivotX = panelX + panelW / 2.0F;
		this.openPivotY = panelY + panelH / 2.0F;
		boolean scaled = scale < 0.999F;
		if (scaled) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(this.openPivotX, this.openPivotY);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-this.openPivotX, -this.openPivotY);
		}

		PvDraw.panel(graphics, panelX, panelY, panelW, panelH);
		if (!this.dataReady) {
			InventorySplitLayout.hideInventorySearch(this.inventorySearch);
			hidePlayerSearch();
			drawLoadingFace(graphics, panelX, panelY, panelW, panelH);
		} else {
			this.topBar.drawTopFrameTabs(
				graphics, this.font, panelX, panelY, mouseX, mouseY, topTabEntries(), this.tab
			);
			Object[] left = this.tab.leftTabs();
			if (left.length > 0) {
				List<IconButtonBar.Entry> side = sideTabEntries();
				Object selectedLeft = selectedLeftKey();
				this.sideBar.drawLeftFrameTabs(
					graphics, this.font, panelX, panelY, panelH, mouseX, mouseY, side, selectedLeft
				);
			}

			positionPlayerSearch(graphics, panelX, panelY, panelW, panelH);
			boolean popupOwnsMouse = this.socialsMenu.blocksMouse(mouseX, mouseY)
				|| this.searchHistory.blocksMouse(mouseX, mouseY);
			int bodyMouseX = popupOwnsMouse ? -10_000 : mouseX;
			int bodyMouseY = popupOwnsMouse ? -10_000 : mouseY;
			this.homePage.setModelLook(mouseX, mouseY);
			renderBody(
				graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, bodyMouseX, bodyMouseY, delta
			);
			this.profileSelector.renderFooter(graphics, this.font, panelX, panelY, panelW, panelH, mouseX, mouseY);
			this.socialsMenu.renderLabel(
				graphics, this.font,
				this.profileSelector.footerX() + this.profileSelector.footerW() + SocialsMenu.LABEL_GAP,
				this.profileSelector.footerY(),
				mouseX, mouseY
			);
			if (this.playerSearchErrorUntilMs > System.currentTimeMillis() && !this.playerSearchError.isBlank()) {
				PvDraw.text(
					graphics, this.font, this.playerSearchError,
					panelX + panelW - 2 - this.font.width(this.playerSearchError),
					panelY + panelH + 3 + InventorySplitLayout.SEARCH_H + 2,
					0xFFFF5555
				);
			}

			graphics.nextStratum();
			DeferredTooltipLayer.render(
				graphics,
				this.font,
				this.width,
				this.height,
				panelX,
				panelY,
				panelW,
				panelH,
				bodyMouseX,
				bodyMouseY,
				this.tab,
				activeSub(PvSubTab.HOME_OVERVIEW),
				this.profileSelector,
				this.inventoryLayout,
				this.bestiaryLayout,
				this.inventoryBar,
				this.homePage,
				this.homeMiscPage,
				this.homeRecordsPage,
				this.homeEssencePage,
				this.dungeonPage,
				this.dungeonChestsPage,
				this.petsPage,
				this.collectionsPage,
				this.gardenPage,
				this.miningPage,
				this.foragingPage,
				this.fishingPage,
				this.fishingTrophyTrialPage,
				this.crimsonPage,
				this.riftPage,
				this.museumPage,
				this.eventsPage,
				this.inventoryPage,
				this.bestiaryPage
			);

			if (dungeonOverview()) {
				this.dungeonPage.renderOverlay(graphics, this.font, this.width, this.height, bodyMouseX, bodyMouseY);
			} else if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				this.homePage.renderSlayerOverlay(graphics, this.font, this.width, this.height, bodyMouseX, bodyMouseY);
			} else if (this.tab.isInventorySplit()) {
				this.inventoryPage.renderOverlay(graphics, this.font, this.width, this.height, bodyMouseX, bodyMouseY);
			} else if (this.tab == PvTab.COLLECTIONS) {
				this.collectionsPage.renderOverlay(graphics, this.font, this.width, this.height, bodyMouseX, bodyMouseY);
			}
		}

		if (scaled) {
			graphics.pose().popMatrix();
		}
		if (this.dataReady && !this.firstVisibleLogged && this.loadStartedNanos > 0L) {
			this.firstVisibleLogged = true;
			BetterPV.LOGGER.info(
				"[PV timing] event=first_visible_frame correlation_id={} player={} elapsed_ms={}",
				this.loadCorrelationId,
				this.requestedName,
				(System.nanoTime() - this.loadStartedNanos) / 1_000_000L
			);
		}
	}

	/** Screen size in the scaled viewport's coordinates, so tooltips clamp to what is actually visible. */
	private int virtualW() {
		return this.scaleViewport == null ? this.width : (int) Math.ceil(this.scaleViewport.toVirtualX(this.width));
	}

	/** Sum of the visible virtual edges, so an overlay centred as (size - panel) / 2 lands mid-screen. */
	private int overlayW() {
		return this.scaleViewport == null
			? this.width
			: (int) Math.round(this.scaleViewport.toVirtualX(0) + this.scaleViewport.toVirtualX(this.width));
	}

	private int overlayH() {
		return this.scaleViewport == null
			? this.height
			: (int) Math.round(this.scaleViewport.toVirtualY(0) + this.scaleViewport.toVirtualY(this.height));
	}

	private int virtualH() {
		return this.scaleViewport == null ? this.height : (int) Math.ceil(this.scaleViewport.toVirtualY(this.height));
	}

	private float openScale() {
		long elapsed = System.currentTimeMillis() - this.openAnimStartMs;
		if (elapsed >= OPEN_ANIM_MS) {
			return 1.0F;
		}
		float t = Math.max(0.0F, Math.min(1.0F, elapsed / (float) OPEN_ANIM_MS));
		// Cubic ease-out: snappy expand from center.
		float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
		return OPEN_SCALE_START + (1.0F - OPEN_SCALE_START) * eased;
	}

	private boolean uiInteractive() {
		return this.dataReady && openScale() >= 0.98F;
	}

	private static String friendlyLoadError(String raw) {
		if (raw == null || raw.isBlank()) {
			return "Player not found";
		}
		String lower = raw.toLowerCase(java.util.Locale.ROOT);
		if (lower.contains("not found") || lower.contains("unknown") || lower.contains("no player")) {
			return "Player not found";
		}
		return raw;
	}

	private void drawLoadingFace(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		if (this.loadError != null && !this.loadError.isBlank()) {
			int cx = panelX + panelW / 2;
			int cy = panelY + panelH / 2 - this.font.lineHeight;
			PvDraw.textCentered(g, this.font, this.loadError, cx, cy, 0xFFFF5555);
			PvDraw.textCentered(
				g,
				this.font,
				"Press Esc to close",
				cx,
				cy + this.font.lineHeight + 6,
				PvDraw.COLOR_MUTED
			);
			return;
		}
		long elapsedMs = System.currentTimeMillis() - this.openAnimStartMs;
		List<LoadingEgg.Stage> stages = LoadingEgg.stagesUnlocked(elapsedMs);
		int cx = panelX + panelW / 2;
		int maxW = panelW - 24;
		int lineH = this.font.lineHeight + 3;

		long phase = (System.currentTimeMillis() / 400L) % 4L;
		String dots = switch ((int) phase) {
			case 1 -> ".";
			case 2 -> "..";
			case 3 -> "...";
			default -> "";
		};
		String loading = "Loading data" + dots;

		if (elapsedMs >= LoadingEgg.BREAK_AT_MS) {
			scheduleBreakFinale();
			PvDraw.fill(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xCC330000);
		} else if (!stages.isEmpty() && stages.get(stages.size() - 1).shout()) {
			PvDraw.fill(g, panelX + 4, panelY + 4, panelW - 8, panelH - 8, 0x33AA0000);
		}

		// Loading Data + every unlocked threat, stacked oldest → newest.
		int rows = 1 + stages.size();
		int blockH = rows * lineH - 3;
		int topY = panelY + Math.max(10, (panelH - blockH) / 2);
		PvDraw.textCentered(g, this.font, loading, cx, topY, PvDraw.COLOR_MUTED);
		int ty = topY + lineH;
		for (LoadingEgg.Stage stage : stages) {
			drawEggLine(g, stage.line(), cx, ty, maxW);
			ty += lineH;
		}
	}

	private void drawEggLine(GuiGraphicsExtractor g, Component line, int cx, int y, int maxW) {
		int tw = this.font.width(line);
		if (tw <= maxW || tw <= 0) {
			PvDraw.textCentered(g, this.font, line, cx, y);
			return;
		}
		float scale = maxW / (float) tw;
		g.pose().pushMatrix();
		g.pose().translate(cx, y);
		g.pose().scale(scale, scale);
		PvDraw.text(g, this.font, line, -tw / 2, 0);
		g.pose().popMatrix();
	}

	private void scheduleBreakFinale() {
		if (this.breakScheduled) {
			return;
		}
		this.breakScheduled = true;
		BetterPV.LOGGER.warn("Loading egg hit finale for {} - starting limbo + fake ban", this.requestedName);
	}

	private Object selectedLeftKey() {
		if (this.tab == PvTab.COLLECTIONS) {
			return activeSub(PvSubTab.COLLECTIONS_LIST) == PvSubTab.COLLECTIONS_MINIONS
				? "minions"
				: Integer.valueOf(this.collectionsPage.category());
		}
		if (this.tab.isInventorySplit()) {
			return this.inventoryPage.pane();
		}
		if (this.tab == PvTab.MUSEUM) {
			return this.museumSort;
		}
		return this.subSelection.get(this.tab);
	}

	private List<IconButtonBar.Entry> topTabEntries() {
		if (this.topTabEntries == null) {
			List<IconButtonBar.Entry> entries = new ArrayList<>();
			for (PvTab t : PvTab.values()) {
				entries.add(new IconButtonBar.Entry(t, t.icon(), t.label(), () -> {
					if (t != PvTab.DUNGEONS) {
						this.dungeonPage.blurField();
					}
					this.homePage.forceCloseSbLevelOverlay();
					this.tab = t;
					ProfileFetcher.prioritizeTab(t);
				}));
			}
			this.topTabEntries = List.copyOf(entries);
		}
		return this.topTabEntries;
	}

	private List<IconButtonBar.Entry> sideTabEntries() {
		if (this.tab.isInventorySplit()) {
			List<IconButtonBar.Entry> panes = new ArrayList<>();
			for (InventoryPane pane : this.inventoryPage.visiblePanes()) {
				panes.add(new IconButtonBar.Entry(
					pane, pane.icon(), pane.label(), () -> this.inventoryPage.setPane(pane),
					pane.textureIcon(), 16
				));
			}
			return panes;
		}
		if (this.tab == PvTab.COLLECTIONS) {
			List<IconButtonBar.Entry> cats = new ArrayList<>();
			List<HypixelCollectionsCache.Category> categories = this.collectionsPage.categories();
			for (int i = 0; i < categories.size(); i++) {
				final int index = i;
				HypixelCollectionsCache.Category category = categories.get(i);
				cats.add(new IconButtonBar.Entry(
					Integer.valueOf(i), CollectionCategoryIcons.icon(category.id()),
					Component.literal(category.name()),
					() -> {
						this.subSelection.put(PvTab.COLLECTIONS, PvSubTab.COLLECTIONS_LIST);
						this.collectionsPage.setCategory(index);
					},
					null, 16
				));
			}
			cats.add(new IconButtonBar.Entry(
				"minions", PvSubTab.COLLECTIONS_MINIONS.icon(), PvSubTab.COLLECTIONS_MINIONS.label(),
				() -> this.subSelection.put(PvTab.COLLECTIONS, PvSubTab.COLLECTIONS_MINIONS),
				null, 16
			));
			return cats;
		}
		if (this.cachedSideTab == this.tab && !this.sideTabEntries.isEmpty()) {
			return this.sideTabEntries;
		}
		Object[] left = this.tab.leftTabs();
		List<IconButtonBar.Entry> side = new ArrayList<>(left.length);
		for (Object entry : left) {
			if (entry instanceof PvSubTab sub) {
				side.add(new IconButtonBar.Entry(
					entry, sub.icon(), sub.label(),
					() -> {
						this.homePage.forceCloseSbLevelOverlay();
						this.subSelection.put(this.tab, sub);
					},
					sub.textureIcon(), sub.textureSize()
				));
			} else if (entry instanceof MuseumSort sort) {
				side.add(new IconButtonBar.Entry(entry, sort.icon(), sort.label(), () -> {
					this.museumSort = sort;
					this.museumPage.setSort(sort);
				}));
			}
		}
		this.cachedSideTab = this.tab;
		this.sideTabEntries = List.copyOf(side);
		return this.sideTabEntries;
	}

	private void prepareStandardBody() {
		InventorySplitLayout.hideInventorySearch(this.inventorySearch);
		this.dungeonPage.blurField();
	}

	private PvSubTab activeSub(PvSubTab fallback) {
		return this.subSelection.getOrDefault(this.tab, fallback);
	}

	private boolean dungeonOverview() {
		return this.tab == PvTab.DUNGEONS && activeSub(PvSubTab.DUNGEON_OVERVIEW) == PvSubTab.DUNGEON_OVERVIEW;
	}

	// Screens don't keep options.keyShift updated; poll GLFW instead.
	private static boolean shiftDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT)
			|| InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RSHIFT);
	}

	private void renderBody(GuiGraphicsExtractor g, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
		switch (this.tab) {
			case HOME -> {
				prepareStandardBody();
				PvSubTab homeSub = activeSub(PvSubTab.HOME_OVERVIEW);
				if (homeSub == PvSubTab.HOME_MISC) {
					this.homeMiscPage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
					);
				} else if (homeSub == PvSubTab.HOME_RECORDS) {
					this.homeRecordsPage.render(g, this.font, x, y, w, h);
				} else if (homeSub == PvSubTab.HOME_ESSENCE) {
					this.homeEssencePage.render(g, this.font, x, y, w, h, mouseX, mouseY);
				} else {
					this.homePage.render(
						g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH(),
						openScale(), this.openPivotX, this.openPivotY,
						this.scaleViewport.renderScale(), this.scaleViewport.offsetX(), this.scaleViewport.offsetY()
					);
				}
			}
			case DUNGEONS -> {
				InventorySplitLayout.hideInventorySearch(this.inventorySearch);
				if (dungeonOverview()) {
					this.dungeonPage.render(g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH());
				} else {
					this.dungeonPage.blurField();
					this.dungeonChestsPage.render(g, this.font, x, y, w, h, mouseX, mouseY);
				}
			}
			case INVENTORIES -> {
				this.dungeonPage.blurField();
				this.inventoryLayout.render(
					g, this.font, this.inventoryPage, this.inventoryBar, this.inventorySearch,
					this.inventorySearchQuery, virtualW(), virtualH(),
					x, y, w, h, mouseX, mouseY, delta
				);
			}
			case BESTIARY -> {
				this.dungeonPage.blurField();
				this.bestiaryLayout.render(
					g, this.font, this.bestiaryPage, this.inventoryBar, this.inventorySearch,
					this.inventorySearchQuery, virtualW(), virtualH(),
					x, y, w, h, mouseX, mouseY, delta
				);
			}
			case PETS -> {
				prepareStandardBody();
				this.petsPage.render(g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH());
			}
			case COLLECTIONS -> {
				prepareStandardBody();
				this.collectionsPage.render(
					g, this.font, activeSub(PvSubTab.COLLECTIONS_LIST),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
			case GARDEN -> {
				prepareStandardBody();
				this.dataLoader.ensureGardenIsland();
				this.dataLoader.ensureGardenWeight();
				PvSubTab sub = activeSub(PvSubTab.GARDEN_OVERVIEW);
				if (sub == PvSubTab.GARDEN_JACOB) {
					this.dataLoader.ensureGardenContests();
				}
				this.gardenPage.render(g, this.font, sub, x, y, w, h, mouseX, mouseY, virtualW(), virtualH());
			}
			case MINING -> {
				prepareStandardBody();
				this.miningPage.render(
					g, this.font, activeSub(PvSubTab.MINING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
			case FORAGING -> {
				prepareStandardBody();
				this.foragingPage.render(
					g, this.font, activeSub(PvSubTab.FORAGING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
			case FISHING -> {
				prepareStandardBody();
				if (activeSub(PvSubTab.FISHING_MAIN) == PvSubTab.FISHING_TROPHIES) {
					this.fishingTrophyTrialPage.render(g, this.font, this.fishingPage.snapshot(), x, y, w, h);
				} else {
					this.fishingPage.render(g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH());
				}
			}
			case CRIMSON -> {
				prepareStandardBody();
				this.crimsonPage.render(
					g, this.font, activeSub(PvSubTab.CRIMSON_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
			case MUSEUM -> {
				prepareStandardBody();
				this.dataLoader.ensureMuseum(false);
				this.museumPage.render(g, this.font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH());
			}
			case EVENTS -> {
				prepareStandardBody();
				this.dataLoader.ensureBingo(false);
				this.eventsPage.render(
					g, this.font, activeSub(PvSubTab.EVENTS_BINGO),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
			case RIFT -> {
				prepareStandardBody();
				this.riftPage.render(
					g, this.font, activeSub(PvSubTab.RIFT_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				);
			}
		}
	}

	private static boolean isDirectionalKey(int key) {
		return key == 262 || key == 263 || key == 264 || key == 265;
	}

	private void positionPlayerSearch(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH) {
		if (this.playerSearch == null) {
			return;
		}
		int boxW = 110;
		int inset = 4;
		int textH = Math.max(8, this.font.lineHeight);
		int boxX = panelX + panelW - boxW;
		int boxY = panelY + panelH + 2;
		boolean focused = this.playerSearch.isFocused();
		PvDraw.roundedFill(g, boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, 3, 0xAA101018);
		PvDraw.roundedRing(g, boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, 3, focused ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		this.playerSearch.setWidth(Math.max(20, boxW - inset * 2));
		this.playerSearch.setHeight(textH);
		this.playerSearch.setX(boxX + inset);
		this.playerSearch.setY(boxY + Math.max(0, (InventorySplitLayout.SEARCH_H - textH) / 2));
		this.playerSearch.setVisible(true);
		this.playerSearch.extractWidgetRenderState(g, 0, 0, 0f);
		if (this.playerSearch.getValue().isEmpty() && !focused) {
			String hint = Component.translatable("betterpv.home.search_hint").getString();
			PvDraw.text(
				g, this.font, hint,
				this.playerSearch.getX(),
				this.playerSearch.getY(),
				PvDraw.COLOR_MUTED
			);
		}
		this.searchHistory.setAnchor(boxX, boxY, boxW, InventorySplitLayout.SEARCH_H);
		if (this.searchHistory.syncSearch(focused, this.playerSearch.getValue())) {
			this.socialsMenu.close();
			this.profileSelector.closeMenu();
		}
		this.searchHistory.layoutDropdown(this.font.lineHeight);
	}

	private void hidePlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		this.playerSearch.setFocused(false);
		this.playerSearch.setVisible(false);
		this.searchHistory.close();
	}

	private boolean playerSearchHit(double mx, double my) {
		return this.playerSearch != null && this.playerSearch.isVisible() && this.searchHistory.anchorContains(mx, my);
	}

	private void openSearchedPlayer(String name) {
		ProfileViewerScreen next = new ProfileViewerScreen(name);
		next.recordSearch = true;
		fishmod.compat.McCompatKt.showScreen(Minecraft.getInstance(), next);
	}

	private void submitPlayerSearch() {
		if (this.playerSearch == null) {
			return;
		}
		String trimmed = this.playerSearch.getValue() == null ? "" : this.playerSearch.getValue().trim();
		if (trimmed.isEmpty()) {
			this.playerSearchError = Component.translatable("betterpv.home.search_empty").getString();
			this.playerSearchErrorUntilMs = System.currentTimeMillis() + 2_500L;
			return;
		}
		this.playerSearchError = "";
		openSearchedPlayer(trimmed);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click == null) {
			return super.mouseClicked(click, doubled);
		}
		double mx = virtualMouseX(click.x());
		double my = virtualMouseY(click.y());
		if (this.searchHistory.mouseClicked(mx, my, click.button())) {
			return true;
		}
		if (uiInteractive() && playerSearchHit(mx, my)) {
			this.socialsMenu.close();
			this.profileSelector.closeMenu();
			this.searchHistory.open();
			super.mouseClicked(virtualClick(click), doubled);
			if (!this.playerSearch.isFocused()) {
				this.setFocused(this.playerSearch);
			}
			return true;
		}
		if (this.socialsMenu.isOpen() && this.socialsMenu.mouseClicked(mx, my, click.button())) {
			return true;
		}
		if (false && this.scaleSlider.mouseClicked(sliderMouseX(click.x()), sliderMouseY(click.y()), click.button())) {
			return true;
		}
		if (!uiInteractive()) {
			return true;
		}
		if (this.socialsMenu.mouseClicked(mx, my, click.button())) {
			if (this.socialsMenu.isOpen()) {
				this.profileSelector.closeMenu();
			}
			return true;
		}
		if (this.profileSelector.mouseClicked(mx, my)) {
			return true;
		}
		if (routePageClick(mx, my)) {
			return true;
		}
		if (this.topBar.click(mx, my) || this.sideBar.click(mx, my) || this.inventoryBar.click(mx, my)) {
			return true;
		}
		if (this.tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
			if (this.homePage.slayerMouseClicked(mx, my)) {
				return true;
			}
			if (this.homePage.clickSbLevelPanel(mx, my)) {
				return true;
			}
			if (this.homePage.isSbLevelOverlayActive()) {
				return true;
			}
			if (this.homePage.hitName(mx, my)) {
				this.dataLoader.ensureUsernameHistory();
				return true;
			}
			if (this.homePage.hitStatus(mx, my)) {
				this.dataLoader.ensurePlayerStatus();
				return true;
			}
			if (this.homePage.clickWeight(mx, my)) {
				return true;
			}
			if (this.homePage.clickNetworth(mx, my, click.button())) {
				return true;
			}
			if (this.homePage.clickLeftPanel(mx, my)) {
				return true;
			}
		}
		return super.mouseClicked(virtualClick(click), doubled);
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		super.mouseMoved(virtualMouseX(mouseX), virtualMouseY(mouseY));
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent click, double dragX, double dragY) {
		if (click == null) {
			return super.mouseDragged(click, dragX, dragY);
		}
		if (false && this.scaleSlider.mouseDragged(sliderMouseX(click.x()))) {
			return true;
		}
		float scale = this.scaleViewport == null ? 1.0F : this.scaleViewport.renderScale();
		return super.mouseDragged(virtualClick(click), dragX / scale, dragY / scale);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent click) {
		if (click == null) {
			return super.mouseReleased(click);
		}
		if (false && this.scaleSlider.mouseReleased(click.button())) {
			return true;
		}
		return super.mouseReleased(virtualClick(click));
	}

	private boolean routePageClick(double mx, double my) {
		return switch (this.tab) {
			case HOME -> switch (activeSub(PvSubTab.HOME_OVERVIEW)) {
				case HOME_MISC -> this.homeMiscPage.mouseClicked(mx, my);
				case HOME_ESSENCE -> this.homeEssencePage.mouseClicked(mx, my);
				default -> false;
			};
			case DUNGEONS -> dungeonOverview() && this.dungeonPage.mouseClicked(mx, my);
			case MINING -> this.miningPage.mouseClicked(mx, my);
			case FORAGING -> this.foragingPage.mouseClicked(mx, my);
			case EVENTS -> this.eventsPage.mouseClicked(mx, my, activeSub(PvSubTab.EVENTS_BINGO));
			case MUSEUM -> {
				if (this.museumPage.clickMissingOnly(mx, my)) {
					yield true;
				}
				if (this.museumPage.clickRefresh(mx, my)) {
					this.dataLoader.ensureMuseum(true);
					yield true;
				}
				yield false;
			}
			case CRIMSON -> this.crimsonPage.mouseClicked(mx, my);
			case RIFT -> this.riftPage.mouseClicked(mx, my, activeSub(PvSubTab.RIFT_OVERVIEW));
			case GARDEN -> this.gardenPage.mouseClicked(mx, my);
			case PETS -> this.petsPage.mouseClicked(mx, my);
			case COLLECTIONS -> this.collectionsPage.mouseClicked(mx, my, activeSub(PvSubTab.COLLECTIONS_LIST));
			case INVENTORIES -> this.inventoryPage.mouseClicked(mx, my);
			default -> false;
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!uiInteractive()) {
			return true;
		}
		double virtualX = virtualMouseX(mouseX);
		double virtualY = virtualMouseY(mouseY);
		if (this.socialsMenu.mouseScrolled(virtualX, virtualY) || this.searchHistory.mouseScrolled(virtualX, virtualY)) {
			return true;
		}
		boolean horizontal = scrollX != 0D || shiftDown();
		if (PvTooltip.panOverflow(scrollX != 0D ? scrollX : scrollY, horizontal)) {
			return true;
		}
		if (this.profileSelector.mouseScrolled(virtualX, virtualY, scrollY)) {
			return true;
		}
		if (routePageScroll(virtualX, virtualY, scrollY)) {
			return true;
		}
		return super.mouseScrolled(virtualX, virtualY, scrollX, scrollY);
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(virtualMouseX(mouseX), virtualMouseY(mouseY));
	}

	private MouseButtonEvent virtualClick(MouseButtonEvent click) {
		return new MouseButtonEvent(virtualMouseX(click.x()), virtualMouseY(click.y()), click.buttonInfo());
	}

	private double virtualMouseX(double mouseX) {
		return this.scaleViewport == null ? mouseX : this.scaleViewport.toVirtualX(mouseX);
	}

	private double virtualMouseY(double mouseY) {
		return this.scaleViewport == null ? mouseY : this.scaleViewport.toVirtualY(mouseY);
	}

	private double sliderMouseX(double mouseX) {
		return this.sliderViewport == null ? mouseX : this.sliderViewport.toVirtualX(mouseX);
	}

	private double sliderMouseY(double mouseY) {
		return this.sliderViewport == null ? mouseY : this.sliderViewport.toVirtualY(mouseY);
	}

	private boolean routePageScroll(double mouseX, double mouseY, double scrollY) {
		return switch (this.tab) {
			case HOME -> {
				if (activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_MISC) {
					yield this.homeMiscPage.mouseScrolled(mouseX, mouseY, scrollY);
				}
				if (activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_RECORDS) {
					yield false;
				}
				if (activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_ESSENCE) {
					yield this.homeEssencePage.mouseScrolled(mouseX, mouseY, scrollY);
				}
				yield this.homePage.mouseScrolled(mouseX, mouseY, scrollY);
			}
			case DUNGEONS -> dungeonOverview()
				? this.dungeonPage.mouseScrolled(scrollY)
				: this.dungeonChestsPage.mouseScrolled(mouseX, mouseY, scrollY);
			case MUSEUM -> this.museumPage.mouseScrolled(mouseX, mouseY, scrollY);
			case MINING -> this.miningPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.MINING_OVERVIEW));
			case FORAGING -> this.foragingPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.FORAGING_OVERVIEW));
			case EVENTS -> this.eventsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.EVENTS_BINGO));
			case PETS -> this.petsPage.mouseScrolled(scrollY);
			case COLLECTIONS -> this.collectionsPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.COLLECTIONS_LIST));
			case GARDEN -> this.gardenPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.GARDEN_OVERVIEW));
			case CRIMSON -> this.crimsonPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.CRIMSON_OVERVIEW));
			case RIFT -> this.riftPage.mouseScrolled(
				mouseX, mouseY, scrollY, activeSub(PvSubTab.RIFT_OVERVIEW));
			case FISHING -> activeSub(PvSubTab.FISHING_MAIN) != PvSubTab.FISHING_TROPHIES
				&& this.fishingPage.mouseScrolled(mouseX, mouseY, scrollY);
			case BESTIARY -> this.bestiaryPage.mouseScrolled(mouseX, mouseY, scrollY);
			case INVENTORIES -> this.inventoryPage.mouseScrolled(mouseX, mouseY, scrollY);
			default -> false;
		};
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event != null) {
			if (this.searchHistory.keyPressed(event.key()) || this.socialsMenu.keyPressed(event.key())) {
				return true;
			}
			boolean typingInvSearch = this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused();
			boolean typingPlayerSearch = this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused();
			if (typingPlayerSearch && (event.key() == 257 || event.key() == 335)) {
				submitPlayerSearch();
				return true;
			}
			boolean directional = isDirectionalKey(event.key());
			if (directional && !typingInvSearch && !typingPlayerSearch) {
				if (uiInteractive() && dungeonOverview() && this.dungeonPage.keyPressed(event.key())) {
					return true;
				}
				if (uiInteractive()
					&& this.tab == PvTab.HOME
					&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_ESSENCE
					&& this.homeEssencePage.keyPressed(event.key())) {
					return true;
				}
				// Screen arrow-key focus navigation would otherwise steal these
				// into the inventory/player search boxes.
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& this.homePage.slayerKeyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
				&& (event.key() == 256 || event.key() == 259)
				&& this.homePage.requestSbLevelBack()) {
				// ESC / Backspace collapses SB Level overlay without closing PV.
				return true;
			}
			if (uiInteractive() && dungeonOverview() && this.dungeonPage.keyPressed(event.key())) {
				return true;
			}
			if (uiInteractive()
				&& this.tab == PvTab.COLLECTIONS
				&& this.collectionsPage.keyPressed(event.key(), activeSub(PvSubTab.COLLECTIONS_LIST))) {
				return true;
			}
			if (uiInteractive() && this.profileSelector.menuOpen() && event.key() == 256) {
				this.profileSelector.closeMenu();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event != null) {
			boolean typingSearch = (this.inventorySearch != null
				&& this.inventorySearch.isVisible()
				&& this.inventorySearch.isFocused())
				|| (this.playerSearch != null
				&& this.playerSearch.isVisible()
				&& this.playerSearch.isFocused());
			if (!typingSearch) {
				String text = event.codepointAsString();
			}
			if (uiInteractive() && dungeonOverview()) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.dungeonPage.charTyped(text.charAt(0))) {
					return true;
				}
			}
			if (uiInteractive()
				&& this.tab == PvTab.HOME
				&& activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
				String text = event.codepointAsString();
				if (text != null && !text.isEmpty() && this.homePage.slayerCharTyped(text.charAt(0))) {
					return true;
				}
			}
		}
		return super.charTyped(event);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	Font font() {
		return this.font;
	}

	int loadGeneration() {
		return this.loadGeneration;
	}

	int bumpLoadGeneration() {
		return ++this.loadGeneration;
	}

	boolean isLoadGeneration(int generation) {
		return generation == this.loadGeneration;
	}

	void setDataReady(boolean ready) {
		this.dataReady = ready;
	}

	ProfileScreenDataLoader dataLoader() {
		return this.dataLoader;
	}

	String profileId() {
		return this.profileId;
	}

	UUID playerUuid() {
		return this.playerUuid;
	}

	private SocialLinks.Target socialTarget() {
		return new SocialLinks.Target(
			this.playerUuid, this.homePage.playerName(), this.profileId, this.homePage.profileName()
		);
	}

	private void openExternalLink(String url) {
		URI uri;
		try {
			uri = new URI(url);
		} catch (URISyntaxException e) {
			BetterPV.LOGGER.warn("Bad social link {}", url, e);
			return;
		}
		// Same path as chat links (honours the confirm prompt); still confirm if chat links are off.
		if (!clickUrlAction(Minecraft.getInstance(), this, uri)) {
			ConfirmLinkScreen.confirmLinkNow(this, uri);
		}
	}

	JsonObject profilesRoot() {
		return this.profilesRoot;
	}

	HomePage homePage() {
		return this.homePage;
	}

	MiscStatsPage homeMiscPage() {
		return this.homeMiscPage;
	}

	GardenPage gardenPage() {
		return this.gardenPage;
	}


	MuseumPage museumPage() {
		return this.museumPage;
	}

	EventsPage eventsPage() {
		return this.eventsPage;
	}
}
