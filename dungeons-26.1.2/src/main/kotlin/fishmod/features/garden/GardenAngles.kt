package fishmod.features.garden

import net.minecraft.client.Minecraft
import fishmod.features.garden.GardenExtraSettings as X

object GardenAngles {

    private fun fmt(v: Float) = String.format("%.${X.gardenAnglesDecimals.coerceIn(0, 4)}f", v)

    // Yaw wrapped to -180..180 like F3
    private fun lines(yaw: Float, pitch: Float): List<String> {
        val c = Garden.legacy(X.gardenAnglesColor)
        return listOf("${c}Yaw §f${fmt(net.minecraft.util.Mth.wrapDegrees(yaw))}", "${c}Pitch §f${fmt(pitch)}")
    }

    fun init() {
        Garden.hud("Yaw / Pitch", "garden_angles", 80, 20,
            { X.gardenAnglesHudX }, { X.gardenAnglesHudX = it }, { X.gardenAnglesHudY }, { X.gardenAnglesHudY = it },
            { X.gardenAnglesHudScale }, { X.gardenAnglesHudScale = it },
            { X.gardenAngles && Garden.inGarden() && Minecraft.getInstance().player != null }, { X.gardenAngles },
            example = { lines(90f, 2.8f) }) {
            val p = Minecraft.getInstance().player
            if (p == null) emptyList() else lines(p.yRot, p.xRot)
        }
    }
}
