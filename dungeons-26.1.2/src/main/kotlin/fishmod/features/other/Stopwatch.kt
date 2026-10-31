package fishmod.features.other

import com.mojang.blaze3d.platform.InputConstants
import fishmod.compat.hudHidden
import fishmod.features.dungeon.f7.terminal.TerminalType
import fishmod.features.dungeon.map.DungeonState
import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.Keybinds
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW

object Stopwatch {
    enum class Trigger(private val label: String) {
        NONE("None (keybind only)"),
        MOVE("Start Moving (WASD)"),
        JUMP("Jump"),
        SNEAK("Sneak"),
        RUN_START("Dungeon Run Start"),
        WITHER_DOOR("Wither Door Opened"),
        BLOOD_OPEN("Blood Door Opened"),
        BOSS_ENTRY("Boss Entered"),
        PHASE_CHANGE("Boss Phase Change"),
        TERMINAL_OPEN("Terminal Opened"),
        MY_TERMINAL("My Terminal Done"),
        ANY_TERMINAL("Any Terminal Done"),
        DEVICE("Device Done"),
        LEVER("Lever Done"),
        SECTION("Section Done"),
        GATE("Gate Destroyed"),
        LEAP("Leap Used"),
        DEATH("I Died"),
        RUN_END("Run End"),
        WORLD_CHANGE("World Change"),
        AREA_CHANGE("Area Change"),
        CHAT("Chat Contains Text"),
        CHAT_REGEX("Chat Matches Regex"),
        GUI_OPEN("GUI Title Contains Text"),
        GUI_CLOSE("Any Container Closed");

        override fun toString(): String = label
    }

    private const val NAME = "Stopwatch"
    private const val MORT_START = "[NPC] Mort: Here, I found this map when I first entered the dungeon."
    private val WITHER_DOOR = Regex("^\\w+ opened a WITHER door!$")

    @ConfigValue @JvmField var enabled: Boolean = false
    @ConfigValue @JvmField var startTrigger: Trigger = Trigger.MOVE
    @ConfigValue @JvmField var startText: String = ""
    @ConfigValue @JvmField var stopTrigger: Trigger = Trigger.TERMINAL_OPEN
    @ConfigValue @JvmField var stopText: String = ""
    @ConfigValue @JvmField var splitTrigger: Trigger = Trigger.NONE
    @ConfigValue @JvmField var splitText: String = ""
    @ConfigValue @JvmField var resetTrigger: Trigger = Trigger.NONE
    @ConfigValue @JvmField var resetText: String = ""
    @ConfigValue @JvmField var restartOnStart: Boolean = false
    @ConfigValue @JvmField var dungeonsOnly: Boolean = false
    @ConfigValue @JvmField var hideWhenIdle: Boolean = false
    @ConfigValue @JvmField var decimals: Int = 2
    @ConfigValue @JvmField var prefix: String = "Stopwatch: "
    @ConfigValue @JvmField var historyCount: Int = 3
    @ConfigValue @JvmField var showBest: Boolean = true
    @ConfigValue @JvmField var bestSeconds: Double = 0.0
    @ConfigValue @JvmField var chatOnStop: Boolean = true
    @ConfigValue @JvmField var prefixColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var runningColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var stoppedColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var historyColor: Int = 0xFFAAAAAA.toInt()
    @ConfigValue @JvmField var bestColor: Int = 0xFFFF55FF.toInt()

    @ConfigValue @JvmField
    var hud: HUDComponent = HUDComponent(
        10.0, 260.0, 110, 40, 1f, NAME, { false }, Stopwatch::render, { enabled }
    )

    @JvmField var toggleKey: KeyMapping? = null
    @JvmField var splitKey: KeyMapping? = null
    @JvmField var resetKey: KeyMapping? = null

    private var startNs = 0L
    private var elapsedNs = 0L
    private var running = false
    private var lastSplitNs = 0L
    private val splits = ArrayList<Long>()
    private val history = ArrayDeque<Long>()

    private var wasMoving = false
    private var wasJumping = false
    private var wasSneaking = false
    private var wasInBoss = false

    @JvmStatic
    fun init() {
        // No ':' in names: options.txt splits on the first ':'
        toggleKey = key("Stopwatch - Start/Stop")
        splitKey = key("Stopwatch - Split")
        resetKey = key("Stopwatch - Reset")
        fishmod.features.FishHudEditor.register(NAME, hud)

        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("Stopwatch.1", "stopwatch tick") { tick(mc) } }
        Events.ON_GAME_MESSAGE.register { msg -> if (enabled) FishDiag.guard("Stopwatch.2", "stopwatch chat") { onChat(fishmod.utils.TextStrip.plain(msg.string)) }; false }
        Events.ON_TERMINAL.register { name, _, objective, _, _ ->
            when (objective) {
                "terminal" -> {
                    if (name == Minecraft.getInstance().player?.gameProfile?.name) fire(Trigger.MY_TERMINAL)
                    fire(Trigger.ANY_TERMINAL)
                }
                "device" -> fire(Trigger.DEVICE)
                "lever" -> fire(Trigger.LEVER)
            }
            false
        }
        Events.ON_SECTION_CHANGE.register { fire(Trigger.SECTION); false }
        Events.ON_PHASE_CHANGE.register { fire(Trigger.PHASE_CHANGE); false }
        Events.ON_LEAP.register { _ -> fire(Trigger.LEAP); false }
        Events.ON_RUN_END.register { fire(Trigger.RUN_END); false }
        Events.ON_WORLD_CHANGE.register { fire(Trigger.WORLD_CHANGE); false }
        Events.ON_LOCATION_CHANGE.register { _ -> fire(Trigger.AREA_CHANGE); false }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (enabled && screen is AbstractContainerScreen<*>) {
                val title = fishmod.utils.TextStrip.plain(screen.title.string)
                if (TerminalType.values().any { title.startsWith(it.windowPrefix) }) fire(Trigger.TERMINAL_OPEN)
                fire(Trigger.GUI_OPEN, title)
                ScreenEvents.remove(screen).register { fire(Trigger.GUI_CLOSE) }
            }
        }
    }

    private fun key(name: String): KeyMapping = KeyMappingHelper.registerKeyMapping(
        KeyMapping(name, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, Keybinds.category())
    )

    private fun tick(mc: Minecraft) {
        // Keybinds: drain clicks even when disabled so they don't pile up
        val toggle = toggleKey?.let { var n = false; while (it.consumeClick()) n = true; n } ?: false
        val split = splitKey?.let { var n = false; while (it.consumeClick()) n = true; n } ?: false
        val reset = resetKey?.let { var n = false; while (it.consumeClick()) n = true; n } ?: false
        if (!enabled) return
        if (toggle) { if (running) stop() else start() }
        if (split) split()
        if (reset) reset()
        if (mc.player == null) return
        val o = mc.options
        val moving = o.keyUp.isDown || o.keyDown.isDown || o.keyLeft.isDown || o.keyRight.isDown
        if (moving && !wasMoving) fire(Trigger.MOVE)
        wasMoving = moving
        val jumping = o.keyJump.isDown
        if (jumping && !wasJumping) fire(Trigger.JUMP)
        wasJumping = jumping
        val sneaking = o.keyShift.isDown
        if (sneaking && !wasSneaking) fire(Trigger.SNEAK)
        wasSneaking = sneaking
        val inBoss = DungeonState.isInBoss()
        if (inBoss && !wasInBoss) fire(Trigger.BOSS_ENTRY)
        wasInBoss = inBoss
    }

    private fun onChat(s: String) {
        if (!enabled) return
        if (s == MORT_START) fire(Trigger.RUN_START)
        if (WITHER_DOOR.matches(s)) fire(Trigger.WITHER_DOOR)
        if (s == "The BLOOD DOOR has been opened!") fire(Trigger.BLOOD_OPEN)
        if (s == "The gate has been destroyed!") fire(Trigger.GATE)
        if (s.startsWith("☠ You ")) fire(Trigger.DEATH)
        fire(Trigger.CHAT, s)
        fire(Trigger.CHAT_REGEX, s)
    }

    private fun matches(t: Trigger, want: Trigger, text: String, arg: String?): Boolean {
        if (t != want || t == Trigger.NONE) return false
        return when (t) {
            Trigger.CHAT, Trigger.GUI_OPEN -> arg != null && text.isNotEmpty() && arg.contains(text, true)
            Trigger.CHAT_REGEX -> arg != null && text.isNotEmpty() && (userRegex(text)?.containsMatchIn(arg) ?: false)
            else -> true
        }
    }

    // Compiled user regexes, keyed by pattern text (null = invalid)
    private val regexCache = HashMap<String, Regex?>()
    private fun userRegex(p: String): Regex? {
        if (regexCache.size > 16) regexCache.clear()
        if (!regexCache.containsKey(p)) regexCache[p] = runCatching { Regex(p) }.getOrNull()
        return regexCache[p]
    }

    // One event does one thing: reset, then stop, then split, then start
    private fun fire(t: Trigger, arg: String? = null) {
        if (!enabled || (dungeonsOnly && !Location.inDungeon())) return
        when {
            matches(t, resetTrigger, resetText, arg) -> reset()
            running && matches(t, stopTrigger, stopText, arg) -> stop()
            running && matches(t, splitTrigger, splitText, arg) -> split()
            matches(t, startTrigger, startText, arg) -> if (!running || restartOnStart) start()
        }
    }

    private fun start() {
        startNs = System.nanoTime(); lastSplitNs = startNs
        elapsedNs = 0L; splits.clear(); running = true
    }

    private fun stop() {
        if (!running) return
        elapsedNs = System.nanoTime() - startNs
        running = false
        history.addFirst(elapsedNs)
        while (history.size > 10) history.removeLast()
        val secs = elapsedNs / 1e9
        val pb = bestSeconds <= 0.0 || secs < bestSeconds
        if (pb) bestSeconds = secs
        if (chatOnStop) Misc.addChatMessage(Component.literal("§6[Stopwatch] §f${fmt(elapsedNs)}" + if (pb) " §d(PB)" else ""))
    }

    private fun split() {
        if (!running) return
        val now = System.nanoTime()
        splits.add(now - lastSplitNs)
        lastSplitNs = now
    }

    private fun reset() { running = false; elapsedNs = 0L; splits.clear() }

    private fun current(): Long = if (running) System.nanoTime() - startNs else elapsedNs

    private fun fmt(ns: Long): String {
        val d = decimals.coerceIn(0, 3)
        val secs = ns / 1e9
        val m = (secs / 60).toInt()
        val s = "%.${d}f".format(secs - m * 60)
        return if (m > 0) "$m:" + (if (s.substringBefore('.').length < 2) "0$s" else s) else s
    }

    @JvmStatic
    fun display(): Boolean = enabled && !(hideWhenIdle && !running && elapsedNs == 0L)

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor) {
        if (!display()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return
        val stack = ctx.pose()
        stack.pushMatrix()
        stack.scale(hud.scale, hud.scale)
        try { render(hud, ctx) } catch (e: Exception) { FishDiag.fail("Stopwatch.3", "stopwatch render threw", e) }
        stack.popMatrix()
    }

    @JvmStatic
    fun render(component: HUDComponent, ctx: GuiGraphicsExtractor) {
        val font = Minecraft.getInstance().font ?: return
        val x = component.scaledX
        var y = component.scaledY
        val rowH = font.lineHeight + 1
        ctx.text(font, prefix, x, y, prefixColor, true)
        ctx.text(font, fmt(current()), x + font.width(prefix), y, if (running) runningColor else stoppedColor, true)
        y += rowH
        splits.forEachIndexed { i, s ->
            ctx.text(font, "Split ${i + 1}: ${fmt(s)}", x + 4, y, historyColor, true); y += rowH
        }
        if (showBest && bestSeconds > 0.0) {
            ctx.text(font, "Best: ${fmt((bestSeconds * 1e9).toLong())}", x + 4, y, bestColor, true); y += rowH
        }
        for (h in history.take(historyCount.coerceIn(0, 10))) {
            ctx.text(font, fmt(h), x + 4, y, historyColor, true); y += rowH
        }
    }

    @JvmStatic
    fun resetBest() { bestSeconds = 0.0; history.clear() }
}
