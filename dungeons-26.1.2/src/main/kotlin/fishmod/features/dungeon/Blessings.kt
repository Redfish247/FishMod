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
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
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
        Events.ON_WORLD_CHANGE.register { clear(); false }
        Events.ON_LOCATION_CHANGE.register { _ -> clear(); false }
    }

    private fun clear() = Type.entries.forEach { it.current = 0 }

    private fun poll() {
        if (!FishSettings.blessingDisplayEnabled || !Location.inDungeon()) return
        if (++tickAcc < 20) return
        tickAcc = 0
        val overlay = Minecraft.getInstance().gui.tabList as? PlayerTabOverlayAccessor ?: return
        val footer = overlay.`fishmod$getFooter`()?.string ?: return
        val plain = COLOR.replace(footer, "")
        for (t in Type.entries) {
            t.current = t.regex.find(plain)?.let { fishmod.utils.data.Roman.toInt(it.groupValues[1]) } ?: 0
        }
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
