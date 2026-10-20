package fishmod.features.garden

import fishmod.compat.curScreen
import fishmod.features.mining.Mining
import fishmod.utils.Keybinds
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import fishmod.features.garden.GardenSettings as S

// Your own keys press the vanilla controls while farming in a plot; a remap only, one key down = one control down.
object GardenKeybinds {

    @JvmField val NAMES = arrayOf("Attack", "Use Item", "Forward", "Back", "Left", "Right", "Jump", "Sneak")

    private val lastDown = BooleanArray(NAMES.size)

    private fun vanilla(mc: Minecraft): Array<KeyMapping> = arrayOf(
        mc.options.keyAttack, mc.options.keyUse, mc.options.keyUp, mc.options.keyDown,
        mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump, mc.options.keyShift
    )

    private fun itemAllowed(mc: Minecraft): Boolean {
        val held = mc.player?.mainHandItem ?: return false
        if (S.gardenKeysHoe && GardenCrops.holdingFarmingTool()) return true
        if (S.gardenKeysRod && held.`is`(net.minecraft.world.item.Items.FISHING_ROD)) return true
        val id = fishmod.utils.data.ItemUtil.getId(held) ?: ""
        if (S.gardenKeysMousemat && id.contains("SQUEAKY_MOUSEMAT")) return true
        return S.gardenKeysGrasp && held.hoverName.string.contains("Sun's Grasp", true)
    }

    private fun active(mc: Minecraft): Boolean =
        S.gardenKeys && mc.curScreen == null && Garden.inGarden() && Mining.sidebar.any { it.contains("Plot", true) } && itemAllowed(mc)

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try { tick(mc) } catch (e: Exception) { FishDiag.fail("GardenKeybinds.1", "garden keybinds tick failed", e) }
        }
    }

    private fun tick(mc: Minecraft) {
        val keys = Keybinds.gardenKeys ?: return
        val on = active(mc)
        val target = vanilla(mc)
        for (i in keys.indices) {
            if (keys[i].isUnbound) continue
            val down = on && keys[i].isDown
            if (down != lastDown[i]) { target[i].setDown(down); lastDown[i] = down }
        }
    }
}
