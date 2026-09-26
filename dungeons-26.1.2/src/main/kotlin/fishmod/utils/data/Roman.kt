package fishmod.utils.data

object Roman {
    @JvmStatic
    fun toInt(raw: String?): Int {
        val s = raw?.trim()?.uppercase() ?: return 0
        if (s.isEmpty()) return 0
        if (s.all { it.isDigit() }) return s.toIntOrNull() ?: 0
        var sum = 0
        var prev = 0
        for (i in s.indices.reversed()) {
            val v = when (s[i]) {
                'I' -> 1; 'V' -> 5; 'X' -> 10; 'L' -> 50; 'C' -> 100; 'D' -> 500; 'M' -> 1000
                else -> return 0
            }
            if (v < prev) sum -= v else sum += v
            prev = v
        }
        return sum
    }
}
