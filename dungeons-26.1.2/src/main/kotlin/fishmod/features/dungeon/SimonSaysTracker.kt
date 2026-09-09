package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
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

/**
 * Tracks Goldor (F7 P3) Simon Says rounds via block scanning. Player must be inside `DEVICE_BOX`
 * to lock onto `DEVICE_CENTER`, then rounds are counted by demo "flashes" (lit sea-lantern rising
 * edges); 5/5 instead comes from the in-game "completed a device!" message. A break is detected
 * via a fixed obsidian/button-column signal, which resets tracking to 0 until the device goes
 * active again.
 */
object SimonSaysTracker {

    private const val SCAN_RADIUS = 3 // lit-cell box around the locked device center (covers the 4x4 lantern grid)
    private const val BURST_GAP_MS = 550L

    // Fixed detection box around the Goldor SS device, from measured corner coords, extended
    // upward a few blocks so the whole player (standing or jumping) counts as "at the device".
    private val DEVICE_BOX = AABB(
        106.65, 120.0, 92.70,
        110.70, 126.0, 95.30
    )
    private val DEVICE_CENTER = BlockPos(108, 120, 94)

    // Break/restart detection: a fixed obsidian
    // column behind the device and the button column in front of it. Any obsidian cell not
    // being obsidian = the device is "active" (mid demo/attempt). Once that settles for
    // BREAK_COOLDOWN_TICKS and every button cell reads air, the device has reset — a break.
    private const val DEV_BUTTONS_X = 110
    private const val DEV_OBSIDIAN_X = 111
    private const val DEV_Y_MIN = 120
    private const val DEV_Y_MAX = 123
    private const val DEV_Z_MIN = 92
    private const val DEV_Z_MAX = 95
    private const val BREAK_COOLDOWN_TICKS = 12

    // Grace window after the "all reset" block pattern first appears before it's treated as a
    // break. A legit 5/5 finish flips the exact same obsidian/button cells as a break does — the
    // only difference is the "completed a device!" chat message. Under P3 chat load that line
    // regularly lags 1-2s behind the block update, and if the break commits first it fires a
    // bogus "Simon Says: FAILED!" that a moment later is contradicted by 5/5. Wait long enough
    // that completion reliably wins the race; a genuine grief's FAILED notice being ~2.5s late
    // is harmless. Completion also disarms a pending break outright (see tryComplete()).
    private const val BREAK_GRACE_MS = 2500L

    private var round = 0          // completed-round count shown on the HUD (0..5)
    private var maxLen = 0         // longest demo sequence length seen this run
    private var lastAnnounced = 0  // highest count already sent (dedupe)
    private var burstFlashes = 0   // rising-edge flashes in the current demo
    private var lastFlashMs = 0L
    private var lastAtDeviceMs = 0L // last tick the player was at the device
    private var primed = false
    private var completed = false // SS done this run — ignore everything until next run
    // Authoritative completion lock. Once the current device instance has been completed, no later
    // scan / break-grace / party-chat message may re-open scanning, emit "FAILED", or lower the
    // round. Cleared only by reset() (ON_LOCATION_CHANGE = genuine new dungeon/puzzle instance).
    private var completeLatched = false
    private var armed = false     // Goldor's intro line seen — scanning starts here
    private var breakTicks = 0    // cooldown before an "inactive" reading can count as a break
    private var canBreak = false  // device has been seen active since the last break
    private var broken = false    // device just reset — fully off (no scan/announce) until it restarts
    private var breakArmedAtMs = 0L // all-air reset pattern first seen; grace period before treating it as a break
    private var inP3 = false      // HUD only
    private var atDevice = false
    private var deviceCenter: BlockPos? = null
    private var doneAtMs = 0L // when 5/5 fired — HUD unrenders 2s later
    private val litPrev = HashSet<Long>()
    private val scanBuf = HashSet<Long>() // reused scratch set for scanLitCells — avoids a per-tick allocation
    private var scanCounter = 0
    private const val SCAN_INTERVAL_TICKS = 2 // throttle the 7^3 block scan; 100ms max added latency is well under BURST_GAP_MS

    // Reads "Simon Says: N/5" out of party chat so the HUD also registers when SOMEONE ELSE
    // does SS (we can't block-scan their device — but their mod announces to party chat).
    private val SS_CHAT: Pattern = Pattern.compile("Simon Says: (\\d)/5")

    // Goldor's intro line — arms scanning so we don't watch the device box before P3 starts.
    private const val GOLDOR_INTRO = "who dares trespass into my domain"

    // debug: log every block transition in a cube around the player (/ssdbg)
    @JvmField
    var debug = false
    private const val DBG_R = 6
    private val dbgPrev = HashMap<Long, Block?>()

    @JvmStatic
    fun init() {
        fishmod.utils.events.Events.ON_LOCATION_CHANGE.register { reset(); false }

        // "<you> completed a device! (x/7) (time | time)" → our SS finish (5/5).
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            if (!FishSettings.simonSaysEnabled) return@register false
            val s = message.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "")

            if (!armed && s.lowercase().contains(GOLDOR_INTRO)) {
                armed = true
                if (debug) log("armed (Goldor intro seen)")
            }

            // A locked completion outranks any later party-chat "Simon Says: N/5" (a teammate's
            // stale/duplicate announce must not drag a finished 5/5 back down).
            if (!completeLatched) {
                val ss = SS_CHAT.matcher(s)
                if (ss.find()) {
                    val n = ss.group(1)[0] - '0'
                    if (n in 1..4) {
                        round = n
                    } else if (n >= 5) {
                        // A "5/5" seen in chat (ours echoed back, or a teammate's) — lock the
                        // tracker done. No announce here: re-broadcasting would echo the line to
                        // party chat from every observer.
                        round = 5
                        doneAtMs = System.currentTimeMillis()
                        completed = true
                        completeLatched = true
                    }
                }
            }

            if (debug && s.contains("device")) log("msg: \"$s\"")
            if (!s.contains("completed a device")) return@register false
            // Must be OUR completion (teammates' device completions also broadcast). Match the name
            // loosely (anywhere in the line) so color-code spacing can't break it.
            val mc = Minecraft.getInstance()
            val self = mc.player?.gameProfile?.name()
            val mine = (self == null) || s.contains(self)
            if (debug) log("completed-a-device match; self=$self mine=$mine completed=$completed")
            if (mine) tryComplete()
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { debugTick(it); tick(it) })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "simon_says_tracker")) { ctx, tc -> renderHud(ctx, tc) }

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
            inP3 = false; atDevice = false; deviceCenter = null; reset(); return
        }
        inP3 = safeInP3()

        if (completed) { atDevice = false; return }

        if (!armed) { atDevice = false; return }

        tickBreakState(client.level!!)
        if (broken) {
            atDevice = false; deviceCenter = null; primed = false; burstFlashes = 0; litPrev.clear()
            return
        }

        val now = System.currentTimeMillis()

        // Anyone (not just us — a teammate may be the one doing SS) standing at the fixed device box?
        atDevice = false
        for (p: Player in client.level!!.players()) {
            if (p.boundingBox.intersects(DEVICE_BOX)) { atDevice = true; break }
        }
        if (atDevice) lastAtDeviceMs = now

        if (!atDevice) {
            deviceCenter = null; primed = false; burstFlashes = 0; litPrev.clear()
            return
        }

        if (deviceCenter == null) {
            deviceCenter = DEVICE_CENTER
            primed = false; burstFlashes = 0; litPrev.clear()
            if (debug) log("locked device center " + deviceCenter!!.toShortString())
        }

        // Skip this tick's scan entirely on an unloaded chunk — a stale/empty read would look
        // like every lantern just went dark, corrupting the demo-length count.
        if (!client.level!!.hasChunk(deviceCenter!!.x shr 4, deviceCenter!!.z shr 4)) return

        // Throttle the block scan itself — skipped ticks just keep last tick's lit set (litPrev)
        // and burst state untouched, so state stays consistent between scans.
        scanCounter++
        if (scanCounter < SCAN_INTERVAL_TICKS) return
        scanCounter = 0

        scanBuf.clear()
        scanLitCells(client.level!!, deviceCenter!!, scanBuf)
        val cur = scanBuf

        // Prime on the first scan so the always-lit decorative frame lanterns aren't miscounted.
        if (!primed) { litPrev.clear(); litPrev.addAll(cur); primed = true; return }

        var newlyLit = 0
        for (p in cur) if (!litPrev.contains(p)) newlyLit++
        litPrev.clear()
        litPrev.addAll(cur)

        if (newlyLit > 0) {
            // A gap before this light = a NEW demo just started. Announce here, on the FIRST light.
            if (now - lastFlashMs > BURST_GAP_MS) {
                if (burstFlashes > maxLen) maxLen = burstFlashes // finalize previous demo
                burstFlashes = 0
                val done = minOf(5, maxLen)
                if (done in 1..4 && done > lastAnnounced) {
                    lastAnnounced = done
                    round = done
                    announceRound(done)
                }
            }
            burstFlashes += newlyLit
            lastFlashMs = now
            if (debug) log("flash +$newlyLit burst=$burstFlashes maxLen=$maxLen")
        }
    }

    /**
     * 5/5 finish, triggered by the in-game "<you> completed a device!" message (the reliable
     * signal — block detection of rounds can miss). Fires once per run; the run reset clears it.
     */
    private fun tryComplete() {
        if (debug) log("tryComplete called (completed=$completed)")
        if (completed) return
        round = 5
        lastAnnounced = 5
        doneAtMs = System.currentTimeMillis()
        announceRound(5)
        completed = true
        completeLatched = true
        // Disarm any break that was mid-grace: the all-air pattern we were about to call a
        // FAILED is actually this finish. Also clears `broken` so a race can't leave the tracker
        // wedged "off" after a completed run.
        breakArmedAtMs = 0L
        canBreak = false
        broken = false
    }

    /** Center-screen title hook (titles are local-only, so a loose match is safe). */
    @JvmStatic
    fun onTitle(title: String?) {
        if (!FishSettings.simonSaysEnabled || title == null) return
        val s = title.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").lowercase()
        if (s.contains("device") && s.contains("complete")) tryComplete()
    }

    private fun announceRound(r: Int) {
        val label = "$r/5" + (if (r >= 5) " (done)" else "")
        Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§bSimon Says: §a" + label))
        if (FishSettings.simonSaysPartyChat) fishmod.utils.ChatQueue.enqueue("pc Simon Says: $label")
    }

    /** Obsidian cell missing = active; once that holds for `BREAK_COOLDOWN_TICKS` and buttons are all air, it's a break. */
    private fun tickBreakState(world: Level) {
        // A locked completion is final — never re-interpret the board as a break afterwards.
        // (tick() already returns before this on `completed`; this is the explicit invariant.)
        if (completeLatched) { breakArmedAtMs = 0L; return }
        // Don't trust block reads from a chunk that isn't actually loaded — under lag/chunk churn
        // an unloaded chunk can read back as air, which looks identical to a break.
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

        // All-air reset pattern seen — could be a break, or it could be the exact same block
        // flip a legit 5/5 finish causes. Give the "completed a device!" chat message a grace
        // window to arrive and set `completed` before committing to a break.
        val now = System.currentTimeMillis()
        if (breakArmedAtMs == 0L) { breakArmedAtMs = now; return }
        if (now - breakArmedAtMs < BREAK_GRACE_MS) return

        canBreak = false
        broken = true
        breakArmedAtMs = 0L
        round = 0; maxLen = 0; lastAnnounced = 0; burstFlashes = 0
        if (debug) log("device broke — reset + fully off until restart")

        if (FishSettings.simonSaysFailEnabled) {
            Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§c" + FishSettings.simonSaysFailMessage))
            if (FishSettings.simonSaysPartyChat) fishmod.utils.ChatQueue.enqueue("pc " + FishSettings.simonSaysFailMessage)
        }
    }

    /** Fills `litOut` with lit sea-lantern positions in the box around the locked device center. */
    private fun scanLitCells(world: Level, center: BlockPos, litOut: HashSet<Long>) {
        val cx = center.x; val cy = center.y; val cz = center.z
        val m = BlockPos.MutableBlockPos()
        for (dx in -SCAN_RADIUS..SCAN_RADIUS)
            for (dy in -SCAN_RADIUS..SCAN_RADIUS)
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    m.set(cx + dx, cy + dy, cz + dz)
                    if (world.getBlockState(m).block == Blocks.SEA_LANTERN) litOut.add(m.asLong())
                }
    }

    /** Phase.inP3() but never throws; treat errors as false. */
    private fun safeInP3(): Boolean {
        return try { Phase.inP3() } catch (t: Throwable) { false }
    }

    private fun reset() {
        round = 0
        maxLen = 0
        lastAnnounced = 0
        burstFlashes = 0
        primed = false
        completed = false
        completeLatched = false
        armed = false
        breakTicks = 0
        canBreak = false
        broken = false
        breakArmedAtMs = 0L
        doneAtMs = 0L
        litPrev.clear()
        scanCounter = 0
        deviceCenter = null
    }

    @JvmStatic
    fun getStage(): Int = round

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tc: DeltaTracker) {
        if (!FishSettings.simonSaysEnabled || !FishSettings.simonSaysHudEnabled) return
        if (round <= 0) return
        // Auto-hide 2 seconds after completion.
        if (round >= 5 && doneAtMs > 0 && System.currentTimeMillis() - doneAtMs > 2000) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        // Don't render when standing right at the device (~3 blocks) — you can see it yourself.
        val dc = deviceCenter
        if (dc != null && player.blockPosition().distSqr(dc) <= 12) return

        val label = if (round == 0)
            "§bSimon Says: §7—"
        else
            "§bSimon Says: §a$round§7/5" + (if (round >= 5) " §7(done)" else "")

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

    /** /ssdbg: logs every block transition in a cube around the player — for locating the device. */
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
