package fishmod.features.extras

import fishmod.utils.debug.FishDiag

object Extras {
    @JvmStatic
    fun init() {
        FishDiag.guard("Extras.1", "ArrowHud.init failed") { ArrowHud.init() }
        FishDiag.guard("Extras.2", "LimboAlert.init failed") { LimboAlert.init() }
        FishDiag.guard("Extras.3", "PartyJoinSound.init failed") { PartyJoinSound.init() }
        FishDiag.guard("Extras.4", "PatcherWaypoints.init failed") { PatcherWaypoints.init() }
        FishDiag.guard("Extras.5", "FairySouls.init failed") { FairySouls.init() }
        FishDiag.guard("Extras.6", "MiscReminders.init failed") { MiscReminders.init() }
        FishDiag.guard("Extras.7", "VanquisherShare.init failed") { fishmod.features.crimson.VanquisherShare.init() }
    }
}
