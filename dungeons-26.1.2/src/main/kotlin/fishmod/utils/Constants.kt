package fishmod.utils

import java.text.DecimalFormat

object Constants {
    @JvmField val NAMESPACE: String = "fishmod"

    @JvmField val DECIMAL_FORMAT: DecimalFormat = DecimalFormat("0.00")
    @JvmField val TICK_DURATION: Double = 0.05
    @JvmField val TEXT_HEIGHT: Int = 10

    @JvmField val DARK_RED: Int = 0xffaa0000.toInt()
    @JvmField val DARK_PURPLE: Int = 0xffaa00aa.toInt()
    @JvmField val GOLD: Int = 0xffffaa00.toInt()
    @JvmField val GRAY: Int = 0xffaaaaaa.toInt()
    @JvmField val BLUE: Int = 0xff5555ff.toInt()
    @JvmField val GREEN: Int = 0xff55ff55.toInt()
    @JvmField val RED: Int = 0xffff5555.toInt()
    @JvmField val LIGHT_PURPLE: Int = 0xffff55ff.toInt()
    @JvmField val YELLOW: Int = 0xffffff55.toInt()

    @JvmField val FAIL: Int = 0
    @JvmField val SUCCESS: Int = 1

    @JvmField val STRIP_COLOR_REGEX: Regex = Regex("§.")
}
