package fishmod.features.dungeon

import fishmod.utils.SkyblockLocation
import fishmod.utils.FishKeys
import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

object DungeonAbilities {

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            var ult = false
            while (FishKeys.dungeonAbility.consumeClick()) ult = true
            var mini = false
            while (FishKeys.dungeonAbilityMini.consumeClick()) mini = true
            if (!FishSettings.dungeonAbilitiesEnabled || !SkyblockLocation.inDungeon()) return@EndTick

            if (ult) drop(mc, fullStack = false)
            if (mini) drop(mc, fullStack = true)
        })
    }

    private fun drop(mc: Minecraft, fullStack: Boolean) {
        val p = mc.player ?: return
        if (p.mainHandItem.isEmpty) return
        FishDiag.guard("DungeonAbilities.1", "dungeon ability drop failed (fullStack=$fullStack)") { p.drop(fullStack) }
    }
}
