package fishmod.cosmetic;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Replaces every occurrence of the real IGN inside a Text with the styled cosmetic name, preserving styling. */
public final class NameRewriter {
    private NameRewriter() {}

    public static Text replaceName(Text original, String realName, Text replacement) {
        if (original == null || realName == null || realName.isEmpty()) return original;
        if (!original.getString().contains(realName)) return original;

        List<Segment> segs = new ArrayList<>();
        original.visit((style, text) -> {
            if (!text.isEmpty()) segs.add(new Segment(text, style));
            return Optional.empty();
        }, Style.EMPTY);

        StringBuilder sb = new StringBuilder();
        for (Segment s : segs) sb.append(s.text());
        String full = sb.toString();
        if (!full.contains(realName)) return original;

        // The cosmetic name usually embeds the real IGN (e.g. "RedFish2471 [Twitch]" or
        // "[TTV] RedFish2471"). After one swap the text still contains the IGN, so a second pass
        // would decorate it again — and since chat insert and GUI draw both swap, it compounds.
        // To stay idempotent we detect an already-decorated block: an IGN occurrence whose
        // surrounding text exactly matches the full cosmetic string at the right offset. Such a
        // block is consumed whole and emitted as a single cosmetic, so re-running is a no-op.
        String cosmetic = replacement.getString();
        int nameOffInCosmetic = cosmetic.indexOf(realName); // where the IGN sits inside the cosmetic

        MutableText out = Text.empty();
        int charPos = 0;
        int idx;
        while ((idx = full.indexOf(realName, charPos)) >= 0) {
            if (nameOffInCosmetic >= 0) {
                int blockStart = idx - nameOffInCosmetic;
                if (blockStart >= charPos
                        && blockStart + cosmetic.length() <= full.length()
                        && full.regionMatches(blockStart, cosmetic, 0, cosmetic.length())) {
                    // Already decorated here — emit text before the block, then one cosmetic.
                    appendRange(out, segs, charPos, blockStart);
                    out.append(replacement.copy());
                    charPos = blockStart + cosmetic.length();
                    continue;
                }
            }
            // Bare IGN occurrence — replace it with the cosmetic name.
            appendRange(out, segs, charPos, idx);
            out.append(replacement.copy());
            charPos = idx + realName.length();
        }
        appendRange(out, segs, charPos, full.length());
        return out;
    }

    private static void appendRange(MutableText out, List<Segment> segs, int from, int to) {
        if (from >= to) return;
        int pos = 0;
        for (Segment s : segs) {
            int segEnd = pos + s.text().length();
            if (segEnd <= from) {
                pos = segEnd;
                continue;
            }
            if (pos >= to) break;
            int sliceFrom = Math.max(0, from - pos);
            int sliceTo = Math.min(s.text().length(), to - pos);
            if (sliceTo > sliceFrom) {
                out.append(Text.literal(s.text().substring(sliceFrom, sliceTo)).setStyle(s.style()));
            }
            pos = segEnd;
        }
    }

    private record Segment(String text, Style style) {}
}
