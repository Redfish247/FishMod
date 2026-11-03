/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import com.google.gson.JsonObject
import com.mojang.blaze3d.platform.InputConstants
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.data.PlayerStatus
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.data.UsernameHistory
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.networth.NetworthBreakdown
import fishmod.pv.networth.NetworthMode
import fishmod.pv.slayer.SlayerCalcOverlay
import fishmod.pv.weight.WeightBreakdown
import fishmod.pv.weight.WeightSystem
import fishmod.utils.HypixelApi
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack

class HomePage(private var snapshot: ProfileSnapshot?) {
	/** Two-stage SkyBlock Level panel expand/collapse within Home bounds. */
	enum class SbXpExpandPhase {
		CLOSED,
		EXPANDING_LEFT,
		EXPANDING_RIGHT,
		OPEN,
		COLLAPSING_RIGHT,
		COLLAPSING_LEFT
	}

	private var playerStats: PlayerStatsSnapshot = PlayerStatsSnapshot.empty()
	private var senither: WeightBreakdown = WeightBreakdown.empty(WeightSystem.SENITHER)
	private var lily: WeightBreakdown = WeightBreakdown.empty(WeightSystem.LILY)
	private var networthNormal: NetworthBreakdown = NetworthBreakdown.empty("")
	private var fishNetworth = -1.0
	private var fishNetworthKey: String? = null
	private var networthNonCosmetic: NetworthBreakdown = NetworthBreakdown.empty("")
	private var networthUnsoulbound: NetworthBreakdown = NetworthBreakdown.empty("")
	private var networthUnsoulboundNonCosmetic: NetworthBreakdown = NetworthBreakdown.empty("")
	private var weightSystem = WeightSystem.SENITHER
	private var networthIncludeCosmetics = true
	private var networthUnsoulboundOnly = false
	private var loadError: String? = null
	private val slayerOverlay = SlayerCalcOverlay()
	private val sbXpOverlay = HomeSbXpOverlay()
	private val leftColumn = HomeLeftColumn()
	private val bars = HomeSkillSlayerBars()

	private var usernameHistory: UsernameHistory = UsernameHistory.idle()
	private var historyScroll = 0
	private var historyMaxScroll = 0
	private var playerStatus: PlayerStatus = PlayerStatus.idle()
	private var playerRank: JsonObject? = null
	private var armor: Array<ItemStack> = emptyArmor()
	private val zones = ArrayList<HoverZone>()
	private var cachedLayout: Layout? = null
	private var layoutCacheW = Int.MIN_VALUE
	private var layoutCacheLineH = -1
	private var openScale = 1.0F
	private var openPivotX = 0F
	private var openPivotY = 0F
	private var uiScale = 1.0F
	private var uiOffsetX = 0F
	private var uiOffsetY = 0F

	fun applyLoaded(
		snapshot: ProfileSnapshot?,
		senither: WeightBreakdown?,
		lily: WeightBreakdown?,
		networthNormal: NetworthBreakdown?,
		networthNonCosmetic: NetworthBreakdown?,
		networthUnsoulbound: NetworthBreakdown?,
		networthUnsoulboundNonCosmetic: NetworthBreakdown?,
		armor: Array<ItemStack>?,
		playerStats: PlayerStatsSnapshot?,
		error: String?
	) {
		val prevUuid = this.snapshot?.playerUuid
		val nextUuid = snapshot?.playerUuid
		this.snapshot = snapshot
		leftColumn.clearCaches()
		bars.clearCaches()
		sbXpOverlay.resetEmblemScroll()
		this.senither = senither ?: WeightBreakdown.empty(WeightSystem.SENITHER)
		this.lily = lily ?: WeightBreakdown.empty(WeightSystem.LILY)
		this.networthNormal = networthNormal ?: NetworthBreakdown.empty("")
		this.networthNonCosmetic = networthNonCosmetic ?: NetworthBreakdown.empty("")
		this.networthUnsoulbound = networthUnsoulbound ?: NetworthBreakdown.empty("")
		this.networthUnsoulboundNonCosmetic = networthUnsoulboundNonCosmetic ?: NetworthBreakdown.empty("")
		this.armor = armor ?: emptyArmor()
		this.playerStats = playerStats ?: PlayerStatsSnapshot.empty()
		loadError = error
		slayerOverlay.close()
		usernameHistory = UsernameHistory.idle()
		playerStatus = PlayerStatus.idle()
		if (nextUuid == null || prevUuid == null || nextUuid != prevUuid) {
			playerRank = null
		}
		requestFishNetworth(snapshot)
		invalidateLayoutCache()
	}

	// FishMod networth (same as !nw); only for the profile it was computed on.
	private fun requestFishNetworth(snap: ProfileSnapshot?) {
		if (snap == null) {
			return
		}
		val key = snap.playerName + "|" + snap.profileName
		if (key == fishNetworthKey) {
			return
		}
		fishNetworthKey = key
		fishNetworth = -1.0
		HypixelApi.getNetworth(Minecraft.getInstance(), snap.playerName) { nw, profile ->
			if (key != fishNetworthKey || nw <= 0) {
				return@getNetworth
			}
			if (profile != null && !profile.equals(snap.profileName, ignoreCase = true)) {
				return@getNetworth
			}
			fishNetworth = nw
			leftColumn.clearCaches()
			invalidateLayoutCache()
		}
	}

	fun applyPlayerRank(player: JsonObject?) {
		playerRank = player
		leftColumn.clearStyledNameCache()
	}

	fun applyUsernameHistory(history: UsernameHistory?) {
		usernameHistory = history ?: UsernameHistory.idle()
		historyScroll = 0
		historyMaxScroll = 0
	}

	/** Real cursor for the 3D model when the hover mouse passed to [render] is masked. */
	fun setModelLook(x: Int, y: Int) {
		leftColumn.setModelLook(x, y)
	}

	fun applyPlayerStatus(status: PlayerStatus?) {
		playerStatus = status ?: PlayerStatus.idle()
	}

	fun usernameHistory(): UsernameHistory = usernameHistory

	fun playerStatus(): PlayerStatus = playerStatus

	fun profileName(): String = snapshot?.profileName ?: ""

	fun playerName(): String = snapshot?.playerName ?: ""

	fun applyNetworth(
		normal: NetworthBreakdown?,
		nonCosmetic: NetworthBreakdown?,
		unsoulbound: NetworthBreakdown?,
		unsoulboundNonCosmetic: NetworthBreakdown?
	) {
		normal?.let { networthNormal = it }
		nonCosmetic?.let { networthNonCosmetic = it }
		unsoulbound?.let { networthUnsoulbound = it }
		unsoulboundNonCosmetic?.let { networthUnsoulboundNonCosmetic = it }
		// NW label is cached by total only; a failed load keeps total 0 and would stay "...".
		leftColumn.clearCaches()
		invalidateLayoutCache()
	}

	fun clickWeight(mouseX: Double, mouseY: Double): Boolean {
		if (leftColumn.showingStatsFace()) {
			return false
		}
		if (leftColumn.hitWeight(mouseX, mouseY)) {
			weightSystem = weightSystem.other()
			return true
		}
		return false
	}

	fun slayerMouseClicked(mouseX: Double, mouseY: Double): Boolean {
		if (slayerOverlay.isOpen()) {
			return slayerOverlay.mouseClicked(mouseX, mouseY)
		}
		return clickSlayerName(mouseX, mouseY)
	}

	fun slayerKeyPressed(key: Int): Boolean = slayerOverlay.keyPressed(key)

	fun slayerCharTyped(ch: Char): Boolean = slayerOverlay.charTyped(ch)

	fun slayerOverlayOpen(): Boolean = slayerOverlay.isOpen()

	fun renderSlayerOverlay(g: GuiGraphicsExtractor, font: Font, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
		slayerOverlay.render(g, font, screenW, screenH, mouseX, mouseY)
	}

	private fun clickSlayerName(mouseX: Double, mouseY: Double): Boolean {
		if (sbXpOverlay.phase() != SbXpExpandPhase.CLOSED) {
			return false
		}
		for (hit in bars.slayerNameHits()) {
			if (mouseX < hit.x || mouseX >= hit.x + hit.w || mouseY < hit.y || mouseY >= hit.y + hit.h) {
				continue
			}
			val slayer = slayerById(hit.slayerId) ?: return false
			slayerOverlay.open(slayer, snapshot?.slayerMods, hit.accent)
			return true
		}
		return false
	}

	private fun slayerById(id: String?): ProfileSnapshot.SlayerEntry? {
		val snap = snapshot
		if (snap == null || id.isNullOrBlank()) {
			return null
		}
		return snap.slayers.firstOrNull { id.equals(it.id, ignoreCase = true) }
	}

	fun clickLeftPanel(mouseX: Double, mouseY: Double): Boolean {
		if (slayerOverlay.isOpen()) {
			return true
		}
		if (clickSlayerName(mouseX, mouseY)) {
			return true
		}
		if (sbXpOverlay.phase() != SbXpExpandPhase.CLOSED) {
			return false
		}
		if (!leftColumn.contains(mouseX, mouseY)) {
			return false
		}
		if (hitName(mouseX, mouseY) || hitStatus(mouseX, mouseY)) {
			return false
		}
		return leftColumn.beginFlip()
	}

	/** Click the SkyBlock Level strip (closed → expand) or the open page (→ collapse). */
	fun clickSbLevelPanel(mouseX: Double, mouseY: Double): Boolean = sbXpOverlay.click(mouseX, mouseY)

	/** ESC / back: collapse expanded SB Level view. */
	fun requestSbLevelBack(): Boolean = sbXpOverlay.requestBack()

	fun isSbLevelOverlayActive(): Boolean = sbXpOverlay.isActive()

	/** Snap closed when leaving Home overview (tab switch, etc.). */
	fun forceCloseSbLevelOverlay() {
		sbXpOverlay.forceClose()
	}

	fun sbXpExpandPhase(): SbXpExpandPhase = sbXpOverlay.phase()

	fun hitName(mouseX: Double, mouseY: Double): Boolean = leftColumn.hitName(mouseX, mouseY)

	fun hitStatus(mouseX: Double, mouseY: Double): Boolean = leftColumn.hitStatus(mouseX, mouseY)

	fun clickNetworth(mouseX: Double, mouseY: Double, button: Int): Boolean {
		if (leftColumn.showingStatsFace()) {
			return false
		}
		if (!leftColumn.hitNetworth(mouseX, mouseY)) {
			return false
		}
		if (button == 0) {
			networthIncludeCosmetics = !networthIncludeCosmetics
			return true
		}
		if (button == 1) {
			networthUnsoulboundOnly = !networthUnsoulboundOnly
			return true
		}
		return false
	}

	private fun networthMode(): NetworthMode {
		if (networthUnsoulboundOnly) {
			return if (networthIncludeCosmetics) NetworthMode.UNSOULBOUND else NetworthMode.UNSOULBOUND_NON_COSMETIC
		}
		return if (networthIncludeCosmetics) NetworthMode.NORMAL else NetworthMode.NON_COSMETIC
	}

	private fun activeNetworth(): NetworthBreakdown = when (networthMode()) {
		NetworthMode.NORMAL -> if (fishNetworth > 0) networthNormal.withTotal(fishNetworth) else networthNormal
		NetworthMode.NON_COSMETIC -> networthNonCosmetic
		NetworthMode.UNSOULBOUND -> networthUnsoulbound
		NetworthMode.UNSOULBOUND_NON_COSMETIC -> networthUnsoulboundNonCosmetic
	}

	fun preferredHeight(font: Font, width: Int): Int = layoutFor(font, width).contentH

	private fun invalidateLayoutCache() {
		layoutCacheW = Int.MIN_VALUE
		layoutCacheLineH = -1
		cachedLayout = null
	}

	private fun layoutFor(font: Font, width: Int): Layout {
		val cached = cachedLayout
		if (cached != null && width == layoutCacheW && font.lineHeight == layoutCacheLineH) {
			return cached
		}
		layoutCacheW = width
		layoutCacheLineH = font.lineHeight
		return measure(font, width).also { cachedLayout = it }
	}

	fun render(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		mouseX: Int,
		mouseY: Int,
		screenW: Int,
		screenH: Int,
		openScale: Float,
		openPivotX: Float,
		openPivotY: Float,
		uiScale: Float,
		uiOffsetX: Float,
		uiOffsetY: Float
	) {
		this.openScale = openScale
		this.openPivotX = openPivotX
		this.openPivotY = openPivotY
		this.uiScale = uiScale
		this.uiOffsetX = uiOffsetX
		this.uiOffsetY = uiOffsetY
		zones.clear()
		sbXpOverlay.tick()
		val layout = layoutFor(font, w)
		val contentH = minOf(h, layout.contentH)
		val leftX = x
		val levelX = x + layout.leftW + layout.gap
		val barsX = levelX + layout.levelW + (if (layout.levelW > 0) layout.gap else 0)

		sbXpOverlay.setLayoutBounds(x, y, w, contentH, levelX, layout.levelW)

		val phase = sbXpOverlay.phase()
		val overlay = phase != SbXpExpandPhase.CLOSED
		val stageP = HomeUi.easeInOutCubic(sbXpOverlay.stageProgress())
		var animLeft = levelX
		var animRight = levelX + layout.levelW
		var leftMask = 0F
		var barsMask = 0F
		var cardFade = 1F

		when (phase) {
			SbXpExpandPhase.EXPANDING_LEFT -> {
				animLeft = Math.round(lerp(levelX.toFloat(), x.toFloat(), stageP))
				animRight = levelX + layout.levelW
				leftMask = stageP
				cardFade = 1F - stageP
			}
			SbXpExpandPhase.EXPANDING_RIGHT -> {
				animLeft = x
				animRight = Math.round(lerp((levelX + layout.levelW).toFloat(), (x + w).toFloat(), stageP))
				leftMask = 1F
				barsMask = stageP
				cardFade = 0F
			}
			SbXpExpandPhase.OPEN -> {
				animLeft = x
				animRight = x + w
				leftMask = 1F
				barsMask = 1F
				cardFade = 0F
			}
			SbXpExpandPhase.COLLAPSING_RIGHT -> {
				animLeft = x
				animRight = Math.round(lerp((x + w).toFloat(), (levelX + layout.levelW).toFloat(), stageP))
				leftMask = 1F
				barsMask = 1F - stageP
				cardFade = 0F
			}
			SbXpExpandPhase.COLLAPSING_LEFT -> {
				animLeft = Math.round(lerp(x.toFloat(), levelX.toFloat(), stageP))
				animRight = levelX + layout.levelW
				leftMask = 1F - stageP
				barsMask = 0F
				cardFade = stageP
			}
			SbXpExpandPhase.CLOSED -> {}
		}

		if (phase != SbXpExpandPhase.OPEN) {
			leftColumn.draw(
				g, font, leftX, y, layout.leftW, contentH, layout, mouseX, mouseY,
				snapshot, playerStats, playerStatus, playerRank, armor,
				activeNetworth(), senither, lily, weightSystem, loadError,
				this.openScale, this.openPivotX, this.openPivotY,
				this.uiScale, this.uiOffsetX, this.uiOffsetY, zones
			)
			if (leftMask > 0.01F) {
				PvDraw.fill(g, leftX, y, layout.leftW, contentH, withAlpha(HomeSbXpOverlay.SB_XP_MASK, leftMask))
			}
			// SkyBlock level card removed; the skill bars take its width.
			snapshot?.let { bars.draw(g, font, barsX, y, layout.barsW, contentH, layout.rowH, it, zones) }
			if (barsMask > 0.01F) {
				PvDraw.fill(g, barsX, y, layout.barsW, contentH, withAlpha(HomeSbXpOverlay.SB_XP_MASK, barsMask))
			}
		}

		if (overlay) {
			val animW = maxOf(1, animRight - animLeft)
			snapshot?.let { sbXpOverlay.draw(g, font, animLeft, y, animW, contentH, it, cardFade, false, zones) }
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		if (slayerOverlay.isOpen()) {
			return
		}
		val mx = mouseX.toDouble()
		val my = mouseY.toDouble()
		val phase = sbXpOverlay.phase()
		val onProfileFace = !leftColumn.showingStatsFace() && phase == SbXpExpandPhase.CLOSED
		if (onProfileFace && hitName(mx, my)) {
			val scrollOut = IntArray(2)
			HomeTooltips.drawUsernameHistory(
				g, font, usernameHistory, historyScroll,
				mouseX, mouseY, screenW, screenH, scrollOut
			)
			historyScroll = scrollOut[0]
			historyMaxScroll = scrollOut[1]
			return
		}
		var styledTip: List<PvTooltip.Line>? = null
		if (onProfileFace && hitStatus(mx, my)) {
			styledTip = HomeTooltips.status(playerStatus)
		} else if (onProfileFace && leftColumn.hitWeight(mx, my)) {
			val err = loadError
			styledTip = if (!err.isNullOrBlank()) {
				listOf(
					PvTooltip.Line.of("Weight unavailable", PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(err, PvDraw.COLOR_MUTED)
				)
			} else {
				activeWeight().tooltipStyledLines()
			}
		} else if (onProfileFace && leftColumn.hitNetworth(mx, my)) {
			styledTip = activeNetworth().tooltipStyledLines(networthMode(), leftShiftDown())
		} else if (onProfileFace && leftColumn.hitBank(mx, my)) {
			styledTip = snapshot?.let { HomeTooltips.bank(it) }
		} else if (phase == SbXpExpandPhase.CLOSED || phase == SbXpExpandPhase.OPEN || leftColumn.showingStatsFace()) {
			styledTip = zones.firstOrNull {
				mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h
			}?.lines
		}
		if (styledTip != null) {
			PvTooltip.drawStyled(g, font, styledTip, mouseX, mouseY, screenW, screenH)
		}
	}

	fun render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		render(
			g, font, x, y, w, h, mouseX, mouseY, screenW, screenH,
			1.0F, x + w / 2F, y + h / 2F, 1.0F, 0.0F, 0.0F
		)
	}

	private fun activeWeight(): WeightBreakdown = if (weightSystem == WeightSystem.SENITHER) senither else lily

	private fun measure(font: Font, w: Int): Layout {
		val layout = Layout()
		layout.gap = 6
		layout.levelW = 0
		layout.leftW = maxOf(100, (w - layout.gap) * 30 / 100)
		layout.barsW = w - layout.leftW - layout.gap
		layout.line = font.lineHeight + 2
		layout.rowH = font.lineHeight + BAR_LABEL_GAP + BAR_H + BAR_AFTER_GAP
		val extraSkillH = layout.rowH
		layout.barsInnerH = SKILL_ROWS * layout.rowH + extraSkillH + SECTION_GAP + SLAYER_ROWS * layout.rowH - BAR_AFTER_GAP
		layout.barsH = PAD * 2 + layout.barsInnerH
		layout.lastSlayerNameY = PAD + SKILL_ROWS * layout.rowH + extraSkillH + SECTION_GAP + (SLAYER_ROWS - 1) * layout.rowH
		layout.statsH = PAD + layout.line * 4 + 6
		layout.profileLineH = font.lineHeight
		layout.nameLineH = font.lineHeight
		layout.nameGap = 2
		layout.boxToFooterGap = 8
		layout.statusH = font.lineHeight + 4
		layout.footerH = layout.statusH
		layout.footerY = 0
		layout.boxBottom = 0
		layout.nameY = layout.statsH
		layout.boxTop = layout.nameY + layout.nameLineH + layout.nameGap
		layout.contentH = layout.barsH
		layout.levelH = layout.contentH
		layout.leftH = layout.contentH
		layout.footerY = layout.leftH - PAD - layout.statusH
		layout.boxBottom = layout.footerY - layout.boxToFooterGap
		layout.boxH = maxOf(48, layout.boxBottom - layout.boxTop)
		return layout
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (sbXpOverlay.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true
		}
		if (!leftColumn.showingStatsFace() && hitName(mouseX, mouseY)
			&& usernameHistory.state == UsernameHistory.State.READY
			&& historyMaxScroll > 0
		) {
			val step = 12
			val delta = if (scrollY > 0) -step else step
			val next = maxOf(0, minOf(historyMaxScroll, historyScroll + delta))
			if (next != historyScroll) {
				historyScroll = next
				return true
			}
		}
		return false
	}

	@JvmRecord
	data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	class Layout {
		@JvmField var gap = 0
		@JvmField var leftW = 0
		@JvmField var levelW = 0
		@JvmField var barsW = 0
		@JvmField var line = 0
		@JvmField var rowH = 0
		@JvmField var barsInnerH = 0
		@JvmField var barsH = 0
		@JvmField var lastSlayerNameY = 0
		@JvmField var statsH = 0
		@JvmField var profileLineH = 0
		@JvmField var statusH = 0
		@JvmField var nameLineH = 0
		@JvmField var nameGap = 0
		@JvmField var boxToFooterGap = 0
		@JvmField var footerH = 0
		@JvmField var footerY = 0
		@JvmField var nameY = 0
		@JvmField var boxTop = 0
		@JvmField var boxBottom = 0
		@JvmField var boxH = 0
		@JvmField var leftH = 0
		@JvmField var levelH = 0
		@JvmField var contentH = 0
	}

	private companion object {
		const val BAR_H = 6
		const val BAR_LABEL_GAP = 2
		const val BAR_AFTER_GAP = 6
		const val SECTION_GAP = 8
		const val PAD = 6
		const val SKILL_ROWS = 5
		const val SLAYER_ROWS = 3

		fun emptyArmor(): Array<ItemStack> = arrayOf(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)

		fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

		fun withAlpha(argb: Int, alpha: Float): Int {
			val a = Math.round(((argb ushr 24) and 0xFF) * alpha).coerceIn(0, 255)
			return (a shl 24) or (argb and 0x00FFFFFF)
		}

		/** GUI screens do not keep `options.keyShift` updated; poll GLFW instead. */
		fun leftShiftDown(): Boolean {
			val mc = Minecraft.getInstance() ?: return false
			val window = mc.window ?: return false
			return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
		}
	}
}
