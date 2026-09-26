package fishmod.cosmetic.badge

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import java.util.Optional

object BadgeRenderer {

    private val LEVEL_ANYWHERE = Regex("""\[(\d{1,4})[^\[\]\d]{0,4}]""")
    private val NAME_AFTER = Regex("""^\s*([A-Za-z0-9_]{1,16})""")

    private class Seg(@JvmField val text: String, @JvmField val style: Style)

    private fun segments(c: Component): List<Seg> {
        val segs = ArrayList<Seg>()
        c.visit({ style, text -> if (text.isNotEmpty()) segs.add(Seg(text, style)); Optional.empty<Any>() }, Style.EMPTY)
        return segs
    }

    private fun cleanFor(segs: List<Seg>): Triple<String, IntArray, Int> {
        val full = StringBuilder()
        for (s in segs) full.append(s.text)
        val fullStr = full.toString()
        val clean = StringBuilder(fullStr.length)
        val mapToFull = IntArray(fullStr.length)
        var i = 0
        while (i < fullStr.length) {
            if (fullStr[i] == '§' && i + 1 < fullStr.length) { i += 2; continue }
            mapToFull[clean.length] = i
            clean.append(fullStr[i]); i++
        }
        return Triple(clean.toString(), mapToFull, fullStr.length)
    }

    private fun appendRange(out: MutableComponent, segs: List<Seg>, from: Int, to: Int) {
        if (from >= to) return
        var pos = 0
        for (s in segs) {
            val segEnd = pos + s.text.length
            if (segEnd <= from) { pos = segEnd; continue }
            if (pos >= to) break
            val a = maxOf(0, from - pos)
            val b = minOf(s.text.length, to - pos)
            if (b > a) out.append(Component.literal(s.text.substring(a, b)).setStyle(s.style))
            pos = segEnd
        }
    }

    private fun badgesComponent(defs: List<BadgeDef>, leadingSpace: Boolean): MutableComponent {
        val out: MutableComponent = Component.empty()
        if (leadingSpace) out.append(Component.literal(" "))
        for (d in defs) {
            out.append(Component.literal(d.symbol).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(d.colorRgb))))
        }
        return out
    }

    private fun spliceAtFullOffset(segs: List<Seg>, fullLen: Int, insertAt: Int, badges: MutableComponent): Component {
        val out: MutableComponent = Component.empty()
        appendRange(out, segs, 0, insertAt)
        out.append(badges)
        appendRange(out, segs, insertAt, fullLen)
        return out
    }

    @JvmStatic
    fun insertKnown(component: Component?, uuidNoDashes: String?): Component? {
        if (component == null || uuidNoDashes == null) return component
        val defs = BadgeManager.badgesFor(uuidNoDashes)
        if (defs.isEmpty()) return component
        val segs = segments(component)
        if (segs.isEmpty()) return component
        val (cleanStr, mapToFull, fullLen) = cleanFor(segs)
        val m = LEVEL_ANYWHERE.find(cleanStr)
        val insertAt = if (m != null) mapToFull[m.range.last] + 1 else 0
        return spliceAtFullOffset(segs, fullLen, insertAt, badgesComponent(defs, leadingSpace = m != null))
    }

    @JvmStatic
    fun insertForChat(component: Component?): Component? {
        if (component == null) return component
        val segs = segments(component)
        if (segs.isEmpty()) return component
        val (cleanStr, mapToFull, fullLen) = cleanFor(segs)
        val m = LEVEL_ANYWHERE.find(cleanStr) ?: return component
        val afterBracketClean = m.range.last + 1
        if (afterBracketClean >= cleanStr.length) return component
        val nameMatch = NAME_AFTER.find(cleanStr.substring(afterBracketClean)) ?: return component
        val candidate = nameMatch.groupValues[1]
        val uuid = BadgeManager.uuidForName(candidate) ?: return component
        val defs = BadgeManager.badgesFor(uuid)
        if (defs.isEmpty()) return component
        val nameStartClean = afterBracketClean + nameMatch.range.first
        val insertAt = mapToFull[nameStartClean]
        return spliceAtFullOffset(segs, fullLen, insertAt, badgesComponent(defs, leadingSpace = false))
    }
}
