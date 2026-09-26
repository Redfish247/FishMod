package fishmod.utils.data

import net.minecraft.client.Minecraft

object ScoreboardUtil {

    private class ClassInfo(val className: String, val level: Int)

    @JvmStatic
    fun getCurrentClassLevel(): Int {
        val info = getClassInfo()
        return info?.level ?: 0
    }

    private fun getClassInfo(): ClassInfo? {
        val mc = Minecraft.getInstance()
        if (mc == null || mc.player == null || mc.connection == null) return null

        val myName = mc.player!!.name.string

        for (entry in mc.connection!!.onlinePlayers) {
            val profileName = entry.profile.name
            if (profileName != myName) continue

            val display = entry.tabListDisplayName?.string ?: profileName

            val start = display.indexOf("(")
            val end = display.indexOf(")")
            if (start == -1 || end == -1) return null

            val inside = display.substring(start + 1, end).trim()

            val parts = inside.split(" ")
            if (parts.size != 2) return null

            val className = parts[0]
            val roman = parts[1]

            val level = Roman.toInt(roman)

            return ClassInfo(className, level)
        }

        return null
    }

}
