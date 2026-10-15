package fishmod.features.achievements

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
import fishmod.features.FishModScreen.DropdownSetting
import fishmod.features.FishModScreen.InputDoubleSetting
import fishmod.features.FishModScreen.InputIntSetting
import fishmod.features.FishModScreen.LabelSetting
import fishmod.features.FishModScreen.SliderDoubleSetting
import fishmod.features.FishModScreen.SliderIntSetting
import fishmod.features.FishModScreen.SoundSearchSetting
import fishmod.features.FishModScreen.ToggleSetting
import fishmod.features.achievements.AchSettings as S

// The Achievements tab in /fm: settings plus one browsable section per achievement group
object AchMenu {

    private val rows = ArrayList<Pair<LabelSetting, AchRegistry.Ach>>()
    private var summary: LabelSetting? = null

    fun column(): Column {
        val col = Column("Achievements")
        rows.clear()

        val main = Feature("Achievements", S::achEnabled)
        summary = LabelSetting("", "").also { main.sub.add(it) }
        main.sub.add(ButtonSetting("Open Achievements", "Browse every achievement", "Open") {
            val mc = net.minecraft.client.Minecraft.getInstance()
            mc.setScreen(AchScreen(mc.screen))
        })
        main.sub.add(ToggleSetting("Unlock Popup", "Banner at the top of the screen on unlock (move it in the HUD editor)", S::achToast))
        main.sub.add(SliderDoubleSetting("Popup Scale", "", S::achPopupScale, 0.5, 2.5).gatedBy { S.achToast })
        main.sub.add(ButtonSetting("Test Popup", "Preview the popup and sound, nothing is saved", "Test") { AchPopup.test() })
        main.sub.add(ToggleSetting("Chat Message", "", S::achChat))
        main.sub.add(ToggleSetting("Sound", "", S::achSound))
        main.sub.add(DropdownSetting("Sound Style", "Layered unlock sound", AchPopup.STYLES, { S.achSoundStyle }, { v -> S.achSoundStyle = v }).gatedBy { S.achSound })
        main.sub.add(SoundSearchSetting("Vanilla Sound", "Used by the Vanilla style",
            { S.achSoundName }, { v -> S.achSoundName = v }, { S.achSoundVolume }, { S.achSoundPitch }).gatedBy { S.achSound && S.achSoundStyle == "Vanilla" })
        main.sub.add(SliderIntSetting("Sound Volume %", "", S::achSoundVolume, 0, 200, 10).gatedBy { S.achSound })
        main.sub.add(SliderDoubleSetting("Sound Pitch", "", S::achSoundPitch, 0.5, 2.0).gatedBy { S.achSound })
        main.sub.add(ToggleSetting("API Sync On Login", "One Hypixel profile pull per launch", S::achApiSyncOnLogin))
        main.sub.add(ButtonSetting("Sync Now", "Same as /fm ach sync", "Sync") { AchApiSync.sync(true) })
        main.sub.add(InputIntSetting("RNG Min Value", "Drops worth at least this count as RNG", { S.achRngMinValue }, { v -> S.achRngMinValue = v.coerceAtLeast(0) }))
        main.sub.add(ToggleSetting("Reveal Secrets", "Show secret names before unlocking", S::achShowSecrets))
        main.sub.add(ToggleSetting("Hide Untracked", "Hide ones FishMod can't track yet", S::achHideUntracked))
        col.features.add(main)

        val lb = Feature("Leaderboard Board", S::achLbEnabled)
        lb.sub.add(LabelSetting("In-world top 10", "Shows within 48 blocks of the spot; /fm ach lb here sets it to your feet"))
        lb.sub.add(InputDoubleSetting("X", "", { S.achLbX }, { v -> S.achLbX = v }))
        lb.sub.add(InputDoubleSetting("Y", "", { S.achLbY }, { v -> S.achLbY = v }))
        lb.sub.add(InputDoubleSetting("Z", "", { S.achLbZ }, { v -> S.achLbZ = v }))
        lb.sub.add(SliderDoubleSetting("Scale", "", S::achLbScale, 0.5, 4.0))
        lb.sub.add(ToggleSetting("Village Only", "Only show the board while in the Hub Village", S::achLbVillageOnly))
        lb.sub.add(DropdownSetting("Area", "Only show in this area (Any = everywhere)", arrayOf("Any") + fishmod.utils.Location.values().map { it.name2 }.filter { it != "Unknown" }.toTypedArray(), { S.achLbArea }, { v -> S.achLbArea = v }))
        lb.sub.add(ButtonSetting("Set To My Position", "Same as /fm ach lb here", "Here") { AchLeaderboard.here() })
        lb.sub.add(ButtonSetting("Submit Score", "Send your totals now", "Submit") { AchLeaderboard.submit(true) })
        col.features.add(lb)

        for (sec in AchRegistry.SECTIONS) {
            val f = Feature(sec, null, null)
            for (a in AchRegistry.ALL) if (a.section == sec) {
                val l = LabelSetting(a.name, "")
                l.gatedBy { !(S.achHideUntracked && !a.tracked && !AchEngine.unlocked(a)) }
                rows.add(l to a)
                f.sub.add(l)
            }
            col.features.add(f)
        }
        refresh()
        return col
    }

    fun refresh() {
        summary?.let {
            val all = AchRegistry.ALL
            it.name = "${all.count { a -> AchEngine.unlocked(a) }}/${all.size} unlocked"
            it.description = "${AchEngine.points()}/${AchEngine.maxPoints()} AP"
        }
        for ((l, a) in rows) {
            val done = AchEngine.unlocked(a)
            val hidden = a.secret && !done && !S.achShowSecrets
            l.name = (if (done) "✔ " else "") + (if (hidden) "???" else a.name) + "  (${a.points} AP)"
            l.description = when {
                hidden -> a.hint.ifEmpty { "Secret achievement" }
                done -> a.desc
                a.serverSide -> "Needs FishMod server support - " + a.desc
                !a.tracked -> "Not tracked yet - " + a.desc
                a.metric.startsWith("meta.") || a.metric.startsWith("secret.") -> a.desc
                else -> "${AchEngine.fmt(a, minOf(AchEngine.value(a), a.goal))}/${AchEngine.fmt(a, a.goal)} - ${a.desc}"
            }
        }
    }

    fun describe(name: String): String? = when (name) {
        "Achievements" -> "Unlock toasts, API sync and settings"
        else -> null
    }
}
