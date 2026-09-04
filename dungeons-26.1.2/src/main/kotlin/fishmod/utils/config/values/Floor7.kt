package fishmod.utils.config.values

import config.practical.data.SoundData
import config.practical.manager.ConfigValue
import net.minecraft.sounds.SoundEvents

object Floor7 {

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

    // LB release window — countdown from 30s to 34.35s on the Storm (P2) clock
    @ConfigValue
    @JvmField
    var enableLbReleaseTimer: Boolean = false

    @ConfigValue
    @JvmField
    var lbReleaseTimerColor: Int = 0xffff5555.toInt()

    // ping (ms) to fire the release cue early so the shot leaves the bow on time; 0 = no shift
    @ConfigValue
    @JvmField
    var lbReleaseTimerPingMs: Int = 0

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
    var enableRelicStartTimer: Boolean = false

    @ConfigValue
    @JvmField
    var relicSpawnTicks: Int = 42

    @ConfigValue
    @JvmField
    var renderRelicHighlight: Boolean = false

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

    // master gate for the Tick Timers group; when off nothing in it renders regardless of child toggles
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

    // Gate Display: world-space X/check over each Goldor gate (S1-S3)
    @ConfigValue
    @JvmField
    var gateDisplayEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var gateDisplayScale: Float = 6f

    // Blood Solver: Watcher speed alert + blood-mob move predictor
    @ConfigValue
    @JvmField
    var bloodSolverEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var bloodSolverKillTitle: Boolean = false

    @ConfigValue
    @JvmField
    var bloodSolverSpeedAlert: Boolean = false

    @ConfigValue
    @JvmField
    var bloodSolverSpeedAlertParty: Boolean = false

    @ConfigValue
    @JvmField
    var bloodSolverDecimals: Int = 1

    @ConfigValue
    @JvmField
    var bloodSolverBoxColor: Int = 0xffff00ff.toInt()

    @ConfigValue
    @JvmField
    var bloodSolverLineColor: Int = 0xff55ffff.toInt()

    @ConfigValue
    @JvmField
    var bloodSolverTimerColor: Int = 0xffffffff.toInt()
}
