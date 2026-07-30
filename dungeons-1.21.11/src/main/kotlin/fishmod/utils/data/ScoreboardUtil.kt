package fishmod.utils.data

import net.minecraft.client.MinecraftClient

object ScoreboardUtil {

    private class ClassInfo(val className: String, val level: Int)

    @JvmStatic
    fun getCurrentClass(): String? {
        val info = getClassInfo()
        return info?.className
    }

    @JvmStatic
    fun getCurrentClassLevel(): Int {
        val info = getClassInfo()
        return info?.level ?: 0
    }

    private fun getClassInfo(): ClassInfo? {
        val mc = MinecraftClient.getInstance()
        if (mc == null || mc.player == null || mc.networkHandler == null) return null

        val myName = mc.player!!.name.string

        for (entry in mc.networkHandler!!.playerList) {
            val profileName = entry.profile.name
            if (profileName != myName) continue

            val display = entry.displayName?.string ?: profileName

            // Look for "(Mage XLIX)"
            val start = display.indexOf("(")
            val end = display.indexOf(")")
            if (start == -1 || end == -1) return null

            val inside = display.substring(start + 1, end).trim()
            // inside = "Mage XLIX"

            val parts = inside.split(" ")
            if (parts.size != 2) return null

            val className = parts[0]
            val roman = parts[1]

            val level = romanToInt(roman)

            return ClassInfo(className, level)
        }

        return null
    }

    private fun romanToInt(roman: String): Int {
        var sum = 0
        var prev = 0

        for (i in roman.length - 1 downTo 0) {
            val value = romanValue(roman[i])
            if (value < prev) sum -= value else sum += value
            prev = value
        }

        return sum
    }

    private fun romanValue(c: Char): Int {
        return when (c) {
            'I' -> 1
            'V' -> 5
            'X' -> 10
            'L' -> 50
            'C' -> 100
            'D' -> 500
            'M' -> 1000
            else -> 0
        }
    }
}
