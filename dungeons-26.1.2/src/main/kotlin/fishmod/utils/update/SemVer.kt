package fishmod.utils.update

// SemVer 2.0 ordering; a trailing "-<mc version>" / "-dungeons" build tag is not treated as a prerelease.
class SemVer private constructor(
    private val core: IntArray,
    private val pre: List<String>,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        for (i in 0 until maxOf(core.size, other.core.size)) {
            val c = core.getOrElse(i) { 0 }.compareTo(other.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        if (pre.isEmpty() || other.pre.isEmpty()) return other.pre.size.coerceAtMost(1) - pre.size.coerceAtMost(1)
        for (i in 0 until minOf(pre.size, other.pre.size)) {
            val a = pre[i]; val b = other.pre[i]
            val an = a.toLongOrNull(); val bn = b.toLongOrNull()
            val c = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (c != 0) return c
        }
        return pre.size.compareTo(other.pre.size)
    }

    override fun equals(other: Any?) = other is SemVer && compareTo(other) == 0
    override fun hashCode() = core.contentHashCode() * 31 + pre.hashCode()
    override fun toString() = core.joinToString(".") + if (pre.isEmpty()) "" else "-" + pre.joinToString(".")

    companion object {
        private val CORE = Regex("""^(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$""")

        fun parse(raw: String?, mcVersion: String? = null): SemVer? {
            var s = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('+') ?: return null
            s = s.removeSuffix("-dungeons")
            if (!mcVersion.isNullOrBlank()) s = s.removeSuffix("-$mcVersion")
            val m = CORE.matchEntire(s) ?: return null
            val core = intArrayOf(
                m.groupValues[1].toIntOrNull() ?: return null,
                m.groupValues[2].toIntOrNull() ?: 0,
                m.groupValues[3].toIntOrNull() ?: 0,
            )
            val pre = m.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            return SemVer(core, pre)
        }
    }
}
