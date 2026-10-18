package fishmod.features.garden

import fishmod.utils.Misc
import net.minecraft.sounds.SoundEvents
import fishmod.features.garden.GardenSettings as S

// Visitor / pest / composter title messages as a HUD, placed and scaled in the HUD editor
object GardenTitles {

    private var lines: List<String> = emptyList()
    private var until = 0L

    fun show(title: String, sub: String, ms: Int, sound: Boolean) {
        lines = listOf(title, sub).filter { it.isNotEmpty() }
        until = System.currentTimeMillis() + ms
        if (sound) Misc.sendSound2D(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f)
    }

    fun init() {
        Garden.hud("Garden Titles", "garden_titles", 160, 24,
            { S.gardenTitleX }, { S.gardenTitleX = it }, { S.gardenTitleY }, { S.gardenTitleY = it },
            { S.gardenTitleScale }, { S.gardenTitleScale = it },
            { System.currentTimeMillis() < until }) { lines }
    }
}
