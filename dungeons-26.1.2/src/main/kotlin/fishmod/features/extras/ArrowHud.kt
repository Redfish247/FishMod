package fishmod.features.extras

import fishmod.features.FishHudEditor
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.data.ItemUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.Items
import fishmod.features.extras.ExtrasSettings as S

// Selected quiver arrow + count, with low / out warnings
object ArrowHud {
    private val SELECTED = Regex("""^You set your selected arrow type to (.+?)!$""")
    private val LOW = Regex("""^You only have ([\d,]+) arrows? left in your Quiver!$""")
    private val FILLED = Regex("""^You filled your quiver with ([\d,]+) extra arrows?!$""")
    private val LORE_ARROW = Regex("""^(?:Active|Selected) Arrow: (.+?)(?: \(([\d,]+)\))?$""")
    private val REMAINING = Regex("""(?i)arrows remaining:?\s*([\d,]+)|([\d,]+)\s*arrows remaining""")
    private const val EMPTY_MSG = "You don't have any more arrows left in your Quiver!"

    private var type = "Unknown"
    private var count = -1L
    private var rarityRgb = 0
    private var extra = 0L
    private var warnedLow = false
    private var warnedOut = false
    private var tickN = 0

    fun init() {
        FishHudEditor.register("Arrow HUD", { S.arrowHudX }, { S.arrowHudX = it }, { S.arrowHudY }, { S.arrowHudY = it }, 110, 10,
            { S.arrowHudScale }, { S.arrowHudScale = it })
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", "arrow_hud")) { ctx, _ ->
            try { if (!FishHudEditor.isOpen()) render(ctx) } catch (t: Throwable) { FishDiag.fail("ArrowHud.1", "arrow hud render failed", t) }
        }
        Events.ON_GAME_MESSAGE.register { text ->
            if (S.arrowHud) onChat(text.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim())
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (S.arrowHud && ++tickN % 10 == 0) scan() }
    }

    private fun onChat(s: String) {
        SELECTED.find(s)?.let { type = it.groupValues[1]; return }
        LOW.find(s)?.let { setCount(it.groupValues[1].replace(",", "").toLongOrNull() ?: return); return }
        FILLED.find(s)?.let { if (count >= 0) setCount(count + (it.groupValues[1].replace(",", "").toLongOrNull() ?: 0)); return }
        if (s == EMPTY_MSG) setCount(0)
    }

    private fun holdingBow(): Boolean {
        val it = Minecraft.getInstance().player?.mainHandItem ?: return false
        return it.item is BowItem || it.item is CrossbowItem
    }

    // Bow lore shows the active arrow; real arrow stacks (outside the quiver) are counted too
    private fun scan() {
        val p = Minecraft.getInstance().player ?: return
        if (!Location.inSkyblock()) return
        if (slotNine(p)) return
        if (holdingBow()) {
            p.mainHandItem.get(DataComponents.LORE)?.lines()?.forEach { line ->
                val m = LORE_ARROW.find(line.string.replace(fishmod.utils.Constants.STRIP_COLOR_REGEX, "").trim()) ?: return@forEach
                type = m.groupValues[1]
                m.groupValues[2].replace(",", "").toLongOrNull()?.let { setCount(it) }
            }
        }
        var inv = 0L; var loose = 0L
        for ((i, st) in p.inventory.nonEquipmentItems.withIndex()) {
            if (st.isEmpty) continue
            if (st.item == Items.ARROW || st.item == Items.TIPPED_ARROW || st.item == Items.SPECTRAL_ARROW
                || ItemUtil.getId(st)?.endsWith("ARROW") == true) { inv += st.count; if (i != 8) loose += st.count }
        }
        extra = loose
        if (inv > 0 && count < 0) setCount(inv)
    }

    // Hotbar slot 9 holds the selected arrow stack; its name is the type and stack size the count
    private fun slotNine(p: net.minecraft.world.entity.player.Player): Boolean {
        val st = p.inventory.getItem(8)
        if (st.isEmpty) return false
        val name = fishmod.utils.Constants.STRIP_COLOR_REGEX.replace(st.hoverName.string, "").trim()
        val id = ItemUtil.getId(st)
        val arrowy = st.item == Items.ARROW || st.item == Items.TIPPED_ARROW || st.item == Items.SPECTRAL_ARROW ||
            id?.endsWith("ARROW") == true || name.contains("Arrow", true)
        if (!arrowy) return false
        type = name.replace(Regex("""\s*x?[\d,]+$"""), "").ifEmpty { name }
        rarityRgb = fishmod.features.ItemRarityHotbar.getRarity(st).color and 0xFFFFFF
        setCount(remainingFromLore(st) ?: st.count.toLong())
        return true
    }

    // "Arrows Remaining: 1,234" lore line is the real quiver count; the stack size caps at 64
    private fun remainingFromLore(st: net.minecraft.world.item.ItemStack): Long? {
        st.get(DataComponents.LORE)?.lines()?.forEach { line ->
            val m = REMAINING.find(fishmod.utils.Constants.STRIP_COLOR_REGEX.replace(line.string, "").trim()) ?: return@forEach
            (m.groupValues[1].ifEmpty { m.groupValues[2] }).replace(",", "").toLongOrNull()?.let { return it }
        }
        return null
    }

    private fun setCount(n: Long) {
        count = n
        if (n > S.arrowLowThreshold) warnedLow = false
        if (n > 0) warnedOut = false
        if (n == 0L && S.arrowOutWarn && !warnedOut) {
            warnedOut = true
            Misc.forceTitle(Component.literal("§cOUT OF ARROWS"), Component.empty(), 2000)
            ExtrasSounds.play(S.arrowWarnSound, 100, "fm_arrow_out")
        } else if (n in 1..S.arrowLowThreshold.toLong() && S.arrowLowWarn && !warnedLow) {
            warnedLow = true
            Misc.forceTitle(Component.empty(), Component.literal("§eLow arrows: §f$n"), 1500)
            ExtrasSounds.play(S.arrowWarnSound, 100, "fm_arrow_low")
        }
    }

    private fun render(ctx: net.minecraft.client.gui.GuiGraphicsExtractor) {
        if (!S.arrowHud || !Location.inSkyblock()) return
        if (S.arrowHudOnlyBow && !holdingBow()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        val n = when {
            count < 0 -> "§7?"
            count == 0L -> "§c0"
            count <= S.arrowLowThreshold -> "§e" + fishmod.utils.Fmt.grouped(count)
            else -> "§f" + fishmod.utils.Fmt.grouped(count)
        }
        val sc = S.arrowHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.arrowHudX.toFloat(), S.arrowHudY.toFloat())
            ctx.pose().scale(sc, sc)
            ctx.text(mc.font, Component.literal(type).withColor(if (S.arrowHudRarity && rarityRgb != 0) rarityRgb else S.arrowHudColor and 0xFFFFFF).append(Component.literal(" §7x$n")).append(Component.literal(if (S.arrowHudExtra && extra > 0) " §8(+${fishmod.utils.Fmt.grouped(extra)} extra)" else "")), 0, 0, -1, true)
        } finally {
            ctx.pose().popMatrix()
        }
    }
}
