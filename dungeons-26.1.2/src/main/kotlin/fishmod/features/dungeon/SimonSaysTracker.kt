package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import java.util.regex.Pattern

object SimonSaysTracker {


    private val DEVICE_BOX = AABB(
        106.65, 120.0, 92.70,
        110.70, 126.0, 95.30
    )
    private val DEVICE_CENTER = BlockPos(108, 120, 94)

    private const val DEV_BUTTONS_X = 110
    private const val DEV_OBSIDIAN_X = 111
    private const val DEV_Y_MIN = 120
    private const val DEV_Y_MAX = 123
    private const val DEV_Z_MIN = 92
    private const val DEV_Z_MAX = 95
    private const val BREAK_COOLDOWN_TICKS = 12

    private const val BREAK_GRACE_MS = 6000L

    private var round = 0
    private var resetAtMs = 0L
    private var lastAnnounced = 0
    private var primed = false
    private var completed = false
    private var completeLatched = false
    private var armed = false
    private var breakTicks = 0
    private var canBreak = false
    private var broken = false
    private var falseFailSent = false
    private var breakArmedAtMs = 0L
    private var atDevice = false
    private var deviceCenter: BlockPos? = null
    private var doneAtMs = 0L
    private val litPrev = HashSet<Long>()
    private val scanBuf = HashSet<Long>()
    private var seqLen = 0
    private var skipOver = false
    private var buttonsUp: Boolean? = null
    private val BUTTON_CHECK = BlockPos(110, 120, 93)

    private val SS_CHAT: Pattern = Pattern.compile("Simon Says: (\\d)/5")

    private const val GOLDOR_INTRO = "who dares trespass into my domain"

    @JvmField
    var debug = false
    private const val DBG_R = 6
    private val dbgPrev = HashMap<Long, Block?>()

    @JvmStatic
    fun init() {
        fishmod.utils.events.Events.ON_LOCATION_CHANGE.register { reset(); false }

        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            if (!FishSettings.simonSaysEnabled) return@register false
            val s = message.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")

            if (!armed && s.lowercase().contains(GOLDOR_INTRO)) {
                armed = true
                if (debug) log("armed (Goldor intro seen)")
            }

            if (!completeLatched) {
                val ss = SS_CHAT.matcher(s)
                if (ss.find()) {
                    val n = ss.group(1)[0] - '0'
                    if (n in 1..4) {
                        round = n
                    } else if (n >= 5) {
                        round = 5
                        doneAtMs = System.currentTimeMillis()
                        if (!completed && falseFailSent) {
                            Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§a(actually completed — ignore the FAILED above)"))
                        }
                        completed = true
                        completeLatched = true
                        breakArmedAtMs = 0L
                        canBreak = false
                        broken = false
                        falseFailSent = false
                    }
                }
            }

            if (debug && s.contains("device")) log("msg: \"$s\"")
            if (!s.contains("completed a device")) return@register false
            val mc = Minecraft.getInstance()
            val self = mc.player?.gameProfile?.name()
            val mine = (self == null) || s.contains(self)
            if (debug) log("completed-a-device match; self=$self mine=$mine completed=$completed")
            if (mine) tryComplete()
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { debugTick(it); tick(it) })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "simon_says_tracker")) { ctx, tc -> if (!fishmod.features.FishHudEditor.isOpen()) renderHud(ctx, tc) }

        FishHudEditor.register(
            "Simon Says",
            { FishSettings.simonSaysHudX }, { v -> FishSettings.simonSaysHudX = v },
            { FishSettings.simonSaysHudY }, { v -> FishSettings.simonSaysHudY = v },
            110, 14,
            { FishSettings.simonSaysHudScale }, { v -> FishSettings.simonSaysHudScale = v },
            { FishSettings.simonSaysHudEnabled }
        )
    }

    private fun tick(client: Minecraft) {
        if (!FishSettings.simonSaysEnabled || client.player == null || client.level == null) {
            atDevice = false; deviceCenter = null; reset(); return
        }

        if (completed) { atDevice = false; return }

        if (!armed) { atDevice = false; return }

        tickBreakState(client.level!!)
        if (broken) {
            atDevice = false; deviceCenter = null; primed = false; litPrev.clear(); seqLen = 0; buttonsUp = null
            return
        }

        atDevice = false
        for (p: Player in client.level!!.players()) {
            if (p.boundingBox.intersects(DEVICE_BOX)) { atDevice = true; break }
        }

        if (!atDevice) {
            deviceCenter = null; primed = false; litPrev.clear()
            return
        }

        if (deviceCenter == null) {
            deviceCenter = DEVICE_CENTER
            primed = false; seqLen = 0; skipOver = false; litPrev.clear(); buttonsUp = null
            if (debug) log("locked device center " + deviceCenter!!.toShortString())
        }

        val world = client.level!!
        if (!world.hasChunk(DEV_OBSIDIAN_X shr 4, DEV_Z_MIN shr 4)) return

        // NoammAddons approach: count lanterns lighting on the 4x4 grid, lock the stage in when the buttons come back.
        scanBuf.clear()
        val m = BlockPos.MutableBlockPos()
        for (y in DEV_Y_MIN..DEV_Y_MAX) for (z in DEV_Z_MIN..DEV_Z_MAX) {
            m.set(DEV_OBSIDIAN_X, y, z)
            if (world.getBlockState(m).block == Blocks.SEA_LANTERN) scanBuf.add(m.asLong())
        }
        if (!primed) { litPrev.clear(); litPrev.addAll(scanBuf); primed = true }
        else {
            for (p in scanBuf) if (!litPrev.contains(p)) {
                seqLen = (seqLen + 1).coerceAtMost(5)
                if (FishSettings.simonSaysSkipCompat && seqLen == 2 && !skipOver) seqLen--
                if (debug) log("flash seqLen=$seqLen")
            }
            litPrev.clear(); litPrev.addAll(scanBuf)
        }

        val btn = world.getBlockState(BUTTON_CHECK).block
        val up = btn == Blocks.STONE_BUTTON
        if (btn == Blocks.AIR) seqLen = 0
        if (up && buttonsUp == false) {
            skipOver = true
            if (seqLen > 0 && seqLen > round && seqLen < 5) {
                round = seqLen
                if (round > lastAnnounced) { lastAnnounced = round; announceRound(round) }
            }
        }
        buttonsUp = up
    }

    private fun tryComplete() {
        if (debug) log("tryComplete called (completed=$completed)")
        if (completed) return
        round = 5
        lastAnnounced = 5
        doneAtMs = System.currentTimeMillis()
        announceRound(5)
        if (falseFailSent) {
            Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§a(actually completed — ignore the FAILED above)"))
            if (FishSettings.simonSaysPartyChat) fishmod.utils.ChatQueue.enqueue("pc Simon Says: actually completed, ignore the FAILED above")
        }
        completed = true
        completeLatched = true
        breakArmedAtMs = 0L
        canBreak = false
        broken = false
        falseFailSent = false
    }

    @JvmStatic
    fun onTitle(title: String?) {
        if (!FishSettings.simonSaysEnabled || title == null) return
        val s = title.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").lowercase()
        if (s.contains("device") && s.contains("complete")) tryComplete()
    }

    private fun announceRound(r: Int) {
        val label = "$r/5" + (if (r >= 5) " (done)" else "")
        Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§bSimon Says: §a" + label))
        val (custom, text) = when (r) {
            1 -> FishSettings.simon1Enabled to FishSettings.simon1Message
            2 -> FishSettings.simon2Enabled to FishSettings.simon2Message
            3 -> FishSettings.simon3Enabled to FishSettings.simon3Message
            4 -> FishSettings.simon4Enabled to FishSettings.simon4Message
            else -> FishSettings.simon5Enabled to FishSettings.simon5Message
        }
        when {
            custom && text.isNotBlank() -> fishmod.utils.ChatQueue.enqueue("pc $text")
            FishSettings.simonSaysPartyChat -> fishmod.utils.ChatQueue.enqueue("pc Simon Says: $label")
        }
    }

    private fun tickBreakState(world: Level) {
        if (completeLatched) { breakArmedAtMs = 0L; return }
        if (!world.hasChunk(DEV_OBSIDIAN_X shr 4, DEV_Z_MIN shr 4)) { breakArmedAtMs = 0L; return }

        breakTicks--

        var active = false
        val m = BlockPos.MutableBlockPos()
        outer@ for (y in DEV_Y_MIN..DEV_Y_MAX)
            for (z in DEV_Z_MIN..DEV_Z_MAX) {
                m.set(DEV_OBSIDIAN_X, y, z)
                if (world.getBlockState(m).block != Blocks.OBSIDIAN) { active = true; break@outer }
            }

        if (active) {
            breakTicks = BREAK_COOLDOWN_TICKS
            canBreak = true
            breakArmedAtMs = 0L
            if (broken) {
                broken = false
                if (debug) log("device restarted — resuming")
            }
            return
        }

        if (breakTicks > 0 || !canBreak) return

        var allAir = true
        outer2@ for (y in DEV_Y_MIN..DEV_Y_MAX)
            for (z in DEV_Z_MIN..DEV_Z_MAX) {
                m.set(DEV_BUTTONS_X, y, z)
                if (world.getBlockState(m).block != Blocks.AIR) { allAir = false; break@outer2 }
            }
        if (!allAir) { breakArmedAtMs = 0L; return }

        val now = System.currentTimeMillis()
        if (breakArmedAtMs == 0L) { breakArmedAtMs = now; return }

        val sinceClick = now - fishmod.features.dungeon.f7.SimonSaysSolver.lastRoundCompleteMs
        if (fishmod.features.dungeon.f7.SimonSaysSolver.lastRoundCompleteMs != 0L && sinceClick in 0..BREAK_GRACE_MS) {
            tryComplete()
            return
        }

        if (now - breakArmedAtMs < BREAK_GRACE_MS) return

        canBreak = false
        broken = true
        breakArmedAtMs = 0L
        round = 0; lastAnnounced = 0
        resetAtMs = now
        if (debug) log("device broke — reset + fully off until restart")

        if (FishSettings.simonSaysFailEnabled) {
            falseFailSent = true
            Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§c" + FishSettings.simonSaysFailMessage))
            if (FishSettings.simonSaysPartyChat) fishmod.utils.ChatQueue.enqueue("pc " + FishSettings.simonSaysFailMessage)
        }
    }

    private fun reset() {
        round = 0
        lastAnnounced = 0
        primed = false
        completed = false
        completeLatched = false
        armed = false
        breakTicks = 0
        canBreak = false
        broken = false
        breakArmedAtMs = 0L
        doneAtMs = 0L
        falseFailSent = false
        litPrev.clear()
        deviceCenter = null
        seqLen = 0; skipOver = false; buttonsUp = null
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tc: DeltaTracker) {
        if (!FishSettings.simonSaysEnabled || !FishSettings.simonSaysHudEnabled) return
        val now = System.currentTimeMillis()
        val showReset = round <= 0 && FishSettings.ssProgressShowReset && resetAtMs > 0 && now - resetAtMs < 3000
        if (round <= 0 && !showReset) return
        if (round >= 5 && doneAtMs > 0 && now - doneAtMs > 2000) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val dc = deviceCenter
        if (dc != null && player.blockPosition().distSqr(dc) <= 12) return

        val (text, color) = when {
            showReset -> FishSettings.ssProgressResetText to FishSettings.ssProgressResetColor
            round >= 5 -> {
                if (!FishSettings.ssProgressShowCompleted) return
                FishSettings.ssProgressCompletedText to FishSettings.ssProgressCompletedColor
            }
            else -> {
                if (!FishSettings.ssProgressShowProgress) return
                FishSettings.ssProgressProgressText.replace("(n)", round.toString()) to FishSettings.ssProgressProgressColor
            }
        }
        val label = Component.literal(text).withColor(color and 0xFFFFFF)

        val sc = FishSettings.simonSaysHudScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.simonSaysHudX.toFloat(), FishSettings.simonSaysHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label, 0, 0, -1, true)
        ctx.pose().popMatrix()
    }

    private fun log(line: String) {
        fishmod.utils.debug.Debug.LOGGER.info("[SS] {}", line)
        Misc.addChatMessage(Component.literal("§e[SS] $line"))
    }

    private fun debugTick(client: Minecraft) {
        if (!debug || client.player == null || client.level == null) { dbgPrev.clear(); return }
        val world = client.level!!
        val c = client.player!!.blockPosition()
        val m = BlockPos.MutableBlockPos()
        val first = dbgPrev.isEmpty()
        for (dx in -DBG_R..DBG_R)
            for (dy in -DBG_R..DBG_R)
                for (dz in -DBG_R..DBG_R) {
                    m.set(c.x + dx, c.y + dy, c.z + dz)
                    val b = world.getBlockState(m).block
                    val key = m.asLong()
                    val old = dbgPrev.put(key, b)
                    if (first || old === b) continue
                    val oldId = if (old == null) "none" else net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(old).path
                    val newId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).path
                    log("($dx,$dy,$dz) $oldId -> $newId")
                }
    }
}
