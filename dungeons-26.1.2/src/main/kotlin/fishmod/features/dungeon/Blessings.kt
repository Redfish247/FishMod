package fishmod.features.dungeon
import fishmod.compat.hud
import fishmod.utils.TextStrip
import fishmod.compat.hudHidden

import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.mixin.accessors.PlayerTabOverlayAccessor
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
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
    private var tickAcc = 0

    @JvmStatic
    fun init() {
        registerPreview()
        FishHudEditor.register(
            NAME,
            { FishSettings.blessingHudX }, { v -> FishSettings.blessingHudX = v },
            { FishSettings.blessingHudY }, { v -> FishSettings.blessingHudY = v },
            70, 50,
            { FishSettings.blessingScale }, { v -> FishSettings.blessingScale = v },
            { FishSettings.blessingDisplayEnabled }
        )

        FishEvents.serverTick.register { FishDiag.guard("Blessings.2", "blessing tab poll failed") { poll() }; false }
        FishEvents.worldChange.register { clear(); false }
        FishEvents.locationChange.register { _ -> clear(); false }
    }

    private fun clear() = Type.entries.forEach { it.current = 0 }

    private fun poll() {
        if (!FishSettings.blessingDisplayEnabled || !SkyblockLocation.inDungeon()) return
        if (++tickAcc < 20) return
        tickAcc = 0
        val overlay = Minecraft.getInstance().hud.tabList as? PlayerTabOverlayAccessor ?: return
        val footer = overlay.`fishmod$getFooter`()?.string ?: return
        val plain = TextStrip.plain(footer)
        for (t in Type.entries) {
            t.current = t.regex.find(plain)?.let { m ->
                fishmod.utils.data.Roman.toInt(m.groupValues[1]).also {
                    if (it <= 0 && m.groupValues[1].isNotEmpty()) FishDiag.fail("Blessings.1", "blessing ${t.display} level unparsed: '${m.value}'")
                }
            } ?: 0
        }
    }

    private val shown = ArrayList<Type>()

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.blessingDisplayEnabled || !SkyblockLocation.inDungeon()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.hudHidden) return

        shown.clear()
        for (t in Type.entries) if (t.enabled() && t.current > 0) shown.add(t)
        if (shown.isEmpty()) return

        val sc = FishSettings.blessingScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(FishSettings.blessingHudX.toFloat(), FishSettings.blessingHudY.toFloat())
            ctx.pose().scale(sc, sc)
            drawRows(ctx, shown.map { it to it.current })
        } catch (e: Exception) {
            FishDiag.fail("Blessings.3", "blessing hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }

    private fun label(t: Type, level: Int): Component = Component.literal("${t.display}: ").withColor(t.color() and 0xFFFFFF)
        .append(Component.literal(level.toString()).withColor(0x55FF55))

    private fun drawRows(ctx: GuiGraphicsExtractor, rows: List<Pair<Type, Int>>) {
        val font = Minecraft.getInstance().font
        rows.forEachIndexed { i, (t, n) -> ctx.text(font, label(t, n), 0, i * LINE_H, -1, true) }
    }

    // Example data: obviously fake levels, only the blessings the player has enabled
    private fun registerPreview() {
        HudPreview.register(NAME) {
            val levels = mapOf(Type.POWER to 14, Type.LIFE to 12, Type.WISDOM to 9, Type.STONE to 11, Type.TIME to 5)
            val rows = Type.entries.filter { it.enabled() }.map { it to levels.getValue(it) }
            if (rows.isEmpty()) return@register null
            val font = Minecraft.getInstance().font
            HudExample(rows.maxOf { font.width(label(it.first, it.second)) }, HudPreview.textHeight(rows.size, LINE_H)) { drawRows(it, rows) }
        }
    }
}
