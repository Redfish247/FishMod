package fishmod.cosmetic

interface ScaleHolder {
    fun `fishmod$getScaleX`(): Float
    fun `fishmod$getScaleY`(): Float
    fun `fishmod$getScaleZ`(): Float
    fun `fishmod$setScale`(x: Float, y: Float, z: Float)
}
