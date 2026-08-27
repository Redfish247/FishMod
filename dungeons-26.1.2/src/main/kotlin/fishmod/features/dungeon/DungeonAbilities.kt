package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Keybinds
import fishmod.utils.config.values.FishSettings
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft

/**
 * Dungeon Ability Keybinds (ported from OdinClient's DungeonAbilities, keybind-only). Two dedicated
 * keys that reproduce the vanilla drop inputs Hypixel reads to fire a class ability:
 *  - Ult      → tap-drop      (drop one item, like Q)
 *  - Mini Ult → ctrl-drop     (drop the whole stack, like Ctrl+Q)
 */
object DungeonAbilities {

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            if (!FishSettings.dungeonAbilitiesEnabled || !Location.inDungeon()) return@EndTick

            var ult = false
            while (Keybinds.dungeonAbility.consumeClick()) ult = true
            if (ult) drop(mc, fullStack = false)

            var mini = false
            while (Keybinds.dungeonAbilityMini.consumeClick()) mini = true
            if (mini) drop(mc, fullStack = true)
        })
    }

    private fun drop(mc: Minecraft, fullStack: Boolean) {
        val p = mc.player ?: return
        if (p.mainHandItem.isEmpty) return
        p.drop(fullStack)
    }
}
