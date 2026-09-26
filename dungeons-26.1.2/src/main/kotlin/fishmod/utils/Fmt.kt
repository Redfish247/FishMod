package fishmod.utils

object Fmt {
    private val oneDecimal = HashMap<Long, String>()
    private val twoDecimals = HashMap<Long, String>()
    private val noDecimals = HashMap<Long, String>()
    private val grouped = HashMap<Long, String>()

    private fun cached(cache: HashMap<Long, String>, key: Long, make: () -> String): String {
        cache[key]?.let { return it }
        if (cache.size > 4096) cache.clear()
        return make().also { cache[key] = it }
    }

    @JvmStatic
    fun f1(v: Double): String {
        val k = Math.round(v * 10)
        return cached(oneDecimal, k) { String.format("%.1f", k / 10.0) }
    }

    @JvmStatic
    fun f2(v: Double): String {
        val k = Math.round(v * 100)
        return cached(twoDecimals, k) { String.format("%.2f", k / 100.0) }
    }

    @JvmStatic
    fun f0(v: Double): String {
        val k = Math.round(v)
        return cached(noDecimals, k) { String.format("%.0f", k.toDouble()) }
    }

    @JvmStatic
    fun grouped(v: Long): String = cached(grouped, v) { String.format("%,d", v) }
}
