package fishmod.utils.config.values

import config.practical.data.SoundData
import config.practical.manager.ConfigValue
import net.minecraft.sounds.SoundEvents

object Floor7 {

    @ConfigValue
    @JvmField
    var enableBossWaypoints: Boolean = false

    @ConfigValue
    @JvmField
    var nextWaypointColor: Int = 0xff00F7F7.toInt()

    @ConfigValue
    @JvmField
    var nextWaypointThroughWall: Boolean = false

    @ConfigValue
    @JvmField
    var enableCrystalSpawnTime: Boolean = false

    @ConfigValue
    @JvmField
    var enableStormTickTimer: Boolean = false

    @ConfigValue
    @JvmField
    var stormTickTimerColor: Int = 0xffffffff.toInt()

    @ConfigValue
    @JvmField
    var tickDownStormTickTimer: Boolean = false

    @ConfigValue
    @JvmField
    var enableStormDeathTime: Boolean = false

    // LB (Last Breath) release window — countdown shown from 30s until 34.35s on the Storm (P2)
    // clock, telling you when to shoot Last Breath.
    @ConfigValue
    @JvmField
    var enableLbReleaseTimer: Boolean = false

    @ConfigValue
    @JvmField
    var lbReleaseTimerColor: Int = 0xffff5555.toInt()

    @ConfigValue
    @JvmField
    var notifyUsedSpiritMask: Boolean = false

    @ConfigValue
    @JvmField
    var displayDistanceToLedge: Boolean = false

    @ConfigValue
    @JvmField
    var enableGoldorTickTimer: Boolean = false

    @ConfigValue
    @JvmField
    var inDeathTicks: Boolean = true

    @ConfigValue
    @JvmField
    var enableTermStartTimer: Boolean = false

    @ConfigValue
    @JvmField
    var enablePositionalMessages: Boolean = false

    @ConfigValue
    @JvmField
    var enableRelicStartTimer: Boolean = false

    @ConfigValue
    @JvmField
    var relicSpawnTicks: Int = 42

    @ConfigValue
    @JvmField
    var enableRelicPlaceTime: Boolean = false

    @ConfigValue
    @JvmField
    var renderRelicHighlight: Boolean = false

    @ConfigValue
    @JvmField
    var blockIncorrectRelicPlace: Boolean = false

    @ConfigValue
    @JvmField
    var replaceWithProgressBar: Boolean = false

    @ConfigValue
    @JvmField
    var useValleyBar: Boolean = true

    @ConfigValue
    @JvmField
    var combineTickTimers: Boolean = false

    @ConfigValue
    @JvmField
    var dragSpawnTimers: Boolean = false

    @ConfigValue
    @JvmField
    var sendSoundOnDragSpawn: Boolean = false

    @ConfigValue
    @JvmField
    var displayLocationNotification: Boolean = false

    @ConfigValue
    @JvmField
    var notificationDuration: Int = 15

    @ConfigValue
    @JvmField
    var atLocationSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f)

    @ConfigValue
    @JvmField
    var notificationRepetitions: Int = 3

    @ConfigValue
    @JvmField
    var predevForAll: Boolean = true

    @ConfigValue
    @JvmField
    var showAllRelicTimes: Boolean = true

    @ConfigValue
    @JvmField
    var notifyPre4Completion: Boolean = false

    @ConfigValue
    @JvmField
    var notifyStormCrush: Boolean = false

    @ConfigValue
    @JvmField
    var timePillarExplosion: Boolean = false

    @ConfigValue
    @JvmField
    var notifiyMelody: Boolean = false

    @ConfigValue
    @JvmField
    var disableTitlesAtPre4: Boolean = false

    @ConfigValue
    @JvmField
    var showDistanceAtYellowOnly: Boolean = false

    @ConfigValue
    @JvmField
    var dontNotifiyForYourself: Boolean = true

    @ConfigValue
    @JvmField
    var hideTerminalTitles: Boolean = false

    @ConfigValue
    @JvmField
    var terminalTimeStamps: Boolean = false

    @ConfigValue
    @JvmField
    var crystalPlaceReminder: Boolean = false

    @ConfigValue
    @JvmField
    var showSectionProgress: Boolean = false

    @ConfigValue
    @JvmField
    var notifySSCompletion: Boolean = false

    @ConfigValue
    @JvmField
    var disableTitlesAtSS: Boolean = false

    @ConfigValue
    @JvmField
    var instantlyDisplayCrystalReminder: Boolean = false

    @ConfigValue
    @JvmField
    var dragonHealth: Boolean = false

    @ConfigValue
    @JvmField
    var maxorStunDuration: Boolean = false

    @ConfigValue
    @JvmField
    var makeGoldorTickUp: Boolean = true

    @ConfigValue
    @JvmField
    var capitalizeHealthNumbers: Boolean = true

    @ConfigValue
    @JvmField
    var sectionCompletionNotification: Boolean = false

    @ConfigValue
    @JvmField
    var sectionChangeSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 0f, 1f)

    // Master switch for the combined "Tick Timers" settings group (Maxor/Storm/Goldor + related
    // P2/terminal notifications); when off, none of that group renders regardless of its own toggle.
    @ConfigValue
    @JvmField
    var enableTickTimers: Boolean = true

    @ConfigValue
    @JvmField
    var enableMaxorTickTimer: Boolean = false

    @ConfigValue
    @JvmField
    var showCurrentSection: Boolean = false

    @ConfigValue
    @JvmField
    var sectionColorProgress: Boolean = false

    @ConfigValue
    @JvmField
    var sectionPrevObjective: Boolean = false

    @ConfigValue
    @JvmField
    var dragonTracer: Boolean = false

    @ConfigValue
    @JvmField
    var assumeCore: Boolean = true

    @ConfigValue
    @JvmField
    var assumeSplitEE2: Boolean = false

    // --- S4 term/leap failure tracker ---

    @ConfigValue
    @JvmField
    var s4TrackerEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var s4DebugHudEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var s4AlertsEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var s4AlertSoundEnabled: Boolean = true

    @ConfigValue
    @JvmField
    var s4EarlyLeapAlert: Boolean = true

    @ConfigValue
    @JvmField
    var s4LateLeapAlert: Boolean = true

    @ConfigValue
    @JvmField
    var s4MissedTermAlert: Boolean = true

    @ConfigValue
    @JvmField
    var s4DeathAlert: Boolean = true

    // Time after Section 5 (Core open) starts before someone still outside Core is flagged "late".
    @ConfigValue
    @JvmField
    var s4LateLeapThresholdTicks: Int = 100

    @ConfigValue
    @JvmField
    var s4AlertDurationTicks: Int = 60

    @ConfigValue
    @JvmField
    var s4AlertCooldownTicks: Int = 40

    @ConfigValue
    @JvmField
    var s4AlertSound: SoundData = SoundData(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f)

    // --- Gate Display: big world-space X/check over each Goldor gate (S1-S3) ---

    @ConfigValue
    @JvmField
    var gateDisplayEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var gateDisplayScale: Float = 6f
}
