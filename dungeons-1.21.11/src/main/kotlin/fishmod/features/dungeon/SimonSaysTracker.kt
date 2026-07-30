package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.math.Box
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.regex.Pattern

/** Tracks Goldor (F7 P3) Simon Says rounds via block scanning of a fixed world-space device box. */
object SimonSaysTracker {

    private const val SCAN_RADIUS = 7 // lit-cell box around the locked device center
    private const val BURST_GAP_MS = 550L

    // Fixed box around the Goldor SS device, extended upward so a jumping player still counts as "at the device".
    private val DEVICE_BOX = Box(
        106.65, 120.0, 92.70,
        110.70, 126.0, 95.30
    )
    private val DEVICE_CENTER = BlockPos(108, 120, 94)

    // Break detection: obsidian column behind device (active = any cell not obsidian), button column in front
    // (reset = all air) once settled for BREAK_COOLDOWN_TICKS.
    private const val DEV_BUTTONS_X = 110
    private const val DEV_OBSIDIAN_X = 111
    private const val DEV_Y_MIN = 120
    private const val DEV_Y_MAX = 123
    private const val DEV_Z_MIN = 92
    private const val DEV_Z_MAX = 95
    private const val BREAK_COOLDOWN_TICKS = 12

    private var round = 0          // completed-round count shown on the HUD (0..5)
    private var maxLen = 0         // longest demo sequence length seen this run
    private var lastAnnounced = 0  // highest count already sent (dedupe)
    private var burstFlashes = 0   // rising-edge flashes in the current demo
    private var lastFlashMs = 0L
    private var lastAtDeviceMs = 0L // last tick the player was at the device
    private var primed = false
    private var completed = false // SS done this run — ignore everything until next run
    private var armed = false     // Goldor's intro line seen — scanning starts here
    private var breakTicks = 0    // cooldown before an "inactive" reading can count as a break
    private var canBreak = false  // device has been seen active since the last break
    private var broken = false    // device just reset — fully off (no scan/announce) until it restarts
    private var inP3 = false      // HUD only
    private var atDevice = false
    private var deviceCenter: BlockPos? = null
    private var doneAtMs = 0L // when 5/5 fired — HUD unrenders 2s later
    private val litPrev = HashSet<Long>()

    // Reads "Simon Says: N/5" from party chat so the HUD also registers a teammate doing SS.
    private val SS_CHAT: Pattern = Pattern.compile("Simon Says: (\\d)/5")

    private const val GOLDOR_INTRO = "who dares trespass into my domain"

    // ── debug: log every block transition in a cube around the player (/ssdbg) ──
    @JvmField
    var debug = false
    private const val DBG_R = 6
    private val dbgPrev = HashMap<Long, Block?>()

    @JvmStatic
    fun init() {
        fishmod.utils.events.Events.ON_LOCATION_CHANGE.register { reset(); false }

        fishmod.utils.events.Events.ON_GAME_MESSAGE.register { message ->
            if (!FishSettings.simonSaysEnabled) return@register false
            val s = message.string.replace(Regex("§."), "")

            if (!armed && s.lowercase().contains(GOLDOR_INTRO)) {
                armed = true
                if (debug) log("armed (Goldor intro seen)")
            }

            val ss = SS_CHAT.matcher(s)
            if (ss.find()) {
                val n = ss.group(1)[0] - '0'
                if (n in 1..5) {
                    round = n
                    if (n >= 5) doneAtMs = System.currentTimeMillis()
                }
            }

            if (debug && s.contains("device")) log("msg: \"$s\"")
            if (!s.contains("completed a device")) return@register false
            // Loose name match since teammates' completions also broadcast and color codes can shift spacing.
            val mc = MinecraftClient.getInstance()
            val self = mc.player?.gameProfile?.name
            val mine = (self == null) || s.contains(self)
            if (debug) log("completed-a-device match; self=$self mine=$mine completed=$completed")
            if (mine) tryComplete()
            false
        }

        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { debugTick(it) })
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick(it) })
        HudRenderCallback.EVENT.register(HudRenderCallback { ctx, tc -> renderHud(ctx, tc) })

        FishHudEditor.register(
            "Simon Says",
            { FishSettings.simonSaysHudX }, { v -> FishSettings.simonSaysHudX = v },
            { FishSettings.simonSaysHudY }, { v -> FishSettings.simonSaysHudY = v },
            110, 14,
            { FishSettings.simonSaysHudScale }, { v -> FishSettings.simonSaysHudScale = v },
            { FishSettings.simonSaysHudEnabled && round > 0 }
        )
    }

    private fun tick(client: MinecraftClient) {
        if (!FishSettings.simonSaysEnabled || client.player == null || client.world == null) {
            inP3 = false; atDevice = false; deviceCenter = null; reset(); return
        }
        inP3 = safeInP3()

        if (completed) { atDevice = false; return }
        if (!armed) { atDevice = false; return }

        tickBreakState(client.world!!)
        if (broken) {
            atDevice = false; deviceCenter = null; primed = false; burstFlashes = 0; litPrev.clear()
            return
        }

        val now = System.currentTimeMillis()

        atDevice = false
        for (p: PlayerEntity in client.world!!.players) {
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

        val cur = HashSet<Long>()
        scanLitCells(client.world!!, deviceCenter!!, cur)

        // Prime on the first scan so the always-lit decorative frame lanterns aren't miscounted.
        if (!primed) { litPrev.clear(); litPrev.addAll(cur); primed = true; return }

        var newlyLit = 0
        for (p in cur) if (!litPrev.contains(p)) newlyLit++
        litPrev.clear()
        litPrev.addAll(cur)

        if (newlyLit > 0) {
            // A gap before this light means a new demo just started; announce on the first light.
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

    /** 5/5 finish, triggered by the in-game completion message; fires once per run. */
    private fun tryComplete() {
        if (debug) log("tryComplete called (completed=$completed)")
        if (completed) return
        round = 5
        lastAnnounced = 5
        doneAtMs = System.currentTimeMillis()
        announceRound(5)
        completed = true
    }

    /** Center-screen title hook (titles are local-only, so a loose match is safe). */
    @JvmStatic
    fun onTitle(title: String?) {
        if (!FishSettings.simonSaysEnabled || title == null) return
        val s = title.replace(Regex("§."), "").lowercase()
        if (s.contains("device") && s.contains("complete")) tryComplete()
    }

    private fun announceRound(r: Int) {
        val label = "$r/5" + (if (r >= 5) " (done)" else "")
        Misc.addChatMessage(Text.literal(fishmod.utils.FishMsg.prefix() + "§bSimon Says: §a" + label))
        if (FishSettings.simonSaysPartyChat) Misc.executeCommand("pc Simon Says: $label")
    }

    /** Watches the fixed obsidian/button columns for a break and resets round tracking when one occurs. */
    private fun tickBreakState(world: World) {
        breakTicks--

        var active = false
        val m = BlockPos.Mutable()
        outer@ for (y in DEV_Y_MIN..DEV_Y_MAX)
            for (z in DEV_Z_MIN..DEV_Z_MAX) {
                m.set(DEV_OBSIDIAN_X, y, z)
                if (world.getBlockState(m).block != Blocks.OBSIDIAN) { active = true; break@outer }
            }

        if (active) {
            breakTicks = BREAK_COOLDOWN_TICKS
            canBreak = true
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
        if (!allAir) return

        canBreak = false
        broken = true
        round = 0; maxLen = 0; lastAnnounced = 0; burstFlashes = 0
        if (debug) log("device broke — reset + fully off until restart")

        if (FishSettings.simonSaysFailEnabled) {
            Misc.addChatMessage(Text.literal(fishmod.utils.FishMsg.prefix() + "§c" + FishSettings.simonSaysFailMessage))
            if (FishSettings.simonSaysPartyChat) Misc.executeCommand("pc " + FishSettings.simonSaysFailMessage)
        }
    }

    /** Fills `litOut` with lit sea-lantern positions in the box around the locked device center. */
    private fun scanLitCells(world: World, center: BlockPos, litOut: HashSet<Long>) {
        val cx = center.x; val cy = center.y; val cz = center.z
        val m = BlockPos.Mutable()
        for (dx in -SCAN_RADIUS..SCAN_RADIUS)
            for (dy in -SCAN_RADIUS..SCAN_RADIUS)
                for (dz in -SCAN_RADIUS..SCAN_RADIUS) {
                    m.set(cx + dx, cy + dy, cz + dz)
                    if (world.getBlockState(m).block == Blocks.SEA_LANTERN) litOut.add(m.asLong())
                }
    }

    /** Phase.inP3() but never throws — blade-addons' Phase may differ; treat errors as false. */
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
        armed = false
        breakTicks = 0
        canBreak = false
        broken = false
        doneAtMs = 0L
        litPrev.clear()
        deviceCenter = null
    }

    @JvmStatic
    fun getStage(): Int = round

    @JvmStatic
    fun renderHud(ctx: DrawContext, tc: RenderTickCounter) {
        if (!FishSettings.simonSaysEnabled || !FishSettings.simonSaysHudEnabled) return
        if (round <= 0) return
        if (round >= 5 && doneAtMs > 0 && System.currentTimeMillis() - doneAtMs > 2000) return
        val mc = MinecraftClient.getInstance()
        val player = mc.player ?: return
        // Don't render when standing at the device (~3 blocks) — you can see it yourself.
        val dc = deviceCenter
        if (dc != null && player.blockPos.getSquaredDistance(dc) <= 12) return

        val label = if (round == 0)
            "§bSimon Says: §7—"
        else
            "§bSimon Says: §a$round§7/5" + (if (round >= 5) " §7(done)" else "")

        val sc = FishSettings.simonSaysHudScale.toFloat()
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(FishSettings.simonSaysHudX.toFloat(), FishSettings.simonSaysHudY.toFloat())
        ctx.matrices.scale(sc, sc)
        ctx.drawText(mc.textRenderer, label, 0, 0, -1, true)
        ctx.matrices.popMatrix()
    }

    private fun log(line: String) {
        fishmod.utils.debug.Debug.LOGGER.info("[SS] {}", line)
        Misc.addChatMessage(Text.literal("§e[SS] $line"))
    }

    /** /ssdbg: logs every block transition in a cube around the player — for locating the device. */
    private fun debugTick(client: MinecraftClient) {
        if (!debug || client.player == null || client.world == null) { dbgPrev.clear(); return }
        val world = client.world!!
        val c = client.player!!.blockPos
        val m = BlockPos.Mutable()
        val first = dbgPrev.isEmpty()
        for (dx in -DBG_R..DBG_R)
            for (dy in -DBG_R..DBG_R)
                for (dz in -DBG_R..DBG_R) {
                    m.set(c.x + dx, c.y + dy, c.z + dz)
                    val b = world.getBlockState(m).block
                    val key = m.asLong()
                    val old = dbgPrev.put(key, b)
                    if (first || old === b) continue
                    val oldId = if (old == null) "none" else Registries.BLOCK.getId(old).path
                    val newId = Registries.BLOCK.getId(b).path
                    log("($dx,$dy,$dz) $oldId -> $newId")
                }
    }
}
