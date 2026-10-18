package fishmod.features.extras

import fishmod.utils.debug.FishDiag

object Extras {
    @JvmStatic
    fun init() {
        FishDiag.guard("Extras.1", "ArrowHud.init failed") { ArrowHud.init() }
        FishDiag.guard("Extras.2", "LimboAlert.init failed") { LimboAlert.init() }
        FishDiag.guard("Extras.3", "PartyJoinSound.init failed") { PartyJoinSound.init() }
        FishDiag.guard("Extras.4", "PatcherWaypoints.init failed") { PatcherWaypoints.init() }
    }
}
