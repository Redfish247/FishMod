package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.Vec3
import fishmod.utils.debug.FishDiag

object DeviceDoneAlert {
    private val REVIVE = Regex("§aYou will be revived in \\ds")

    private const val TOTAL_DURATION = 1500L

    private val SS_POSITION = Vec3(108.0, 119.0, 94.0)
    private const val DISTANCE = 3.0

    private var completedTime = 0L
    private var showNotification = false

    @JvmStatic
    fun init() {
        FishEvents.ON_TERMINAL.register { name, _, objective, _, _ ->
            if (objective != "device" || !EntityTools.isClientPlayer(name)) return@register false

            try {
                if ((Floor7Settings.notifyPre4Completion && at4thDev()) || (Floor7Settings.notifySSCompletion && atSS())) {
                    completedTime = System.currentTimeMillis()
                    showNotification = true
                    TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
                }
            } catch (e: Exception) { FishDiag.fail("DeviceNotifier.1", "device notifier terminal handler threw", e) }
            false
        }
    }

    @JvmStatic
    fun atSS(): Boolean {
        val player: LocalPlayer = Minecraft.getInstance().player ?: return false
        return SS_POSITION.distanceTo(player.position()) <= DISTANCE
    }

    @JvmStatic
    fun at4thDev(): Boolean {
        val player: LocalPlayer = Minecraft.getInstance().player ?: return false
        return player.x >= 63 && player.x <= 64 && player.y == 127.0 && player.z >= 35 && player.z <= 36
    }

    @JvmStatic
    fun disableTitles(title: Component): Boolean {
        if (!(((Floor7Settings.disableTitlesAtPre4 && at4thDev()) || (Floor7Settings.disableTitlesAtSS && atSS())) && BossPhase.inTerminals()))
            return false

        val string = title.string

        if (string == "§eYou became a ghost!" || string == "§7Hopefully your teammates will be able to revive you!" || string == "§e§lBEING REVIVED") {
            return false
        }

        return !string.matches(REVIVE)
    }

    @JvmStatic
    fun display(): Boolean {
        return showNotification
    }

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        if (System.currentTimeMillis() - completedTime >= TOTAL_DURATION) showNotification = false
        FishDiag.guard("DeviceNotifier.2", "device notifier render threw") { FishDraw.drawCenteredText(context, component, Component.literal("§aDevice Completed!")) }
    }
}
