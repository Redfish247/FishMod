package fishmod.features.diana

object DianaMessageHider {

    fun shouldHide(s: String): Boolean {
        if (!DianaSettings.dianaMessageHider) return false
        return when {
            s == "Follow the arrows to find the treasure!" -> DianaSettings.dianaHideArrows
            s == "Warping..." -> DianaSettings.dianaHideWarping
            s == "There are blocks in the way!" -> DianaSettings.dianaHideBlocked
            s.startsWith("This ability is on cooldown for") -> DianaSettings.dianaHideCooldown
            else -> false
        }
    }

    val hideBurrow get() = DianaSettings.dianaMessageHider && DianaSettings.dianaHideBurrow
    val hideDugMob get() = DianaSettings.dianaMessageHider && DianaSettings.dianaHideDugMob
}
