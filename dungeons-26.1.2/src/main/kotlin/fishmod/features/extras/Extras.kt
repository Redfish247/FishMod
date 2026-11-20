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
        FishDiag.guard("Extras.8", "WikiLookup.init failed") { WikiLookup.init() }
        FishDiag.guard("Extras.9", "CustomTodos.init failed") { CustomTodos.init() }
        FishDiag.guard("Extras.10", "BackpackPreview.init failed") { fishmod.features.storage.BackpackPreview.init() }
        FishDiag.guard("Extras.11", "SkillProgressHud.init failed") { SkillProgressHud.init() }
        FishDiag.guard("Extras.12", "ItemPickupLog.init failed") { ItemPickupLog.init() }
        FishDiag.guard("Extras.13", "CommandTooltip.init failed") { fishmod.features.chat.CommandTooltip.init() }
        FishDiag.guard("Extras.14", "CompactBestiary.init failed") { fishmod.features.chat.CompactBestiary.init() }
        FishDiag.guard("Extras.15", "OrderFilledAlert.init failed") { OrderFilledAlert.init() }
        FishDiag.guard("Extras.16", "SkyblockXpChat.init failed") { SkyblockXpChat.init() }
        FishDiag.guard("Extras.17", "EventHelpers.init failed") { EventHelpers.init() }
    }
}
