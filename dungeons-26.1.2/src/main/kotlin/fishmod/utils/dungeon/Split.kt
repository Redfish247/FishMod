package fishmod.utils.dungeon

import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.utils.Constants
import fishmod.utils.config.values.FishSettings
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class Split(
    val name: String,
    private val startString: String,
    private val endString: String,
    private val color: Int,
    val avg: Double
) {

    enum class TimerType(private val label: String) {
        TICK_TIME("Tick time"), DIFFRENCE("difference");

        override fun toString(): String = label
    }

    companion object {
        const val GREEN: Int = 5635925
        const val GRAY: Int = 11184810
        const val DARK_GRAY: Int = 5592405
        const val PB_COLOR: Int = 0xFF55FF
        const val AVG_COLOR: Int = 0xFFAA00

        @ConfigValue
        @JvmField
        var realTimeColorInactive: Int = GREEN
        @ConfigValue
        @JvmField
        var realTimeColorOngoing: Int = GREEN
        @ConfigValue
        @JvmField
        var realTimeColorComplete: Int = GREEN

        @ConfigValue
        @JvmField
        var serverTimeColorInactive: Int = GRAY
        @ConfigValue
        @JvmField
        var serverTimeColorOngoing: Int = GRAY
        @ConfigValue
        @JvmField
        var serverTimeColorComplete: Int = GRAY

        @ConfigValue
        @JvmField
        var parenthesesColorInactive: Int = DARK_GRAY
        @ConfigValue
        @JvmField
        var parenthesesColorOngoing: Int = DARK_GRAY
        @ConfigValue
        @JvmField
        var parenthesesColorComplete: Int = DARK_GRAY

        @ConfigValue
        @JvmField
        var timerType: TimerType = TimerType.TICK_TIME

        private var cachedRaw: String? = null
        private var cachedNameColors: Map<String, Int> = emptyMap()

        @JvmStatic
        fun nameColors(): Map<String, Int> {
            val raw = FishSettings.splitNameColors
            if (raw != cachedRaw) {
                cachedRaw = raw
                cachedNameColors = raw.split(';').mapNotNull { e ->
                    val i = e.lastIndexOf('=')
                    if (i <= 0) null else e.substring(i + 1).toLongOrNull(16)?.let { e.substring(0, i) to it.toInt() }
                }.toMap()
            }
            return cachedNameColors
        }

        @JvmStatic
        fun setNameColor(name: String, color: Int) {
            val m = nameColors().toMutableMap()
            m[name] = color
            FishSettings.splitNameColors = m.entries.joinToString(";") { "${it.key}=${Integer.toHexString(it.value)}" }
        }

        @JvmStatic
        fun resetNameColors() {
            FishSettings.splitNameColors = ""
        }
    }

    fun nameColor(): Int = nameColors()[name] ?: color

    private var tick: Int = 0
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var started: Boolean = false
    private var ended: Boolean = false

    @JvmField var paceColor: Int = 0

    fun parseMessage(string: String) {
        if (!started) {
            if (startString == string) {
                start()
            }
        } else if (!ended) {
            if (endString == string) {
                end()
            }
        }
    }

    fun tick() {
        if (started && !ended) {
            tick++
        }
    }

    fun reset() {
        paceColor = 0
        tick = 0
        ended = false
        started = false
        cachedAt = 0L
    }

    fun end() {
        if (ended) return
        endTime = System.currentTimeMillis()
        started = false
        ended = true
        cachedAt = 0L
    }

    fun start() {
        paceColor = 0
        startTime = System.currentTimeMillis()
        started = true
        ended = false
        cachedAt = 0L
    }

    fun started(): Boolean = started

    fun ended(): Boolean = ended

    fun getTickTime(): Double = tick * Constants.TICK_DURATION

    fun getRealTime(): Double {
        if (startTime == 0L) return 0.0
        return if (ended) {
            (endTime - startTime) / 1000.0
        } else if (started) {
            (System.currentTimeMillis() - startTime) / 1000.0
        } else {
            0.0
        }
    }

    fun createNameText(): MutableComponent = Component.literal("$name ").withColor(nameColor() and 0xFFFFFF)

    fun getTimeDifference(): Double = getRealTime() - getTickTime()

    fun createTimeText(): MutableComponent {
        val realTimeColor: Int
        val serverTimeColor: Int
        val parenthesesColor: Int

        if (!started) {
            realTimeColor = realTimeColorInactive
            serverTimeColor = serverTimeColorInactive
            parenthesesColor = parenthesesColorInactive
        } else if (!ended) {
            realTimeColor = realTimeColorOngoing
            serverTimeColor = serverTimeColorOngoing
            parenthesesColor = parenthesesColorOngoing
        } else {
            val pace = when (paceColor) {
                PB_COLOR -> FishSettings.splitPbColor
                AVG_COLOR -> FishSettings.splitAvgColor
                else -> 0
            }
            realTimeColor = if (pace != 0 && FishSettings.splitPbColors) pace else realTimeColorComplete
            serverTimeColor = serverTimeColorComplete
            parenthesesColor = parenthesesColorComplete
        }

        val tickTime = getTickTime()
        val realTime = getRealTime()

        val serverTime: String = if (timerType == TimerType.DIFFRENCE) {
            val diff = realTime - tickTime
            if (diff > 0) {
                "+" + Constants.DECIMAL_FORMAT.format(diff) + "s"
            } else {
                Constants.DECIMAL_FORMAT.format(diff) + "s"
            }
        } else {
            Constants.DECIMAL_FORMAT.format(tickTime) + "s"
        }

        val realTimeString = (if (realTime >= 60) (realTime / 60).toInt().toString() + "m " else "") + Constants.DECIMAL_FORMAT.format(realTime % 60) + "s"
        return Component.literal(realTimeString).withColor(realTimeColor and 0xFFFFFF)
            .append(
                Component.literal(" (").withColor(parenthesesColor and 0xFFFFFF)
                    .append(Component.literal(serverTime).withColor(serverTimeColor and 0xFFFFFF))
                    .append(Component.literal(")").withColor(parenthesesColor and 0xFFFFFF))
            )
    }

    private var cachedName: Component? = null
    private var cachedTimer: Component? = null
    private var cachedTimerWidth = 0
    private var cachedAt = 0L

    fun drawSplit(context: GuiGraphicsExtractor, textRenderer: Font, x: Int, y: Int, maxWidth: Int) {
        val now = System.currentTimeMillis()
        val refreshMs = if (started && !ended) 50L else 500L
        var nameText = cachedName
        var timerText = cachedTimer
        if (nameText == null || timerText == null || now - cachedAt >= refreshMs) {
            nameText = createNameText()
            timerText = createTimeText()
            cachedName = nameText
            cachedTimer = timerText
            cachedTimerWidth = textRenderer.width(timerText)
            cachedAt = now
        }
        context.text(textRenderer, nameText, x, y, -0x1, true)
        context.text(textRenderer, timerText, x + maxWidth - cachedTimerWidth, y, -0x1, true)
    }
}
