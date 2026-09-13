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

/**
 * Auto BIN Price: the "Create BIN Auction"/"Create Auction" menu's Price button opens a sign-edit
 * GUI (Hypixel repurposes the vanilla sign screen for text entry). This prefills that field with
 * the item's looked-up value minus [FishSettings.auctionAutofillPercent]%, so undercutting the
 * market is a one-click confirm instead of typing a price by hand — same idea as Coflnet's mod.
 *
 * The item being priced is cached from slot 13 of the auction menu every frame it's open (rather
 * than only on the Price-button click) since by the time the sign screen opens, the original
 * container is already gone.
 */
object AuctionPriceAutofill {

    private val GUI_NAMES = setOf("Create BIN Auction", "Create Auction")

    private var pendingItem: ItemStack? = null

    @JvmStatic
    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, screen, w, h -> onScreenInit(screen, w, h) })
    }

    /**
     * Called every frame the auction menu is drawn (from HandledScreenMixin's render hook). Also
     * primes [ItemValue.estimate]'s live-price cache right away — its quality/tier-matched lookup is
     * two sequential HTTP calls, which won't finish in the single tick between clicking Price and the
     * sign screen opening, so without this the first suggestion always falls back to the crude
     * unscaled estimate. Warming it while the player is still just browsing the menu gives it the
     * few seconds it actually needs.
     */
    @JvmStatic
    fun trackScreen(screen: AbstractContainerScreen<*>) {
        if (!FishSettings.auctionPriceAutofillEnabled || !Location.inSkyblock()) return
        val title = screen.title.string.replace(Constants.STRIP_COLOR_REGEX, "")
        if (title !in GUI_NAMES) return
        val stack = screen.menu.slots.getOrNull(13)?.item ?: return
        if (stack.isEmpty) return
        pendingItem = stack.copy()
        ItemValue.estimate(stack)
    }

    private fun onScreenInit(screen: Screen, width: Int, height: Int) {
        if (!FishSettings.auctionPriceAutofillEnabled || !Location.inSkyblock()) return
        if (screen !is AbstractSignEditScreen) return
        val item = pendingItem ?: return
        val sign = (screen as AbstractSignEditScreenAccessor).`fishmod$getSign`() ?: return
        val lines = Array(4) { i -> sign.frontText.getMessage(i, false).string }
        // Hypixel's auction-price sign always carries this fixed layout on lines 1-3
        if (lines[1] != "^^^^^^^^^^^^^^^" || lines[2] != "Your auction" || lines[3] != "starting bid") return

        val value = ItemValue.estimate(item)
        val pct = FishSettings.auctionAutofillPercent.coerceIn(0, 50) / 100.0
        val suggested = if (value > 0.0) Math.floor(value * (1.0 - pct)).toLong() else 0L

        // Assign the field directly (not Minecraft.setScreen) so the sign screen's removed() never
        // fires — that would immediately send a blank-price sign update (server: "Couldn't read
        // this number!") before this screen even shows. Mirrors how Hypixel-adjacent mods (e.g.
        // NoammAddons) swap in their own price-entry screen over this same sign GUI.
        val mc = Minecraft.getInstance()
        mc.execute {
            val replacement = AuctionPriceScreen(sign, lines, item, suggested)
            mc.screen = replacement
            replacement.init(width, height)
        }
    }
}
