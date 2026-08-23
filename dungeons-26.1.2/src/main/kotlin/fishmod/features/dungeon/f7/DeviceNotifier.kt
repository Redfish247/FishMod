package fishmod.features.dungeon.f7

import config.practical.hud.HUDComponent
import fishmod.utils.Scheduler
import fishmod.utils.config.values.Floor7
import fishmod.utils.data.EntityUtil
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * "Device Completed!" notification at the pre-4th-device / Simon Says (SS) spots, plus the
 * title-suppression logic for those same spots. Ported from blade-addons.
 */
object DeviceNotifier {

    private const val TOTAL_DURATION = 1500L

    private val SS_POSITION = Vec3(108.0, 119.0, 94.0)
    private const val DISTANCE = 3.0

    private var completedTime = 0L
    private var showNotification = false

    @JvmStatic
    fun init() {
        Events.ON_TERMINAL.register { name, _, objective, _, _ ->
            if (objective != "device" || !EntityUtil.isClientPlayer(name)) return@register false

            if ((Floor7.notifyPre4Completion && at4thDev()) || (Floor7.notifySSCompletion && atSS())) {
                completedTime = System.currentTimeMillis()
                showNotification = true
                Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
            }
            false
        }
    }

    @JvmStatic
    fun atSS(): Boolean {
        val player: LocalPlayer = Minecraft.getInstance().player ?: return false
        return SS_POSITION.distanceTo(player.position()) <= DISTANCE
    }

    @JvmStatic
    fun atSS(player: Player): Boolean {
        return SS_POSITION.distanceTo(player.position()) <= DISTANCE
    }

    @JvmStatic
    fun at4thDev(): Boolean {
        val player: LocalPlayer = Minecraft.getInstance().player ?: return false
        return player.x >= 63 && player.x <= 64 && player.y == 127.0 && player.z >= 35 && player.z <= 36
    }

    @JvmStatic
    fun disableTitles(title: Component): Boolean {
        if (!(((Floor7.disableTitlesAtPre4 && at4thDev()) || (Floor7.disableTitlesAtSS && atSS())) && Phase.inTerminals()))
            return false

        val string = title.string

        if (string == "§eYou became a ghost!" || string == "§7Hopefully your teammates will be able to revive you!" || string == "§e§lBEING REVIVED") {
            return false
        }

        return !string.matches(Regex("§aYou will be revived in \\ds"))
    }

    @JvmStatic
    fun display(): Boolean {
        return showNotification
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        if (System.currentTimeMillis() - completedTime >= TOTAL_DURATION) showNotification = false
        RenderUtils.drawCenteredText(context, component, Component.literal("§aDevice Completed!"))
    }
}
