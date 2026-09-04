package fishmod.cosmetic

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import java.util.Optional

/** Replaces every occurrence of the real IGN inside a Text with the styled cosmetic name, preserving styling. */
object NameRewriter {

    @JvmStatic
    fun replaceName(original: Component?, realName: String?, replacement: Component): Component? {
        if (original == null || realName == null || realName.isEmpty()) return original
        if (!original.string.contains(realName)) return original

        val segs = ArrayList<Segment>()
        original.visit({ style, text ->
            if (text.isNotEmpty()) segs.add(Segment(text, style))
            Optional.empty<Any>()
        }, Style.EMPTY)

        val sb = StringBuilder()
        for (s in segs) sb.append(s.text)
        val full = sb.toString()
        if (!full.contains(realName)) return original

        // Idempotent: an IGN already wrapped in a full cosmetic block is consumed whole, so re-running is a no-op.
        val cosmetic = replacement.string
        val nameOffInCosmetic = cosmetic.indexOf(realName)

        val out: MutableComponent = Component.empty()
        var charPos = 0
        while (true) {
            val idx = full.indexOf(realName, charPos)
            if (idx < 0) break
            if (nameOffInCosmetic >= 0) {
                val blockStart = idx - nameOffInCosmetic
                if (blockStart >= charPos
                    && blockStart + cosmetic.length <= full.length
                    && full.regionMatches(blockStart, cosmetic, 0, cosmetic.length)
                ) {
                    appendRange(out, segs, charPos, blockStart)
                    out.append(replacement.copy())
                    charPos = blockStart + cosmetic.length
                    continue
                }
            }
            appendRange(out, segs, charPos, idx)
            out.append(replacement.copy())
            charPos = idx + realName.length
        }
        appendRange(out, segs, charPos, full.length)
        return out
    }

    private fun appendRange(out: MutableComponent, segs: List<Segment>, from: Int, to: Int) {
        if (from >= to) return
        var pos = 0
        for (s in segs) {
            val segEnd = pos + s.text.length
            if (segEnd <= from) {
                pos = segEnd
                continue
            }
            if (pos >= to) break
            val sliceFrom = maxOf(0, from - pos)
            val sliceTo = minOf(s.text.length, to - pos)
            if (sliceTo > sliceFrom) {
                out.append(Component.literal(s.text.substring(sliceFrom, sliceTo)).setStyle(s.style))
            }
            pos = segEnd
        }
    }

    private data class Segment(val text: String, val style: Style)
}
