package fishmod.features.item

import fishmod.mixin.accessors.AbstractSignEditScreenAccessor
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
import net.minecraft.world.item.ItemStack

object AuctionPriceAutofill {

    private val GUI_NAMES = setOf("Create BIN Auction", "Create Auction")

    private var pendingItem: ItemStack? = null
    private var lastSeenStack: ItemStack? = null

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, w, h -> onScreenInit(screen, w, h) })
    }

    @JvmStatic
    fun trackScreen(screen: AbstractContainerScreen<*>) {
        if (!FishSettings.auctionPriceAutofillEnabled || !Location.inSkyblock()) return
        val title = screen.title.string.replace(Constants.STRIP_COLOR_REGEX, "")
        if (title !in GUI_NAMES) return
        val stack = screen.menu.slots.getOrNull(13)?.item ?: return
        if (stack.isEmpty) return
        val prev = lastSeenStack
        if (prev != null && ItemStack.isSameItemSameComponents(prev, stack)) return
        lastSeenStack = stack.copy()
        pendingItem = stack.copy()
        ItemValue.estimate(stack)
    }

    private fun onScreenInit(screen: Screen, width: Int, height: Int) {
        if (!FishSettings.auctionPriceAutofillEnabled || !Location.inSkyblock()) return
        if (screen !is AbstractSignEditScreen) return
        val item = pendingItem ?: return
        val sign = (screen as AbstractSignEditScreenAccessor).`fishmod$getSign`() ?: return
        val lines = Array(4) { i -> sign.frontText.getMessage(i, false).string }
        if (lines[1] != "^^^^^^^^^^^^^^^" || lines[2] != "Your auction" || lines[3] != "starting bid") return

        val value = ItemValue.estimate(item)
        val pct = FishSettings.auctionAutofillPercent.coerceIn(0, 50) / 100.0
        val suggested = if (value > 0.0) Math.floor(value * (1.0 - pct)).toLong() else 0L

        val mc = Minecraft.getInstance()
        mc.execute {
            val replacement = AuctionPriceScreen(sign, lines, item, suggested)
            mc.screen = replacement
            replacement.init(width, height)
        }
    }
}
