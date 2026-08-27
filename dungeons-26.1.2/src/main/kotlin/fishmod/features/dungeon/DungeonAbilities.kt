package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import java.util.regex.Pattern

/**
 * Dungeon Ability Keybinds (ported from OdinClient's DungeonAbilities). A dedicated key that "drops"
 * the held item — the same input Hypixel uses to fire a dungeon class ability (Berserk/Mage ult,
 * Healer, etc.). Optional Auto Ult drops on the known boss-enrage lines (Maxor / Goldor / Sadan).
 */
object DungeonAbilities {

    private val MAXOR = Pattern.compile("⚠ Maxor is enraged! ⚠")
    private val GOLDOR = Pattern.compile("\\[BOSS] Goldor: You have done it, you destroyed the factory.*")
    private val SADAN = Pattern.compile("\\[BOSS] Sadan: My giants! Unleashed!")

    private var pendingTicks = -1

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            var fired = false
            while (fishmod.utils.Keybinds.dungeonAbility.consumeClick()) fired = true
            if (fired && FishSettings.dungeonAbilitiesEnabled && Location.inDungeon()) useAbility(mc)

            if (pendingTicks >= 0) {
                if (pendingTicks == 0) useAbility(mc)
                pendingTicks--
            }
        })

        Events.ON_GAME_MESSAGE.register { text ->
            if (!FishSettings.dungeonAbilitiesEnabled || !FishSettings.dungeonAbilitiesAutoUlt || !Location.inDungeon())
                return@register false
            val s = text.string.replace(Regex("§."), "").trim()
            when {
                MAXOR.matcher(s).matches() || GOLDOR.matcher(s).matches() -> {
                    useAbility(Minecraft.getInstance()); modMessage("Using ult!")
                }
                SADAN.matcher(s).matches() -> { pendingTicks = 25; modMessage("Using ult in 25t!") }
            }
            false
        }
    }

    private fun useAbility(mc: Minecraft) {
        val p = mc.player ?: return
        if (p.mainHandItem.isEmpty) return
        p.drop(true)
    }

    private fun modMessage(msg: String) = Misc.addChatMessage(Component.literal("§3Dungeon Abilities §7» §a$msg"))
}
