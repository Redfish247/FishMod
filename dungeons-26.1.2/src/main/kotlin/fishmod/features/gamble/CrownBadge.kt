package fishmod.features.gamble

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

// Crown for the gamble #1: cache + helper API for chat/nametag/tab. Chat is wired here; nametag/tab call prefix()/decorate().
object CrownBadge {

    const val GLYPH = "§6♛§r"
    private val FONT = net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("fishmod", "crown"))

    // Pixel crown from our own font (the unicode queen glyph renders as a squid)
    @JvmStatic fun icon(rgb: Int = 0xFFAA00): net.minecraft.network.chat.MutableComponent =
        Component.literal("").setStyle(Style.EMPTY.withFont(FONT).withColor(rgb))
    private const val TTL_MS = 60_000L
    private const val HOLDER_REFRESH_MS = 60_000L

    private class Entry(val crowned: Boolean, val at: Long)

    private val cache = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var holder = ""
    @Volatile private var holderUuid = ""

    // Crown holder's uuid (no dashes) for the badge system, only when the crown is on
    @JvmStatic fun crownUuid(): String = if (active()) holderUuid else ""
    @Volatile private var lastHolderFetch = 0L

    private fun key(name: String) = name.lowercase()

    fun setHolder(name: String, uuid: String) {
        val now = System.currentTimeMillis()
        val old = holder
        holder = name
        holderUuid = uuid.replace("-", "")
        if (old.isNotEmpty() && !old.equals(name, true)) cache[key(old)] = Entry(false, now)
        cache[key(name)] = Entry(true, now)
        if (uuid.isNotEmpty()) cache[key(uuid.replace("-", ""))] = Entry(true, now)
    }

    private fun active(): Boolean = GambleSettings.gambleEnabled && GambleSettings.gambleCrown

    // Cached answer; stale or unknown names trigger a background GET /crown
    fun isCrowned(uuid: String?, name: String?): Boolean {
        if (!active() || name.isNullOrEmpty()) return false
        val k = key(name)
        val e = cache[k]
        val now = System.currentTimeMillis()
        if (e == null || now - e.at > TTL_MS) lookup(k, uuid, name)
        return e?.crowned == true
    }

    private fun lookup(k: String, uuid: String?, name: String) {
        if (GambleLeaderboard.fake != null) {
            val me = uuid?.replace("-", "")
            cache[k] = Entry(holder.equals(name, true) || (!me.isNullOrEmpty() && me == holderUuid), System.currentTimeMillis())
            return
        }
        if (!inFlight.add(k)) return
        GambleApi.call("GET", "/crown", null) { res ->
            val o = res?.json
            val hName = o?.get("name")?.takeIf { !it.isJsonNull }?.asString
            val hUuid = o?.get("uuid")?.takeIf { !it.isJsonNull }?.asString
            if (res?.status == 200 && !hName.isNullOrEmpty()) setHolder(hName, hUuid ?: "")
            val me = uuid?.replace("-", "")
            val crowned = res?.status == 200 && hName != null &&
                (hName.equals(name, true) || (!me.isNullOrEmpty() && hUuid?.replace("-", "") == me))
            cache[k] = Entry(crowned, System.currentTimeMillis())
            inFlight.remove(k)
        }
    }

    // "♛ " or "" for nametag/tab callers (check the per-surface setting there)
    fun prefix(uuid: String?, name: String?): String = if (isCrowned(uuid, name)) "$GLYPH " else ""

    fun decorate(c: Component, uuid: String?, name: String?): Component =
        if (isCrowned(uuid, name)) icon().append(Component.literal(" ")).append(c) else c

    fun init() {
        ClientReceiveMessageEvents.MODIFY_GAME.register { msg, overlay ->
            if (overlay || !active() || !GambleSettings.gambleCrownChat) msg else decorateChat(msg)
        }
    }

    // Keeps the holder fresh without a per-player request
    fun onTick(mc: Minecraft) {
        if (mc.player == null || !active()) return
        val now = System.currentTimeMillis()
        if (now - lastHolderFetch < HOLDER_REFRESH_MS) return
        lastHolderFetch = now
        GambleLeaderboard.fetch { }
    }

    // Dev reset: forget holder and cached answers
    fun clear() {
        holder = ""; holderUuid = ""; lastHolderFetch = 0L
        cache.clear()
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun decorateChat(msg: Component): Component {
        val h = holder
        if (h.isEmpty() || !msg.string.contains(h)) return msg
        val parts = ArrayList<Pair<Style, String>>()
        msg.visit(FormattedText.StyledContentConsumer<Unit> { style, s -> parts.add(style to s); Optional.empty() }, Style.EMPTY)
        var changed = false
        val out = Component.empty()
        for ((style, s) in parts) {
            var from = 0
            while (true) {
                val i = s.indexOf(h, from)
                if (i < 0) break
                val before = if (i > 0) s[i - 1] else ' '
                val after = if (i + h.length < s.length) s[i + h.length] else ' '
                if (isWordChar(before) || isWordChar(after) || before == '♛') { from = i + h.length; continue }
                out.append(Component.literal(s.substring(0, i)).setStyle(style))
                out.append(icon()).append(Component.literal(" "))
                changed = true
                // Only the first hit per segment is badged
                out.append(Component.literal(s.substring(i)).setStyle(style))
                from = -1
                break
            }
            if (from != -1) out.append(Component.literal(s).setStyle(style))
        }
        return if (changed) out else msg
    }
}
