package fishmod.features.dungeon.map

import fishmod.utils.Constants
import net.minecraft.resources.Identifier

/** Bundled dungeon-map textures under assets/fishmod/map/. */
object MapTextures {
    @JvmField val SELF_MARKER: Identifier = id("map/self_marker.png")
    @JvmField val CROSS: Identifier = id("map/cross.png")
    @JvmField val GREEN_CHECK: Identifier = id("map/green_check.png")
    @JvmField val WHITE_CHECK: Identifier = id("map/white_check.png")
    @JvmField val PRINCE_CROWN: Identifier = id("map/prince_crown_ziyno.png")
    // no question-mark asset exists; reuse WHITE_CHECK as the unopened-room placeholder icon
    @JvmField val QUESTION: Identifier = id("map/white_check.png")

    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(Constants.NAMESPACE, path)
}
