package fishmod.cosmetic

import fishmod.utils.debug.FishDiag
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import java.util.IdentityHashMap
import java.util.Optional

object NickState {
    @Volatile
    private var nick: String? = null

    private val HEX_COLOR_CODE = Regex("&#[0-9a-fA-F]{6}")
    private val FORMAT_CODE = Regex("[&§][0-9a-fxA-FX]")
    private val LEGACY_FORMAT_CODE = Regex("[&§][klmnorKLMNOR]")
    private val HEX_INLINE = Regex("[0-9a-fA-F]{6}")

    @Volatile private var uploadPending = false

    private fun scheduleUpload() {
        if (uploadPending) return
        uploadPending = true
        fishmod.utils.TickScheduler.scheduleTask({ uploadPending = false; RemoteNicks.uploadOwn() }, 15)
    }

    @JvmStatic
    fun set(name: String?) {
        var n = name
        if (n != null && n.isNotEmpty()) n = ProfanityFilter.censor(n)
        nick = if (n != null && n.isNotEmpty()) n else null
        NickData.save(nick)
        scheduleUpload()
    }

    @JvmStatic
    fun applyFromSettings() {
        val custom = fishmod.utils.config.values.FishSettings.nickCustomName
        val fs = fishmod.utils.config.values.FishSettings
        val fmt = (if (fs.nickBold) "&l" else "") + (if (fs.nickItalic) "&o" else "") + (if (fs.nickUnderline) "&n" else "") + (if (fs.nickStrike) "&m" else "") + (if (fs.nickMagic) "&k" else "")
        val base = fmt + (if (custom != null && custom.isNotEmpty()) custom else realName())
        val stripped = base.replace(HEX_COLOR_CODE, "").replace(FORMAT_CODE, "")
        val visibleOnly = stripped.replace(LEGACY_FORMAT_CODE, "")
        if (visibleOnly.isEmpty()) {
            reset()
            return
        }
        val mode = fishmod.utils.config.values.FishSettings.nickColorMode.uppercase()
        val stops: Array<IntArray> = when (mode) {
            "SOLID" -> arrayOf(GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart))
            "GRADIENT3" -> arrayOf(
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorMid),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorEnd)
            )
            "RAINBOW" -> GradientNick.rainbow()
            else -> {
                FishDiag.check(mode == "GRADIENT", "NickState.1") { "unknown nickColorMode '$mode'" }
                arrayOf(
                    GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart),
                    GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorEnd)
                )
            }
        }
        animBase = ProfanityFilter.censor(stripped)
        animStops = stops
        set(FishDiag.guard("NickState.2", "gradient nick build failed for '$stripped' mode=$mode") { GradientNick.build(stripped, stops) } ?: stripped)
    }

    // source text + colour stops kept for the animated mode
    @Volatile private var animBase: String? = null
    @Volatile private var animStops: Array<IntArray> = emptyArray()
    @Volatile private var animParsed = Parsed(null, Component.empty())
    @Volatile private var animBucket = -1L
    private const val ANIM_PERIOD_MS = 3000L
    private const val ANIM_STEP_MS = 50L

    // per-frame animated component, rebuilt every ANIM_STEP_MS; null when animation does not apply
    private fun animatedComponent(n: String): Component? {
        if (!fishmod.utils.config.values.FishSettings.nickColorAnimated) return null
        val base = animBase ?: return null
        val stops = animStops
        if (stops.size < 2) return null
        val now = System.currentTimeMillis()
        val bucket = now / ANIM_STEP_MS
        val cached = animParsed
        if (bucket == animBucket && cached.raw == n) return cached.component
        val phase = animPhase(now)
        val raw = FishDiag.guard("NickState.4", "animated nick build failed") { GradientNick.build(base, GradientNick.shifted(stops, phase, 12)) } ?: return null
        val component = FishDiag.guard("NickState.5", "animated nick parse failed") { parse(raw) } ?: return null
        animParsed = Parsed(n, component)
        animBucket = bucket
        return component
    }

    // speed % scales the cycle length; 1 - x flips the scroll direction
    private fun animPhase(now: Long): Double {
        val period = (ANIM_PERIOD_MS * 100 / fishmod.utils.config.values.FishSettings.nickColorSpeed.coerceIn(25, 300)).coerceAtLeast(1L)
        return 1.0 - (now % period).toDouble() / period
    }

    // ---- chat: stored lines are frozen, so the nick letters carry registered colours that animateChatLine recolours per frame ----
    private const val CHAT_MAX_LETTERS = 40
    private val chatKeys = IdentityHashMap<TextColor, IntArray>() // registered colour -> [letter index, letter count]
    private val chatTable = HashMap<Int, TextColor>()
    @Volatile private var chatSamplesBucket = -1L
    @Volatile private var chatSamples: Array<IntArray> = emptyArray()

    @Synchronized
    private fun chatColor(n: Int, idx: Int, base: TextColor): TextColor =
        chatTable.getOrPut(n * 64 + idx) { val c = TextColor.fromRgb(base.value); chatKeys[c] = intArrayOf(idx, n); c }

    @Synchronized
    private fun chatKey(c: TextColor): IntArray? = chatKeys[c]

    /** Nick component for chat lines. Same look as [asComponent], but its letters are recoloured per frame when animation is on. */
    @JvmStatic
    fun asChatComponent(): Component {
        val n = nick ?: return asComponent()
        if (!fishmod.utils.config.values.FishSettings.nickColorAnimated || animBase == null || animStops.size < 2) return asComponent()
        return FishDiag.guard("NickState.6", "chat nick build failed") {
            val segs = ArrayList<Pair<String, Style>>()
            parse(n).visit({ st, text -> if (text.isNotEmpty()) segs.add(text to st); Optional.empty<Any>() }, Style.EMPTY)
            var letters = 0
            for ((t, st) in segs) if (st.color != null) letters += t.codePointCount(0, t.length)
            if (letters < 2 || letters > CHAT_MAX_LETTERS) return@guard asComponent()
            var idx = 0
            val out: MutableComponent = Component.empty()
            for ((t, st) in segs) {
                val col = st.color
                if (col == null) { out.append(Component.literal(t).setStyle(st)); continue }
                var i = 0
                while (i < t.length) {
                    val len = Character.charCount(t.codePointAt(i))
                    out.append(Component.literal(t.substring(i, i + len)).setStyle(st.withColor(chatColor(letters, idx, col))))
                    idx++; i += len
                }
            }
            out as Component
        } ?: asComponent()
    }

    /** Draw-time hook for chat lines: recolours registered nick letters with the current animation frame. */
    @JvmStatic
    fun animateChatLine(seq: FormattedCharSequence?): FormattedCharSequence? {
        if (seq == null || chatKeys.isEmpty()) return seq
        val stops = animStops
        if (stops.size < 2) return seq
        val animated = fishmod.utils.config.values.FishSettings.nickColorAnimated
        val now = System.currentTimeMillis()
        val bucket = if (animated) now / ANIM_STEP_MS else 0L
        var samples = chatSamples
        if (bucket != chatSamplesBucket || samples.isEmpty()) {
            samples = if (animated) GradientNick.shifted(stops, animPhase(now), 12) else stops
            chatSamples = samples
            chatSamplesBucket = bucket
        }
        val frame = samples
        return FormattedCharSequence { sink ->
            seq.accept { idx, style, cp ->
                val c = style.color
                val key = if (c == null) null else chatKey(c)
                if (key == null) sink.accept(idx, style, cp)
                else sink.accept(idx, style.withColor(TextColor.fromRgb(GradientNick.sampleRgb(frame, key[0].toDouble() / (key[1] - 1)))), cp)
            }
        }
    }

    @JvmStatic
    fun reset() {
        nick = null
        animBase = null
        NickData.save(null)
        scheduleUpload()
    }

    @JvmStatic
    fun applyFromDisk(raw: String?) {
        var r = raw
        if (r != null && r.isNotEmpty()) r = ProfanityFilter.censor(r)
        nick = if (r != null && r.isNotEmpty()) r else null
    }

    @JvmStatic
    fun isActive(): Boolean = nick != null

    @JvmStatic
    fun getRaw(): String? = nick

    @JvmStatic
    fun realName(): String {
        val mc = Minecraft.getInstance()
        if (mc.user != null) {
            val n = mc.user.name
            if (n != null && n.isNotEmpty()) return n
        }
        return ""
    }

    private class Parsed(val raw: String?, val component: Component)

    @Volatile
    private var parsed = Parsed(null, Component.empty())

    @JvmStatic
    fun asComponent(): Component {
        val n = nick
        if (n != null) animatedComponent(n)?.let { return it }
        val p = parsed
        if (p.raw == n) return p.component
        val component = FishDiag.guard("NickState.3", "nick parse failed for '$n'") { parse(n) } ?: Component.literal(n ?: "")
        parsed = Parsed(n, component)
        return component
    }

    @JvmStatic
    fun parse(input: String?): Component {
        val root: MutableComponent = Component.empty()
        if (input == null || input.isEmpty()) return root

        var style = Style.EMPTY
        val buf = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if ((c == '&' || c == '§') && i + 1 < input.length) {
                val next = input[i + 1]
                if (next == '*') {
                    buf.append('✪')
                    i += 2
                    continue
                }
                if (next == '#' && i + 7 < input.length) {
                    val hex = input.substring(i + 2, i + 8)
                    if (hex.matches(HEX_INLINE)) {
                        if (buf.isNotEmpty()) {
                            root.append(Component.literal(buf.toString()).setStyle(style))
                            buf.setLength(0)
                        }
                        style = Style.EMPTY.withColor(TextColor.parseColor("#$hex").getOrThrow())
                        i += 8
                        continue
                    }
                }

                val fmt = ChatFormatting.getByCode(next.lowercaseChar())
                if (fmt != null) {
                    if (buf.isNotEmpty()) {
                        root.append(Component.literal(buf.toString()).setStyle(style))
                        buf.setLength(0)
                    }
                    style = if (fmt == ChatFormatting.RESET) {
                        Style.EMPTY
                    } else if (TextColor.fromLegacyFormat(fmt) != null) {
                        Style.EMPTY.withColor(fmt)
                    } else {
                        style.applyFormat(fmt)
                    }
                    i += 2
                    continue
                }
            }

            buf.append(c)
            i++
        }

        if (buf.isNotEmpty()) {
            root.append(Component.literal(buf.toString()).setStyle(style))
        }
        return root
    }
}
