package fishmod.features.achievements

import fishmod.features.FishModScreen.ButtonSetting
import fishmod.features.FishModScreen.Column
import fishmod.features.FishModScreen.Feature
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
        main.sub.add(ToggleSetting("Unlock Toast", "Corner pop-up on unlock", S::achToast))
        main.sub.add(ToggleSetting("Chat Message", "", S::achChat))
        main.sub.add(ToggleSetting("Sound", "", S::achSound))
        main.sub.add(SoundSearchSetting("Unlock Sound", "Type to search every game sound",
            { S.achSoundName }, { v -> S.achSoundName = v }, { S.achSoundVolume }, { S.achSoundPitch }).gatedBy { S.achSound })
        main.sub.add(SliderIntSetting("Sound Volume %", "", S::achSoundVolume, 0, 200, 10).gatedBy { S.achSound })
        main.sub.add(SliderDoubleSetting("Sound Pitch", "", S::achSoundPitch, 0.5, 2.0).gatedBy { S.achSound })
        main.sub.add(ToggleSetting("API Sync On Login", "One Hypixel profile pull per launch", S::achApiSyncOnLogin))
        main.sub.add(ButtonSetting("Sync Now", "Same as /fm ach sync", "Sync") { AchApiSync.sync(true) })
        main.sub.add(InputIntSetting("RNG Min Value", "Drops worth at least this count as RNG", { S.achRngMinValue }, { v -> S.achRngMinValue = v.coerceAtLeast(0) }))
        main.sub.add(ToggleSetting("Reveal Secrets", "Show secret names before unlocking", S::achShowSecrets))
        main.sub.add(ToggleSetting("Hide Untracked", "Hide ones FishMod can't track yet", S::achHideUntracked))
        col.features.add(main)

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
                hidden -> "Secret achievement"
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
