package fishmod.features.kuudra

import fishmod.shaded.practicalconfig.manager.ConfigValue

object KuudraSettings {

    @ConfigValue @JvmField var kuudraSupplyMaster: Boolean = true
    @ConfigValue @JvmField var kuudraBuildMaster: Boolean = true
    @ConfigValue @JvmField var kuudraBossMaster: Boolean = true
    @ConfigValue @JvmField var kuudraChestsMaster: Boolean = true

    // Splits
    @ConfigValue @JvmField var kuudraSplits: Boolean = false
    @ConfigValue @JvmField var kuudraSplitsChat: Boolean = true
    @ConfigValue @JvmField var kuudraPbs: String = ""
    @ConfigValue @JvmField var kuudraSplitsX: Int = 10
    @ConfigValue @JvmField var kuudraSplitsY: Int = 120
    @ConfigValue @JvmField var kuudraSplitsScale: Double = 1.0

    // Alerts
    @ConfigValue @JvmField var kuudraAlerts: Boolean = false
    @ConfigValue @JvmField var kuudraAlertPhase: Boolean = true
    @ConfigValue @JvmField var kuudraAlertSupplies: Boolean = true
    @ConfigValue @JvmField var kuudraAlertNoPre: Boolean = true
    @ConfigValue @JvmField var kuudraAlertStunPct: Int = 93
    @ConfigValue @JvmField var kuudraAlertSide: Boolean = true
    @ConfigValue @JvmField var kuudraAlertSound: Boolean = true

    // Supplies
    @ConfigValue @JvmField var kuudraSupplyTimes: Boolean = false
    @ConfigValue @JvmField var kuudraSupplyHideOriginal: Boolean = false
    @ConfigValue @JvmField var kuudraSupplyParty: Boolean = false
    @ConfigValue @JvmField var kuudraSupplyMessage: String = "Supply {n}/{total} recovered"
    @ConfigValue @JvmField var kuudraSupplyHud: Boolean = false
    @ConfigValue @JvmField var kuudraSupplyHudX: Int = 10
    @ConfigValue @JvmField var kuudraSupplyHudY: Int = 190
    @ConfigValue @JvmField var kuudraSupplyHudScale: Double = 1.0
    @ConfigValue @JvmField var kuudraSupplyWaypoints: Boolean = false
    @ConfigValue @JvmField var kuudraSupplyColor: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var kuudraDropWaypoints: Boolean = false
    @ConfigValue @JvmField var kuudraGiantBox: Boolean = false
    @ConfigValue @JvmField var kuudraPreAreaLabels: Boolean = false
    @ConfigValue @JvmField var kuudraNoPre: Boolean = false
    @ConfigValue @JvmField var kuudraNoPreParty: Boolean = false
    @ConfigValue @JvmField var kuudraCratePriority: Boolean = false
    @ConfigValue @JvmField var kuudraCratePriorityAdvanced: Boolean = false
    @ConfigValue @JvmField var kuudraPearlWaypoints: Boolean = false
    @ConfigValue @JvmField var kuudraPearlHideFar: Boolean = true

    // Build
    @ConfigValue @JvmField var kuudraBuildHud: Boolean = false
    @ConfigValue @JvmField var kuudraBuildHudX: Int = 10
    @ConfigValue @JvmField var kuudraBuildHudY: Int = 230
    @ConfigValue @JvmField var kuudraBuildHudScale: Double = 1.0
    @ConfigValue @JvmField var kuudraPileWaypoints: Boolean = false
    @ConfigValue @JvmField var kuudraPileColor: Int = 0xFFAA0000.toInt()
    @ConfigValue @JvmField var kuudraBallistaText: Boolean = false
    @ConfigValue @JvmField var kuudraFreshParty: Boolean = false
    @ConfigValue @JvmField var kuudraFreshMessage: String = "FRESH"
    @ConfigValue @JvmField var kuudraFreshHud: Boolean = false
    @ConfigValue @JvmField var kuudraFreshHudX: Int = 10
    @ConfigValue @JvmField var kuudraFreshHudY: Int = 270
    @ConfigValue @JvmField var kuudraFreshHudScale: Double = 1.0

    // Boss
    @ConfigValue @JvmField var kuudraHighlight: Boolean = false
    @ConfigValue @JvmField var kuudraHighlightColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var kuudraHpWorld: Boolean = false
    @ConfigValue @JvmField var kuudraHpHud: Boolean = false
    @ConfigValue @JvmField var kuudraHpScaled: Boolean = true
    @ConfigValue @JvmField var kuudraHpHudX: Int = 10
    @ConfigValue @JvmField var kuudraHpHudY: Int = 100
    @ConfigValue @JvmField var kuudraHpHudScale: Double = 1.0
    @ConfigValue @JvmField var kuudraTeamHighlight: Boolean = false
    @ConfigValue @JvmField var kuudraTeamColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var kuudraElleHighlight: Boolean = false
    @ConfigValue @JvmField var kuudraElleColor: Int = 0xFFFF55FF.toInt()

    // Chests
    @ConfigValue @JvmField var kuudraChestValue: Boolean = false
    @ConfigValue @JvmField var kuudraChestValueX: Int = 10
    @ConfigValue @JvmField var kuudraChestValueY: Int = 40
    @ConfigValue @JvmField var kuudraChestValueScale: Double = 1.0
    @ConfigValue @JvmField var kuudraOpenedHighlight: Boolean = false
    @ConfigValue @JvmField var kuudraProfit: Boolean = false
    @ConfigValue @JvmField var kuudraProfitTotal: Double = 0.0
    @ConfigValue @JvmField var kuudraProfitChests: Int = 0
    @ConfigValue @JvmField var kuudraProfitMode: String = "Session"
    @ConfigValue @JvmField var kuudraProfitRows: Int = 10
    @ConfigValue @JvmField var kuudraProfitX: Int = 10
    @ConfigValue @JvmField var kuudraProfitY: Int = 300
    @ConfigValue @JvmField var kuudraProfitScale: Double = 1.0
    @ConfigValue @JvmField var kuudraChestCounter: Boolean = false
    @ConfigValue @JvmField var kuudraChestCount: Int = 0
    @ConfigValue @JvmField var kuudraChestCap: Int = 60
    @ConfigValue @JvmField var kuudraChestCapParty: Boolean = false
    @ConfigValue @JvmField var kuudraChestCounterX: Int = 10
    @ConfigValue @JvmField var kuudraChestCounterY: Int = 330
    @ConfigValue @JvmField var kuudraChestCounterScale: Double = 1.0

    // Extras
    @ConfigValue @JvmField var kuudraAnnounce: Boolean = false
    @ConfigValue @JvmField var kuudraAnnounceSpirit: Boolean = true
    @ConfigValue @JvmField var kuudraAnnounceHollowed: Boolean = true
    @ConfigValue @JvmField var kuudraAnnounceWind: Boolean = true
    @ConfigValue @JvmField var kuudraAnnounceIchor: Boolean = true
    @ConfigValue @JvmField var kuudraIchorArea: Boolean = false
    @ConfigValue @JvmField var kuudraIchorColor: Int = 0xD260EEFF.toInt()
    @ConfigValue @JvmField var kuudraRendAlert: Boolean = false
    @ConfigValue @JvmField var kuudraBackbone: Boolean = false
    @ConfigValue @JvmField var kuudraBackboneSound: Boolean = true
    @ConfigValue @JvmField var kuudraBackboneAdvance: Int = 0
    @ConfigValue @JvmField var kuudraBackboneX: Int = 10
    @ConfigValue @JvmField var kuudraBackboneY: Int = 360
    @ConfigValue @JvmField var kuudraBackboneScale: Double = 1.0
    @ConfigValue @JvmField var kuudraStunWaypoints: Boolean = false
    @ConfigValue @JvmField var kuudraStunPod: String = "Left Pod"
    @ConfigValue @JvmField var kuudraStunStyle: String = "Outline"
    @ConfigValue @JvmField var kuudraStunColor: Int = 0xC800F5FF.toInt()

    // Perks
    @ConfigValue @JvmField var kuudraBlockPerks: Boolean = false
    @ConfigValue @JvmField var kuudraBlockPerksList: String = "Steady Hands, Bomberman, Mining Frenzy, Elle's Lava Rod, Elle's Pickaxe, Auto Revive"
}
