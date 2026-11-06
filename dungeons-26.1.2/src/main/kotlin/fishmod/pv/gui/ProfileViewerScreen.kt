/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import fishmod.compat.curScreen
import fishmod.compat.showScreen
import fishmod.pv.ProfileViewer
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.api.ProfileFetcher
import fishmod.pv.api.PvConfig
import fishmod.pv.data.MiscStatsSnapshot
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.gui.bestiary.BestiaryPage
import fishmod.pv.gui.collections.CollectionCategoryIcons
import fishmod.pv.gui.collections.CollectionsPage
import fishmod.pv.gui.crimson.CrimsonPage
import fishmod.pv.gui.dungeons.DungeonChestsPage
import fishmod.pv.gui.dungeons.DungeonPage
import fishmod.pv.gui.events.EventsPage
import fishmod.pv.gui.fishing.FishingPage
import fishmod.pv.gui.fishing.FishingTrophyTrialPage
import fishmod.pv.gui.foraging.ForagingPage
import fishmod.pv.gui.garden.GardenPage
import fishmod.pv.gui.home.HomePage
import fishmod.pv.gui.home.page.EssencePage
import fishmod.pv.gui.home.page.MiscStatsPage
import fishmod.pv.gui.home.page.RecordsPage
import fishmod.pv.gui.inventories.InventoryPage
import fishmod.pv.gui.mining.MiningPage
import fishmod.pv.gui.museum.MuseumPage
import fishmod.pv.gui.nav.IconButtonBar
import fishmod.pv.gui.nav.MuseumSort
import fishmod.pv.gui.nav.PvSubTab
import fishmod.pv.gui.nav.PvTab
import fishmod.pv.gui.pets.PetsPage
import fishmod.pv.gui.rift.RiftPage
import fishmod.utils.config.values.FishSettings
import java.net.URI
import java.net.URISyntaxException
import java.util.EnumMap
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

class ProfileViewerScreen @JvmOverloads constructor(
	playerName: String?,
	initialTab: PvTab? = PvTab.HOME,
	initialSub: PvSubTab? = null
) : Screen(Component.translatable("fishmod.pv.screen.title")) {
	private val requestedName: String = if (playerName.isNullOrBlank()) "?" else playerName.trim()
	private val homePage = HomePage(ProfileSnapshot.loading(requestedName))
	private val homeMiscPage = MiscStatsPage()
	private val homeRecordsPage = RecordsPage()
	private val homeEssencePage = EssencePage()
	private val dungeonPage = DungeonPage()
	private val dungeonChestsPage = DungeonChestsPage()
	private val inventoryPage = InventoryPage()
	private val petsPage = PetsPage()
	private val collectionsPage = CollectionsPage()
	private val gardenPage = GardenPage()
	private val fishingPage = FishingPage()
	private val fishingTrophyTrialPage = FishingTrophyTrialPage()
	private val crimsonPage = CrimsonPage()
	private val riftPage = RiftPage()
	private val topBar = IconButtonBar()
	private val sideBar = IconButtonBar()
	private val inventoryBar = IconButtonBar()
	private val subSelection = EnumMap<PvTab, PvSubTab>(PvTab::class.java)
	private val openAnimStartMs = System.currentTimeMillis()
	private val profileSelector = ProfileSelectorController(this)
	private val dataLoader = ProfileScreenDataLoader(this)
	private val inventoryLayout = InventorySplitLayout()
	private val bestiaryPage = BestiaryPage()
	private val miningPage = MiningPage()
	private val foragingPage = ForagingPage()
	private val museumPage = MuseumPage()
	private val eventsPage = EventsPage()
	private var museumSort = MuseumSort.ALL
	private val bestiaryLayout = BestiarySplitLayout()
	private var openPivotX = 0f
	private var openPivotY = 0f
	private val scaleSlider = ProfileScaleSlider()
	private val socialsMenu = SocialsMenu({ socialTarget() }, { openExternalLink(it) })
	private val searchHistory = SearchHistoryMenu({ PvConfig.searchHistory() }, { openSearchedPlayer(it) })
	private var recordSearch = false
	private var scaleViewport: ProfileViewerScale.Viewport? = null
	private var sliderViewport: ProfileViewerScale.Viewport? = null

	private var tab: PvTab = initialTab ?: PvTab.HOME
	private var fetchStarted = false
	private var dataReady = false
	private var loadCorrelationId: String? = null
	private var loadStartedNanos = 0L
	private var firstVisibleLogged = false
	/** Set when fetch fails (e.g. unknown IGN). Shown instead of infinite loading. */
	private var loadError: String? = null
	/** Bumps on each fetch/switch so stale async results cannot overwrite newer state. */
	private var loadGeneration = 0
	private val networthListener = Consumer<ProfileFetcher.LoadedProfile> { onDeferredNetworth(it) }
	private var breakScheduled = false
	private var profileId: String? = null
	private var preferredProfileId: String? = null
	private var playerUuid: UUID? = null
	private var inventorySearch: EditBox? = null
	private var inventorySearchQuery = ""
	private var topTabEntries: List<IconButtonBar.Entry>? = null
	private var cachedSideTab: PvTab? = null
	private var sideTabEntries: List<IconButtonBar.Entry> = listOf()

	private var profilesRoot: JsonObject? = null
	private var panelXCache = 0
	private var panelYCache = 0
	private var panelWCache = 0
	private var panelHCache = 0
	private var playerSearch: EditBox? = null
	private var playerSearchError = ""
	private var playerSearchErrorUntilMs = 0L

	init {
		for (t in PvTab.entries) {
			val subs = t.subTabs()
			if (subs.isNotEmpty()) {
				subSelection[t] = subs[0]
			}
		}
		if (initialSub != null && PvTab.Route(tab, initialSub).valid()) {
			subSelection[tab] = initialSub
		}
	}

	/** Same SkyBlock profile and page, different co-op member. */
	fun forCoopMember(playerName: String?, coopProfileId: String?): ProfileViewerScreen {
		val next = ProfileViewerScreen(playerName, tab)
		next.subSelection.putAll(subSelection)
		next.homeEssencePage.select(homeEssencePage.selectedEntry().def.id)
		next.museumSort = museumSort
		next.preferredProfileId = coopProfileId
		return next
	}

	override fun init() {
		super.init()
		PvTheme.apply(FishSettings.pvTheme)
		try {
			val fontField = Screen::class.java.getDeclaredField("font")
			fontField.isAccessible = true
			fontField.set(this, PvFont.get(Minecraft.getInstance().font))
		} catch (exception: ReflectiveOperationException) {
			ProfileViewer.LOGGER.warn("Could not swap the viewer font", exception)
		} catch (exception: RuntimeException) {
			ProfileViewer.LOGGER.warn("Could not swap the viewer font", exception)
		}
		val invSearch = EditBox(font, 0, 0, 100, InventorySplitLayout.SEARCH_H, Component.translatable("fishmod.pv.inv.search"))
		invSearch.setMaxLength(64)
		invSearch.setBordered(false)
		// EditBox colours are ARGB - stripping alpha makes typed text invisible.
		invSearch.setTextColor(PvDraw.COLOR_TEXT)
		invSearch.setTextColorUneditable(PvDraw.COLOR_MUTED)
		invSearch.setValue(inventorySearchQuery)
		invSearch.setResponder { value ->
			inventorySearchQuery = value
			inventoryPage.setSearchQuery(value)
			bestiaryPage.setSearchQuery(value)
		}
		invSearch.setVisible(false)
		addWidget(invSearch)
		inventorySearch = invSearch

		val search = EditBox(font, 0, 0, 120, InventorySplitLayout.SEARCH_H, Component.translatable("fishmod.pv.home.search_hint"))
		search.setMaxLength(16)
		search.setBordered(false)
		search.setTextColor(PvDraw.COLOR_TEXT)
		search.setTextColorUneditable(PvDraw.COLOR_MUTED)
		search.setHint(Component.translatable("fishmod.pv.home.search_hint"))
		search.setVisible(false)
		addWidget(search)
		playerSearch = search

		if (fetchStarted) {
			// removed() drops this while a link confirm screen is up; late networth still needs to land.
			ProfileFetcher.addNetworthListener(networthListener)
			return
		}
		fetchStarted = true
		val correlationId = UUID.randomUUID().toString()
		loadCorrelationId = correlationId
		loadStartedNanos = System.nanoTime()
		firstVisibleLogged = false
		val generation = ++loadGeneration
		ProfileFetcher.prioritizeTab(tab)
		ProfileFetcher.addNetworthListener(networthListener)
		ProfileFetcher.fetch(requestedName, preferredProfileId, { updated ->
			val client = Minecraft.getInstance() ?: return@fetch
			client.execute {
				if (client.curScreen !== this || generation != loadGeneration) {
					return@execute
				}
				if (updated == null || !updated.ok()) {
					return@execute
				}
				applyLoadedProfile(updated)
			}
		}, correlationId, loadStartedNanos).whenComplete { loaded, error ->
			val client = Minecraft.getInstance() ?: return@whenComplete
			client.execute {
				if (client.curScreen !== this || generation != loadGeneration) {
					return@execute
				}
				var displayed = loaded
				if (error != null || loaded == null) {
					ProfileViewer.LOGGER.warn("Profile fetch failed for {}", requestedName, error)
					displayed = ProfileFetcher.failed(requestedName, error?.message ?: "Couldn't load profile")
				}
				if (!displayed.ok()) {
					ProfileViewer.LOGGER.warn("Profile fetch failed for {}: {}", requestedName, displayed.error)
					loadError = friendlyLoadError(displayed.error)
					return@execute
				}
				loadError = null
				applyLoadedProfile(displayed)
				dataReady = true
				val snapshot = displayed.snapshot
				if (recordSearch && snapshot != null) {
					recordSearch = false
					PvConfig.recordSearch(snapshot.playerUuid, snapshot.playerName)
				}
				ProfileViewer.LOGGER.info(
					"[PV timing] event=render_thread_applied correlation_id={} player={} elapsed_ms={}",
					loadCorrelationId,
					requestedName,
					(System.nanoTime() - loadStartedNanos) / 1_000_000L
				)
			}
		}
	}

	private fun onDeferredNetworth(updated: ProfileFetcher.LoadedProfile?) {
		val client = Minecraft.getInstance()
		if (client == null || updated == null || !updated.ok()) {
			return
		}
		client.execute {
			if (client.curScreen !== this || !dataReady) {
				return@execute
			}
			val uuid = playerUuid
			val snapshot = updated.snapshot
			if (uuid != null && snapshot != null && uuid == snapshot.playerUuid &&
				(profileId == null || profileId == updated.profileId)
			) {
				applyLoadedProfile(updated)
			}
		}
	}

	override fun removed() {
		socialsMenu.close()
		searchHistory.close()
		ProfileFetcher.removeNetworthListener(networthListener)
		super.removed()
	}

	fun applyLoadedProfile(loaded: ProfileFetcher.LoadedProfile?) {
		if (loaded == null) {
			return
		}
		val prevUuid = playerUuid
		val prevProfileId = profileId
		val nextUuid = loaded.snapshot?.playerUuid
		val nextProfileId = loaded.profileId
		val sameIdentity = prevUuid != null && prevUuid == nextUuid &&
			prevProfileId != null && prevProfileId == nextProfileId

		profileSelector.clearFooterNameCache()
		homePage.applyLoaded(
			loaded.snapshot,
			loaded.senither,
			loaded.lily,
			loaded.networthNormal,
			loaded.networthNonCosmetic,
			loaded.networthUnsoulbound,
			loaded.networthUnsoulboundNonCosmetic,
			loaded.armor,
			loaded.playerStats,
			loaded.error
		)
		homeMiscPage.reset()
		homeMiscPage.apply(loaded.misc ?: MiscStatsSnapshot.empty())
		homeMiscPage.setGuildClickHandler { dataLoader.ensureGuild() }
		homeRecordsPage.apply(loaded.misc)
		homeEssencePage.apply(loaded.dungeons, loaded.mining, loaded.foraging, loaded.crimson)
		dungeonPage.apply(loaded.dungeons)
		dungeonChestsPage.apply(loaded.dungeons)
		inventoryPage.apply(loaded.inventories)
		petsPage.apply(loaded.pets)
		collectionsPage.apply(loaded.collections)
		gardenPage.apply(loaded.garden)
		gardenPage.setPlayerStats(loaded.playerStats)
		miningPage.apply(loaded.mining)
		miningPage.setPlayerStats(loaded.playerStats)
		foragingPage.apply(loaded.foraging)
		fishingPage.apply(loaded.fishing, loaded.bestiary)
		crimsonPage.apply(loaded.crimson)
		riftPage.apply(loaded.rift)
		bestiaryPage.apply(loaded.bestiary)
		eventsPage.apply(loaded.events)
		val museumMember = loaded.museumMember
		if (museumMember != null) {
			museumPage.applyMuseum(museumMember)
		} else if (!sameIdentity) {
			museumPage.reset()
		}
		profileId = loaded.profileId
		playerUuid = nextUuid
		loaded.profilesRoot?.let { profilesRoot = it }
		val viewed = playerUuid?.let { HypixelApiClient.undashed(it) } ?: ""
		profileSelector.applyChoices(loaded.profiles, viewed, homePage.playerName())
		if (!sameIdentity) {
			dataLoader.resetForNewLoad()
			if (museumMember != null) {
				dataLoader.markMuseumLoaded()
			}
			eventsPage.resetBingoFetch()
			dataLoader.ensurePlayerRank()
		} else if (museumMember != null) {
			dataLoader.markMuseumLoaded()
		}
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
		val screenWidth = width
		val screenHeight = height
		val window = Minecraft.getInstance()?.window
		val framebufferWidth = window?.width ?: (screenWidth * TARGET_GUI_SCALE)
		val framebufferHeight = window?.height ?: (screenHeight * TARGET_GUI_SCALE)
		val guiScale = window?.guiScale ?: TARGET_GUI_SCALE
		val slider = ProfileViewerScale.viewport(
			screenWidth, screenHeight, framebufferWidth, framebufferHeight, guiScale, 100, null
		)
		sliderViewport = slider
		val virtualWidth = slider.virtualWidth
		val virtualHeight = slider.virtualHeight
		scaleSlider.layoutViewport(virtualWidth, virtualHeight)

		val topRoom = IconButtonBar.TAB + 4
		val leftRoom = IconButtonBar.TAB + 4
		val tabsW = 8 + PvTab.entries.size * (IconButtonBar.TAB + IconButtonBar.GAP)
		val panelW = minOf(maxOf(520, tabsW), maxOf(maxOf(420, tabsW), virtualWidth - 80 - leftRoom))
		var contentH = homePage.preferredHeight(font, panelW - PAD * 2)
		if (tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) != PvSubTab.HOME_OVERVIEW) {
			contentH = maxOf(contentH, 220)
		}
		val maxPanelH = maxOf(200, virtualHeight - topRoom - 24)
		val panelH = minOf(maxPanelH, maxOf(200, contentH + PAD * 2))
		val panelX = (virtualWidth - panelW) / 2 + leftRoom / 2
		val panelY = (virtualHeight - panelH - topRoom) / 2 + topRoom

		val bounds = ProfileViewerScale.Bounds(
			(panelX - leftRoom - 2).toFloat(),
			(panelY - topRoom - 2).toFloat(),
			(panelX + panelW + 2).toFloat(),
			(panelY + panelH + BOTTOM_FIT_ROOM).toFloat()
		)
		val selectedPercent = PvConfig.profileViewerScalePercent()
		val viewport = ProfileViewerScale.viewport(
			screenWidth, screenHeight, framebufferWidth, framebufferHeight, guiScale, selectedPercent, bounds
		)
		scaleViewport = viewport
		IconButtonBar.setVirtualScreen(virtualW(), virtualH())
		val virtualMouseX = Math.round(viewport.toVirtualX(mouseX.toDouble())).toInt()
		val virtualMouseY = Math.round(viewport.toVirtualY(mouseY.toDouble())).toInt()

		graphics.pose().pushMatrix()
		graphics.pose().translate(viewport.offsetX, viewport.offsetY)
		graphics.pose().scale(viewport.renderScale, viewport.renderScale)
		val previousWidth = width
		val previousHeight = height
		width = virtualWidth
		height = virtualHeight
		try {
			super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta)
			val dimLeft = floor(viewport.toVirtualX(0.0)).toInt()
			val dimTop = floor(viewport.toVirtualY(0.0)).toInt()
			val dimRight = ceil(viewport.toVirtualX(screenWidth.toDouble())).toInt()
			val dimBottom = ceil(viewport.toVirtualY(screenHeight.toDouble())).toInt()
			PvDraw.fill(graphics, dimLeft, dimTop, dimRight - dimLeft, dimBottom - dimTop, 0x99000000.toInt())
			socialsMenu.setViewport(dimLeft, dimTop, dimRight, dimBottom)
			searchHistory.setViewport(dimLeft, dimTop, dimRight, dimBottom)
			extractProfileRenderState(graphics, virtualMouseX, virtualMouseY, delta, panelX, panelY, panelW, panelH)
		} finally {
			width = previousWidth
			height = previousHeight
			graphics.pose().popMatrix()
		}

		graphics.nextStratum()
		graphics.pose().pushMatrix()
		graphics.pose().translate(slider.offsetX, slider.offsetY)
		graphics.pose().scale(slider.renderScale, slider.renderScale)
		// scale slider hidden
		graphics.pose().popMatrix()

		if (dataReady && (socialsMenu.isOpen() || searchHistory.isVisible())) {
			// Last stratum so the tip and scale slider can't draw over the popup.
			graphics.nextStratum()
			graphics.pose().pushMatrix()
			graphics.pose().translate(viewport.offsetX, viewport.offsetY)
			graphics.pose().scale(viewport.renderScale, viewport.renderScale)
			try {
				socialsMenu.renderPopup(graphics, font, virtualMouseX, virtualMouseY)
				searchHistory.render(graphics, font, virtualMouseX, virtualMouseY)
			} finally {
				graphics.pose().popMatrix()
			}
		}
	}

	private fun extractProfileRenderState(
		graphics: GuiGraphicsExtractor,
		mouseX: Int,
		mouseY: Int,
		delta: Float,
		panelX: Int,
		panelY: Int,
		panelW: Int,
		panelH: Int
	) {
		topBar.clearHits()
		sideBar.clearHits()
		inventoryBar.clearHits()
		panelXCache = panelX
		panelYCache = panelY
		panelWCache = panelW
		panelHCache = panelH
		val scale = openScale()
		openPivotX = panelX + panelW / 2.0f
		openPivotY = panelY + panelH / 2.0f
		val scaled = scale < 0.999f
		if (scaled) {
			graphics.pose().pushMatrix()
			graphics.pose().translate(openPivotX, openPivotY)
			graphics.pose().scale(scale, scale)
			graphics.pose().translate(-openPivotX, -openPivotY)
		}

		PvDraw.panel(graphics, panelX, panelY, panelW, panelH)
		if (!dataReady) {
			InventorySplitLayout.hideInventorySearch(inventorySearch)
			hidePlayerSearch()
			drawLoadingFace(graphics, panelX, panelY, panelW, panelH)
		} else {
			topBar.drawTopFrameTabs(graphics, font, panelX, panelY, mouseX, mouseY, topTabEntries(), tab)
			if (tab.leftTabs().isNotEmpty()) {
				sideBar.drawLeftFrameTabs(
					graphics, font, panelX, panelY, panelH, mouseX, mouseY, sideTabEntries(), selectedLeftKey()
				)
			}

			positionPlayerSearch(graphics, panelX, panelY, panelW, panelH)
			val popupOwnsMouse = socialsMenu.blocksMouse(mouseX.toDouble(), mouseY.toDouble()) ||
				searchHistory.blocksMouse(mouseX.toDouble(), mouseY.toDouble())
			val bodyMouseX = if (popupOwnsMouse) -10_000 else mouseX
			val bodyMouseY = if (popupOwnsMouse) -10_000 else mouseY
			homePage.setModelLook(mouseX, mouseY)
			renderBody(graphics, panelX + PAD, panelY + PAD, panelW - PAD * 2, panelH - PAD * 2, bodyMouseX, bodyMouseY, delta)
			profileSelector.renderFooter(graphics, font, panelX, panelY, panelW, panelH, mouseX, mouseY)
			socialsMenu.renderLabel(
				graphics, font,
				profileSelector.footerX() + profileSelector.footerW() + SocialsMenu.LABEL_GAP,
				profileSelector.footerY(),
				mouseX, mouseY
			)
			if (playerSearchErrorUntilMs > System.currentTimeMillis() && playerSearchError.isNotBlank()) {
				PvDraw.text(
					graphics, font, playerSearchError,
					panelX + panelW - 2 - font.width(playerSearchError),
					panelY + panelH + 3 + InventorySplitLayout.SEARCH_H + 2,
					0xFFFF5555.toInt()
				)
			}

			graphics.nextStratum()
			DeferredTooltipLayer.render(
				graphics,
				font,
				width,
				height,
				panelX,
				panelY,
				panelW,
				panelH,
				bodyMouseX,
				bodyMouseY,
				tab,
				activeSub(PvSubTab.HOME_OVERVIEW),
				profileSelector,
				inventoryLayout,
				bestiaryLayout,
				inventoryBar,
				homePage,
				homeMiscPage,
				homeRecordsPage,
				homeEssencePage,
				dungeonPage,
				dungeonChestsPage,
				petsPage,
				collectionsPage,
				gardenPage,
				miningPage,
				foragingPage,
				fishingPage,
				fishingTrophyTrialPage,
				crimsonPage,
				riftPage,
				museumPage,
				eventsPage,
				inventoryPage,
				bestiaryPage
			)

			when {
				dungeonOverview() -> dungeonPage.renderOverlay(graphics, font, width, height, bodyMouseX, bodyMouseY)
				tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW ->
					homePage.renderSlayerOverlay(graphics, font, width, height, bodyMouseX, bodyMouseY)
				tab.isInventorySplit() -> inventoryPage.renderOverlay(graphics, font, width, height, bodyMouseX, bodyMouseY)
				tab == PvTab.COLLECTIONS -> collectionsPage.renderOverlay(graphics, font, width, height, bodyMouseX, bodyMouseY)
			}
		}

		if (scaled) {
			graphics.pose().popMatrix()
		}
		if (dataReady && !firstVisibleLogged && loadStartedNanos > 0L) {
			firstVisibleLogged = true
			ProfileViewer.LOGGER.info(
				"[PV timing] event=first_visible_frame correlation_id={} player={} elapsed_ms={}",
				loadCorrelationId,
				requestedName,
				(System.nanoTime() - loadStartedNanos) / 1_000_000L
			)
		}
	}

	/** Screen size in the scaled viewport's coordinates, so tooltips clamp to what is actually visible. */
	private fun virtualW(): Int = scaleViewport?.let { ceil(it.toVirtualX(width.toDouble())).toInt() } ?: width

	/** Sum of the visible virtual edges, so an overlay centred as (size - panel) / 2 lands mid-screen. */
	private fun overlayW(): Int =
		scaleViewport?.let { Math.round(it.toVirtualX(0.0) + it.toVirtualX(width.toDouble())).toInt() } ?: width

	private fun overlayH(): Int =
		scaleViewport?.let { Math.round(it.toVirtualY(0.0) + it.toVirtualY(height.toDouble())).toInt() } ?: height

	private fun virtualH(): Int = scaleViewport?.let { ceil(it.toVirtualY(height.toDouble())).toInt() } ?: height

	private fun openScale(): Float {
		val elapsed = System.currentTimeMillis() - openAnimStartMs
		if (elapsed >= OPEN_ANIM_MS) {
			return 1.0f
		}
		val t = maxOf(0.0f, minOf(1.0f, elapsed / OPEN_ANIM_MS.toFloat()))
		// Cubic ease-out: snappy expand from center.
		val eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
		return OPEN_SCALE_START + (1.0f - OPEN_SCALE_START) * eased
	}

	private fun uiInteractive(): Boolean = dataReady && openScale() >= 0.98f

	private fun drawLoadingFace(g: GuiGraphicsExtractor, panelX: Int, panelY: Int, panelW: Int, panelH: Int) {
		val error = loadError
		if (!error.isNullOrBlank()) {
			val cx = panelX + panelW / 2
			val cy = panelY + panelH / 2 - font.lineHeight
			PvDraw.textCentered(g, font, error, cx, cy, 0xFFFF5555.toInt())
			PvDraw.textCentered(g, font, "Press Esc to close", cx, cy + font.lineHeight + 6, PvDraw.COLOR_MUTED)
			return
		}
		val elapsedMs = System.currentTimeMillis() - openAnimStartMs
		val stages = LoadingEgg.stagesUnlocked(elapsedMs)
		val cx = panelX + panelW / 2
		val maxW = panelW - 24
		val lineH = font.lineHeight + 3

		val dots = when ((System.currentTimeMillis() / 400L) % 4L) {
			1L -> "."
			2L -> ".."
			3L -> "..."
			else -> ""
		}
		val loading = "Loading data$dots"

		if (elapsedMs >= LoadingEgg.BREAK_AT_MS) {
			scheduleBreakFinale()
			PvDraw.fill(g, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 0xCC330000.toInt())
		} else if (stages.isNotEmpty() && stages.last().shout) {
			PvDraw.fill(g, panelX + 4, panelY + 4, panelW - 8, panelH - 8, 0x33AA0000)
		}

		// Loading Data + every unlocked threat, stacked oldest → newest.
		val rows = 1 + stages.size
		val blockH = rows * lineH - 3
		val topY = panelY + maxOf(10, (panelH - blockH) / 2)
		PvDraw.textCentered(g, font, loading, cx, topY, PvDraw.COLOR_MUTED)
		var ty = topY + lineH
		for (stage in stages) {
			drawEggLine(g, stage.line, cx, ty, maxW)
			ty += lineH
		}
	}

	private fun drawEggLine(g: GuiGraphicsExtractor, line: Component, cx: Int, y: Int, maxW: Int) {
		val tw = font.width(line)
		if (tw <= maxW || tw <= 0) {
			PvDraw.textCentered(g, font, line, cx, y)
			return
		}
		val scale = maxW / tw.toFloat()
		g.pose().pushMatrix()
		g.pose().translate(cx.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		PvDraw.text(g, font, line, -tw / 2, 0)
		g.pose().popMatrix()
	}

	private fun scheduleBreakFinale() {
		if (breakScheduled) {
			return
		}
		breakScheduled = true
		ProfileViewer.LOGGER.warn("Loading egg hit finale for {} - starting limbo + fake ban", requestedName)
	}

	private fun selectedLeftKey(): Any? = when {
		tab == PvTab.COLLECTIONS ->
			if (activeSub(PvSubTab.COLLECTIONS_LIST) == PvSubTab.COLLECTIONS_MINIONS) "minions" else collectionsPage.category()
		tab.isInventorySplit() -> inventoryPage.pane()
		tab == PvTab.MUSEUM -> museumSort
		else -> subSelection[tab]
	}

	private fun topTabEntries(): List<IconButtonBar.Entry> = topTabEntries ?: PvTab.entries.map { t ->
		IconButtonBar.Entry(t, t.icon(), t.label()) {
			if (t != PvTab.DUNGEONS) {
				dungeonPage.blurField()
			}
			homePage.forceCloseSbLevelOverlay()
			tab = t
			ProfileFetcher.prioritizeTab(t)
		}
	}.also { topTabEntries = it }

	private fun sideTabEntries(): List<IconButtonBar.Entry> {
		if (tab.isInventorySplit()) {
			return inventoryPage.visiblePanes().map { pane ->
				IconButtonBar.Entry(
					pane, pane.icon(), pane.label(), { inventoryPage.setPane(pane) },
					pane.textureIcon(), 16
				)
			}
		}
		if (tab == PvTab.COLLECTIONS) {
			val cats = ArrayList<IconButtonBar.Entry>()
			collectionsPage.categories().forEachIndexed { index, category ->
				cats.add(IconButtonBar.Entry(
					index, CollectionCategoryIcons.icon(category.id),
					Component.literal(category.name),
					{
						subSelection[PvTab.COLLECTIONS] = PvSubTab.COLLECTIONS_LIST
						collectionsPage.setCategory(index)
					},
					null, 16
				))
			}
			cats.add(IconButtonBar.Entry(
				"minions", PvSubTab.COLLECTIONS_MINIONS.icon(), PvSubTab.COLLECTIONS_MINIONS.label(),
				{ subSelection[PvTab.COLLECTIONS] = PvSubTab.COLLECTIONS_MINIONS },
				null, 16
			))
			return cats
		}
		if (cachedSideTab == tab && sideTabEntries.isNotEmpty()) {
			return sideTabEntries
		}
		val side = ArrayList<IconButtonBar.Entry>()
		for (entry in tab.leftTabs()) {
			if (entry is PvSubTab) {
				side.add(IconButtonBar.Entry(
					entry, entry.icon(), entry.label(),
					{
						homePage.forceCloseSbLevelOverlay()
						subSelection[tab] = entry
					},
					entry.textureIcon(), entry.textureSize()
				))
			} else if (entry is MuseumSort) {
				side.add(IconButtonBar.Entry(entry, entry.icon(), entry.label()) {
					museumSort = entry
					museumPage.setSort(entry)
				})
			}
		}
		cachedSideTab = tab
		sideTabEntries = java.util.List.copyOf(side)
		return sideTabEntries
	}

	private fun prepareStandardBody() {
		InventorySplitLayout.hideInventorySearch(inventorySearch)
		dungeonPage.blurField()
	}

	private fun activeSub(fallback: PvSubTab): PvSubTab = subSelection[tab] ?: fallback

	private fun dungeonOverview(): Boolean =
		tab == PvTab.DUNGEONS && activeSub(PvSubTab.DUNGEON_OVERVIEW) == PvSubTab.DUNGEON_OVERVIEW

	private fun renderBody(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int, delta: Float) {
		when (tab) {
			PvTab.HOME -> {
				prepareStandardBody()
				when (activeSub(PvSubTab.HOME_OVERVIEW)) {
					PvSubTab.HOME_MISC -> homeMiscPage.render(g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
					PvSubTab.HOME_RECORDS -> homeRecordsPage.render(g, font, x, y, w, h)
					PvSubTab.HOME_ESSENCE -> homeEssencePage.render(g, font, x, y, w, h, mouseX, mouseY)
					else -> {
						val viewport = scaleViewport
						homePage.render(
							g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH(),
							openScale(), openPivotX, openPivotY,
							viewport?.renderScale ?: 1f, viewport?.offsetX ?: 0f, viewport?.offsetY ?: 0f
						)
					}
				}
			}
			PvTab.DUNGEONS -> {
				InventorySplitLayout.hideInventorySearch(inventorySearch)
				if (dungeonOverview()) {
					dungeonPage.render(g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
				} else {
					dungeonPage.blurField()
					dungeonChestsPage.render(g, font, x, y, w, h, mouseX, mouseY)
				}
			}
			PvTab.INVENTORIES -> {
				dungeonPage.blurField()
				inventoryLayout.render(
					g, font, inventoryPage, inventoryBar, inventorySearch,
					inventorySearchQuery, virtualW(), virtualH(),
					x, y, w, h, mouseX, mouseY, delta
				)
			}
			PvTab.BESTIARY -> {
				dungeonPage.blurField()
				bestiaryLayout.render(
					g, font, bestiaryPage, inventoryBar, inventorySearch,
					inventorySearchQuery, virtualW(), virtualH(),
					x, y, w, h, mouseX, mouseY, delta
				)
			}
			PvTab.PETS -> {
				prepareStandardBody()
				petsPage.render(g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
			}
			PvTab.COLLECTIONS -> {
				prepareStandardBody()
				collectionsPage.render(
					g, font, activeSub(PvSubTab.COLLECTIONS_LIST),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			PvTab.GARDEN -> {
				prepareStandardBody()
				dataLoader.ensureGardenIsland()
				dataLoader.ensureGardenWeight()
				val sub = activeSub(PvSubTab.GARDEN_OVERVIEW)
				if (sub == PvSubTab.GARDEN_JACOB) {
					dataLoader.ensureGardenContests()
				}
				gardenPage.render(g, font, sub, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
			}
			PvTab.MINING -> {
				prepareStandardBody()
				miningPage.render(
					g, font, activeSub(PvSubTab.MINING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			PvTab.FORAGING -> {
				prepareStandardBody()
				foragingPage.render(
					g, font, activeSub(PvSubTab.FORAGING_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			PvTab.FISHING -> {
				prepareStandardBody()
				if (activeSub(PvSubTab.FISHING_MAIN) == PvSubTab.FISHING_TROPHIES) {
					fishingTrophyTrialPage.render(g, font, fishingPage.snapshot(), x, y, w, h)
				} else {
					fishingPage.render(g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
				}
			}
			PvTab.CRIMSON -> {
				prepareStandardBody()
				crimsonPage.render(
					g, font, activeSub(PvSubTab.CRIMSON_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			PvTab.MUSEUM -> {
				prepareStandardBody()
				dataLoader.ensureMuseum(false)
				museumPage.render(g, font, x, y, w, h, mouseX, mouseY, virtualW(), virtualH())
			}
			PvTab.EVENTS -> {
				prepareStandardBody()
				dataLoader.ensureBingo(false)
				eventsPage.render(
					g, font, activeSub(PvSubTab.EVENTS_BINGO),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			PvTab.RIFT -> {
				prepareStandardBody()
				riftPage.render(
					g, font, activeSub(PvSubTab.RIFT_OVERVIEW),
					x, y, w, h, mouseX, mouseY, virtualW(), virtualH()
				)
			}
			else -> {}
		}
	}

	private fun positionPlayerSearch(g: GuiGraphicsExtractor, panelX: Int, panelY: Int, panelW: Int, panelH: Int) {
		val search = playerSearch ?: return
		val boxW = 110
		val inset = 4
		val textH = maxOf(8, font.lineHeight)
		val boxX = panelX + panelW - boxW
		val boxY = panelY + panelH + 2
		val focused = search.isFocused
		PvDraw.roundedFill(g, boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, 3, 0xAA101018.toInt())
		PvDraw.roundedRing(g, boxX, boxY, boxW, InventorySplitLayout.SEARCH_H, 3, if (focused) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER, PvDraw.C_ALL)
		search.width = maxOf(20, boxW - inset * 2)
		search.setHeight(textH)
		search.x = boxX + inset
		search.y = boxY + maxOf(0, (InventorySplitLayout.SEARCH_H - textH) / 2)
		search.setVisible(true)
		search.extractWidgetRenderState(g, 0, 0, 0f)
		if (search.value.isEmpty() && !focused) {
			val hint = Component.translatable("fishmod.pv.home.search_hint").string
			PvDraw.text(g, font, hint, search.x, search.y, PvDraw.COLOR_MUTED)
		}
		searchHistory.setAnchor(boxX, boxY, boxW, InventorySplitLayout.SEARCH_H)
		if (searchHistory.syncSearch(focused, search.value)) {
			socialsMenu.close()
			profileSelector.closeMenu()
		}
		searchHistory.layoutDropdown(font.lineHeight)
	}

	private fun hidePlayerSearch() {
		val search = playerSearch ?: return
		search.setFocused(false)
		search.setVisible(false)
		searchHistory.close()
	}

	private fun playerSearchHit(mx: Double, my: Double): Boolean =
		playerSearch?.isVisible == true && searchHistory.anchorContains(mx, my)

	private fun openSearchedPlayer(name: String) {
		val next = ProfileViewerScreen(name)
		next.recordSearch = true
		Minecraft.getInstance().showScreen(next)
	}

	private fun submitPlayerSearch() {
		val search = playerSearch ?: return
		val trimmed = search.value?.trim() ?: ""
		if (trimmed.isEmpty()) {
			playerSearchError = Component.translatable("fishmod.pv.home.search_empty").string
			playerSearchErrorUntilMs = System.currentTimeMillis() + 2_500L
			return
		}
		playerSearchError = ""
		openSearchedPlayer(trimmed)
	}

	override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
		val mx = virtualMouseX(click.x())
		val my = virtualMouseY(click.y())
		if (searchHistory.mouseClicked(mx, my, click.button())) {
			return true
		}
		if (uiInteractive() && playerSearchHit(mx, my)) {
			socialsMenu.close()
			profileSelector.closeMenu()
			searchHistory.open()
			super.mouseClicked(virtualClick(click), doubled)
			val search = playerSearch
			if (search != null && !search.isFocused) {
				setFocused(search)
			}
			return true
		}
		if (socialsMenu.isOpen() && socialsMenu.mouseClicked(mx, my, click.button())) {
			return true
		}
		if (!uiInteractive()) {
			return true
		}
		if (socialsMenu.mouseClicked(mx, my, click.button())) {
			if (socialsMenu.isOpen()) {
				profileSelector.closeMenu()
			}
			return true
		}
		if (profileSelector.mouseClicked(mx, my)) {
			return true
		}
		if (routePageClick(mx, my)) {
			return true
		}
		if (topBar.click(mx, my) || sideBar.click(mx, my) || inventoryBar.click(mx, my)) {
			return true
		}
		if (tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
			if (homePage.slayerMouseClicked(mx, my)) {
				return true
			}
			if (homePage.clickSbLevelPanel(mx, my)) {
				return true
			}
			if (homePage.isSbLevelOverlayActive()) {
				return true
			}
			if (homePage.hitName(mx, my)) {
				dataLoader.ensureUsernameHistory()
				return true
			}
			if (homePage.hitStatus(mx, my)) {
				dataLoader.ensurePlayerStatus()
				return true
			}
			if (homePage.clickWeight(mx, my)) {
				return true
			}
			if (homePage.clickNetworth(mx, my, click.button())) {
				return true
			}
			if (homePage.clickLeftPanel(mx, my)) {
				return true
			}
		}
		return super.mouseClicked(virtualClick(click), doubled)
	}

	override fun mouseMoved(mouseX: Double, mouseY: Double) {
		super.mouseMoved(virtualMouseX(mouseX), virtualMouseY(mouseY))
	}

	override fun mouseDragged(click: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
		val scale = scaleViewport?.renderScale ?: 1.0f
		return super.mouseDragged(virtualClick(click), dragX / scale, dragY / scale)
	}

	override fun mouseReleased(click: MouseButtonEvent): Boolean = super.mouseReleased(virtualClick(click))

	private fun routePageClick(mx: Double, my: Double): Boolean = when (tab) {
		PvTab.HOME -> when (activeSub(PvSubTab.HOME_OVERVIEW)) {
			PvSubTab.HOME_MISC -> homeMiscPage.mouseClicked(mx, my)
			PvSubTab.HOME_ESSENCE -> homeEssencePage.mouseClicked(mx, my)
			else -> false
		}
		PvTab.DUNGEONS -> dungeonOverview() && dungeonPage.mouseClicked(mx, my)
		PvTab.MINING -> miningPage.mouseClicked(mx, my)
		PvTab.FORAGING -> foragingPage.mouseClicked(mx, my)
		PvTab.EVENTS -> eventsPage.mouseClicked(mx, my, activeSub(PvSubTab.EVENTS_BINGO))
		PvTab.MUSEUM -> when {
			museumPage.clickMissingOnly(mx, my) -> true
			museumPage.clickRefresh(mx, my) -> {
				dataLoader.ensureMuseum(true)
				true
			}
			else -> museumPage.clickCheapest()
		}
		PvTab.CRIMSON -> crimsonPage.mouseClicked(mx, my)
		PvTab.RIFT -> riftPage.mouseClicked(mx, my, activeSub(PvSubTab.RIFT_OVERVIEW))
		PvTab.GARDEN -> gardenPage.mouseClicked(mx, my)
		PvTab.PETS -> petsPage.mouseClicked(mx, my)
		PvTab.COLLECTIONS -> collectionsPage.mouseClicked(mx, my, activeSub(PvSubTab.COLLECTIONS_LIST))
		PvTab.INVENTORIES -> inventoryPage.mouseClicked(mx, my)
		else -> false
	}

	override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
		if (!uiInteractive()) {
			return true
		}
		val virtualX = virtualMouseX(mouseX)
		val virtualY = virtualMouseY(mouseY)
		if (socialsMenu.mouseScrolled(virtualX, virtualY) || searchHistory.mouseScrolled(virtualX, virtualY)) {
			return true
		}
		val horizontal = scrollX != 0.0 || shiftDown()
		if (PvTooltip.panOverflow(if (scrollX != 0.0) scrollX else scrollY, horizontal)) {
			return true
		}
		// Shift expands some tips (networth) taller than the screen; with no sideways overflow the wheel pans vertically.
		if (horizontal && scrollX == 0.0 && PvTooltip.panOverflow(scrollY, false)) {
			return true
		}
		if (profileSelector.mouseScrolled(virtualX, virtualY, scrollY)) {
			return true
		}
		if (routePageScroll(virtualX, virtualY, scrollY)) {
			return true
		}
		return super.mouseScrolled(virtualX, virtualY, scrollX, scrollY)
	}

	override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean =
		super.isMouseOver(virtualMouseX(mouseX), virtualMouseY(mouseY))

	private fun virtualClick(click: MouseButtonEvent): MouseButtonEvent =
		MouseButtonEvent(virtualMouseX(click.x()), virtualMouseY(click.y()), click.buttonInfo())

	private fun virtualMouseX(mouseX: Double): Double = scaleViewport?.toVirtualX(mouseX) ?: mouseX

	private fun virtualMouseY(mouseY: Double): Double = scaleViewport?.toVirtualY(mouseY) ?: mouseY

	private fun routePageScroll(mouseX: Double, mouseY: Double, scrollY: Double): Boolean = when (tab) {
		PvTab.HOME -> when (activeSub(PvSubTab.HOME_OVERVIEW)) {
			PvSubTab.HOME_MISC -> homeMiscPage.mouseScrolled(mouseX, mouseY, scrollY)
			PvSubTab.HOME_RECORDS -> false
			PvSubTab.HOME_ESSENCE -> homeEssencePage.mouseScrolled(mouseX, mouseY, scrollY)
			else -> homePage.mouseScrolled(mouseX, mouseY, scrollY)
		}
		PvTab.DUNGEONS -> if (dungeonOverview()) {
			dungeonPage.mouseScrolled(scrollY)
		} else {
			dungeonChestsPage.mouseScrolled(mouseX, mouseY, scrollY)
		}
		PvTab.MUSEUM -> museumPage.mouseScrolled(mouseX, mouseY, scrollY)
		PvTab.MINING -> miningPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.MINING_OVERVIEW))
		PvTab.FORAGING -> foragingPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.FORAGING_OVERVIEW))
		PvTab.EVENTS -> eventsPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.EVENTS_BINGO))
		PvTab.PETS -> petsPage.mouseScrolled(scrollY)
		PvTab.COLLECTIONS -> collectionsPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.COLLECTIONS_LIST))
		PvTab.GARDEN -> gardenPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.GARDEN_OVERVIEW))
		PvTab.CRIMSON -> crimsonPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.CRIMSON_OVERVIEW))
		PvTab.RIFT -> riftPage.mouseScrolled(mouseX, mouseY, scrollY, activeSub(PvSubTab.RIFT_OVERVIEW))
		PvTab.FISHING -> activeSub(PvSubTab.FISHING_MAIN) != PvSubTab.FISHING_TROPHIES &&
			fishingPage.mouseScrolled(mouseX, mouseY, scrollY)
		PvTab.BESTIARY -> bestiaryPage.mouseScrolled(mouseX, mouseY, scrollY)
		PvTab.INVENTORIES -> inventoryPage.mouseScrolled(mouseX, mouseY, scrollY)
		else -> false
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		val key = event.key()
		if (searchHistory.keyPressed(key) || socialsMenu.keyPressed(key)) {
			return true
		}
		val typingInvSearch = inventorySearch?.let { it.isVisible && it.isFocused } == true
		val typingPlayerSearch = playerSearch?.let { it.isVisible && it.isFocused } == true
		if (typingPlayerSearch && (key == 257 || key == 335)) {
			submitPlayerSearch()
			return true
		}
		val homeOverview = tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW
		if (isDirectionalKey(key) && !typingInvSearch && !typingPlayerSearch) {
			if (uiInteractive() && dungeonOverview() && dungeonPage.keyPressed(key)) {
				return true
			}
			if (uiInteractive() &&
				tab == PvTab.HOME &&
				activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_ESSENCE &&
				homeEssencePage.keyPressed(key)
			) {
				return true
			}
			// Screen arrow-key focus navigation would otherwise steal these
			// into the inventory/player search boxes.
			return true
		}
		if (uiInteractive() && homeOverview && homePage.slayerKeyPressed(key)) {
			return true
		}
		if (uiInteractive() && homeOverview && (key == 256 || key == 259) && homePage.requestSbLevelBack()) {
			// ESC / Backspace collapses SB Level overlay without closing PV.
			return true
		}
		if (uiInteractive() && dungeonOverview() && dungeonPage.keyPressed(key)) {
			return true
		}
		if (uiInteractive() &&
			tab == PvTab.COLLECTIONS &&
			collectionsPage.keyPressed(key, activeSub(PvSubTab.COLLECTIONS_LIST))
		) {
			return true
		}
		if (uiInteractive() && profileSelector.menuOpen() && key == 256) {
			profileSelector.closeMenu()
			return true
		}
		return super.keyPressed(event)
	}

	override fun charTyped(event: CharacterEvent): Boolean {
		if (uiInteractive() && dungeonOverview()) {
			val text = event.codepointAsString()
			if (!text.isNullOrEmpty() && dungeonPage.charTyped(text[0])) {
				return true
			}
		}
		if (uiInteractive() && tab == PvTab.HOME && activeSub(PvSubTab.HOME_OVERVIEW) == PvSubTab.HOME_OVERVIEW) {
			val text = event.codepointAsString()
			if (!text.isNullOrEmpty() && homePage.slayerCharTyped(text[0])) {
				return true
			}
		}
		return super.charTyped(event)
	}

	override fun isPauseScreen(): Boolean = true

	fun font(): Font = font

	fun loadGeneration(): Int = loadGeneration

	fun bumpLoadGeneration(): Int = ++loadGeneration

	fun isLoadGeneration(generation: Int): Boolean = generation == loadGeneration

	fun setDataReady(ready: Boolean) {
		dataReady = ready
	}

	fun dataLoader(): ProfileScreenDataLoader = dataLoader

	fun profileId(): String? = profileId

	fun playerUuid(): UUID? = playerUuid

	private fun socialTarget(): SocialLinks.Target =
		SocialLinks.Target(playerUuid, homePage.playerName(), profileId, homePage.profileName())

	private fun openExternalLink(url: String) {
		val uri = try {
			URI(url)
		} catch (e: URISyntaxException) {
			ProfileViewer.LOGGER.warn("Bad social link {}", url, e)
			return
		}
		// Same path as chat links (honours the confirm prompt); still confirm if chat links are off.
		if (!clickUrlAction(Minecraft.getInstance(), this, uri)) {
			ConfirmLinkScreen.confirmLinkNow(this, uri)
		}
	}

	fun profilesRoot(): JsonObject? = profilesRoot

	fun homePage(): HomePage = homePage

	fun homeMiscPage(): MiscStatsPage = homeMiscPage

	fun gardenPage(): GardenPage = gardenPage

	fun museumPage(): MuseumPage = museumPage

	fun eventsPage(): EventsPage = eventsPage

	private companion object {
		const val PAD = 8
		const val TARGET_GUI_SCALE = 2
		const val BOTTOM_FIT_ROOM = 39
		const val OPEN_ANIM_MS = 260L
		const val OPEN_SCALE_START = 0.12f

		fun friendlyLoadError(raw: String?): String {
			if (raw.isNullOrBlank()) {
				return "Player not found"
			}
			val lower = raw.lowercase(Locale.ROOT)
			if (lower.contains("not found") || lower.contains("unknown") || lower.contains("no player")) {
				return "Player not found"
			}
			return raw
		}

		fun isDirectionalKey(key: Int): Boolean = key == 262 || key == 263 || key == 264 || key == 265

		// Screens don't keep options.keyShift updated; poll GLFW instead.
		fun shiftDown(): Boolean {
			val window = Minecraft.getInstance()?.window ?: return false
			return fishmod.compat.GLFW.keyDown(window, InputConstants.KEY_LSHIFT) ||
				fishmod.compat.GLFW.keyDown(window, InputConstants.KEY_RSHIFT)
		}
	}
}
