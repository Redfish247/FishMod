package fishmod.utils.rendering

import fishmod.utils.debug.FishDiag

object TextFit {
    @JvmStatic
    fun prefixLength(s: String, suffix: String, maxW: Float, minLen: Int, width: (String) -> Float): Int {
        if (minLen < 0 || maxW.isNaN()) FishDiag.fail("TextFit.1", "prefixLength bad input minLen=$minLen maxW=$maxW len=${s.length}")
        var lo = minLen
        var hi = s.length
        if (lo >= hi) return hi
        if (width(s.substring(0, lo) + suffix) > maxW) return lo
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (width(s.substring(0, mid) + suffix) <= maxW) lo = mid else hi = mid - 1
        }
        return lo
    }
}
