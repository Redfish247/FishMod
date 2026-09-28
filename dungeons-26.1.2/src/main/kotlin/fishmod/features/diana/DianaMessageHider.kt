package fishmod.features.diana

// Diana chat spam SBO hides; burrow/mob dug lines are hidden by DianaTracker as it parses them
object DianaMessageHider {

    private val EXACT = setOf("Follow the arrows to find the treasure!", "Warping...", "There are blocks in the way!")

    fun shouldHide(s: String): Boolean =
        DianaSettings.dianaMessageHider && (s in EXACT || s.startsWith("This ability is on cooldown for"))
}
