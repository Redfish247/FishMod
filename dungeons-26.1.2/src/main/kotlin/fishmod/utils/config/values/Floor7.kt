package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.data.SoundData
import fishmod.shaded.practicalconfig.manager.ConfigValue
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

    @ConfigValue
    @JvmField
    var enableLbReleaseTimer: Boolean = false

    @ConfigValue
    @JvmField
    var lbReleaseTimerColor: Int = 0xffff5555.toInt()

    @ConfigValue
    @JvmField
    var lbReleaseTimerPingMs: Int = 0

    @ConfigValue
    @JvmField
    var enablePyTimer: Boolean = false

    @ConfigValue
    @JvmField
    var pyTimerColor: Int = 0xffff55ff.toInt()

    @ConfigValue
    @JvmField
    var pyTimerPingMs: Int = 0

    @ConfigValue
    @JvmField
    var enableNecronLbTimer: Boolean = false

    @ConfigValue
    @JvmField
    var necronLbPingMs: Int = 0

    @ConfigValue
    @JvmField
    var necronLbColor: Int = 0xffff5555.toInt()

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
    var relicTimesEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var relicTimesParty: Boolean = false

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
    var playersLeapedEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var playersLeapedAnyClass: Boolean = false

    @ConfigValue
    @JvmField
    var gateDisplayEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var gateDisplayScale: Float = 6f

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
