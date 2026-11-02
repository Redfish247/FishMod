package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object Floor7Settings {

    @ConfigValue
    @JvmField
    var crystalSpawnHud: Boolean = false

    @ConfigValue
    @JvmField
    var stormTicksHud: Boolean = false

    @ConfigValue
    @JvmField
    var stormTicksColor: Int = 0xffffffff.toInt()

    @ConfigValue
    @JvmField
    var stormTicksCountDown: Boolean = false

    @ConfigValue
    @JvmField
    var stormKillTimeHud: Boolean = false

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
    var enableNecronDropPredict: Boolean = false

    @ConfigValue
    @JvmField
    var necronDropColor: Int = 0xffff0000.toInt()

    @ConfigValue
    @JvmField
    var necronDropStyle: String = "Box"

    @ConfigValue
    @JvmField
    var necronDropLavaPopup: Boolean = true

    @ConfigValue
    @JvmField
    var goldorTicksHud: Boolean = false

    @ConfigValue
    @JvmField
    var goldorUseDeathTicks: Boolean = true

    @ConfigValue
    @JvmField
    var termOpenHud: Boolean = false

    @ConfigValue
    @JvmField
    var relicStartHud: Boolean = false

    @ConfigValue
    @JvmField
    var relicHighlight: Boolean = false

    @ConfigValue
    @JvmField
    var relicTimesEnabled: Boolean = false

    @ConfigValue
    @JvmField
    var relicTimesParty: Boolean = false

    @ConfigValue
    @JvmField
    var pre4DoneAlert: Boolean = false

    @ConfigValue
    @JvmField
    var stormCrushAlert: Boolean = false

    @ConfigValue
    @JvmField
    var pillarBlastTimer: Boolean = false

    @ConfigValue
    @JvmField
    var melodyAlert: Boolean = false

    @ConfigValue
    @JvmField
    var muteTitlesPre4: Boolean = false

    @ConfigValue
    @JvmField
    var muteTerminalTitles: Boolean = false

    @ConfigValue
    @JvmField
    var termSplitTimestamps: Boolean = false

    @ConfigValue
    @JvmField
    var crystalPlaceAlert: Boolean = false

    @ConfigValue
    @JvmField
    var sectionProgressHud: Boolean = false

    @ConfigValue
    @JvmField
    var ssDoneAlert: Boolean = false

    @ConfigValue
    @JvmField
    var muteTitlesSS: Boolean = false

    @ConfigValue
    @JvmField
    var crystalAlertInstant: Boolean = false

    @ConfigValue
    @JvmField
    var goldorTicksCountUp: Boolean = true

    @ConfigValue
    @JvmField
    var bossHpCapitalized: Boolean = true

    @ConfigValue
    @JvmField
    var sectionDoneAlert: Boolean = false

    @ConfigValue
    @JvmField
    var enableTickTimers: Boolean = false

    @ConfigValue
    @JvmField
    var maxorTicksHud: Boolean = false

    @ConfigValue
    @JvmField
    var currentSectionHud: Boolean = false

    @ConfigValue
    @JvmField
    var sectionProgressColored: Boolean = false

    @ConfigValue
    @JvmField
    var sectionShowPrevious: Boolean = false

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
