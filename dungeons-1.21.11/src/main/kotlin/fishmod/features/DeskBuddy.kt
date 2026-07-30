package fishmod.features

import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.render.RenderTickCounter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Desk-Buddy — a tiny kaomoji companion that lives in the corner of your HUD. It idles with a gentle
 * bob and the odd blink, curls up to sleep when you go AFK, and breaks into a little dance whenever
 * RNG smiles on you (rare drops, "PRAISE RNGESUS", etc.). Purely cosmetic; rendered from text glyphs
 * so it needs no texture assets.
 *
 * Reaction triggers are detected from chat in [init] so the feature is fully self-contained —
 * other trackers don't need to know it exists.
 */
object DeskBuddy {

    // ── reaction state ─────────────────────────────────────────────────────────
    private var reactUntilMs: Long = 0 // reacting while now < this
    private var reactType = 0 // R_DANCE
    private var lastReactMs: Long = 0 // de-dupe burst of drop lines into one reaction
    private var lastActivityMs: Long = System.currentTimeMillis() // for AFK
    private var lastBlinkMs: Long = 0
    private var blinking = false

    // Last-known pose, to detect activity (any move/look = not AFK).
    private var pX = 0.0
    private var pY = 0.0
    private var pZ = 0.0
    private var pYaw = 0f
    private var pPitch = 0f
    private var poseInit = false

    // Reaction kinds.
    private const val R_DANCE = 0
    private const val R_FAINT = 1
    private const val R_LOVE = 2
    private const val DANCE_MS = 4200L
    private const val FAINT_MS = 5000L
    private const val LOVE_MS = 3500L
    private const val REACT_DEBOUNCE = 600L // collapse multi-line drop spam into one cheer
    private const val BLINK_EVERY_MS = 3800L
    private const val BLINK_HOLD_MS = 160L

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (text == null) {
                false
            } else {
                if (FishSettings.deskBuddyEnabled && FishSettings.deskBuddyReactToRng) {
                    onChat(text.string.replace(Regex("§."), "").trim())
                }
                false
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc -> tick(mc) }
    }

    /** Make the buddy cheer (dance) right now (used by chat triggers and the debug command). */
    @JvmStatic
    fun cheer() {
        react(R_DANCE, DANCE_MS)
    }

    private fun react(type: Int, ms: Long) {
        val now = System.currentTimeMillis()
        if (now - lastReactMs < REACT_DEBOUNCE && now < reactUntilMs && reactType == type) return // mid-burst
        lastReactMs = now
        reactType = type
        reactUntilMs = now + ms
    }

    private fun onChat(plain: String) {
        val up = plain.uppercase()
        if (up.contains("RARE DROP") // covers RARE / VERY RARE / CRAZY RARE
            || up.contains("INSANE DROP")
            || up.contains("PRAISE RNGESUS")
            || up.contains("PET DROP")
            || up.contains("GREAT CATCH")
        ) {
            react(R_DANCE, DANCE_MS)
        } else if (up.contains("LEVELED UP TO LEVEL") || up.contains("LEVELLED UP TO LEVEL")) {
            react(R_LOVE, LOVE_MS) // pet level-up
        } else if (up.contains("YOU DIED") || up.contains("BECAME A GHOST")
            || up.contains("AND BECAME A GHOST") || up == "☠ YOU DIED"
        ) {
            react(R_FAINT, FAINT_MS) // you died
        }
    }

    private fun tick(mc: MinecraftClient) {
        if (!FishSettings.deskBuddyEnabled) return
        val p = mc.player ?: return
        val now = System.currentTimeMillis()

        // Activity = any movement or camera change since last tick.
        if (!poseInit) {
            pX = p.x; pY = p.y; pZ = p.z; pYaw = p.yaw; pPitch = p.pitch
            poseInit = true
            lastActivityMs = now
        } else {
            val moved = p.x != pX || p.y != pY || p.z != pZ || p.yaw != pYaw || p.pitch != pPitch
            if (moved) lastActivityMs = now
            pX = p.x; pY = p.y; pZ = p.z; pYaw = p.yaw; pPitch = p.pitch
        }
    }

    private fun isAfk(): Boolean {
        val afkMs = max(10, FishSettings.deskBuddyAfkSeconds) * 1000L
        return System.currentTimeMillis() - lastActivityMs > afkMs
    }

    private fun isReacting(): Boolean = System.currentTimeMillis() < reactUntilMs
    private fun isDancing(): Boolean = isReacting() && reactType == R_DANCE

    // ── faces ──────────────────────────────────────────────────────────────────
    private val DANCE = arrayOf("§a\\(^o^)/", "§a/(^o^)\\", "§e♪\\(•o•)/♪", "§a<(^-^<)", "§a(>^-^)>")
    private val LOVE = arrayOf("§d(♥‿♥)", "§d\\(♥▽♥)/", "§d(♥o♥)")

    private fun face(): String {
        if (isReacting()) {
            val t = System.currentTimeMillis()
            when (reactType) {
                R_DANCE -> return DANCE[((t / 140) % DANCE.size).toInt()]
                R_LOVE -> return LOVE[((t / 250) % LOVE.size).toInt()]
                R_FAINT -> return "§7(x_x)"
            }
        }
        if (isAfk()) return "§7(-.-) zzz"
        return if (blinking) "§f(-‿-)" else "§f(•‿•)"
    }

    private fun mood(): String {
        if (isReacting()) {
            when (reactType) {
                R_DANCE -> return "§a✦ POG ✦"
                R_LOVE -> return "§d♥ nice ♥"
                R_FAINT -> return "§8oof…"
            }
        }
        if (isAfk()) return "§8sleeping…"
        return "§7idle"
    }

    @JvmStatic
    fun renderHud(ctx: DrawContext, tickCounter: RenderTickCounter) {
        if (!FishSettings.deskBuddyEnabled) return
        val mc = MinecraftClient.getInstance()
        if (mc.player == null) return
        if (mc.currentScreen != null && mc.currentScreen !is ChatScreen) return

        val now = System.currentTimeMillis()
        // Blink scheduler (only while awake & not mid-reaction).
        if (!isAfk() && !isReacting()) {
            if (!blinking && now - lastBlinkMs > BLINK_EVERY_MS) {
                blinking = true
                lastBlinkMs = now
            } else if (blinking && now - lastBlinkMs > BLINK_HOLD_MS) {
                blinking = false
            }
        } else {
            blinking = false
        }

        val name = if (FishSettings.deskBuddyName == null || FishSettings.deskBuddyName.isBlank()) {
            "Rocky"
        } else {
            FishSettings.deskBuddyName
        }
        val faceStr = face()
        val moodStr = mood()

        val x = FishSettings.deskBuddyHudX
        val y = FishSettings.deskBuddyHudY
        val sc = FishSettings.deskBuddyScale.toFloat()

        // Bob: slow gentle float while idle, fast bouncy hop while dancing.
        val t = now / 1000.0
        var bob: Float
        var wiggle = 0f
        if (isDancing()) {
            bob = (-abs(sin(t * 10)) * 4.0).toFloat() // hop up off the "floor"
            wiggle = (sin(t * 18) * 2.0).toFloat()
        } else if (isReacting() && reactType == R_LOVE) {
            bob = (-abs(sin(t * 6)) * 2.5).toFloat() // gentle happy bounce
        } else if (isReacting() && reactType == R_FAINT) {
            bob = 3f // slumped down, lying still
        } else if (isAfk()) {
            bob = (sin(t * 1.2) * 0.8).toFloat() // slow breathing
        } else {
            bob = (sin(t * 2.2) * 1.6).toFloat()
        }

        val lh = Constants.TEXT_HEIGHT + 1
        ctx.matrices.pushMatrix()
        ctx.matrices.translate(x.toFloat(), y.toFloat())
        ctx.matrices.scale(sc, sc)
        ctx.drawText(mc.textRenderer, "§6$name", 0, 0, 0xFFFFFFFF.toInt(), true)
        ctx.drawText(mc.textRenderer, faceStr, wiggle.roundToInt(), lh + bob.roundToInt(), 0xFFFFFFFF.toInt(), true)
        ctx.drawText(mc.textRenderer, moodStr, 0, lh * 2 + 2, 0xFFFFFFFF.toInt(), true)
        ctx.matrices.popMatrix()
    }
}
