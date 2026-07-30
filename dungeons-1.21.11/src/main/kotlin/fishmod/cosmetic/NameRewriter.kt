package fishmod.cosmetic

import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import java.util.Optional

/** Replaces every occurrence of the real IGN inside a Text with the styled cosmetic name, preserving styling. */
object NameRewriter {

    @JvmStatic
    fun replaceName(original: Text?, realName: String?, replacement: Text): Text? {
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

        // The cosmetic name usually embeds the real IGN (e.g. "RedFish2471 [Twitch]" or
        // "[TTV] RedFish2471"). After one swap the text still contains the IGN, so a second pass
        // would decorate it again — and since chat insert and GUI draw both swap, it compounds.
        // To stay idempotent we detect an already-decorated block: an IGN occurrence whose
        // surrounding text exactly matches the full cosmetic string at the right offset. Such a
        // block is consumed whole and emitted as a single cosmetic, so re-running is a no-op.
        val cosmetic = replacement.string
        val nameOffInCosmetic = cosmetic.indexOf(realName) // where the IGN sits inside the cosmetic

        val out: MutableText = Text.empty()
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
                    // Already decorated here — emit text before the block, then one cosmetic.
                    appendRange(out, segs, charPos, blockStart)
                    out.append(replacement.copy())
                    charPos = blockStart + cosmetic.length
                    continue
                }
            }
            // Bare IGN occurrence — replace it with the cosmetic name.
            appendRange(out, segs, charPos, idx)
            out.append(replacement.copy())
            charPos = idx + realName.length
        }
        appendRange(out, segs, charPos, full.length)
        return out
    }

    private fun appendRange(out: MutableText, segs: List<Segment>, from: Int, to: Int) {
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
                out.append(Text.literal(s.text.substring(sliceFrom, sliceTo)).setStyle(s.style))
            }
            pos = segEnd
        }
    }

    private data class Segment(val text: String, val style: Style)
}
