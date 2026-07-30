package fishmod.utils.dungeon

import config.practical.manager.ConfigValue
import fishmod.utils.Constants
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
    }

    private var tick: Int = 0
    private var startTime: Long = 0
    private var endTime: Long = 0
    private var started: Boolean = false
    private var ended: Boolean = false

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
        tick = 0
        ended = false
        started = false
    }

    fun end() {
        if (ended) return
        endTime = System.currentTimeMillis()
        started = false
        ended = true
    }

    fun start() {
        startTime = System.currentTimeMillis()
        started = true
    }

    fun started(): Boolean = started

    fun ended(): Boolean = ended

    fun getTickTime(): Double = tick * Constants.TICK_DURATION

    fun getRealTime(): Double {
        // startTime == 0 means start() was never called; guard against returning
        // epoch-time-in-seconds when printSplits() force-ends a never-started split.
        if (startTime == 0L) return 0.0
        return if (ended) {
            (endTime - startTime) / 1000.0
        } else if (started) {
            (System.currentTimeMillis() - startTime) / 1000.0
        } else {
            0.0
        }
    }

    fun createNameText(): MutableComponent = Component.literal("$name ").withColor(color)

    fun getTimeDiffrence(): Double = getRealTime() - getTickTime()

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
            realTimeColor = realTimeColorComplete
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
            // default to tick timer
            Constants.DECIMAL_FORMAT.format(tickTime) + "s"
        }

        val realTimeString = (if (realTime >= 60) (realTime / 60).toInt().toString() + "m " else "") + Constants.DECIMAL_FORMAT.format(realTime % 60) + "s"
        return Component.literal(realTimeString).withColor(realTimeColor)
            .append(
                Component.literal(" (").withColor(parenthesesColor)
                    .append(Component.literal(serverTime).withColor(serverTimeColor))
                    .append(Component.literal(")").withColor(parenthesesColor))
            )
    }

    fun drawSplit(context: GuiGraphicsExtractor, textRenderer: Font, x: Int, y: Int, maxWidth: Int) {
        val nameText = createNameText()
        val timerText = createTimeText()

        val timerWidth = textRenderer.width(timerText)
        context.text(textRenderer, nameText, x, y, -0x1, true)
        context.text(textRenderer, timerText, x + maxWidth - timerWidth, y, -0x1, true)
    }
}
