package fishmod.cosmetic

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

/** Holds the client-side cosmetic display name and turns the raw "&"-coded string into a styled Text. */
object NickState {
    @Volatile
    private var nick: String? = null

    @JvmStatic
    fun set(name: String?) {
        // Single chokepoint: censor banned words before anything is stored/shown/uploaded (filter understands color codes).
        var n = name
        if (n != null && n.isNotEmpty()) n = ProfanityFilter.censor(n)
        nick = if (n != null && n.isNotEmpty()) n else null
        NickData.save(nick)
        RemoteNicks.uploadOwn()
    }

    /** Recolors the player's real username with a gradient over the given RGB stops. */
    @JvmStatic
    fun setGradient(stops: Array<IntArray>) {
        val raw = GradientNick.build(realName(), stops)
        set(raw)
    }

    /**
     * Applies the configured nick: takes the custom name (if set) or the real IGN as the base text,
     * strips any existing color codes, then re-colors it in the chosen mode (Solid or Gradient).
     */
    @JvmStatic
    fun applyFromSettings() {
        val custom = fishmod.utils.config.values.FishSettings.nickCustomName
        val base = if (custom != null && custom.isNotEmpty()) custom else realName()
        // Strip only color codes; keep format codes so GradientNick can re-emit them per letter.
        val stripped = base.replace(Regex("&#[0-9a-fA-F]{6}"), "").replace(Regex("[&§][0-9a-fxA-FX]"), "")
        // Empty-visible check (strip format codes too, just for this test).
        val visibleOnly = stripped.replace(Regex("[&§][klmnorKLMNOR]"), "")
        if (visibleOnly.isEmpty()) {
            reset()
            return
        }
        val stops: Array<IntArray> = when (fishmod.utils.config.values.FishSettings.nickColorMode.uppercase()) {
            "SOLID" -> arrayOf(GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart))
            "GRADIENT3" -> arrayOf(
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorMid),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorEnd)
            )
            "RAINBOW" -> GradientNick.rainbow()
            else -> arrayOf(
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorStart),
                GradientNick.rgb(fishmod.utils.config.values.FishSettings.nickColorEnd)
            )
        }
        set(GradientNick.build(stripped, stops))
    }

    @JvmStatic
    fun reset() {
        nick = null
        NickData.save(null)
        RemoteNicks.uploadOwn()
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

    /** The player's real in-game username, used as the search target when swapping. */
    @JvmStatic
    fun realName(): String {
        val mc = Minecraft.getInstance()
        if (mc.user != null) {
            val n = mc.user.name
            if (n != null && n.isNotEmpty()) return n
        }
        return ""
    }

    @JvmStatic
    fun asComponent(): Component = parse(nick)

    /** Parses a string with &-codes (and &#rrggbb hex codes) into a styled Text. */
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
                // "&*" inserts a SkyBlock star (✪) in the current color, e.g. "&6&*" = gold star.
                if (next == '*') {
                    buf.append('✪')
                    i += 2
                    continue
                }
                if (next == '#' && i + 7 < input.length) {
                    val hex = input.substring(i + 2, i + 8)
                    if (hex.matches(Regex("[0-9a-fA-F]{6}"))) {
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
