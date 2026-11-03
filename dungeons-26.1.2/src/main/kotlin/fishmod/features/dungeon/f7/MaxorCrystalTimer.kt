package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EndCrystal

// P1 energy crystals: respawn countdown after Maxor is lasered, and a nag while you still hold one.
object MaxorCrystalTimer {

    private const val PICKUP_SUFFIX = " picked up an Energy Crystal!"
    private val LASERED = setOf("[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!", "[BOSS] Maxor: YOU TRICKED ME!")
    private const val RESPAWN_TICKS = 34
    private const val NAG_AFTER_TICKS = 240
    private const val PLACED_Y = 224.375

    private var respawnIn = 0
    private var holding = false
    private var heldFor = 0

    private fun inMaxor() = SkyblockLocation.inDungeon() && BossPhase.inP1()

    private fun dropHeld() { holding = false; heldFor = 0 }

    @JvmStatic
    fun init() {
        FishEvents.chat.register { text ->
            if (!Floor7Settings.crystalSpawnHud || !inMaxor()) return@register false
            val line = text.string
            when {
                line in LASERED -> respawnIn = RESPAWN_TICKS
                line.endsWith(PICKUP_SUFFIX) -> {
                    val who = line.removeSuffix(PICKUP_SUFFIX).substringAfterLast(' ')
                    if (EntityTools.isClientPlayer(who)) holding = true
                }
                line.contains("picked up an Energy Crystal") -> FishDiag.fail("MaxorCrystalTimer.1", "crystal pickup line changed: '$line'")
            }
            false
        }
        FishEvents.serverTick.register {
            if (respawnIn > 0) respawnIn--
            if (holding && ++heldFor == 20 * 600) FishDiag.fail("MaxorCrystalTimer.4", "held crystal never placed after 10 minutes")
            false
        }
        FishEvents.locationChange.register { respawnIn = 0; dropHeld(); false }
        FishEvents.entitySpawn.register { entity, _ ->
            if (holding && entity is EndCrystal && Floor7Settings.crystalSpawnHud && inMaxor()) {
                val me = Minecraft.getInstance().player
                if (me != null && entity.y == PLACED_Y && FishUtil.getDistanceSq(me, entity) < 36) dropHeld()
            }
            false
        }
    }

    @JvmStatic
    fun display(): Boolean = respawnIn > 0 && Floor7Settings.crystalSpawnHud && inMaxor()

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("MaxorCrystalTimer.2", "crystal respawn hud threw") { FishDraw.drawTimer(component, context, respawnIn, FishConstants.LIGHT_PURPLE) }
    }

    @JvmStatic
    fun displayNotification(): Boolean {
        if (!holding || !Floor7Settings.crystalSpawnHud || !Floor7Settings.crystalPlaceAlert || !inMaxor()) return false
        return Floor7Settings.crystalAlertInstant || heldFor > NAG_AFTER_TICKS
    }

    @JvmStatic
    fun renderNotification(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("MaxorCrystalTimer.3", "crystal nag threw") { FishDraw.drawCenteredText(context, component, Component.literal("§d§lCRYSTAL NOT PLACED")) }
    }
}
