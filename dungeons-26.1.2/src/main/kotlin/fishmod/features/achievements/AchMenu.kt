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

// The Achievements tab in /fm: settings only; the list lives in /fm ach
object AchMenu {

    private var summary: LabelSetting? = null

    fun column(): Column {
        val col = Column("Achievements")

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
        lb.sub.add(LabelSetting("In-world top 10", "Shows within 48 blocks of the spot; /fm ach lb here [points|secrets] sets it to your feet"))
        lb.sub.add(InputDoubleSetting("Points X", "", { S.achPtsX }, { v -> S.achPtsX = v }))
        lb.sub.add(InputDoubleSetting("Points Y", "", { S.achPtsY }, { v -> S.achPtsY = v }))
        lb.sub.add(InputDoubleSetting("Points Z", "", { S.achPtsZ }, { v -> S.achPtsZ = v }))
        lb.sub.add(InputDoubleSetting("Secrets X", "", { S.achSecX }, { v -> S.achSecX = v }))
        lb.sub.add(InputDoubleSetting("Secrets Y", "", { S.achSecY }, { v -> S.achSecY = v }))
        lb.sub.add(InputDoubleSetting("Secrets Z", "", { S.achSecZ }, { v -> S.achSecZ = v }))
        lb.sub.add(SliderDoubleSetting("Scale", "", S::achLbScale, 0.5, 4.0))
        lb.sub.add(ToggleSetting("Hub Only", "Only show the board while in the Hub", S::achLbVillageOnly))
        lb.sub.add(DropdownSetting("Area", "Only show in this area (Any = everywhere)", arrayOf("Any") + fishmod.utils.Location.values().map { it.name2 }.filter { it != "Unknown" }.toTypedArray(), { S.achLbArea }, { v -> S.achLbArea = v }))
        lb.sub.add(ButtonSetting("Points Board To Me", "Same as /fm ach lb here points", "Here") { AchLeaderboard.here(false) })
        lb.sub.add(ButtonSetting("Secrets Board To Me", "Same as /fm ach lb here secrets", "Here") { AchLeaderboard.here(true) })
        lb.sub.add(ButtonSetting("Submit Score", "Send your totals now", "Submit") { AchLeaderboard.submit(true) })
        col.features.add(lb)

        refresh()
        return col
    }

    fun refresh() {
        summary?.let {
            val all = AchRegistry.ALL
            it.name = "${all.count { a -> AchEngine.unlocked(a) }}/${all.size} unlocked"
            it.description = "${AchEngine.points()}/${AchEngine.maxPoints()} AP"
        }
    }

    fun describe(name: String): String? = when (name) {
        "Achievements" -> "Unlock toasts, API sync and settings"
        else -> null
    }
}
