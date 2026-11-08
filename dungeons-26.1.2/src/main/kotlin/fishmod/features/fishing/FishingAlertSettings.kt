package fishmod.features.fishing

import fishmod.shaded.practicalconfig.manager.ConfigValue

// Phase 2 (alerts/share/compact) settings; everything ships OFF. Strings behind them are UNVERIFIED in game.
object FishingAlertSettings {
    @ConfigValue @JvmField var fishingAlerts: Boolean = false

    // Sea creature title + sound
    @ConfigValue @JvmField var alertSeaCreature: Boolean = false
    @ConfigValue @JvmField var alertScRareOnly: Boolean = true
    @ConfigValue @JvmField var alertScSound: String = "Ping"
    @ConfigValue @JvmField var alertScVolume: Int = 100
    // Title stay time in ms
    @ConfigValue @JvmField var alertTitleMs: Int = 2000

    // Rare drop title with price
    @ConfigValue @JvmField var alertRareDrop: Boolean = false
    // Only alert at or above this price in coins (0 = every rare drop)
    @ConfigValue @JvmField var alertDropMinPrice: Int = 0

    @ConfigValue @JvmField var alertHotspotFound: Boolean = false
    @ConfigValue @JvmField var alertHotspotGone: Boolean = false
    @ConfigValue @JvmField var alertWormholeGone: Boolean = false
    @ConfigValue @JvmField var alertNewTrophy: Boolean = false

    @ConfigValue @JvmField var alertDeployable: Boolean = false
    // Alert when this many seconds remain
    @ConfigValue @JvmField var alertDeployableSecs: Int = 30

    @ConfigValue @JvmField var alertBait: Boolean = false
    @ConfigValue @JvmField var alertBaitLow: Int = 16

    // Sound for every non-creature alert
    @ConfigValue @JvmField var alertSound: String = "Ping"
    @ConfigValue @JvmField var alertSoundVolume: Int = 100

    // Share: opt-in, off by default, never automatic for hotspots
    @ConfigValue @JvmField var shareEnabled: Boolean = false
    // Comma list of big-mob names ticked in the checklist (empty = none)
    @ConfigValue @JvmField var shareCreatures: String = ""
    @ConfigValue @JvmField var shareParty: Boolean = true
    // All chat with coords, world bosses only
    @ConfigValue @JvmField var shareWorldBossAll: Boolean = false
    @ConfigValue @JvmField var shareDrops: Boolean = false
    @ConfigValue @JvmField var shareDropMinPrice: Int = 10_000_000
    // Show clickable [Share] buttons when a hotspot is found (never auto-sends)
    @ConfigValue @JvmField var shareHotspot: Boolean = false

    // Compact catch messages
    @ConfigValue @JvmField var compactDoubleHook: Boolean = false
    @ConfigValue @JvmField var compactSpawn: Boolean = false
}
