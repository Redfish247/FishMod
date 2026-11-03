package fishmod.compat

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

// Runtime-only: when blade-addons is installed, our overlapping features stand down (never saved to config).
object BladeCompat {

    @JvmField
    val active: Boolean = try { FabricLoader.getInstance().isModLoaded("blade-addons") } catch (_: Throwable) { false }

    private val pausedFeatures = listOf(
        "Auto Requeue", "Key Pickup Alert", "Party Warp Timer", "F7 boss timers & alerts",
        "Goldor section HUDs", "Inventory Search", "Inventory Buttons", "Item Rarity Background"
    )

    private var told = false

    @JvmStatic
    fun tellOnce() {
        if (!active || told) return
        val mc = Minecraft.getInstance()
        if (mc.player == null) return
        told = true
        fishmod.utils.FishUtil.addChatMessage(Component.literal(
            "§7Blade Addons detected, paused to avoid doubles: §f" + pausedFeatures.joinToString("§7, §f")
        ))
    }
}
