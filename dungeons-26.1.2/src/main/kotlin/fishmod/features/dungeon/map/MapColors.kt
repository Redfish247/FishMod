package fishmod.features.dungeon.map

import com.mojang.blaze3d.platform.InputConstants
import fishmod.utils.Addons
import fishmod.utils.Keybinds
import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

/** Color math + "legit mode" gating for the dungeon map feature. */
object MapColors {

    /** Held to temporarily peek behind legit-mode blur/hiding, regardless of [DungeonMapSettings.mapInsightLegit]. */
    private var mapInsightKey: KeyMapping? = null

    @JvmStatic
    fun init() {
        mapInsightKey = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "FishMod: Map insight (peek through legit mode)",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                Keybinds.category()
            )
        )
    }

    @JvmStatic
    fun darker(argb: Int, mult: Float): Int {
        val a = argb ushr 24 and 255
        val r = ((argb shr 16 and 255) * mult).toInt().coerceAtLeast(0)
        val g = ((argb shr 8 and 255) * mult).toInt().coerceAtLeast(0)
        val b = ((argb and 255) * mult).toInt().coerceAtLeast(0)
        return a shl 24 or (r shl 16) or (g shl 8) or b
    }

    /** Non-legit mode is a FishModAddons-only option; without it, the map is always legit. */
    @JvmStatic
    fun legit(): Boolean {
        if (!Addons.fishModAddonsInstalled) return true
        return DungeonMapSettings.mapLegitMode && (!peeking() || DungeonMapSettings.mapInsightLegit)
    }

    @JvmStatic
    fun peeking(): Boolean = mapInsightKey?.isDown == true

    @JvmStatic
    fun darkenMultiplier(): Float = DungeonMapSettings.mapDarkenMultiplier

    @JvmStatic
    fun roomColor(type: Room.Type): Int = when (type) {
        Room.Type.BLOOD -> DungeonMapSettings.mapBloodRoomColor
        Room.Type.NORMAL -> DungeonMapSettings.mapNormalRoomColor
        Room.Type.PUZZLE -> DungeonMapSettings.mapPuzzleRoomColor
        Room.Type.CHAMPION -> DungeonMapSettings.mapChampionRoomColor
        Room.Type.TRAP -> DungeonMapSettings.mapTrapRoomColor
        Room.Type.ENTRANCE -> DungeonMapSettings.mapEntranceRoomColor
        Room.Type.FAIRY -> DungeonMapSettings.mapFairyRoomColor
        Room.Type.RARE -> DungeonMapSettings.mapRareRoomColor
        else -> DungeonMapSettings.mapUnopenedRoomColor
    }
}
