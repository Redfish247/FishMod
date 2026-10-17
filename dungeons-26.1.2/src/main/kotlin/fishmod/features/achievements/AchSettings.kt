package fishmod.features.achievements

import fishmod.shaded.practicalconfig.manager.ConfigValue

object AchSettings {
    @ConfigValue @JvmField var achEnabled: Boolean = true
    @ConfigValue @JvmField var achToast: Boolean = true
    @ConfigValue @JvmField var achChat: Boolean = true
    @ConfigValue @JvmField var achSound: Boolean = true
    @ConfigValue @JvmField var achSoundName: String = "ui.toast.challenge_complete"
    @ConfigValue @JvmField var achSoundStyle: String = "Fanfare"
    @ConfigValue @JvmField var achPopupX: Int = -1
    @ConfigValue @JvmField var achPopupY: Int = 40
    @ConfigValue @JvmField var achPopupScale: Double = 1.0
    @ConfigValue @JvmField var achSoundVolume: Int = 100
    @ConfigValue @JvmField var achSoundPitch: Double = 1.0
    @ConfigValue @JvmField var achApiSyncOnLogin: Boolean = true
    @ConfigValue @JvmField var achShowSecrets: Boolean = false
    @ConfigValue @JvmField var achHideUntracked: Boolean = false
    @ConfigValue @JvmField var achRngMinValue: Int = 1_000_000
    @ConfigValue @JvmField var achLbEnabled: Boolean = true
    @ConfigValue @JvmField var achPtsX: Double = 19.5
    @ConfigValue @JvmField var achPtsY: Double = 69.0
    @ConfigValue @JvmField var achPtsZ: Double = 6.5
    @ConfigValue @JvmField var achSecX: Double = 27.5
    @ConfigValue @JvmField var achSecY: Double = 69.0
    @ConfigValue @JvmField var achSecZ: Double = -1.5
    @ConfigValue @JvmField var achLbScale: Double = 1.0
    @ConfigValue @JvmField var achLbArea: String = "Any"
    @ConfigValue @JvmField var achLbVillageOnly: Boolean = true
}
