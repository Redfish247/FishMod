package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.mixin.accessors.PlayerTabOverlayAccessor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * Dungeon Blessing display (ported from Odin's BlessingDisplay). Blessing levels are read from the
 * tab-list **footer** — Hypixel writes the active blessings there as "Blessing of Power IX" etc.
 * (Odin parses the same S47 header/footer packet text.) Roman numeral -> int via [romanToInt].
 */
object Blessings {

    enum class Type(
        val regex: Regex,
        val display: String,
        val enabled: () -> Boolean,
        val color: () -> Int,
    ) {
        POWER(Regex("Blessing of Power (X{0,3}(?:IX|IV|V?I{0,3}))"), "Power",
            { FishSettings.blessingPower }, { FishSettings.blessingPowerColor }),
        LIFE(Regex("Blessing of Life (X{0,3}(?:IX|IV|V?I{0,3}))"), "Life",
            { FishSettings.blessingLife }, { FishSettings.blessingLifeColor }),
        WISDOM(Regex("Blessing of Wisdom (X{0,3}(?:IX|IV|V?I{0,3}))"), "Wisdom",
            { FishSettings.blessingWisdom }, { FishSettings.blessingWisdomColor }),
        STONE(Regex("Blessing of Stone (X{0,3}(?:IX|IV|V?I{0,3}))"), "Stone",
            { FishSettings.blessingStone }, { FishSettings.blessingStoneColor }),
        TIME(Regex("Blessing of Time (V)"), "Time",
            { FishSettings.blessingTime }, { FishSettings.blessingTimeColor });

        @JvmField var current = 0
    }

    private const val NAME = "Blessings"
    private const val LINE_H = 10
    private val COLOR = Regex("§.")
    private val ROMAN = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    private var tickAcc = 0

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.blessingHudX }, { v -> FishSettings.blessingHudX = v },
            { FishSettings.blessingHudY }, { v -> FishSettings.blessingHudY = v },
            70, 50,
            { FishSettings.blessingScale }, { v -> FishSettings.blessingScale = v }
        )

        Events.ON_SERVER_TICK.register { poll(); false }
        Events.ON_WORLD_CHANGE.register { Type.entries.forEach { it.current = 0 }; false }
    }

    private fun poll() {
        if (!FishSettings.blessingDisplayEnabled || !Location.inDungeon()) return
        if (++tickAcc < 20) return
        tickAcc = 0
        val overlay = Minecraft.getInstance().gui.tabList as? PlayerTabOverlayAccessor ?: return
        val footer = overlay.`fishmod$getFooter`()?.string ?: return
        val plain = COLOR.replace(footer, "")
        for (t in Type.entries) {
            val m = t.regex.find(plain) ?: continue
            t.current = romanToInt(m.groupValues[1])
        }
    }

    private fun romanToInt(s: String): Int {
        if (s.isEmpty()) return 0
        if (s.all { it.isDigit() }) return s.toInt()
        var result = 0
        for (i in 0 until s.length - 1) {
            val cur = ROMAN[s[i]] ?: 0
            val next = ROMAN[s[i + 1]] ?: 0
            result += if (cur < next) -cur else cur
        }
        return result + (ROMAN[s.last()] ?: 0)
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.blessingDisplayEnabled || !Location.inDungeon()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val shown = Type.entries.filter { it.enabled() && it.current > 0 }
        if (shown.isEmpty()) return

        val sc = FishSettings.blessingScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.blessingHudX.toFloat(), FishSettings.blessingHudY.toFloat())
        ctx.pose().scale(sc, sc)
        shown.forEachIndexed { i, t ->
            val label = Component.literal("${t.display}: ").withColor(t.color() and 0xFFFFFF)
                .append(Component.literal(t.current.toString()).withColor(0x55FF55))
            ctx.text(mc.font, label, 0, i * LINE_H, -1, true)
        }
        ctx.pose().popMatrix()
    }
}
