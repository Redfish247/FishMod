package fishmod.features.kuudra

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.ColorPickerSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.InputSetting
import fishmod.features.FishModScreen.SliderDoubleSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.kuudra.KuudraSettings as S

// The Kuudra tab in /fm
object KuudraMenu {

    fun column(): Column {
        val col = Column("Kuudra")

        val rq = Feature("Auto Requeue", S::class.java, "kuudraRequeue")
        rq.sub.add(SliderDoubleSetting("Delay (s)", "Wait after the run ends, e.g. to open your chests",
            { S.kuudraRequeueDelayMs / 1000.0 }, { v -> S.kuudraRequeueDelayMs = (v * 1000).toInt() }, 0.0, 30.0))
        col.features.add(rq)

        val splits = Feature("Kuudra Splits", S::class.java, "kuudraSplits")
        splits.sub.add(ToggleSetting("Chat Splits", "Print each phase time and PB", S::class.java, "kuudraSplitsChat"))
        splits.sub.add(ButtonSetting("Reset PBs", "", "Reset") { S.kuudraPbs = "" })
        col.features.add(splits)

        val alerts = Feature("Kuudra Alerts", S::class.java, "kuudraAlerts")
        alerts.sub.add(ToggleSetting("Phase Change", "Build start, ballista built, kill phase", S::class.java, "kuudraAlertPhase"))
        alerts.sub.add(ToggleSetting("Supplies Done", "Title at 6/6", S::class.java, "kuudraAlertSupplies"))
        alerts.sub.add(ToggleSetting("No Pre Called", "Title when a party member calls a missing crate", S::class.java, "kuudraAlertNoPre"))
        alerts.sub.add(SliderIntSetting("Go To Stun At %", "Build % for the stun reminder (T3+), 0 = off", S::class.java, "kuudraAlertStunPct", 0, 100))
        alerts.sub.add(ToggleSetting("Spawn Side", "Kuudra's side in the kill phase", S::class.java, "kuudraAlertSide"))
        alerts.sub.add(ToggleSetting("Sound", "", S::class.java, "kuudraAlertSound"))
        col.features.add(alerts)

        val sup = Feature("Supply Helper", S::class.java, "kuudraSupplyMaster")
        sup.sub.add(ToggleSetting("Supply Times", "Who picked up a supply and how fast", S::class.java, "kuudraSupplyTimes"))
        sup.sub.add(ToggleSetting("Hide Original Message", "", S::class.java, "kuudraSupplyHideOriginal").gatedBy { S.kuudraSupplyTimes })
        sup.sub.add(ToggleSetting("Recover Party Message", "One party message when you recover a supply", S::class.java, "kuudraSupplyParty"))
        sup.sub.add(InputSetting("Recover Message", "{n} = count, {total} = needed", S::class.java, "kuudraSupplyMessage").gatedBy { S.kuudraSupplyParty })
        sup.sub.add(ToggleSetting("Supply Progress HUD", "", S::class.java, "kuudraSupplyHud"))
        sup.sub.add(ToggleSetting("Supply Waypoints", "Beam on every crate", S::class.java, "kuudraSupplyWaypoints"))
        sup.sub.add(ColorPickerSetting("Supply Color", "", S::class.java, "kuudraSupplyColor"))
        sup.sub.add(ToggleSetting("Giant Hitbox", "Outline the supply giants", S::class.java, "kuudraGiantBox"))
        sup.sub.add(ToggleSetting("Drop-off Waypoints", "Where to place supplies (green = called missing)", S::class.java, "kuudraDropWaypoints"))
        sup.sub.add(ToggleSetting("Pre-spot Labels", "Name each pre spot", S::class.java, "kuudraPreAreaLabels"))
        sup.sub.add(ToggleSetting("Pearl Waypoints", "Pearl lineups for the closest spot", S::class.java, "kuudraPearlWaypoints"))
        sup.sub.add(ToggleSetting("Closest Lineup Only", "", S::class.java, "kuudraPearlHideFar").gatedBy { S.kuudraPearlWaypoints })
        col.features.add(sup)

        val pre = Feature("Pre-spot Alert", S::class.java, "kuudraNoPre")
        pre.sub.add(ToggleSetting("Party Message", "Send \"No <spot>!\" once when your pre or second crate is missing", S::class.java, "kuudraNoPreParty"))
        pre.sub.add(ToggleSetting("Crate Priority", "Where to go when someone calls a missing crate", S::class.java, "kuudraCratePriority"))
        pre.sub.add(ToggleSetting("Advanced Priority", "", S::class.java, "kuudraCratePriorityAdvanced").gatedBy { S.kuudraCratePriority })
        col.features.add(pre)

        val build = Feature("Build Helper", S::class.java, "kuudraBuildMaster")
        build.sub.add(ToggleSetting("Build HUD", "Build %, builders, freshers", S::class.java, "kuudraBuildHud"))
        build.sub.add(ToggleSetting("Pile Waypoints", "Beam + progress on unfinished piles", S::class.java, "kuudraPileWaypoints"))
        build.sub.add(ColorPickerSetting("Pile Color", "", S::class.java, "kuudraPileColor").gatedBy { S.kuudraPileWaypoints })
        build.sub.add(ToggleSetting("Ballista Hologram", "Big build % over the ballista", S::class.java, "kuudraBallistaText"))
        build.sub.add(ToggleSetting("Fresh Timers HUD", "Your party's Fresh Tools time left", S::class.java, "kuudraFreshHud"))
        build.sub.add(ToggleSetting("Fresh Party Message", "One party message when your Fresh Tools procs", S::class.java, "kuudraFreshParty"))
        build.sub.add(InputSetting("Fresh Message", "", S::class.java, "kuudraFreshMessage").gatedBy { S.kuudraFreshParty })
        col.features.add(build)

        val boss = Feature("Kuudra Boss", S::class.java, "kuudraBossMaster")
        boss.sub.add(ToggleSetting("Highlight Kuudra", "", S::class.java, "kuudraHighlight"))
        boss.sub.add(ColorPickerSetting("Kuudra Color", "", S::class.java, "kuudraHighlightColor").gatedBy { S.kuudraHighlight })
        boss.sub.add(ToggleSetting("Health In World", "", S::class.java, "kuudraHpWorld"))
        boss.sub.add(ToggleSetting("Health HUD", "", S::class.java, "kuudraHpHud"))
        boss.sub.add(ToggleSetting("Scaled T5 Health", "Real HP under 25k in T5", S::class.java, "kuudraHpScaled"))
        boss.sub.add(ToggleSetting("Team Highlight", "Outline teammates", S::class.java, "kuudraTeamHighlight"))
        boss.sub.add(ColorPickerSetting("Team Color", "", S::class.java, "kuudraTeamColor").gatedBy { S.kuudraTeamHighlight })
        boss.sub.add(ToggleSetting("Elle Highlight", "", S::class.java, "kuudraElleHighlight"))
        boss.sub.add(ColorPickerSetting("Elle Color", "", S::class.java, "kuudraElleColor").gatedBy { S.kuudraElleHighlight })
        col.features.add(boss)

        val extra = Feature("Kuudra Fight Extras", S::class.java, "kuudraBossMaster")
        extra.sub.add(ToggleSetting("Ability Announcer", "Party message with your position when you cast a spell", S::class.java, "kuudraAnnounce"))
        extra.sub.add(ToggleSetting("Spirit Spark", "", S::class.java, "kuudraAnnounceSpirit").gatedBy { S.kuudraAnnounce })
        extra.sub.add(ToggleSetting("Hollowed Rush", "", S::class.java, "kuudraAnnounceHollowed").gatedBy { S.kuudraAnnounce })
        extra.sub.add(ToggleSetting("Raging Wind", "", S::class.java, "kuudraAnnounceWind").gatedBy { S.kuudraAnnounce })
        extra.sub.add(ToggleSetting("Ichor Pool", "", S::class.java, "kuudraAnnounceIchor").gatedBy { S.kuudraAnnounce })
        extra.sub.add(ToggleSetting("Ichor Pool Area", "Circle where a party member's Ichor Pool lands", S::class.java, "kuudraIchorArea"))
        extra.sub.add(ColorPickerSetting("Ichor Color", "", S::class.java, "kuudraIchorColor").gatedBy { S.kuudraIchorArea })
        extra.sub.add(ToggleSetting("Rend Damage Alert", "Chat message when anyone pulls with Rend", S::class.java, "kuudraRendAlert"))
        extra.sub.add(ToggleSetting("Backbone Alert", "Timer bar after throwing a Bonemerang", S::class.java, "kuudraBackbone"))
        extra.sub.add(ToggleSetting("Backbone Sound", "", S::class.java, "kuudraBackboneSound").gatedBy { S.kuudraBackbone })
        extra.sub.add(SliderIntSetting("Backbone Advance", "Ticks early to alert", S::class.java, "kuudraBackboneAdvance", 0, 10).gatedBy { S.kuudraBackbone })
        extra.sub.add(ToggleSetting("Stun Waypoints", "Where to hit the pod while stunning", S::class.java, "kuudraStunWaypoints"))
        extra.sub.add(DropdownSetting("Stun Pod", "", arrayOf("Left Pod", "Right Pod", "Back Pod"), { S.kuudraStunPod }, { v -> S.kuudraStunPod = v }).gatedBy { S.kuudraStunWaypoints })
        extra.sub.add(DropdownSetting("Stun Style", "", arrayOf("Outline", "Filled", "Both"), { S.kuudraStunStyle }, { v -> S.kuudraStunStyle = v }).gatedBy { S.kuudraStunWaypoints })
        extra.sub.add(ColorPickerSetting("Stun Color", "", S::class.java, "kuudraStunColor").gatedBy { S.kuudraStunWaypoints })
        col.features.add(extra)

        val chests = Feature("Kuudra Chests", S::class.java, "kuudraChestsMaster")
        chests.sub.add(ToggleSetting("Chest Value", "Item values in the Paid/Free Chest", S::class.java, "kuudraChestValue"))
        chests.sub.add(ToggleSetting("Opened Chest Highlight", "Vesuvius/Croesus: red = done, orange = partly opened", S::class.java, "kuudraOpenedHighlight"))
        chests.sub.add(ToggleSetting("Profit Tracker", "Adds a paid chest's profit when you open it", S::class.java, "kuudraProfit"))
        chests.sub.add(DropdownSetting("Tracker View", "Session = this launch, Total = saved", arrayOf("Session", "Total"), { S.kuudraProfitMode }, { v -> S.kuudraProfitMode = v }).gatedBy { S.kuudraProfit })
        chests.sub.add(SliderIntSetting("Tracker Rows", "Top items shown by value", S::class.java, "kuudraProfitRows", 3, 25, 1).gatedBy { S.kuudraProfit })
        chests.sub.add(ButtonSetting("Reset Profit", "Clears session and total", "Reset") { S.kuudraProfitTotal = 0.0; S.kuudraProfitChests = 0; KuudraLoot.reset() })
        chests.sub.add(ToggleSetting("Chest Counter", "Runs since you last claimed at Vesuvius", S::class.java, "kuudraChestCounter"))
        chests.sub.add(SliderIntSetting("Chest Cap", "", S::class.java, "kuudraChestCap", 10, 60))
        chests.sub.add(ToggleSetting("Cap Party Reminder", "One party message when you hit the cap", S::class.java, "kuudraChestCapParty").gatedBy { S.kuudraChestCounter })
        chests.sub.add(ButtonSetting("Reset Counter", "", "Reset") { S.kuudraChestCount = 0 })
        col.features.add(chests)

        val perks = Feature("Block Useless Perks", S::class.java, "kuudraBlockPerks")
        for (perk in BLOCKABLE_PERKS) perks.sub.add(ToggleSetting(perk, "Ignore clicks on this perk in the Perk Menu", { perkBlocked(perk) }, { on -> setPerkBlocked(perk, on) }))
        col.features.add(perks)

        return col
    }

    private val BLOCKABLE_PERKS = listOf("Steady Hands", "Bomberman", "Mining Frenzy", "Elle's Lava Rod", "Elle's Pickaxe", "Auto Revive", "Human Cannonball", "Elle's Supplies")

    private fun perkList(): List<String> = S.kuudraBlockPerksList.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun perkBlocked(perk: String): Boolean = perkList().any { it.equals(perk, true) }

    // Other entries in the saved list are kept as they are
    private fun setPerkBlocked(perk: String, on: Boolean) {
        val rest = perkList().filterNot { it.equals(perk, true) }
        S.kuudraBlockPerksList = (if (on) rest + perk else rest).joinToString(", ")
    }

    fun describe(name: String): String? = when (name) {
        "Kuudra Splits" -> "Phase split HUD with per-tier PBs"
        "Kuudra Alerts" -> "Titles for phases, supplies, stun and spawn side"
        "Supply Helper" -> "Supply times, waypoints, drop-offs and pearl lineups"
        "Pre-spot Alert" -> "Detects your pre spot and whether its crate spawned"
        "Build Helper" -> "Build progress, pile waypoints and fresh timers"
        "Kuudra Boss" -> "Kuudra highlight and health, team and Elle boxes"
        "Kuudra Fight Extras" -> "Ability announcer, ichor pool circle, rend alert, backbone timer, stun waypoints"
        "Kuudra Chests" -> "Chest value, profit, Vesuvius highlight, chest counter"
        "Block Useless Perks" -> "Stops misclicks on useless Kuudra perks"
        else -> null
    }
}
