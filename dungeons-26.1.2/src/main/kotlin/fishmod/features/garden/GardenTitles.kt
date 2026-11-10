package fishmod.features.garden

import fishmod.utils.FishUtil
import net.minecraft.sounds.SoundEvents
import fishmod.features.garden.GardenSettings as S

// Visitor / pest / composter title messages as a HUD, placed and scaled in the HUD editor
object GardenTitles {

    private var lines: List<String> = emptyList()
    private var until = 0L

    fun show(title: String, sub: String, ms: Int, sound: Boolean) {
        lines = listOf(title, sub).filter { it.isNotEmpty() }
        until = System.currentTimeMillis() + ms
        if (sound) FishUtil.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    // Title texts, shared by the callers and the HUD editor example
    fun visitorFull() = Garden.legacy(S.gardenVisitorFullColor) + "Visitor Queue Full!"
    fun visitorArrived(name: String) = S.gardenVisitorArrivalText.replace("{name}", name)
    fun pest(plot: String) = S.gardenPestTitleText.replace("{plot}", plot)
    fun composterIdle() = Garden.legacy(S.gardenComposterWarnColor) + "Composter Inactive!"

    private fun visitorFullOn() = S.gardenVisitorSixthWarn && S.gardenVisitorTimer

    // First title the player has turned on
    private fun example(): List<String> = listOf(when {
        visitorFullOn() -> visitorFull()
        S.gardenVisitorArrivalTitle -> visitorArrived("Example Visitor")
        S.gardenPestTitle -> pest("3")
        else -> composterIdle()
    })

    fun init() {
        Garden.hud("Garden Titles", "garden_titles", 160, 24,
            { S.gardenTitleX }, { S.gardenTitleX = it }, { S.gardenTitleY }, { S.gardenTitleY = it },
            { S.gardenTitleScale }, { S.gardenTitleScale = it },
            { System.currentTimeMillis() < until },
            { visitorFullOn() || S.gardenVisitorArrivalTitle || S.gardenPestTitle || S.gardenComposterWarn },
            example = ::example) { lines }
    }
}
