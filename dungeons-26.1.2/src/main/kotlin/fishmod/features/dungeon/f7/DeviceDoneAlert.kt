package fishmod.features.dungeon.f7

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.TickScheduler
import fishmod.utils.config.values.Floor7Settings
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.AABB

// Pings when your own device finishes at Simon Says or the 4th device, and can mute P3 titles while you stand there.
object DeviceDoneAlert {

    // SS counts within 3 blocks of the box centre
    private val SIMON_SAYS = AABB(105.0, 116.0, 91.0, 111.0, 122.0, 97.0)
    private val FOURTH_DEVICE = AABB(63.0, 127.0, 35.0, 64.0, 127.0, 36.0)
    private const val SHOW_MS = 1500L
    private val KEEP_TITLES = setOf("§eYou became a ghost!", "§7Hopefully your teammates will be able to revive you!", "§e§lBEING REVIVED")
    private val REVIVE_TITLE = Regex("§aYou will be revived in \\ds")

    private var shownAt = 0L

    @JvmStatic
    fun init() {
        FishEvents.terminalStep.register { who, _, kind, _, _ ->
            if (kind == "device" && EntityTools.isClientPlayer(who)) {
                FishDiag.guard("DeviceDoneAlert.1", "device done handler threw") {
                    val wanted = (Floor7Settings.pre4DoneAlert && at4thDev()) || (Floor7Settings.ssDoneAlert && atSS())
                    if (wanted) {
                        shownAt = System.currentTimeMillis()
                        TickScheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)
                    }
                }
            }
            false
        }
    }

    @JvmStatic
    fun atSS(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return p.position().distanceToSqr(SIMON_SAYS.center) <= 9.0
    }

    @JvmStatic
    fun at4thDev(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return p.x in FOURTH_DEVICE.minX..FOURTH_DEVICE.maxX && p.y == FOURTH_DEVICE.minY && p.z in FOURTH_DEVICE.minZ..FOURTH_DEVICE.maxZ
    }

    @JvmStatic
    fun disableTitles(title: Component): Boolean {
        if (!BossPhase.inTerminals()) return false
        val here = (Floor7Settings.muteTitlesPre4 && at4thDev()) || (Floor7Settings.muteTitlesSS && atSS())
        if (!here) return false
        val s = title.string
        return s !in KEEP_TITLES && !REVIVE_TITLE.matches(s)
    }

    @JvmStatic
    fun display(): Boolean = shownAt != 0L && System.currentTimeMillis() - shownAt < SHOW_MS

    @JvmStatic
    fun render(component: HUDComponent, context: GuiGraphicsExtractor) {
        FishDiag.guard("DeviceDoneAlert.2", "device done banner threw") { FishDraw.drawCenteredText(context, component, Component.literal("§a§lDEVICE DONE")) }
    }
}
