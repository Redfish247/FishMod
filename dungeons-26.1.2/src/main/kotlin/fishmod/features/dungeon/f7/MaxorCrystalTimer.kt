package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.FishConstants
import fishmod.utils.SkyblockLocation
import fishmod.utils.FishUtil
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.util.regex.Pattern
import fishmod.utils.debug.FishDiag

object MaxorCrystalTimer {

    private val RELIC_PICK_UP: Pattern = Pattern.compile("(\\w+) picked up an Energy Crystal!$")
    private const val REMINDER_TICK = 240
    private const val TICK_SPAWN = 34

    private var pickedUp = false
    private var tick = 0
    private var tickSincePicked = 0

    @JvmStatic
    fun init() {
        FishEvents.ON_GAME_MESSAGE.register { text ->
            if (!SkyblockLocation.inDungeon() || !BossPhase.inP1()
                || !Floor7Settings.crystalSpawnHud
            ) {
                return@register false
            }
            val string = text.string
            if (string == "[BOSS] Maxor: THAT BEAM! IT HURTS! IT HURTS!!"
                || string == "[BOSS] Maxor: YOU TRICKED ME!"
            ) {
                tick = TICK_SPAWN
                return@register false
            }
            val matcher = RELIC_PICK_UP.matcher(string)
            if (matcher.find()) {
                if (EntityTools.isClientPlayer(matcher.group(1))) pickedUp = true
            } else if (string.contains("picked up an Energy Crystal")) {
                FishDiag.fail("CrystalSpawn.1", "crystal pickup line didn't match: '$string'")
            }
            false
        }
        FishEvents.ON_SERVER_TICK.register {
            tick = maxOf(tick - 1, 0)
            if (pickedUp) tickSincePicked++
            if (tickSincePicked == 20 * 600) FishDiag.fail("CrystalSpawn.4", "crystal pickup never cleared after 10 minutes")
            false
        }
        FishEvents.ON_LOCATION_CHANGE.register { _ ->
            tick = 0
            tickSincePicked = 0
            pickedUp = false
            false
        }
        FishEvents.ON_ENTITY_SPAWNED.register { entity, world ->
            if (!pickedUp || !SkyblockLocation.inDungeon() || !Floor7Settings.crystalSpawnHud || !BossPhase.inP1()) {
                return@register false
            }
            if (entity is EndCrystal) {
                val player = Minecraft.getInstance().player
                if (player == null) return@register false
                if (FishUtil.getDistanceSq(player, entity) < 36 && entity.y == 224.375) {
                    pickedUp = false
                    tickSincePicked = 0
                }
            }
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return tick > 0 && SkyblockLocation.inDungeon() && BossPhase.inP1() && Floor7Settings.crystalSpawnHud
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("CrystalSpawn.2", "crystal spawn timer render threw") { FishDraw.drawTimer(component, context, tick, FishConstants.LIGHT_PURPLE) }
    }

    @JvmStatic
    fun displayNotification(): Boolean {
        return (Floor7Settings.crystalAlertInstant || tickSincePicked > REMINDER_TICK)
                && SkyblockLocation.inDungeon() && BossPhase.inP1() && Floor7Settings.crystalSpawnHud && Floor7Settings.crystalPlaceAlert && pickedUp
    }

    @JvmStatic
    fun renderNotification(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("CrystalSpawn.3", "crystal reminder render threw") { FishDraw.drawCenteredText(context, component, Component.literal("§bPlace Crystal!")) }
    }
}
