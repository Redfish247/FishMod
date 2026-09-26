package fishmod.features.item

import fishmod.features.ItemRarityHotbar
import fishmod.features.croesus.CroesusPrices
import fishmod.features.storage.StorageOverlay
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.Location
import fishmod.utils.Misc.abbr
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

object ContainerValue {

    private const val MAX_LINES = 32
    private const val RECOMPUTE_MS = 500L
    private const val NAME_MAX = 24
    private const val GOLD = 0xFFAA00
    private const val GREY = 0xAAAAAA
    private const val DIM = 0x808080
    private const val YELLOW = 0xFFFF55

    private val CODE = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val STORAGE_TITLE =
        Regex("^(Storage|Ender Chest(?: ✦)? \\([1-9]/[1-9]\\)|.+ Backpack(?: ✦)? \\(Slot #\\d+\\))$")
    private val PLAIN_CHEST = setOf("Chest", "Large Chest", "Ender Chest", "Personal Vault")

    private class Row(val name: String, var count: Int, var value: Double, val color: Int)

    private var rows: List<Row> = emptyList()
    private var total = 0.0
    private var widthPx = 0
    private var lastCompute = 0L
    private var lastRefresh = 0L
    private var cachedLines: List<Component> = emptyList()
    private var cachedMoreLine: Component? = null
    private var cachedTotalLine: Component? = null

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register {
            if (!FishSettings.containerValueEnabled || !Location.inSkyblock()) return@register
            val now = System.currentTimeMillis()
            if (now - lastRefresh > 60_000L) { lastRefresh = now; CroesusPrices.refreshIfStale() }
        }
    }

    @JvmStatic
    fun storageSidebarWidthGuiPx(): Int =
        if (FishSettings.containerValueEnabled && rows.isNotEmpty()) widthPx else 0

    @JvmStatic
    fun render(ctx: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>) {
        if (!FishSettings.containerValueEnabled || !Location.inSkyblock() || !eligible(screen)) return
        val font = Minecraft.getInstance().font

        recompute(screen)
        if (rows.isEmpty()) return

        val shown = rows.take(MAX_LINES)
        val storageMode = StorageOverlay.isActive(screen)
        val lineCount = 1 + shown.size + (if (rows.size > shown.size) 1 else 0) + 1
        val (x, y) = anchor(screen, storageMode, widthPx, lineCount * (font.lineHeight + 1))

        var cy = y
        line(ctx, font, x, cy, Component.literal("Container Value").withStyle { it.withColor(YELLOW) }); cy += font.lineHeight + 1
        for (c in cachedLines) {
            line(ctx, font, x, cy, c); cy += font.lineHeight + 1
        }
        cachedMoreLine?.let {
            line(ctx, font, x, cy, it)
            cy += font.lineHeight + 1
        }
        cachedTotalLine?.let { line(ctx, font, x, cy, it) }
    }

    private fun line(ctx: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, x: Int, y: Int, c: Component) =
        ctx.text(font, c, x, y, -1, true)

    private fun eligible(screen: AbstractContainerScreen<*>): Boolean {
        if (StorageOverlay.isActive(screen)) return true
        if (screen is InventoryScreen) return true
        val t = fishmod.utils.ScreenTitle.plain(screen).trim()
        return t in PLAIN_CHEST || STORAGE_TITLE.containsMatchIn(t)
    }

    private fun anchor(screen: AbstractContainerScreen<*>, storageMode: Boolean, w: Int, h: Int): Pair<Int, Int> {
        val win = Minecraft.getInstance().window
        val sw = win.guiScaledWidth
        val sh = win.guiScaledHeight
        if (storageMode) {
            val y = StorageOverlay.panelTopScreenY().coerceIn(2, (sh - h).coerceAtLeast(2))
            return 3 to y
        }
        val acc = screen as HandledScreenAccessor
        val y = acc.bgY.coerceIn(2, (sh - h).coerceAtLeast(2))
        val left = acc.bgX - w - 6
        if (left >= 2) return left to y
        val right = acc.bgX + acc.bgWidth + 6
        return (if (right + w <= sw) right else 2) to y
    }

    private fun recompute(screen: AbstractContainerScreen<*>) {
        val now = System.currentTimeMillis()
        if (now - lastCompute < RECOMPUTE_MS) return
        lastCompute = now

        val agg = LinkedHashMap<String, Row>()
        var sum = 0.0
        val skipPlayerInv = screen !is InventoryScreen
        for (slot in screen.menu.slots) {
            if (skipPlayerInv && slot.container is Inventory) continue
            val stack: ItemStack = slot.item
            if (stack.isEmpty) continue
            if (ItemUtil.getId(stack) == null) continue
            val unit = ItemValue.estimate(stack)
            if (unit <= 0.0) continue
            val v = unit * stack.count
            sum += v
            val name = CODE.replace(stack.hoverName.string, "")
            val ex = agg[name]
            if (ex == null) agg[name] = Row(name, stack.count, v, ItemRarityHotbar.getRarity(stack).color)
            else { ex.count += stack.count; ex.value += v }
        }

        val list = agg.values.sortedByDescending { it.value }
        rows = list
        total = sum

        val font = Minecraft.getInstance().font
        var maxw = font.width("Container Value")
        val shown = list.take(MAX_LINES)
        val lines = ArrayList<Component>(shown.size)
        for (r in shown) {
            val label = if (r.count > 1) "${r.count}x ${trim(r.name)}" else trim(r.name)
            maxw = maxOf(maxw, font.width("$label  ${abbr(r.value)}"))
            val rgb = if (r.color != 0) r.color and 0xFFFFFF else GREY
            lines.add(
                Component.literal(label).withStyle { it.withColor(rgb) }
                    .append(Component.literal("  ").withStyle { it.withColor(GREY) })
                    .append(Component.literal(abbr(r.value)).withStyle { it.withColor(GOLD) }),
            )
        }
        cachedLines = lines
        cachedMoreLine = if (list.size > shown.size)
            Component.literal("… +${list.size - shown.size} more").withStyle { it.withColor(DIM) }
        else null
        cachedTotalLine = Component.literal("Total: ").withStyle { it.withColor(YELLOW) }
            .append(Component.literal(abbr(sum)).withStyle { it.withColor(GOLD) })
        widthPx = maxOf(maxw, font.width("Total: ${abbr(total)}"))
    }

    private fun trim(s: String): String = if (s.length <= NAME_MAX) s else s.take(NAME_MAX - 1) + "…"

}
