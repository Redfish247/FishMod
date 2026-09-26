package fishmod.cosmetic

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object NickState {
    @Volatile
    private var nick: String? = null

    private val HEX_COLOR_CODE = Regex("&#[0-9a-fA-F]{6}")
    private val FORMAT_CODE = Regex("[&§][0-9a-fxA-FX]")
    private val LEGACY_FORMAT_CODE = Regex("[&§][klmnorKLMNOR]")
    private val HEX_INLINE = Regex("[0-9a-fA-F]{6}")

    @JvmStatic
    fun set(name: String?) {
        var n = name
        if (n != null && n.isNotEmpty()) n = ProfanityFilter.censor(n)
        nick = if (n != null && n.isNotEmpty()) n else null
        NickData.save(nick)
        RemoteNicks.uploadOwn()
    }

    @JvmStatic
    fun applyFromSettings() {
        val custom = fishmod.utils.config.values.FishSettings.nickCustomName
        val base = if (custom != null && custom.isNotEmpty()) custom else realName()
        val stripped = base.replace(HEX_COLOR_CODE, "").replace(FORMAT_CODE, "")
        val visibleOnly = stripped.replace(LEGACY_FORMAT_CODE, "")
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
        val p = parsed
        if (p.raw == n) return p.component
        val component = parse(n)
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
