package fishmod.features.extras

import fishmod.features.alerts.BitsWarning
import fishmod.features.alerts.LimboAlert
import fishmod.features.alerts.MiscChatAlerts
import fishmod.features.alerts.OrderFilledAlert
import fishmod.features.alerts.PartyJoinSound
import fishmod.features.alerts.VanquisherShare
import fishmod.features.chat.CommandTooltip
import fishmod.features.chat.CompactBestiary
import fishmod.features.chat.PatcherWaypoints
import fishmod.features.chat.SkyblockXpChat
import fishmod.features.chat.WikiLookup
import fishmod.features.events.CarnivalShootout
import fishmod.features.events.EventHelpers
import fishmod.features.events.FairySouls
import fishmod.features.events.HoppityEggWaypoints
import fishmod.features.events.JerryHighlight
import fishmod.features.events.StrayRabbitAlert
import fishmod.features.inventory.AccessoryHelper
import fishmod.features.inventory.BackpackPreview
import fishmod.features.inventory.PersonalCompactorTooltip
import fishmod.features.inventory.StackingEnchantTooltip

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
        FishDiag.guard("Extras.7", "VanquisherShare.init failed") { fishmod.features.alerts.VanquisherShare.init() }
        FishDiag.guard("Extras.8", "WikiLookup.init failed") { WikiLookup.init() }
        FishDiag.guard("Extras.9", "CustomTodos.init failed") { CustomTodos.init() }
        FishDiag.guard("Extras.10", "BackpackPreview.init failed") { fishmod.features.inventory.BackpackPreview.init() }
        FishDiag.guard("Extras.11", "SkillProgressHud.init failed") { SkillProgressHud.init() }
        FishDiag.guard("Extras.12", "ItemPickupLog.init failed") { ItemPickupLog.init() }
        FishDiag.guard("Extras.13", "CommandTooltip.init failed") { fishmod.features.chat.CommandTooltip.init() }
        FishDiag.guard("Extras.14", "CompactBestiary.init failed") { fishmod.features.chat.CompactBestiary.init() }
        FishDiag.guard("Extras.15", "OrderFilledAlert.init failed") { OrderFilledAlert.init() }
        FishDiag.guard("Extras.16", "SkyblockXpChat.init failed") { SkyblockXpChat.init() }
        FishDiag.guard("Extras.17", "EventHelpers.init failed") { EventHelpers.init() }
        FishDiag.guard("Extras.18", "MiscChatAlerts.init failed") { MiscChatAlerts.init() }
        FishDiag.guard("Extras.19", "TrevorHelper.init failed") { TrevorHelper.init() }
        FishDiag.guard("Extras.20", "GiftTracker.init failed") { GiftTracker.init() }
        FishDiag.guard("Extras.21", "TeleportPadNames.init failed") { TeleportPadNames.init() }
        FishDiag.guard("Extras.22", "HoppityEggWaypoints.init failed") { HoppityEggWaypoints.init() }
        FishDiag.guard("Extras.23", "PersonalCompactorTooltip.init failed") { PersonalCompactorTooltip.init() }
        FishDiag.guard("Extras.24", "StackingEnchantTooltip.init failed") { StackingEnchantTooltip.init() }
        FishDiag.guard("Extras.25", "JerryHighlight.init failed") { JerryHighlight.init() }
        FishDiag.guard("Extras.26", "CarnivalShootout.init failed") { CarnivalShootout.init() }
        FishDiag.guard("Extras.27", "StrayRabbitAlert.init failed") { StrayRabbitAlert.init() }
        FishDiag.guard("Extras.28", "AccessoryHelper.init failed") { AccessoryHelper.init() }
        FishDiag.guard("Extras.40", "BitsWarning.init failed") { BitsWarning.init() }
        FishDiag.guard("Extras.41", "BazaarSearch.init failed") { BazaarSearch.init() }
        FishDiag.guard("Extras.29", "HotxHelper.init failed") { fishmod.features.hunting.HotxHelper.init() }
        FishDiag.guard("Extras.30", "BeaconSolver.init failed") { fishmod.features.hunting.BeaconSolver.init() }
    }
}
