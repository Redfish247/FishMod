package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object FishSettings {

    @ConfigValue @JvmField var sendLagToParty: Boolean = false

    @ConfigValue @JvmField var hasSeenWelcomeMessage: Boolean = false

    @ConfigValue @JvmField var showPuzzles: Boolean = false

    @ConfigValue @JvmField var deathMessageEnabled: Boolean = false

    @ConfigValue @JvmField var deathMessageTemplate: String = "{name} died like a bum"

    @ConfigValue @JvmField var deathMessageToParty: Boolean = false

    @ConfigValue @JvmField var rtcaClassXpPerRun: Int = 424200
    @ConfigValue @JvmField var rtcaClassPassiveXpPerRun: Int = 106050
    @ConfigValue @JvmField var rtcCataXpPerRun: Int = 509040
    @ConfigValue @JvmField var rtcaIncludeDailyBonus: Boolean = false

    @ConfigValue @JvmField var soulflowHudEnabled: Boolean = false
    @ConfigValue @JvmField var soulflowWarningThreshold: Int = 1000
    @ConfigValue @JvmField var soulflowMissingNotifier: Boolean = false
    @ConfigValue @JvmField var soulflowHudX: Int = 10
    @ConfigValue @JvmField var soulflowHudY: Int = 60

    @ConfigValue @JvmField var fmColumnOrder: String = ""

    @ConfigValue @JvmField var fmAnimations: Boolean = true

    @ConfigValue @JvmField var fmBgPreset: String = "Dark Glass"
    @ConfigValue @JvmField var fmBgCustomColor: Int = 0xFF14181D.toInt()
    @ConfigValue @JvmField var fmBgAlpha: Int = 100
    @ConfigValue @JvmField var fmDropDurationMs: Int = 450
    @ConfigValue @JvmField var fmStaggerDelayMs: Int = 60
    @ConfigValue @JvmField var fmExitStyle: String = "Floor Fall"
    @ConfigValue @JvmField var fmButtonColor: Int = 0xFF24B6B0.toInt()
    @ConfigValue @JvmField var fmButtonAlpha: Int = 100
    @ConfigValue @JvmField var fmRowColor: Int = 0xFF24B6B0.toInt()
    @ConfigValue @JvmField var fmRowAlpha: Int = 15

    @ConfigValue @JvmField var petXpTamingLevel: Int = 0
    @ConfigValue @JvmField var petXpBeastmasterBonus: Int = 0
    @ConfigValue @JvmField var petXpPetItemBonus: Int = 0
    @ConfigValue @JvmField var petXpBoosterCookie: Boolean = false

    @ConfigValue @JvmField var petHudEnabled: Boolean = false
    @ConfigValue @JvmField var petHudShowLevel: Boolean = false
    @ConfigValue @JvmField var petHudShowRarity: Boolean = true
    @ConfigValue @JvmField var petHudFadeIdle: Boolean = false
    @ConfigValue @JvmField var petHudFadeMs: Int = 5000
    @ConfigValue @JvmField var petHudX: Int = 10
    @ConfigValue @JvmField var petHudY: Int = 80

    @ConfigValue @JvmField var cooldownOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var cooldownShowText: Boolean = false
    @ConfigValue @JvmField var cooldownOnlyUnder3s: Boolean = false

    @ConfigValue @JvmField var catacombsOverflowEnabled: Boolean = false

    @ConfigValue @JvmField var fireFreezeTimerEnabled: Boolean = false

    @ConfigValue @JvmField var sessionStatsEnabled: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeon: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeonHub: Boolean = false
    @ConfigValue @JvmField var sessionStatsResetOnRelog: Boolean = false
    @ConfigValue @JvmField var sessionStatsHudX: Int = 10
    @ConfigValue @JvmField var sessionStatsHudY: Int = 120

    @ConfigValue @JvmField var chatParty: Boolean = false
    @ConfigValue @JvmField var chatGuild: Boolean = false
    @ConfigValue @JvmField var chatOfficer: Boolean = false
    @ConfigValue @JvmField var chatPrivate: Boolean = false
    @ConfigValue @JvmField var chatAll: Boolean = false
    @ConfigValue @JvmField var pfStatsEnabled: Boolean = false
    @ConfigValue @JvmField var pfMenuEnabled: Boolean = false
    @ConfigValue @JvmField var pfShowLevelReq: Boolean = true
    @ConfigValue @JvmField var pfShowMissingClasses: Boolean = true
    @ConfigValue @JvmField var pfTooltipStats: Boolean = true
    @ConfigValue @JvmField var pfShowSecrets: Boolean = true
    @ConfigValue @JvmField var pfShowPb: Boolean = true
    @ConfigValue @JvmField var pfTooltipMissingList: Boolean = true
    @ConfigValue @JvmField var pfHighlightJoinable: Boolean = true
    @ConfigValue @JvmField var pfHighlightNonCata50: Boolean = false
    @ConfigValue @JvmField var pfMyClass: String = "Auto"
    @ConfigValue @JvmField var pfListPanel: Boolean = false
    @ConfigValue @JvmField var pfListWorstPb: Boolean = true
    @ConfigValue @JvmField var pfListNotes: Boolean = true
    @ConfigValue @JvmField var pfListMaxRows: Int = 10
    @ConfigValue @JvmField var pfListClickJoin: Boolean = false
    @ConfigValue @JvmField var pfListX: Int = 6
    @ConfigValue @JvmField var pfListY: Int = 45
    @ConfigValue @JvmField var pfFilterFloor: Int = 0
    @ConfigValue @JvmField var pfFilterMode: Int = 0
    @ConfigValue @JvmField var pfFilterClass: Int = 0
    @ConfigValue @JvmField var pfFilterHideFull: Boolean = false
    @ConfigValue @JvmField var pfFilterMaxLevel: Int = 0
    @ConfigValue @JvmField var pfAutoKick: Boolean = false
    @ConfigValue @JvmField var pfAutoKickMaster: Boolean = true
    @ConfigValue @JvmField var pfAutoKickFloor: Int = 7
    @ConfigValue @JvmField var pfAutoKickMaxSeconds: Int = 400
    @ConfigValue @JvmField var pfAutoKickMinSecretsK: Int = 0
    @ConfigValue @JvmField var pfAutoKickInform: Boolean = false
    @ConfigValue @JvmField var pfAutoKickArcherCata:  Int = 0
    @ConfigValue @JvmField var pfAutoKickArcherSb:    Int = 0
    @ConfigValue @JvmField var pfAutoKickArcherMp:    Int = 0
    @ConfigValue @JvmField var pfAutoKickBerserkCata: Int = 0
    @ConfigValue @JvmField var pfAutoKickBerserkSb:   Int = 0
    @ConfigValue @JvmField var pfAutoKickBerserkMp:   Int = 0
    @ConfigValue @JvmField var pfAutoKickHealerCata:  Int = 0
    @ConfigValue @JvmField var pfAutoKickHealerSb:    Int = 0
    @ConfigValue @JvmField var pfAutoKickHealerMp:    Int = 0
    @ConfigValue @JvmField var pfAutoKickMageCata:    Int = 0
    @ConfigValue @JvmField var pfAutoKickMageSb:      Int = 0
    @ConfigValue @JvmField var pfAutoKickMageMp:      Int = 0
    @ConfigValue @JvmField var pfAutoKickTankCata:    Int = 0
    @ConfigValue @JvmField var pfAutoKickTankSb:      Int = 0
    @ConfigValue @JvmField var pfAutoKickTankMp:      Int = 0
    @ConfigValue @JvmField var chatFeatureEnabled: Boolean = true

    @ConfigValue @JvmField var chatCompact: Boolean = false

    @ConfigValue @JvmField var infiniteChatHistory: Boolean = false
    @ConfigValue @JvmField var infiniteChatHistoryLimit: Int = 2000

    @ConfigValue @JvmField var chatSearch: Boolean = false

    @ConfigValue @JvmField var chatPeek: Boolean = true

    @ConfigValue @JvmField var bridgeBotEnabled: Boolean = false
    @ConfigValue @JvmField var bridgeBotName: String = ""

    @ConfigValue @JvmField var twitchBridgeEnabled: Boolean = false

    @ConfigValue @JvmField var compactTabEnabled: Boolean = false
    @ConfigValue @JvmField var compactTabOpacity: Int = 70
    @ConfigValue @JvmField var compactTabStatBarEnabled: Boolean = true
    @ConfigValue @JvmField var compactTabStatBarPosition: String = "TOP"
    @ConfigValue @JvmField var compactTabSortMode: String = "Rank (Default)"

    @ConfigValue @JvmField var partyCommandsEnabled: Boolean = true
    @ConfigValue @JvmField var pcAllinvite: Boolean = false
    @ConfigValue @JvmField var pcPb: Boolean = false
    @ConfigValue @JvmField var pcCata: Boolean = false
    @ConfigValue @JvmField var pcRtca: Boolean = false
    @ConfigValue @JvmField var pcDprofit: Boolean = false
    @ConfigValue @JvmField var pcCrit: Boolean = false
    @ConfigValue @JvmField var pcRtc: Boolean = false
    @ConfigValue @JvmField var pcCrtc: Boolean = false
    @ConfigValue @JvmField var pcHelp: Boolean = false
    @ConfigValue @JvmField var pcNw: Boolean = false
    @ConfigValue @JvmField var pcBank: Boolean = false
    @ConfigValue @JvmField var pcPowder: Boolean = false
    @ConfigValue @JvmField var pcLevel: Boolean = false
    @ConfigValue @JvmField var pcFarming: Boolean = false
    @ConfigValue @JvmField var pcVisitor: Boolean = false
    @ConfigValue @JvmField var pcNuc: Boolean = false
    @ConfigValue @JvmField var pcWorm: Boolean = false

    @ConfigValue @JvmField var smartCopyChat: Boolean = false

    @ConfigValue @JvmField var modPrefixEnabled: Boolean = false
    @ConfigValue @JvmField var modPrefix: String = "FM"

    @ConfigValue @JvmField var remoteNicksEnabled: Boolean = false

    @ConfigValue @JvmField var playerSizeEnabled: Boolean = false
    @ConfigValue @JvmField var playerSizeScaleX: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleY: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleZ: Double = 1.0
    @ConfigValue @JvmField var playerSizeShared: Boolean = false

    @ConfigValue @JvmField var chatFilterEnabled: Boolean = false
    @ConfigValue @JvmField var cfKillCombo: Boolean = true
    @ConfigValue @JvmField var cfBossMessages: Boolean = false
    @ConfigValue @JvmField var cfFriendJoinLeave: Boolean = false
    @ConfigValue @JvmField var cfBazaar: Boolean = false
    @ConfigValue @JvmField var cfWarping: Boolean = false
    @ConfigValue @JvmField var cfNoammSpam: Boolean = false
    @ConfigValue @JvmField var cfCollapseBlank: Boolean = false
    @ConfigValue @JvmField var cfCustom: Boolean = false
    @ConfigValue @JvmField var cfCustomPatterns: String = ""

    @ConfigValue @JvmField var explosiveShotEnabled: Boolean = false
    @ConfigValue @JvmField var explosiveShotShowTitle: Boolean = true
    @ConfigValue @JvmField var explosiveShotChatMessage: Boolean = false
    @ConfigValue @JvmField var explosiveShotAnnounceParty: Boolean = false

    @ConfigValue @JvmField var nickColorStart: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var nickColorMid: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var nickColorEnd: Int = 0xFF5555FF.toInt()
    @ConfigValue @JvmField var nickCustomName: String = ""
    @ConfigValue @JvmField var nickColorMode: String = "GRADIENT"

    @ConfigValue @JvmField var nickPreviewEnabled: Boolean = false
    @ConfigValue @JvmField var nickPreviewYOffset: Double = 0.0

    @ConfigValue @JvmField var nametagStatsEnabled: Boolean = false
    @ConfigValue @JvmField var nametagStatsShowSelf: Boolean = false
    @ConfigValue @JvmField var nametagStatsShowNetworth: Boolean = true
    @ConfigValue @JvmField var nametagStatsShowCataLevel: Boolean = true
    @ConfigValue @JvmField var nametagStatsShowSecretAvg: Boolean = true
    @ConfigValue @JvmField var nametagStatsShowSkillAvg: Boolean = true
    @ConfigValue @JvmField var nametagStatsAbove: Boolean = true

    @ConfigValue @JvmField var prestigeColorsEnabled: Boolean = false
    @ConfigValue @JvmField var prestigeColorsNametags: Boolean = true
    @ConfigValue @JvmField var prestigeColorsTab: Boolean = true
    @ConfigValue @JvmField var prestigeColorsChat: Boolean = true
    @ConfigValue @JvmField var prestigeColorsGradientTiers: Boolean = true
    @ConfigValue @JvmField var prestigeColorsAnimated: Boolean = true
    @ConfigValue @JvmField var prestigeColorsAnimSpeed: Double = 1.0
    @ConfigValue @JvmField var prestigeColorsAnimStyle: String = "FADE"

    // Badges: server-authoritative (grant/revoke/enable/order all come from the backend, via the
    // local admin dashboard — see BadgeManager/BadgeRegistry). These toggles only control whether
    // the client bothers rendering them, never which badges exist or who has them.
    @ConfigValue @JvmField var badgesEnabled: Boolean = true
    @ConfigValue @JvmField var badgesOnNametags: Boolean = true
    @ConfigValue @JvmField var badgesOnTab: Boolean = true
    @ConfigValue @JvmField var badgesInChat: Boolean = true

    @ConfigValue @JvmField var crosshairEnabled: Boolean = false
    @ConfigValue @JvmField var crosshairMode: String = "Preset"
    @ConfigValue @JvmField var crosshairImageSelection: String = "No image"
    @ConfigValue @JvmField var crosshairPreset: String = "Cross"
    @ConfigValue @JvmField var crosshairColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var crosshairScale: Double = 1.0
    @ConfigValue @JvmField var crosshairHideInF3: Boolean = true

    @ConfigValue @JvmField var disableFrontFacingCamera: Boolean = false

    @ConfigValue @JvmField var dungeonWaypointsEnabled: Boolean = true

    @ConfigValue @JvmField var enableM7LeverWaypoints: Boolean = false
    @ConfigValue @JvmField var m7LeverWaypointColor: Int = 0xFFFF0086.toInt()
    @ConfigValue @JvmField var m7LeverWaypointMode: Int = 2
    @ConfigValue @JvmField var m7LeverWaypointOpacity: Int = 50

    @ConfigValue @JvmField var enableStarredMobHighlight: Boolean = false
    @ConfigValue @JvmField var starredMobHighlightColor: Int = 0x80FFAA00.toInt()

    enum class PriceMode {
        INSTASELL,
        SELL_OFFER,
        NPC_SELL
    }
    @ConfigValue @JvmField var trackerPriceModeEnum: PriceMode = PriceMode.INSTASELL
    @ConfigValue @JvmField var pcCorpse: Boolean = false

    @ConfigValue @JvmField var cooldownInInventory: Boolean = false

    @ConfigValue @JvmField var sessionStatsScale: Double = 1.0
    @ConfigValue @JvmField var petHudScale: Double = 1.0
    @ConfigValue @JvmField var soulflowHudScale: Double = 1.0
    @ConfigValue @JvmField var pcSecrets: Boolean = false
    @ConfigValue @JvmField var pcRuns: Boolean = false
    @ConfigValue @JvmField var pcJoinFloor: Boolean = false
    @ConfigValue @JvmField var pcFps: Boolean = false
    @ConfigValue @JvmField var pcTps: Boolean = false
    @ConfigValue @JvmField var pcPing: Boolean = false
    @ConfigValue @JvmField var pcDisband: Boolean = false
    @ConfigValue @JvmField var pcMp: Boolean = false
    @ConfigValue @JvmField var pcCollection: Boolean = false

    @ConfigValue @JvmField var pcActionKick: Boolean = false
    @ConfigValue @JvmField var pcActionWarp: Boolean = false
    @ConfigValue @JvmField var pcActionTransfer: Boolean = false
    @ConfigValue @JvmField var pcActionPromote: Boolean = false
    @ConfigValue @JvmField var pcActionDemote: Boolean = false
    @ConfigValue @JvmField var pcPartyActionsMode: String = "self"
    @ConfigValue @JvmField var pcPartyActionsWhitelist: String = ""
    @ConfigValue @JvmField var pcPartyActionsBlacklist: String = ""

    @ConfigValue @JvmField var pcKickListEnabled: Boolean = false
    @ConfigValue @JvmField var pcKickList: String = ""

    @ConfigValue @JvmField var lootTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var lootTrackerX: Int = -1
    @ConfigValue @JvmField var lootTrackerY: Int = -1

    @ConfigValue @JvmField var simonSaysEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysHudEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysPartyChat: Boolean = false
    @ConfigValue @JvmField var simonSaysFailEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysFailMessage: String = "Simon Says: FAILED!"
    @ConfigValue @JvmField var simonSaysHudX: Int = 10
    @ConfigValue @JvmField var simonSaysHudY: Int = 360
    @ConfigValue @JvmField var simonSaysHudScale: Double = 1.0

    @ConfigValue @JvmField var simon1Enabled: Boolean = false
    @ConfigValue @JvmField var simon1Message: String = "Simon Says: 1/5"
    @ConfigValue @JvmField var simon2Enabled: Boolean = false
    @ConfigValue @JvmField var simon2Message: String = "Simon Says: 2/5"
    @ConfigValue @JvmField var simon3Enabled: Boolean = false
    @ConfigValue @JvmField var simon3Message: String = "Simon Says: 3/5"
    @ConfigValue @JvmField var simon4Enabled: Boolean = false
    @ConfigValue @JvmField var simon4Message: String = "Simon Says: 4/5"
    @ConfigValue @JvmField var simon5Enabled: Boolean = false
    @ConfigValue @JvmField var simon5Message: String = "Simon Says: 5/5"

    @ConfigValue @JvmField var ssProgressShowProgress: Boolean = true
    @ConfigValue @JvmField var ssProgressProgressText: String = "SS at (n)/5"
    @ConfigValue @JvmField var ssProgressProgressColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var ssProgressShowCompleted: Boolean = true
    @ConfigValue @JvmField var ssProgressCompletedText: String = "SS Completed"
    @ConfigValue @JvmField var ssProgressCompletedColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var ssProgressShowReset: Boolean = true
    @ConfigValue @JvmField var ssProgressResetText: String = "SS RESET!"
    @ConfigValue @JvmField var ssProgressResetColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var pbPaceEnabled: Boolean = false
    @ConfigValue @JvmField var pbPaceHudX: Int = 10
    @ConfigValue @JvmField var pbPaceHudY: Int = 300
    @ConfigValue @JvmField var pbPaceScale: Double = 1.0

    @ConfigValue @JvmField var classColoredBootsEnabled: Boolean = false

    @ConfigValue @JvmField var wardrobeHotkeysEnabled: Boolean = false
    @ConfigValue @JvmField var wardrobeHotkeysAutoClose: Boolean = true

    @ConfigValue @JvmField var loadoutTitleEnabled: Boolean = false

    @ConfigValue @JvmField var autoSprintEnabled: Boolean = false

    @ConfigValue @JvmField var soundMasterEnabled: Boolean = true
    @ConfigValue @JvmField var soundMasterVolume: Int = 100

    @ConfigValue @JvmField var puzzleSolversEnabled: Boolean = false
    @ConfigValue @JvmField var puzzleSolverStyle: String = "Filled Outline"
    @ConfigValue @JvmField var weirdosSolver: Boolean = true
    @ConfigValue @JvmField var weirdosCorrectColor: Int = 0xB355FF55.toInt()
    @ConfigValue @JvmField var weirdosWrongColor: Int = 0xB3FF5555.toInt()
    @ConfigValue @JvmField var blazeSolver: Boolean = true
    @ConfigValue @JvmField var blazeFirstColor: Int = 0xC055FF55.toInt()
    @ConfigValue @JvmField var blazeSecondColor: Int = 0xC0FFAA00.toInt()
    @ConfigValue @JvmField var blazeOtherColor: Int = 0x66FFFFFF
    @ConfigValue @JvmField var blazeLine: Boolean = true
    @ConfigValue @JvmField var blazeLineCount: Int = 1
    @ConfigValue @JvmField var quizSolver: Boolean = true
    @ConfigValue @JvmField var quizColor: Int = 0xC055FF55.toInt()
    @ConfigValue @JvmField var waterSolver: Boolean = true
    @ConfigValue @JvmField var waterOptimized: Boolean = false
    @ConfigValue @JvmField var waterFirstColor: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var waterSecondColor: Int = 0xC0FFAA00.toInt()
    @ConfigValue @JvmField var beamsSolver: Boolean = true
    @ConfigValue @JvmField var beamsTracer: Boolean = false
    @ConfigValue @JvmField var tpMazeSolver: Boolean = true
    @ConfigValue @JvmField var tpMazeNextColor: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var tpMazeVisitedColor: Int = 0x80FF5555.toInt()
    @ConfigValue @JvmField var tttSolver: Boolean = true
    @ConfigValue @JvmField var tttColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var tttPreventMissClick: Boolean = true
    @ConfigValue @JvmField var tttPrediction: Boolean = false
    @ConfigValue @JvmField var tttPredictionColor: Int = 0x99FFAA00.toInt()
    @ConfigValue @JvmField var boulderSolver: Boolean = true
    @ConfigValue @JvmField var boulderShowAll: Boolean = true
    @ConfigValue @JvmField var boulderColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var iceFillSolver: Boolean = true
    @ConfigValue @JvmField var iceFillOptimized: Boolean = false
    @ConfigValue @JvmField var iceFillColor: Int = 0xFFFF55FF.toInt()

    @ConfigValue @JvmField var arrowAlignEnabled: Boolean = false
    @ConfigValue @JvmField var arrowAlignBlockWrong: Boolean = false

    @ConfigValue @JvmField var timeChangerEnabled: Boolean = false
    @ConfigValue @JvmField var timeChangerMode: String = "Day"

    @ConfigValue @JvmField var arrowHitSoundEnabled: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundSuppress: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundName: String = "Note: Harp"
    @ConfigValue @JvmField var arrowHitSoundVolume: Int = 100
    @ConfigValue @JvmField var arrowHitSoundPitch: Double = 1.4

    @ConfigValue @JvmField var dungeonBreakerEnabled: Boolean = false
    @ConfigValue @JvmField var dungeonBreakerHudEnabled: Boolean = true
    @ConfigValue @JvmField var dungeonBreakerHudX: Int = 10
    @ConfigValue @JvmField var dungeonBreakerHudY: Int = 80
    @ConfigValue @JvmField var dungeonBreakerHudScale: Double = 1.0
    @ConfigValue @JvmField var dungeonBreakerDungeonOnly: Boolean = true
    @ConfigValue @JvmField var dungeonBreakerSoundEnabled: Boolean = false
    @ConfigValue @JvmField var dungeonBreakerSoundName: String = "Note: Harp"
    @ConfigValue @JvmField var dungeonBreakerSoundVolume: Int = 100
    @ConfigValue @JvmField var dungeonBreakerSoundPitch: Double = 1.6

    @ConfigValue @JvmField var noCursorReset: Boolean = false
    @ConfigValue @JvmField var noCursorResetMs: Int = 150
    @ConfigValue @JvmField var arrowFixEnabled: Boolean = false
    @ConfigValue @JvmField var monoAudioEnabled: Boolean = false
    @ConfigValue @JvmField var swordBlockingEnabled: Boolean = false

    @ConfigValue @JvmField var actionBarEnabled: Boolean = false
    @ConfigValue @JvmField var abHideHealth: Boolean = false
    @ConfigValue @JvmField var abHideDefense: Boolean = false
    @ConfigValue @JvmField var abHideTrueDefense: Boolean = false
    @ConfigValue @JvmField var abHideMana: Boolean = false
    @ConfigValue @JvmField var abHideOverflowMana: Boolean = false
    @ConfigValue @JvmField var abHideManaUse: Boolean = false
    @ConfigValue @JvmField var abHideSkillXp: Boolean = false
    @ConfigValue @JvmField var abHideTermLaser: Boolean = false
    @ConfigValue @JvmField var abHideArmorStacks: Boolean = false
    @ConfigValue @JvmField var abHideRagAxeTimer: Boolean = false
    @ConfigValue @JvmField var abHideBits: Boolean = false
    @ConfigValue @JvmField var abHideSecrets: Boolean = false
    @ConfigValue @JvmField var abHideVitality: Boolean = false
    @ConfigValue @JvmField var abHideXpBar: Boolean = false
    @ConfigValue @JvmField var abHideArmorRow: Boolean = false
    @ConfigValue @JvmField var abHideAbsorption: Boolean = false

    @ConfigValue @JvmField var animEnabled: Boolean = false
    @ConfigValue @JvmField var animItemScale: Double = 0.0
    @ConfigValue @JvmField var animX: Double = 0.0
    @ConfigValue @JvmField var animY: Double = 0.0
    @ConfigValue @JvmField var animZ: Double = 0.0
    @ConfigValue @JvmField var animRotX: Double = 0.0
    @ConfigValue @JvmField var animRotY: Double = 0.0
    @ConfigValue @JvmField var animRotZ: Double = 0.0
    @ConfigValue @JvmField var animSwingX: Double = 1.0
    @ConfigValue @JvmField var animSwingY: Double = 1.0
    @ConfigValue @JvmField var animSwingZ: Double = 1.0
    @ConfigValue @JvmField var animSwingSpeed: Double = 0.0
    @ConfigValue @JvmField var animIgnoreHaste: Boolean = false
    @ConfigValue @JvmField var animNoEquip: Boolean = false
    @ConfigValue @JvmField var animNoHandMove: Boolean = false

    @ConfigValue @JvmField var storageOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var storageViewerColumns: Int = 3
    @ConfigValue @JvmField var storageMaxHeight: Int = 360
    @ConfigValue @JvmField var storageHideNonMatching: Boolean = false
    @ConfigValue @JvmField var storageOverlayScale: Double = 1.0
    @ConfigValue @JvmField var storageScrollSpeed: Int = 10
    @ConfigValue @JvmField var storageRetainScroll: Boolean = true
    @ConfigValue @JvmField var containerValueEnabled: Boolean = false

    @ConfigValue @JvmField var leapMenuEnabled: Boolean = false
    @ConfigValue @JvmField var leapMenuScale: Int = 135
    @ConfigValue @JvmField var leapMenuLeftClickOnly: Boolean = false
    @ConfigValue @JvmField var leapMenuKeybinds: Boolean = true
    @ConfigValue @JvmField var leapMenuTintDead: Boolean = true
    @ConfigValue @JvmField var leapMenuShowName: Boolean = true
    @ConfigValue @JvmField var leapMenuShowClass: Boolean = true
    @ConfigValue @JvmField var leapMenuSort: Int = 0
    @ConfigValue @JvmField var leapMenuClassOrder: String = "MAGE,BERSERK,ARCHER,HEALER,TANK"
    @ConfigValue @JvmField var leapMenuMap: Boolean = false
    @ConfigValue @JvmField var leapMenuMapAfterBR: Boolean = false

    @ConfigValue @JvmField var itemTooltipPrices: Boolean = false
    @ConfigValue @JvmField var itemTooltipNpcSell: Boolean = false

    @ConfigValue @JvmField var auctionPriceAutofillEnabled: Boolean = false
    @ConfigValue @JvmField var auctionAutofillPercent: Int = 5

    @ConfigValue @JvmField var tooltipScrollEnabled: Boolean = false
    @ConfigValue @JvmField var tooltipScrollScale: Int = 100
    @ConfigValue @JvmField var tooltipScrollSpeed: Int = 3

    @ConfigValue @JvmField var etherwarpHelperEnabled: Boolean = false
    @ConfigValue @JvmField var etherwarpShowGuess: Boolean = true
    @ConfigValue @JvmField var etherwarpColor: Int = 0x80FFAA00.toInt()
    @ConfigValue @JvmField var etherwarpShowFail: Boolean = true
    @ConfigValue @JvmField var etherwarpFailColor: Int = 0x80FF5555.toInt()
    @ConfigValue @JvmField var etherwarpFullBlock: Boolean = false
    @ConfigValue @JvmField var etherwarpDepth: Boolean = false
    @ConfigValue @JvmField var etherwarpRange: Int = 61
    @ConfigValue @JvmField var etherwarpSoundEnabled: Boolean = false
    @ConfigValue @JvmField var etherwarpSoundName: String = "Blaze Hit"
    @ConfigValue @JvmField var etherwarpSoundVolume: Int = 100
    @ConfigValue @JvmField var etherwarpSoundPitch: Double = 1.0

    @ConfigValue @JvmField var extraStatsEnabled: Boolean = false
    @ConfigValue @JvmField var extraStatsBits: Boolean = true
    @ConfigValue @JvmField var extraStatsClassExp: Boolean = true
    @ConfigValue @JvmField var extraStatsCombat: Boolean = true
    @ConfigValue @JvmField var extraStatsTeammates: Boolean = false

    @ConfigValue @JvmField var lavaToWaterEnabled: Boolean = false
    @ConfigValue @JvmField var lavaToWaterTint: Boolean = false
    @ConfigValue @JvmField var lavaToWaterColor: Int = 0xFF3F76E4.toInt()
    @ConfigValue @JvmField var lavaToWaterHideFog: Boolean = true

    @ConfigValue @JvmField var blockOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var blockOverlayMode: Int = 2
    @ConfigValue @JvmField var blockOverlayFillColor: Int = 0x40FFFFFF
    @ConfigValue @JvmField var blockOverlayOutlineColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var blockOverlayOpacity: Int = 100
    @ConfigValue @JvmField var blockOverlayPhase: Boolean = false
    @ConfigValue @JvmField var blockOverlayOutlineThickness: Double = 0.02

    @ConfigValue @JvmField var witherEspEnabled: Boolean = false
    @ConfigValue @JvmField var witherEspMaxorColor: Int = 0xFF5804A4.toInt()
    @ConfigValue @JvmField var witherEspStormColor: Int = 0xFF00D0FF.toInt()
    @ConfigValue @JvmField var witherEspGoldorColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var witherEspNecronColor: Int = 0xFFFF0000.toInt()

    @ConfigValue @JvmField var relicTimerHudX: Int = 10
    @ConfigValue @JvmField var relicTimerHudY: Int = 180
    @ConfigValue @JvmField var relicTimerScale: Double = 1.0

    @ConfigValue @JvmField var itemQualityTooltip: Boolean = false

    @ConfigValue @JvmField var gyroHelperEnabled: Boolean = false
    @ConfigValue @JvmField var gyroBoxColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gyroRingColor: Int = 0xFF55FFFF.toInt()

    @ConfigValue @JvmField var mageBeamEnabled: Boolean = false
    @ConfigValue @JvmField var mageBeamColor: Int = 0xFFAA0000.toInt()
    @ConfigValue @JvmField var mageBeamDurationTicks: Int = 40
    @ConfigValue @JvmField var mageBeamHideParticles: Boolean = true
    @ConfigValue @JvmField var mageBeamDepth: Boolean = true

    @ConfigValue @JvmField var springBootsEnabled: Boolean = false
    @ConfigValue @JvmField var springBootsShowBlocks: Boolean = false
    @ConfigValue @JvmField var springBootsBox: Boolean = true
    @ConfigValue @JvmField var springBootsBoxColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var springBootsHudX: Int = 10
    @ConfigValue @JvmField var springBootsHudY: Int = 200
    @ConfigValue @JvmField var springBootsScale: Double = 1.0

    @ConfigValue @JvmField var melodyMessageEnabled: Boolean = false
    @ConfigValue @JvmField var melodyMessageOnOpen: Boolean = true
    @ConfigValue @JvmField var melodyMessageText: String = "Melody Terminal start!"
    @ConfigValue @JvmField var melodyMessageProgress: Boolean = false

    @ConfigValue @JvmField var simonSolverEnabled: Boolean = false
    @ConfigValue @JvmField var simonSolverDepth: Boolean = false
    @ConfigValue @JvmField var simonSolverColor1: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var simonSolverColor2: Int = 0x80FFAA00.toInt()
    @ConfigValue @JvmField var simonSolverColor3: Int = 0x80FF5555.toInt()
    @ConfigValue @JvmField var simonSolverBlockWrong: Boolean = false

    @ConfigValue @JvmField var arrowsDeviceEnabled: Boolean = false
    @ConfigValue @JvmField var arrowsDeviceDepth: Boolean = true
    @ConfigValue @JvmField var arrowsDeviceCompleteAlert: Boolean = true
    @ConfigValue @JvmField var arrowsDeviceTargetColor: Int = 0x80FF55FF.toInt()
    @ConfigValue @JvmField var arrowsDeviceMarkedColor: Int = 0x8055FFFF.toInt()
    @ConfigValue @JvmField var arrowsDeviceShowAim: Boolean = false
    @ConfigValue @JvmField var arrowsDeviceAim1Color: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var arrowsDeviceAim2Color: Int = 0x80FFAA00.toInt()
    @ConfigValue @JvmField var arrowsDeviceAim3Color: Int = 0x80FF5555.toInt()

    @ConfigValue @JvmField var ragnarockEnabled: Boolean = false
    @ConfigValue @JvmField var ragnarockCastAlert: Boolean = true
    @ConfigValue @JvmField var ragnarockCancelAlert: Boolean = true
    @ConfigValue @JvmField var ragnarockAnnounceParty: Boolean = false
    @ConfigValue @JvmField var ragnarockTimer: Boolean = false
    @ConfigValue @JvmField var ragnarockTimerHudX: Int = 10
    @ConfigValue @JvmField var ragnarockTimerHudY: Int = 150
    @ConfigValue @JvmField var ragnarockTimerScale: Double = 1.5
    @ConfigValue @JvmField var p5RagEnabled: Boolean = false

    @ConfigValue @JvmField var terracottaTimerEnabled: Boolean = false

    @ConfigValue @JvmField var lividSolverEnabled: Boolean = false
    @ConfigValue @JvmField var lividSolverColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var croesusProfitEnabled: Boolean = false

    @ConfigValue @JvmField var spiritBearEnabled: Boolean = false
    @ConfigValue @JvmField var spiritBearHudX: Int = 10
    @ConfigValue @JvmField var spiritBearHudY: Int = 165
    @ConfigValue @JvmField var spiritBearScale: Double = 1.5

    @ConfigValue @JvmField var witherDragonsEnabled: Boolean = false
    @ConfigValue @JvmField var witherDragonsTimerWorld: Boolean = true
    @ConfigValue @JvmField var witherDragonsTimerHud: Boolean = true
    @ConfigValue @JvmField var witherDragonsTimerStyle: Int = 0
    @ConfigValue @JvmField var witherDragonsHealth: Boolean = true
    @ConfigValue @JvmField var witherDragonsSkipBox: Boolean = true
    @ConfigValue @JvmField var witherDragonsBoxFill: Boolean = false
    @ConfigValue @JvmField var witherDragonsTracer: Boolean = false
    @ConfigValue @JvmField var witherDragonsAimAssist: Boolean = false
    @ConfigValue @JvmField var witherDragonsAimColor: Int = 0xFF00FFFF.toInt()
    @ConfigValue @JvmField var witherDragonsSendStats: Boolean = true
    @ConfigValue @JvmField var witherDragonsHudX: Int = -1
    @ConfigValue @JvmField var witherDragonsHudY: Int = 100
    @ConfigValue @JvmField var witherDragonsHudScale: Double = 2.0
    @ConfigValue @JvmField var witherDragonsSpawnAlert: Boolean = false
    @ConfigValue @JvmField var witherDragonsSpawnSound: Boolean = false
    @ConfigValue @JvmField var witherDragonsSpawnParty: Boolean = false
    @ConfigValue @JvmField var witherDragonsPriority: Boolean = false
    @ConfigValue @JvmField var witherDragonsNormalPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsEasyPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsSoloDebuff: Int = 0
    @ConfigValue @JvmField var witherDragonsSoloDebuffAll: Boolean = true

    @ConfigValue @JvmField var architectDraftRefill: Boolean = false

    @ConfigValue @JvmField var tacTimerEnabled: Boolean = false
    @ConfigValue @JvmField var tacTimerReverse: Boolean = false
    @ConfigValue @JvmField var tacTimerPrefix: Boolean = true
    @ConfigValue @JvmField var tacTimerSuffix: Boolean = false
    @ConfigValue @JvmField var tacTimerWaypoint: Boolean = false
    @ConfigValue @JvmField var tacTimerColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var tacTimerHudX: Int = 10
    @ConfigValue @JvmField var tacTimerHudY: Int = 180
    @ConfigValue @JvmField var tacTimerScale: Double = 1.0

    @ConfigValue @JvmField var dungeonAbilitiesEnabled: Boolean = false

    @ConfigValue @JvmField var slotBindsEnabled: Boolean = false
    @ConfigValue @JvmField var slotBindsShow: Boolean = true
    @ConfigValue @JvmField var slotBindsBorder: Boolean = true
    @ConfigValue @JvmField var slotBindsLine: Boolean = true
    @ConfigValue @JvmField var slotBindsHoverOnly: Boolean = false
    @ConfigValue @JvmField var slotBindsColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var slotBindsProfile: String = "Default"

    @ConfigValue @JvmField var inventorySearchEnabled: Boolean = false
    @ConfigValue @JvmField var inventorySearchAlwaysShow: Boolean = false
    @ConfigValue @JvmField var inventorySearchHighlight: Boolean = true
    @ConfigValue @JvmField var inventorySearchHighlightColor: Int = 0xFF55FF55.toInt()

    @ConfigValue @JvmField var cameraTweaksEnabled: Boolean = false
    @ConfigValue @JvmField var cameraFullBright: Boolean = false
    @ConfigValue @JvmField var cameraNoBlindness: Boolean = false
    @ConfigValue @JvmField var cameraNoNausea: Boolean = false

    @ConfigValue @JvmField var practiceServerIps: String = "hypixelp3sim.zapto.org"

    @ConfigValue @JvmField var terminalSolverEnabled: Boolean = false
    @ConfigValue @JvmField var terminalBlockWrongClicks: Boolean = true
    @ConfigValue @JvmField var terminalStopMelody: Boolean = false
    @ConfigValue @JvmField var terminalSolverSound: Boolean = true
    @ConfigValue @JvmField var terminalHighlightColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalOrderColor1: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalOrderColor2: Int = 0x9922AA22.toInt()
    @ConfigValue @JvmField var terminalOrderColor3: Int = 0x99116611.toInt()
    @ConfigValue @JvmField var terminalRubixColor: Int = 0x9900AAAA.toInt()
    @ConfigValue @JvmField var terminalMelodyColor: Int = 0x99AA00AA.toInt()
    @ConfigValue @JvmField var terminalStopTooltips: Boolean = true
    @ConfigValue @JvmField var terminalShowNumbers: Boolean = true
    @ConfigValue @JvmField var terminalHideWrong: Boolean = false
    @ConfigValue @JvmField var terminalFirstClickProtMs: Int = 500
    @ConfigValue @JvmField var terminalMiddleClickGui: Boolean = false
    @ConfigValue @JvmField var terminalStartsWithColor: Int = 0x9900AAAA.toInt()
    @ConfigValue @JvmField var terminalSelectColor: Int = 0x9900AAAA.toInt()
    @ConfigValue @JvmField var terminalRubixColor2: Int = 0x99006464.toInt()
    @ConfigValue @JvmField var terminalRubixNeg1: Int = 0x99AA5500.toInt()
    @ConfigValue @JvmField var terminalRubixNeg2: Int = 0x99D25500.toInt()
    @ConfigValue @JvmField var terminalMelodyPointerColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalWrongCover: Int = 0xF01A1A22.toInt()
    @ConfigValue @JvmField var termSimPbs: String = ""
    @ConfigValue @JvmField var terminalRenderMode: Int = 0
    @ConfigValue @JvmField var terminalCustomScale: Double = 1.0
    @ConfigValue @JvmField var terminalCustomRoundness: Int = 8
    @ConfigValue @JvmField var terminalCustomGap: Int = 4
    @ConfigValue @JvmField var terminalCustomBg: Int = 0xFF141414.toInt()

    @ConfigValue @JvmField var warpCooldownSeconds: Int = 30
    @ConfigValue @JvmField var warpCooldownColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var warpAnnounceKick: Boolean = false
    @ConfigValue @JvmField var warpKickText: String = "Kicked!"
    @ConfigValue @JvmField var warpCooldownHudX: Int = 10
    @ConfigValue @JvmField var warpCooldownHudY: Int = 160
    @ConfigValue @JvmField var warpCooldownScale: Double = 1.0

    @ConfigValue @JvmField var blessingDisplayEnabled: Boolean = false

    @ConfigValue @JvmField var quizHudEnabled: Boolean = false
    @ConfigValue @JvmField var quizHudX: Int = 10
    @ConfigValue @JvmField var quizHudY: Int = 200
    @ConfigValue @JvmField var quizHudScale: Double = 1.5
    @ConfigValue @JvmField var blessingPower: Boolean = true
    @ConfigValue @JvmField var blessingPowerColor: Int = 0xFFAA0000.toInt()
    @ConfigValue @JvmField var blessingTime: Boolean = true
    @ConfigValue @JvmField var blessingTimeColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var blessingStone: Boolean = false
    @ConfigValue @JvmField var blessingStoneColor: Int = 0xFFAAAAAA.toInt()
    @ConfigValue @JvmField var blessingLife: Boolean = false
    @ConfigValue @JvmField var blessingLifeColor: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var blessingWisdom: Boolean = false
    @ConfigValue @JvmField var blessingWisdomColor: Int = 0xFF5555FF.toInt()
    @ConfigValue @JvmField var blessingHudX: Int = 10
    @ConfigValue @JvmField var blessingHudY: Int = 100
    @ConfigValue @JvmField var blessingScale: Double = 1.0

    @ConfigValue @JvmField var invincAnnounce: Boolean = true
    @ConfigValue @JvmField var invincShowCooldown: Boolean = true
    @ConfigValue @JvmField var invincShowWhen: String = "Any"
    @ConfigValue @JvmField var invincShowInBoss: Boolean = false
    @ConfigValue @JvmField var invincShowSpirit: Boolean = true
    @ConfigValue @JvmField var invincShowBonzo: Boolean = true
    @ConfigValue @JvmField var invincShowPhoenix: Boolean = true
    @ConfigValue @JvmField var invincHudX: Int = 10
    @ConfigValue @JvmField var invincHudY: Int = 140
    @ConfigValue @JvmField var invincScale: Double = 1.0

    @ConfigValue @JvmField var keyNotifierTitle: Boolean = true
    @ConfigValue @JvmField var keyNotifierChat: Boolean = false
    @ConfigValue @JvmField var keyNotifierSound: Boolean = true
    @ConfigValue @JvmField var keyNotifierDurationMs: Int = 2000

    @ConfigValue @JvmField var leapMessagesTitle: Boolean = true
    @ConfigValue @JvmField var leapMessagesParty: Boolean = false
    @ConfigValue @JvmField var leapMessagesSound: Boolean = true
    @ConfigValue @JvmField var leapMessagesText: String = "&b&lLEAP &r&7-> &f{name}"

    @ConfigValue @JvmField var roomTimerEnabled: Boolean = false
    @ConfigValue @JvmField var roomTimerClear: Boolean = true
    @ConfigValue @JvmField var roomTimerSecrets: Boolean = true
    @ConfigValue @JvmField var roomTimerShowTime: Boolean = true
    @ConfigValue @JvmField var roomTimerPb: Boolean = true

    @ConfigValue @JvmField var pbMessagesEnabled: Boolean = false
    @ConfigValue @JvmField var pbMessagesOnlyPb: Boolean = true
    @ConfigValue @JvmField var pbMessagesSplits: Boolean = true
    @ConfigValue @JvmField var pbMessagesGoldor: Boolean = true
    @ConfigValue @JvmField var pbMessagesTerminals: Boolean = true
    @ConfigValue @JvmField var pbMessagesRelics: Boolean = true
    @ConfigValue @JvmField var splitPbColors: Boolean = true
    @ConfigValue @JvmField var splitPbColor: Int = 0xFFFF55FF.toInt()
    @ConfigValue @JvmField var splitAvgColor: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var splitNameColors: String = ""

    @ConfigValue @JvmField var mimicAnnounceEnabled: Boolean = false
    @ConfigValue @JvmField var mimicMsgEnabled: Boolean = true
    @ConfigValue @JvmField var mimicMsgText: String = "Mimic Killed!"
    @ConfigValue @JvmField var princeMsgEnabled: Boolean = true
    @ConfigValue @JvmField var princeMsgText: String = "Prince Killed!"
    @ConfigValue @JvmField var batMsgEnabled: Boolean = true
    @ConfigValue @JvmField var batMsgText: String = "Bat Killed!"

    @ConfigValue @JvmField var autoSprintDungeonOnly: Boolean = false

    @ConfigValue @JvmField var autoRequeueDelayMs: Int = 2000

    @ConfigValue @JvmField var routeRecorderEnabled: Boolean = true
    @ConfigValue @JvmField var routeAutoLoad: Boolean = true
    @ConfigValue @JvmField var routeThroughWalls: Boolean = true
    @ConfigValue @JvmField var routeSecretsThroughWalls: Boolean = true
    @ConfigValue @JvmField var routeBoxStyle: String = "Filled Outline"
    @ConfigValue @JvmField var routeFillOpacity: Int = 30
    @ConfigValue @JvmField var routeOutlineOpacity: Int = 100
    @ConfigValue @JvmField var routeOutlineWidth: Double = 2.0
    @ConfigValue @JvmField var routeHighlightCurrent: Boolean = true
    @ConfigValue @JvmField var routeShowLines: Boolean = true
    @ConfigValue @JvmField var routeLineWidth: Double = 4.0
    @ConfigValue @JvmField var routeLineOpacity: Int = 85
    @ConfigValue @JvmField var routeLineToNext: Boolean = true
    @ConfigValue @JvmField var routeShowLabels: Boolean = true
    @ConfigValue @JvmField var routeLabelScale: Double = 1.0
    @ConfigValue @JvmField var routeColorEtherwarp: Int = 0xFFB45CFF.toInt()
    @ConfigValue @JvmField var routeColorPearl: Int = 0xFF20C0A0.toInt()
    @ConfigValue @JvmField var routeColorBreak: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var routeColorSuperboom: Int = 0xFFFF2020.toInt()
    @ConfigValue @JvmField var routeColorChest: Int = 0xFFFFAA00.toInt()
    @ConfigValue @JvmField var routeColorSecret: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var routeColorItem: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var routeColorBat: Int = 0xFFFF55FF.toInt()

    @ConfigValue @JvmField var secretClickedEnabled: Boolean = false
    @ConfigValue @JvmField var secretClickedBoxes: Boolean = true
    @ConfigValue @JvmField var secretClickedBats: Boolean = true
    @ConfigValue @JvmField var secretClickedItems: Boolean = true
    @ConfigValue @JvmField var secretClickedStyle: String = "Filled Outline"
    @ConfigValue @JvmField var secretClickedColor: Int = 0x66FFAA00
    @ConfigValue @JvmField var secretClickedLockedColor: Int = 0x66FF5555
    @ConfigValue @JvmField var secretClickedLineWidth: Double = 2.0
    @ConfigValue @JvmField var secretClickedTimeToStay: Int = 7
    @ConfigValue @JvmField var secretClickedDepthCheck: Boolean = false
    @ConfigValue @JvmField var secretClickedInBoss: Boolean = false
    @ConfigValue @JvmField var secretClickedChime: Boolean = true
    @ConfigValue @JvmField var secretClickedChimeInBoss: Boolean = false
    @ConfigValue @JvmField var secretClickedSoundName: String = "Blaze Hit"
    @ConfigValue @JvmField var secretClickedVolume: Int = 100
    @ConfigValue @JvmField var secretClickedPitch: Double = 2.0

    @ConfigValue @JvmField var customScoreboardEnabled: Boolean = false
    @ConfigValue @JvmField var customScoreboardCompactNumbers: Boolean = false
    @ConfigValue @JvmField var customScoreboardHideInDungeon: Boolean = false
    @ConfigValue @JvmField var customScoreboardOpacity: Int = 30
    @ConfigValue @JvmField var customScoreboardHudY: Int = 2
    @ConfigValue @JvmField var sbSectionDate: Boolean = true
    @ConfigValue @JvmField var sbSectionTime: Boolean = true
    @ConfigValue @JvmField var sbSectionLocation: Boolean = true
    @ConfigValue @JvmField var sbSectionPlayers: Boolean = true
    @ConfigValue @JvmField var sbSectionGameMode: Boolean = true
    @ConfigValue @JvmField var sbSectionPurse: Boolean = true
    @ConfigValue @JvmField var sbSectionBank: Boolean = true
    @ConfigValue @JvmField var sbSectionMotes: Boolean = true
    @ConfigValue @JvmField var sbSectionBits: Boolean = true
    @ConfigValue @JvmField var sbSectionCopper: Boolean = true
    @ConfigValue @JvmField var sbSectionSowdust: Boolean = true
    @ConfigValue @JvmField var sbSectionGems: Boolean = true
    @ConfigValue @JvmField var sbSectionHeat: Boolean = true
    @ConfigValue @JvmField var sbSectionCold: Boolean = true
    @ConfigValue @JvmField var sbSectionNorthStars: Boolean = true
    @ConfigValue @JvmField var sbSectionSoulflow: Boolean = true
    @ConfigValue @JvmField var sbSectionGuild: Boolean = true
    @ConfigValue @JvmField var sbSectionCookie: Boolean = true
    @ConfigValue @JvmField var sbSectionSkillAverage: Boolean = true
    @ConfigValue @JvmField var sbSectionObjective: Boolean = true
    @ConfigValue @JvmField var sbSectionSlayer: Boolean = true
    @ConfigValue @JvmField var sbSectionPowder: Boolean = true
    @ConfigValue @JvmField var sbSectionDiana: Boolean = true
    @ConfigValue @JvmField var sbSectionParty: Boolean = true
    @ConfigValue @JvmField var sbSectionEquipment: Boolean = true
    @ConfigValue @JvmField var sbSectionDungeon: Boolean = true
    @ConfigValue @JvmField var sbSectionPet: Boolean = true
    @ConfigValue @JvmField var sbSectionOther: Boolean = true
    @ConfigValue @JvmField var sbSectionTps: Boolean = true
    @ConfigValue @JvmField var sbSectionPing: Boolean = true
    @ConfigValue @JvmField var sbSectionFps: Boolean = true
    @ConfigValue @JvmField var sbSectionPetExtra: Boolean = false
    @ConfigValue @JvmField var sbSectionSkills: Boolean = false
    @ConfigValue @JvmField var sbSectionBestiary: Boolean = false
    @ConfigValue @JvmField var sbSectionCollections: Boolean = false
    @ConfigValue @JvmField var sbSectionElection: Boolean = false
    @ConfigValue @JvmField var sbSectionFireSales: Boolean = false

    @ConfigValue @JvmField var slayerSpawnAlertEnabled: Boolean = false
    @ConfigValue @JvmField var slayerMiniBossAlert: Boolean = true
    @ConfigValue @JvmField var slayerBossAlert: Boolean = true
    @ConfigValue @JvmField var slayerAlertDurationMs: Int = 1500

    @ConfigValue @JvmField var slayerCocoonAlertEnabled: Boolean = false
    @ConfigValue @JvmField var slayerCocoonAlertDurationMs: Int = 2000

    @ConfigValue @JvmField var slayerSpawnHudEnabled: Boolean = false
    @ConfigValue @JvmField var slayerSpawnHudX: Int = 10
    @ConfigValue @JvmField var slayerSpawnHudY: Int = 140
    @ConfigValue @JvmField var slayerSpawnHudScale: Double = 1.0
    @ConfigValue @JvmField var slayerSpawnOpacity: Int = 0

    @ConfigValue @JvmField var slayerStatsHudEnabled: Boolean = false
    @ConfigValue @JvmField var slayerStatsShowXp: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowKills: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowXpHr: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowKillsHr: Boolean = true
    @ConfigValue @JvmField var slayerStatsOpacity: Int = 50
    @ConfigValue @JvmField var slayerStatsHudX: Int = 10
    @ConfigValue @JvmField var slayerStatsHudY: Int = 170
    @ConfigValue @JvmField var slayerStatsHudScale: Double = 1.0

    @ConfigValue @JvmField var slayerTimerEnabled: Boolean = false
    @ConfigValue @JvmField var slayerTimerStartMode: String = "Spawned"
    @ConfigValue @JvmField var slayerTimerShowCurrent: Boolean = true
    @ConfigValue @JvmField var slayerTimerShowPb: Boolean = true
    @ConfigValue @JvmField var slayerTimerShowNewPb: Boolean = true
    @ConfigValue @JvmField var slayerTimerShowCycle: Boolean = true
    @ConfigValue @JvmField var slayerTimerHudX: Int = 10
    @ConfigValue @JvmField var slayerTimerHudY: Int = 255
    @ConfigValue @JvmField var slayerTimerHudScale: Double = 1.0
    @ConfigValue @JvmField var slayerTimerOpacity: Int = 0

    @ConfigValue @JvmField var slayerProfitEnabled: Boolean = false
    @ConfigValue @JvmField var slayerProfitLines: Int = 10
    @ConfigValue @JvmField var slayerProfitIdleSeconds: Int = 60
    @ConfigValue @JvmField var slayerProfitOpacity: Int = 56
    @ConfigValue @JvmField var slayerProfitHudX: Int = 10
    @ConfigValue @JvmField var slayerProfitHudY: Int = 300
    @ConfigValue @JvmField var slayerProfitHudScale: Double = 1.0
    @ConfigValue @JvmField var slayerProfitDisplayMode: String = "Total"
    @ConfigValue @JvmField var slayerProfitShowHidden: Boolean = false
    @ConfigValue @JvmField var slayerProfitMinValue: Int = 0
    @ConfigValue @JvmField var slayerProfitCountKillCoins: Boolean = true

    @ConfigValue @JvmField var miningProfitEnabled: Boolean = false
    @ConfigValue @JvmField var miningProfitHudX: Int = 10
    @ConfigValue @JvmField var miningProfitHudY: Int = 100
    @ConfigValue @JvmField var miningProfitHudScale: Double = 1.0

    @ConfigValue @JvmField var slayerPhaseEnabled: Boolean = false
    @ConfigValue @JvmField var slayerPhaseWorldText: Boolean = true
    @ConfigValue @JvmField var slayerPhaseTitles: Boolean = true
    @ConfigValue @JvmField var slayerPhaseHealthSplit: Boolean = true


    @JvmStatic
    fun slayerAnyEnabled(): Boolean =
        slayerSpawnAlertEnabled || slayerCocoonAlertEnabled || slayerSpawnHudEnabled ||
            slayerStatsHudEnabled || slayerTimerEnabled || slayerProfitEnabled || slayerPhaseEnabled

}
