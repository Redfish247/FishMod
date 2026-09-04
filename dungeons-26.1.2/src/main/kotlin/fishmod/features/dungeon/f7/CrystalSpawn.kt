package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Constants
import fishmod.utils.Location
import fishmod.utils.Misc
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import java.util.regex.Pattern

/**
 * Maxor crystal spawn countdown + "place crystal" reminder. Crystal-placed detection dismisses
 * the reminder when you place it.
 */
object CrystalSpawn {

    private val RELIC_PICK_UP: Pattern = Pattern.compile("(\\w+) picked up an Energy Crystal!$")
    private const val REMINDER_TICK = 240
    private const val TICK_SPAWN = 34

    private var pickedUp = false
    private var tick = 0
    private var tickSincePicked = 0

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            if (!Location.inDungeon() || !Phase.inP1()
                || (!Floor7.enableCrystalSpawnTime && !Floor7.crystalPlaceReminder)
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
            if (matcher.find() && EntityUtil.isClientPlayer(matcher.group(1))) {
                pickedUp = true
            }
            false
        }
        Events.ON_SERVER_TICK.register {
            tick = maxOf(tick - 1, 0)
            if (pickedUp) tickSincePicked++
            false
        }
        Events.ON_LOCATION_CHANGE.register { newLocation ->
            tick = 0
            tickSincePicked = 0
            pickedUp = false
            false
        }
        Events.ON_ENTITY_SPAWNED.register { entity, world ->
            if (!pickedUp || !Location.inDungeon() || !Floor7.enableCrystalSpawnTime || !Phase.inP1()) {
                return@register false
            }
            if (entity is EndCrystal) {
                val player = Minecraft.getInstance().player
                if (player == null) return@register false
                if (Misc.getDistanceSq(player, entity) < 36 && entity.y == 224.375) {
                    pickedUp = false
                    tickSincePicked = 0
                }
            }
            false
        }
    }

    @JvmStatic
    fun display(): Boolean {
        return tick > 0 && Location.inDungeon() && Phase.inP1() && Floor7.enableCrystalSpawnTime
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawTimer(component, context, tick, Constants.LIGHT_PURPLE)
    }

    @JvmStatic
    fun displayNotification(): Boolean {
        return (Floor7.instantlyDisplayCrystalReminder || tickSincePicked > REMINDER_TICK)
                && Location.inDungeon() && Phase.inP1() && Floor7.crystalPlaceReminder && pickedUp
    }

    @JvmStatic
    fun renderNotification(component: HUDComponent, context: GuiGraphicsExtractor) {
        RenderUtils.drawCenteredText(context, component, Component.literal("§bPlace Crystal!"))
    }
}
