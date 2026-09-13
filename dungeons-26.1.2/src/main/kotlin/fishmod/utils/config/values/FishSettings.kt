package fishmod.utils.config.values

import config.practical.manager.ConfigValue

/**
 * Settings unique to FishMod — lives only in FishMod's jar so it always
 * loads from the correct class file even when blade-addons is also present.
 */
object FishSettings {

    @ConfigValue @JvmField var sendLagToParty: Boolean = false

    /** Whether the first-join welcome chat message has already been shown on this install. */
    @ConfigValue @JvmField var hasSeenWelcomeMessage: Boolean = false

    @ConfigValue @JvmField var showPuzzles: Boolean = false

    @ConfigValue @JvmField var deathMessageEnabled: Boolean = false

    @ConfigValue @JvmField var deathMessageTemplate: String = "{name} died like a bum"

    @ConfigValue @JvmField var deathMessageToParty: Boolean = false

    /** Class XP gained per run when playing as that class — used for .rtca / .crtc */
    @ConfigValue @JvmField var rtcaClassXpPerRun: Int = 424200
    /** Class XP gained per run for the classes you're NOT playing (passive) — used for .rtca */
    @ConfigValue @JvmField var rtcaClassPassiveXpPerRun: Int = 106050
    @ConfigValue @JvmField var rtcCataXpPerRun: Int = 509040
    /** Hypixel's first-5-runs-of-the-day daily bonus (+40% XP). Toggle off after you've used them. */
    @ConfigValue @JvmField var rtcaIncludeDailyBonus: Boolean = false

    @ConfigValue @JvmField var soulflowHudEnabled: Boolean = false
    @ConfigValue @JvmField var soulflowWarningThreshold: Int = 1000
    @ConfigValue @JvmField var soulflowMissingNotifier: Boolean = false
    @ConfigValue @JvmField var soulflowHudX: Int = 10
    @ConfigValue @JvmField var soulflowHudY: Int = 60

    /** Comma-separated column names, left-to-right, saved from drag-reordering the /fm screen's tabs. */
    @ConfigValue @JvmField var fmColumnOrder: String = ""

    /** Master switch for the /fm screen's own animations (open/close cascade, section expand, toggle
     *  slides). Off = everything snaps instantly. */
    @ConfigValue @JvmField var fmAnimations: Boolean = true

    // /fm screen UI Customization (cascading curtain open/close + column card appearance)
    /** Which background swatch is active: "Dark Glass" (default), "Deep Blue", "Crimson", "Violet", or "Custom". */
    @ConfigValue @JvmField var fmBgPreset: String = "Dark Glass"
    /** Used when fmBgPreset == "Custom". Alpha channel is overridden by fmBgAlpha at render time. */
    @ConfigValue @JvmField var fmBgCustomColor: Int = 0xFF14181D.toInt()
    /** Column card background opacity, 0 (invisible) - 100 (solid). */
    @ConfigValue @JvmField var fmBgAlpha: Int = 100
    /** Column drop-in/drop-out animation duration in ms, 200 (snappy) - 1200 (dramatic). */
    @ConfigValue @JvmField var fmDropDurationMs: Int = 450
    /** Extra delay in ms added per column, left to right, for the cascading wave effect. */
    @ConfigValue @JvmField var fmStaggerDelayMs: Int = 60
    /** Exit animation style: "Floor Fall" (drop off the bottom) or "Reverse Curtain" (retract up off the top). */
    @ConfigValue @JvmField var fmExitStyle: String = "Floor Fall"
    /** Accent colour — headers, chevrons, hover glow, tooltip borders, and the on-state of toggle switches/rows. */
    @ConfigValue @JvmField var fmButtonColor: Int = 0xFF24B6B0.toInt()
    /** Accent opacity, 0 (invisible) - 100 (solid). */
    @ConfigValue @JvmField var fmButtonAlpha: Int = 100
    /** Row-tint colour behind an enabled feature row (e.g. "Door Colors" when its master toggle is on). Independent of fmBgAlpha (card background) and fmButtonColor (accent). */
    @ConfigValue @JvmField var fmRowColor: Int = 0xFF24B6B0.toInt()
    /** Row-tint opacity, 0 (invisible) - 100 (solid). Matches today’s fixed ~15%. */
    @ConfigValue @JvmField var fmRowAlpha: Int = 15

    // Pet XP gained = skill XP × (1 + taming×0.01) × (1 + beastmaster%/100) × (1 + petItem%/100) × extraMult.
    /** Taming level — adds +1% pet XP per level (max 60 = +60%). */
    @ConfigValue @JvmField var petXpTamingLevel: Int = 0
    /** Beastmaster Crest bonus % (Coal=10, Iron=20, Gold=30, Diamond=40, Bronze pre-promote=2…). */
    @ConfigValue @JvmField var petXpBeastmasterBonus: Int = 0
    /** Pet item XP bonus % — items like "All Skills XP Boost". 0 = none. */
    @ConfigValue @JvmField var petXpPetItemBonus: Int = 0
    /** Booster cookie active (+20% skill XP, which becomes +20% pet XP for matching pets). */
    @ConfigValue @JvmField var petXpBoosterCookie: Boolean = false

    @ConfigValue @JvmField var petHudEnabled: Boolean = false
    @ConfigValue @JvmField var petHudShowLevel: Boolean = false
    @ConfigValue @JvmField var petHudShowRarity: Boolean = true
    @ConfigValue @JvmField var petHudFadeIdle: Boolean = false
    @ConfigValue @JvmField var petHudFadeMs: Int = 5000
    @ConfigValue @JvmField var petHudX: Int = 10
    @ConfigValue @JvmField var petHudY: Int = 80

    // per-item ability cooldowns drawn on hotbar / inventory slots
    @ConfigValue @JvmField var cooldownOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var cooldownShowText: Boolean = false
    @ConfigValue @JvmField var cooldownOnlyUnder3s: Boolean = false

    // overflow levels drawn on the Hypixel level-up menu past the level-50 cap
    @ConfigValue @JvmField var catacombsOverflowEnabled: Boolean = false

    @ConfigValue @JvmField var fireFreezeTimerEnabled: Boolean = false

    @ConfigValue @JvmField var sessionStatsEnabled: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeon: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeonHub: Boolean = false
    @ConfigValue @JvmField var sessionStatsResetOnRelog: Boolean = false
    @ConfigValue @JvmField var sessionStatsHudX: Int = 10
    @ConfigValue @JvmField var sessionStatsHudY: Int = 120

    // when on, dot-commands work in this channel too and replies go back in the same channel
    @ConfigValue @JvmField var chatParty: Boolean = false
    @ConfigValue @JvmField var chatGuild: Boolean = false
    @ConfigValue @JvmField var chatOfficer: Boolean = false
    @ConfigValue @JvmField var chatPrivate: Boolean = false
    @ConfigValue @JvmField var chatAll: Boolean = false // opt-in (false-positive risk)
    // whispers print the sender's MP/PB/Cata/Gear locally (nothing sent back to them)
    @ConfigValue @JvmField var pfStatsEnabled: Boolean = false
    // in-menu overlay: head level-req + missing classes; Cata/Secrets/PB in the member tooltip
    @ConfigValue @JvmField var pfMenuEnabled: Boolean = false
    @ConfigValue @JvmField var pfShowLevelReq: Boolean = true
    @ConfigValue @JvmField var pfShowMissingClasses: Boolean = true
    @ConfigValue @JvmField var pfTooltipStats: Boolean = true
    @ConfigValue @JvmField var pfShowSecrets: Boolean = true
    @ConfigValue @JvmField var pfShowPb: Boolean = true
    @ConfigValue @JvmField var pfTooltipMissingList: Boolean = true
    @ConfigValue @JvmField var pfHighlightJoinable: Boolean = true
    // orange head for any party with a listed member below Catacombs 50
    @ConfigValue @JvmField var pfHighlightNonCata50: Boolean = false
    @ConfigValue @JvmField var pfMyClass: String = "Auto"
    // list panel: scrollable summary of every listed party beside the menu; hover a row to highlight its head
    @ConfigValue @JvmField var pfListPanel: Boolean = false
    @ConfigValue @JvmField var pfListWorstPb: Boolean = true
    @ConfigValue @JvmField var pfListNotes: Boolean = true
    @ConfigValue @JvmField var pfListMaxRows: Int = 10
    @ConfigValue @JvmField var pfListClickJoin: Boolean = false
    @ConfigValue @JvmField var pfListX: Int = 6
    @ConfigValue @JvmField var pfListY: Int = 45
    @ConfigValue @JvmField var pfFilterFloor: Int = 0        // 0 any, 1-7
    @ConfigValue @JvmField var pfFilterMode: Int = 0         // 0 any, 1 Catacombs, 2 Master Mode
    @ConfigValue @JvmField var pfFilterClass: Int = 0        // 0 any, 1 Archer 2 Berserk 3 Healer 4 Mage 5 Tank (party must still need it)
    @ConfigValue @JvmField var pfFilterHideFull: Boolean = false
    @ConfigValue @JvmField var pfFilterMaxLevel: Int = 0     // 0 off, else hide levelReq > N
    // auto-kick: while leader, kick a joiner whose S+ PB / secrets miss the bar
    @ConfigValue @JvmField var pfAutoKick: Boolean = false
    @ConfigValue @JvmField var pfAutoKickMaster: Boolean = true
    @ConfigValue @JvmField var pfAutoKickFloor: Int = 7
    @ConfigValue @JvmField var pfAutoKickMaxSeconds: Int = 400
    @ConfigValue @JvmField var pfAutoKickMinSecretsK: Int = 0
    @ConfigValue @JvmField var pfAutoKickInform: Boolean = false
    // per-class minimum Catacombs level / SkyBlock level; 0 = don't check. Class comes from
    // the "X joined the dungeon group! (<Class> Level N)" message.
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
    /** Master switch for the whole Chat card (Smart Copy Chat, Compact Chat, Infinite Chat History,
     *  Chat Search, Chat Filter); when off, none of them fire regardless of their own state. */
    @ConfigValue @JvmField var chatFeatureEnabled: Boolean = true

    // collapse identical messages within the last minute into one "(N)" line
    @ConfigValue @JvmField var chatCompact: Boolean = false

    // raise vanilla's 100-line cap on chat scrollback and sent-message history
    @ConfigValue @JvmField var infiniteChatHistory: Boolean = false
    @ConfigValue @JvmField var infiniteChatHistoryLimit: Int = 2000

    // search field on the chat screen that live-filters the visible scrollback
    @ConfigValue @JvmField var chatSearch: Boolean = false

    // reformat "Guild > BotName: Player » msg" into "Guild > [Bridge] Player: msg" and hide the raw bot line
    @ConfigValue @JvmField var bridgeBotEnabled: Boolean = false
    @ConfigValue @JvmField var bridgeBotName: String = ""

    // pipe a Twitch channel's chat into MC chat (read-only anon IRC); channel opts in config/twitch-bridge.json
    @ConfigValue @JvmField var twitchBridgeEnabled: Boolean = false

    // replaces the vanilla player list while tab is held
    @ConfigValue @JvmField var compactTabEnabled: Boolean = false
    /** Panel opacity percentage (0 = fully transparent, 100 = solid). Default 70%. */
    @ConfigValue @JvmField var compactTabOpacity: Int = 70
    /** Master switch for the SERVER/TPS/FPS/PING stat strip; when off only the player columns render. */
    @ConfigValue @JvmField var compactTabStatBarEnabled: Boolean = true
    /** Where the SERVER/TPS/FPS/PING stat strip sits relative to the player columns: TOP/BOTTOM/LEFT/RIGHT. */
    @ConfigValue @JvmField var compactTabStatBarPosition: String = "TOP"

    /** Master switch for the whole .dot-command system; when off, none of the individual
     *  per-command toggles below fire regardless of their own state. */
    @ConfigValue @JvmField var partyCommandsEnabled: Boolean = true
    @ConfigValue @JvmField var pcAllinvite: Boolean = false
    @ConfigValue @JvmField var pcPb: Boolean = false
    @ConfigValue @JvmField var pcCata: Boolean = false
    @ConfigValue @JvmField var pcRtca: Boolean = false
    @ConfigValue @JvmField var pcDprofit: Boolean = false
    @ConfigValue @JvmField var pcCrit: Boolean = false // .crit — latest/average Explosive Shot crit + average storm kill (Archer only)
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
    @ConfigValue @JvmField var pcWorm: Boolean = false // .worm / .scatha (Worm + Scatha bestiary)

    // right-click a chat line to copy the whole message (joins wraps, strips dividers)
    @ConfigValue @JvmField var smartCopyChat: Boolean = false

    // shown as "<prefix> > <message>" on FishMod's chat output (max 10 chars)
    @ConfigValue @JvmField var modPrefixEnabled: Boolean = false
    @ConfigValue @JvmField var modPrefix: String = "FM"

    @ConfigValue @JvmField var remoteNicksEnabled: Boolean = false

    // render-only player model size; Share publishes it so other mod users render you at it too
    @ConfigValue @JvmField var playerSizeEnabled: Boolean = false
    @ConfigValue @JvmField var playerSizeScaleX: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleY: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleZ: Double = 1.0
    @ConfigValue @JvmField var playerSizeShared: Boolean = false

    // hide categories of Hypixel chat spam; master gate + per-category toggles
    @ConfigValue @JvmField var chatFilterEnabled: Boolean = false
    @ConfigValue @JvmField var cfKillCombo: Boolean = true  // "+15 Kill Combo"
    @ConfigValue @JvmField var cfBossMessages: Boolean = false // "[BOSS] Wither King: ..."
    @ConfigValue @JvmField var cfFriendJoinLeave: Boolean = false // "Friend > X joined./left."
    @ConfigValue @JvmField var cfBazaar: Boolean = false // "[Bazaar] Executing instant buy..."
    @ConfigValue @JvmField var cfWarping: Boolean = false // "Warping..."
    @ConfigValue @JvmField var cfNoammSpam: Boolean = false // bundled "useless messages" list
    @ConfigValue @JvmField var cfCollapseBlank: Boolean = false // drop consecutive blank chat lines
    @ConfigValue @JvmField var cfCustom: Boolean = false // apply cfCustomPatterns
    @ConfigValue @JvmField var cfCustomPatterns: String = "" // user regexes, newline- or ;-separated

    // parse "Your Explosive Shot hit N enemy/enemies for D damage." and title the per-enemy damage (D / N)
    @ConfigValue @JvmField var explosiveShotEnabled: Boolean = false
    // Also announce the same per-enemy damage to party chat, only while playing Archer.
    @ConfigValue @JvmField var explosiveShotAnnounceParty: Boolean = false

    // Color applied to your real username (or Custom Name). Mode picks how many of the three stops
    // are used: SOLID (start only), GRADIENT (start→end), GRADIENT3 (start→mid→end), RAINBOW (fixed
    // 6-stop rainbow, start/mid/end ignored).
    @ConfigValue @JvmField var nickColorStart: Int = 0xFFFF5555.toInt()
    @ConfigValue @JvmField var nickColorMid: Int = 0xFFFFFF55.toInt()
    @ConfigValue @JvmField var nickColorEnd: Int = 0xFF5555FF.toInt()
    @ConfigValue @JvmField var nickCustomName: String = ""
    @ConfigValue @JvmField var nickColorMode: String = "GRADIENT"

    @ConfigValue @JvmField var nickPreviewEnabled: Boolean = false
    @ConfigValue @JvmField var nickPreviewYOffset: Double = 0.0

    // Networth (and cata level / secret avg in the Dungeon Hub) under every player's nametag.
    @ConfigValue @JvmField var nametagStatsEnabled: Boolean = false
    @ConfigValue @JvmField var nametagStatsShowSelf: Boolean = false

    // Prestige Colors: recolour Hypixel's SkyBlock "[level]" badge (nametags + tab) by a level-driven
    // tier progression — 15 solid tiers to 300, then 20 three-stop gradient tiers to 700.
    @ConfigValue @JvmField var prestigeColorsEnabled: Boolean = false
    @ConfigValue @JvmField var prestigeColorsNametags: Boolean = true
    @ConfigValue @JvmField var prestigeColorsTab: Boolean = true
    @ConfigValue @JvmField var prestigeColorsChat: Boolean = true
    @ConfigValue @JvmField var prestigeColorsGradientTiers: Boolean = true
    @ConfigValue @JvmField var prestigeColorsAnimated: Boolean = true
    @ConfigValue @JvmField var prestigeColorsAnimSpeed: Double = 1.0
    // FADE = whole number is one colour cycling the palette; FLOW = band slides across the digits
    @ConfigValue @JvmField var prestigeColorsAnimStyle: String = "FADE"

    // Master toggle for the /fm wp dungeon waypoint editor's rendering (boxes, titles, route lines).
    @ConfigValue @JvmField var dungeonWaypointsEnabled: Boolean = true

    // M7/F7 lever waypoints: through-walls box on each boss lever; disappears once flipped.
    @ConfigValue @JvmField var enableM7LeverWaypoints: Boolean = false
    @ConfigValue @JvmField var m7LeverWaypointColor: Int = 0xFFFF0086.toInt() // RGB used; alpha ignored
    @ConfigValue @JvmField var m7LeverWaypointMode: Int = 2                    // 0 outline, 1 fill, 2 filled outline
    @ConfigValue @JvmField var m7LeverWaypointOpacity: Int = 50               // fill opacity %

    // outlines dungeon mobs whose nametag carries the gold ✯ (must be killed to clear the floor)
    @ConfigValue @JvmField var enableStarredMobHighlight: Boolean = false
    @ConfigValue @JvmField var starredMobHighlightColor: Int = 0x80FFAA00.toInt() // ARGB (translucent gold)

    enum class PriceMode {
        INSTASELL,  // bazaar buyPrice  (default)
        SELL_OFFER, // bazaar sellPrice
        NPC_SELL    // items API npc_sell_price
    }
    @ConfigValue @JvmField var trackerPriceModeEnum: PriceMode = PriceMode.INSTASELL
    @ConfigValue @JvmField var pcCorpse: Boolean = false

    @ConfigValue @JvmField var cooldownInInventory: Boolean = false

    // per-HUD scale (1.0 = default), adjusted via scroll wheel in the HUD editor
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

    // Chat-triggered party actions (.kick, .warp, .transfer, .promote, .demote), each with its own toggle.
    // pcPartyActionsMode = who besides you may trigger them: off | self | whitelist | blacklist | everyone.
    // Manage lists with /fmcmd whitelist|blacklist add|remove|list. Default off for safety.
    @ConfigValue @JvmField var pcActionKick: Boolean = false
    @ConfigValue @JvmField var pcActionWarp: Boolean = false
    @ConfigValue @JvmField var pcActionTransfer: Boolean = false
    @ConfigValue @JvmField var pcActionPromote: Boolean = false
    @ConfigValue @JvmField var pcActionDemote: Boolean = false
    @ConfigValue @JvmField var pcPartyActionsMode: String = "self"
    @ConfigValue @JvmField var pcPartyActionsWhitelist: String = ""
    @ConfigValue @JvmField var pcPartyActionsBlacklist: String = ""

    // manual loot/profit tracker (in-inventory panel, Dungeon Hub only)
    @ConfigValue @JvmField var lootTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var lootTrackerX: Int = -1 // -1 = auto-anchor beside the inventory
    @ConfigValue @JvmField var lootTrackerY: Int = -1

    // Simon Says (F7 Goldor) tracker
    @ConfigValue @JvmField var simonSaysEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysHudEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysPartyChat: Boolean = false
    @ConfigValue @JvmField var simonSaysFailEnabled: Boolean = false
    @ConfigValue @JvmField var simonSaysFailMessage: String = "Simon Says: FAILED!"
    @ConfigValue @JvmField var simonSaysHudX: Int = 10
    @ConfigValue @JvmField var simonSaysHudY: Int = 360
    @ConfigValue @JvmField var simonSaysHudScale: Double = 1.0

    // Simon Says per-round announce messages (System22 port) — sent to party chat when that round is reached.
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

    // Simon Says HUD — three independent states (progress / completed / reset), each with its own
    // enable toggle, text (progress text substitutes "(n)" for the current round), and color.
    @ConfigValue @JvmField var ssProgressShowProgress: Boolean = true
    @ConfigValue @JvmField var ssProgressProgressText: String = "SS at (n)/5"
    @ConfigValue @JvmField var ssProgressProgressColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var ssProgressShowCompleted: Boolean = true
    @ConfigValue @JvmField var ssProgressCompletedText: String = "SS Completed"
    @ConfigValue @JvmField var ssProgressCompletedColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var ssProgressShowReset: Boolean = true
    @ConfigValue @JvmField var ssProgressResetText: String = "SS RESET!"
    @ConfigValue @JvmField var ssProgressResetColor: Int = 0xFFFF5555.toInt()


    // PB Pace — live delta vs your personal-best splits during a dungeon run.
    @ConfigValue @JvmField var pbPaceEnabled: Boolean = false
    @ConfigValue @JvmField var pbPaceHudX: Int = 10
    @ConfigValue @JvmField var pbPaceHudY: Int = 300
    @ConfigValue @JvmField var pbPaceScale: Double = 1.0

    // Class Colored Boots — recolor your dungeon boots (leather dye) by your detected class.
    @ConfigValue @JvmField var classColoredBootsEnabled: Boolean = false

    // press a Wardrobe slot hotkey (bind in Options > Controls) to instantly click that
    // set/loadout in an open Wardrobe or Loadouts GUI
    @ConfigValue @JvmField var wardrobeHotkeysEnabled: Boolean = false
    @ConfigValue @JvmField var wardrobeHotkeysAutoClose: Boolean = true

    // parse "You equipped <Name>!" (item-customizer loadout switch) and flash the name as a title
    @ConfigValue @JvmField var loadoutTitleEnabled: Boolean = false

    // keep sprinting while holding forward (only sets the flag, never clears it)
    @ConfigValue @JvmField var autoSprintEnabled: Boolean = false

    // master gate + volume (0-100%) for FishMod sound cues
    @ConfigValue @JvmField var soundMasterEnabled: Boolean = true
    @ConfigValue @JvmField var soundMasterVolume: Int = 100

    @ConfigValue @JvmField var puzzleSolversEnabled: Boolean = false
    @ConfigValue @JvmField var puzzleSolverStyle: String = "Filled Outline" // Filled | Outline | Filled Outline
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

    // Arrow Align (F7 P3 device)
    @ConfigValue @JvmField var arrowAlignEnabled: Boolean = false
    @ConfigValue @JvmField var arrowAlignBlockWrong: Boolean = false

    // Time Changer — client-side world time only
    @ConfigValue @JvmField var timeChangerEnabled: Boolean = false
    @ConfigValue @JvmField var timeChangerMode: String = "Day"

    @ConfigValue @JvmField var arrowHitSoundEnabled: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundSuppress: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundName: String = "Note: Harp"
    @ConfigValue @JvmField var arrowHitSoundVolume: Int = 100
    @ConfigValue @JvmField var arrowHitSoundPitch: Double = 1.4

    @ConfigValue @JvmField var noCursorReset: Boolean = false
    @ConfigValue @JvmField var noCursorResetMs: Int = 150    // 0..1000 (step 10) — ms window after a GUI opens
    @ConfigValue @JvmField var arrowFixEnabled: Boolean = false
    @ConfigValue @JvmField var monoAudioEnabled: Boolean = false
    @ConfigValue @JvmField var swordBlockingEnabled: Boolean = false

    // strips segments out of Hypixel's action bar; the last three hide vanilla HUD overlays instead
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

    // Animations — first-person hand view-model editor
    @ConfigValue @JvmField var animEnabled: Boolean = false
    @ConfigValue @JvmField var animItemScale: Double = 0.0   // -1.5..1.5 (0 = normal)
    @ConfigValue @JvmField var animX: Double = 0.0           // -2..2
    @ConfigValue @JvmField var animY: Double = 0.0
    @ConfigValue @JvmField var animZ: Double = 0.0
    @ConfigValue @JvmField var animRotX: Double = 0.0        // degrees, -50..50
    @ConfigValue @JvmField var animRotY: Double = 0.0
    @ConfigValue @JvmField var animRotZ: Double = 0.0
    @ConfigValue @JvmField var animSwingX: Double = 1.0      // 0..2 multipliers
    @ConfigValue @JvmField var animSwingY: Double = 1.0
    @ConfigValue @JvmField var animSwingZ: Double = 1.0
    @ConfigValue @JvmField var animSwingSpeed: Double = 0.0  // -5..5 (0 normal, +5 ~6x faster, -5 ~6x slower)
    @ConfigValue @JvmField var animIgnoreHaste: Boolean = false
    @ConfigValue @JvmField var animNoEquip: Boolean = false
    @ConfigValue @JvmField var animNoHandMove: Boolean = false

    // Storage Overlay — all-pages viewer + search over /storage
    @ConfigValue @JvmField var storageOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var storageViewerColumns: Int = 3
    @ConfigValue @JvmField var storageMaxHeight: Int = 360
    @ConfigValue @JvmField var storageHideNonMatching: Boolean = false
    @ConfigValue @JvmField var storageOverlayScale: Double = 1.0   // 0.5..2.0
    @ConfigValue @JvmField var storageScrollSpeed: Int = 10        // 1..50
    @ConfigValue @JvmField var storageRetainScroll: Boolean = true
    @ConfigValue @JvmField var containerValueEnabled: Boolean = false   // no-bg value list beside the open container / storage overlay

    // Leap Menu — custom Spirit Leap GUI
    @ConfigValue @JvmField var leapMenuEnabled: Boolean = false
    @ConfigValue @JvmField var leapMenuScale: Int = 135
    @ConfigValue @JvmField var leapMenuLeftClickOnly: Boolean = false
    @ConfigValue @JvmField var leapMenuKeybinds: Boolean = true
    @ConfigValue @JvmField var leapMenuTintDead: Boolean = true
    @ConfigValue @JvmField var leapMenuShowName: Boolean = true
    @ConfigValue @JvmField var leapMenuShowClass: Boolean = true
    @ConfigValue @JvmField var leapMenuSort: Int = 0        // 0 = class order below, 1 = name A-Z, 2 = quadrant sort
    @ConfigValue @JvmField var leapMenuClassOrder: String = "MAGE,BERSERK,ARCHER,HEALER,TANK"
    @ConfigValue @JvmField var leapMenuMap: Boolean = false // show the dungeon map + click a player's head to leap
    @ConfigValue @JvmField var leapMenuMapAfterBR: Boolean = false // map view only kicks in once the blood door is open

    @ConfigValue @JvmField var itemTooltipPrices: Boolean = false
    @ConfigValue @JvmField var itemTooltipNpcSell: Boolean = false

    // scroll = move, shift+scroll = sideways, ctrl+scroll = scale
    @ConfigValue @JvmField var tooltipScrollEnabled: Boolean = false
    @ConfigValue @JvmField var tooltipScrollScale: Int = 100   // percent, 30..150
    @ConfigValue @JvmField var tooltipScrollSpeed: Int = 3     // 1..10

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
    @ConfigValue @JvmField var etherwarpSoundVolume: Int = 100  // percent; >100 widens falloff so it's louder up close
    @ConfigValue @JvmField var etherwarpSoundPitch: Double = 1.0

    // Extra Stats — post-run dungeon summary
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
    @ConfigValue @JvmField var blockOverlayMode: Int = 2 // 0 outline, 1 fill, 2 filled outline
    @ConfigValue @JvmField var blockOverlayFillColor: Int = 0x40FFFFFF
    @ConfigValue @JvmField var blockOverlayOutlineColor: Int = 0xFFFFFFFF.toInt()
    /** Extra multiplier (0-100%) on the fill alpha, on top of the fill colour's own alpha. */
    @ConfigValue @JvmField var blockOverlayOpacity: Int = 100
    @ConfigValue @JvmField var blockOverlayPhase: Boolean = false

    // Wither ESP (F7)
    @ConfigValue @JvmField var witherEspEnabled: Boolean = false
    @ConfigValue @JvmField var witherEspMaxorColor: Int = 0xFF5804A4.toInt()
    @ConfigValue @JvmField var witherEspStormColor: Int = 0xFF00D0FF.toInt()
    @ConfigValue @JvmField var witherEspGoldorColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var witherEspNecronColor: Int = 0xFFFF0000.toInt()

    // M7 Relics HUD (enable flags: Floor7.enableRelicStartTimer / renderRelicHighlight)
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

    // Melody Message (F7 P3)
    @ConfigValue @JvmField var melodyMessageEnabled: Boolean = false
    @ConfigValue @JvmField var melodyMessageOnOpen: Boolean = true
    @ConfigValue @JvmField var melodyMessageText: String = "Melody Terminal start!"
    @ConfigValue @JvmField var melodyMessageProgress: Boolean = false

    // Simon Says Solver (F7 P3)
    @ConfigValue @JvmField var simonSolverEnabled: Boolean = false
    @ConfigValue @JvmField var simonSolverDepth: Boolean = false
    @ConfigValue @JvmField var simonSolverColor1: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var simonSolverColor2: Int = 0x80FFAA00.toInt()
    @ConfigValue @JvmField var simonSolverColor3: Int = 0x80FF5555.toInt()
    @ConfigValue @JvmField var simonSolverBlockWrong: Boolean = false

    // Arrows Device (F7 P3 Sharp Shooter)
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
    @ConfigValue @JvmField var ragnarockTimer: Boolean = false           // moveable HUD: 10s strength buff countdown
    @ConfigValue @JvmField var ragnarockTimerHudX: Int = 10
    @ConfigValue @JvmField var ragnarockTimerHudY: Int = 150
    @ConfigValue @JvmField var ragnarockTimerScale: Double = 1.5
    @ConfigValue @JvmField var p5RagEnabled: Boolean = false          // title "Rag" on Wither King’s pre-fight taunt

    // Terracotta Timer (F6)
    @ConfigValue @JvmField var terracottaTimerEnabled: Boolean = false

    // Livid Solver (F5/M5)
    @ConfigValue @JvmField var lividSolverEnabled: Boolean = false
    @ConfigValue @JvmField var lividSolverColor: Int = 0xFFFF5555.toInt()

    @ConfigValue @JvmField var croesusProfitEnabled: Boolean = false

    // Spirit Bear (F4/M4)
    @ConfigValue @JvmField var spiritBearEnabled: Boolean = false
    @ConfigValue @JvmField var spiritBearHudX: Int = 10
    @ConfigValue @JvmField var spiritBearHudY: Int = 165
    @ConfigValue @JvmField var spiritBearScale: Double = 1.5

    // Wither Dragons (M7 P5)
    @ConfigValue @JvmField var witherDragonsEnabled: Boolean = false
    @ConfigValue @JvmField var witherDragonsTimerWorld: Boolean = true // in-world timer on the hitbox
    @ConfigValue @JvmField var witherDragonsTimerHud: Boolean = true   // movable on-screen countdown
    @ConfigValue @JvmField var witherDragonsTimerStyle: Int = 0        // 0 ms, 1 s, 2 ticks
    @ConfigValue @JvmField var witherDragonsHealth: Boolean = true
    @ConfigValue @JvmField var witherDragonsSkipBox: Boolean = true
    @ConfigValue @JvmField var witherDragonsBoxFill: Boolean = false
    @ConfigValue @JvmField var witherDragonsTracer: Boolean = false
    @ConfigValue @JvmField var witherDragonsAimAssist: Boolean = false
    @ConfigValue @JvmField var witherDragonsAimColor: Int = 0xFF00FFFF.toInt()
    @ConfigValue @JvmField var witherDragonsSendStats: Boolean = true
    @ConfigValue @JvmField var witherDragonsHudX: Int = -1             // <0 = centred
    @ConfigValue @JvmField var witherDragonsHudY: Int = 100
    @ConfigValue @JvmField var witherDragonsHudScale: Double = 2.0
    @ConfigValue @JvmField var witherDragonsSpawnAlert: Boolean = false  // dragon alert: title on spawn
    @ConfigValue @JvmField var witherDragonsSpawnSound: Boolean = false
    @ConfigValue @JvmField var witherDragonsSpawnParty: Boolean = false
    @ConfigValue @JvmField var witherDragonsPriority: Boolean = false
    @ConfigValue @JvmField var witherDragonsNormalPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsEasyPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsSoloDebuff: Int = 0        // 0 Tank, 1 Healer
    @ConfigValue @JvmField var witherDragonsSoloDebuffAll: Boolean = true

    // Architect's First Draft auto-refill (after a puzzle fail)
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
    @ConfigValue @JvmField var slotBindsProfile: String = "Default"   // name of the active bind set (see slot_binds.txt [sections])

    @ConfigValue @JvmField var cameraTweaksEnabled: Boolean = false
    @ConfigValue @JvmField var cameraFullBright: Boolean = false
    @ConfigValue @JvmField var cameraNoBlindness: Boolean = false
    @ConfigValue @JvmField var cameraNoNausea: Boolean = false

    // Comma-separated address list. While connected to one of these, PracticeMode forces
    // inDungeon/inFloor7 and lets /fmpractice set the boss phase by hand (these servers send
    // no location packet and no [BOSS] chat lines).
    @ConfigValue @JvmField var practiceServerIps: String = "hypixelp3sim.zapto.org"

    // F7 Terminal Solver
    @ConfigValue @JvmField var terminalSolverEnabled: Boolean = false
    @ConfigValue @JvmField var terminalBlockWrongClicks: Boolean = true
    @ConfigValue @JvmField var terminalStopMelody: Boolean = false
    @ConfigValue @JvmField var terminalSolverSound: Boolean = true
    @ConfigValue @JvmField var terminalHighlightColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalOrderColor1: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalOrderColor2: Int = 0x9922AA22.toInt()
    @ConfigValue @JvmField var terminalOrderColor3: Int = 0x99116611.toInt()
    @ConfigValue @JvmField var terminalRubixColor: Int = 0x9900AAAA.toInt()       // +1
    @ConfigValue @JvmField var terminalMelodyColor: Int = 0x99AA00AA.toInt()
    @ConfigValue @JvmField var terminalStopTooltips: Boolean = true
    @ConfigValue @JvmField var terminalShowNumbers: Boolean = true
    @ConfigValue @JvmField var terminalHideWrong: Boolean = false                 // paint over non-solution slots
    @ConfigValue @JvmField var terminalFirstClickProtMs: Int = 500  // block clicks for this long after a terminal opens
    // send terminal clicks as middle-click so items never land on the cursor
    @ConfigValue @JvmField var terminalMiddleClickGui: Boolean = false
    @ConfigValue @JvmField var terminalStartsWithColor: Int = 0x9900AAAA.toInt()
    @ConfigValue @JvmField var terminalSelectColor: Int = 0x9900AAAA.toInt()
    @ConfigValue @JvmField var terminalRubixColor2: Int = 0x99006464.toInt()      // +2
    @ConfigValue @JvmField var terminalRubixNeg1: Int = 0x99AA5500.toInt()        // -1
    @ConfigValue @JvmField var terminalRubixNeg2: Int = 0x99D25500.toInt()        // -2
    @ConfigValue @JvmField var terminalMelodyPointerColor: Int = 0x9955FF55.toInt()
    @ConfigValue @JvmField var terminalWrongCover: Int = 0xF01A1A22.toInt()
    @ConfigValue @JvmField var termSimPbs: String = ""                            // csv of 6 best times (s)
    // render mode: 0 = overlay on the vanilla chest, 1 = big rounded slot board (real terminals + /fmtermsim)
    @ConfigValue @JvmField var terminalRenderMode: Int = 0
    @ConfigValue @JvmField var terminalCustomScale: Double = 1.0
    @ConfigValue @JvmField var terminalCustomRoundness: Int = 8
    @ConfigValue @JvmField var terminalCustomGap: Int = 4
    @ConfigValue @JvmField var terminalCustomBg: Int = 0xFF141414.toInt()

    // Warp Cooldown HUD (enable: Dungeons.enableWarpCooldown). Starts on the "entered <floor>
    // Catacombs" chat line; 30s is Hypixel's real re-entry gate.
    @ConfigValue @JvmField var warpCooldownSeconds: Int = 30
    @ConfigValue @JvmField var warpCooldownColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var warpAnnounceKick: Boolean = false
    @ConfigValue @JvmField var warpKickText: String = "Kicked!"
    @ConfigValue @JvmField var warpCooldownHudX: Int = 10
    @ConfigValue @JvmField var warpCooldownHudY: Int = 160
    @ConfigValue @JvmField var warpCooldownScale: Double = 1.0

    // Blessing Display — reads the tab footer
    @ConfigValue @JvmField var blessingDisplayEnabled: Boolean = false
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

    // enable is Dungeons.displayInvincibilityTimer; Dungeons.InvincibilityDuration = show "X.Xs"
    // vs a plain icon; Dungeons.useStatusColorForInvincibility = gold/red/green by state.
    @ConfigValue @JvmField var invincAnnounce: Boolean = true
    @ConfigValue @JvmField var invincShowCooldown: Boolean = true
    @ConfigValue @JvmField var invincShowWhen: String = "Any" // Always | Any | Active | Cooldown
    @ConfigValue @JvmField var invincShowInBoss: Boolean = false
    @ConfigValue @JvmField var invincShowSpirit: Boolean = true
    @ConfigValue @JvmField var invincShowBonzo: Boolean = true
    @ConfigValue @JvmField var invincShowPhoenix: Boolean = true
    @ConfigValue @JvmField var invincHudX: Int = 10
    @ConfigValue @JvmField var invincHudY: Int = 140
    @ConfigValue @JvmField var invincScale: Double = 1.0

    // Key Notifier (enable: Dungeons.enableKeyNotifier)
    @ConfigValue @JvmField var keyNotifierTitle: Boolean = true
    @ConfigValue @JvmField var keyNotifierChat: Boolean = false
    @ConfigValue @JvmField var keyNotifierSound: Boolean = true
    @ConfigValue @JvmField var keyNotifierDurationMs: Int = 2000   // 500..8000 (step 250) — how long the title stays

    // Leap Messages (enable: Dungeons.enableLeapMessages)
    @ConfigValue @JvmField var leapMessagesTitle: Boolean = true
    /** Send the leap message to party chat (/pc). */
    @ConfigValue @JvmField var leapMessagesParty: Boolean = false
    @ConfigValue @JvmField var leapMessagesSound: Boolean = true
    /** {name} = the Spirit-Leap target. */
    @ConfigValue @JvmField var leapMessagesText: String = "&b&lLEAP &r&7-> &f{name}"

    // Room Timer (on-screen "Cleared (t)" / "Secrets done (t)" titles)
    @ConfigValue @JvmField var roomTimerEnabled: Boolean = false
    @ConfigValue @JvmField var roomTimerClear: Boolean = true
    @ConfigValue @JvmField var roomTimerSecrets: Boolean = true
    @ConfigValue @JvmField var roomTimerShowTime: Boolean = true
    @ConfigValue @JvmField var roomTimerPb: Boolean = true

    // Mimic / Prince / Bat kill announce
    @ConfigValue @JvmField var mimicAnnounceEnabled: Boolean = false
    @ConfigValue @JvmField var mimicMsgEnabled: Boolean = true
    @ConfigValue @JvmField var mimicMsgText: String = "Mimic Killed!"
    @ConfigValue @JvmField var princeMsgEnabled: Boolean = true
    @ConfigValue @JvmField var princeMsgText: String = "Prince Killed!"
    @ConfigValue @JvmField var batMsgEnabled: Boolean = true
    @ConfigValue @JvmField var batMsgText: String = "Bat Killed!"

    @ConfigValue @JvmField var autoSprintDungeonOnly: Boolean = false

    // Auto Requeue delay (ms after the "> EXTRA STATS <" line)
    @ConfigValue @JvmField var autoRequeueDelayMs: Int = 2000

    // Secret Clicked
    @ConfigValue @JvmField var secretClickedEnabled: Boolean = false
    @ConfigValue @JvmField var secretClickedBoxes: Boolean = true
    @ConfigValue @JvmField var secretClickedBats: Boolean = true   // count a killed secret bat you were next to
    @ConfigValue @JvmField var secretClickedItems: Boolean = true  // count a ground item you walked over
    @ConfigValue @JvmField var secretClickedStyle: String = "Filled Outline" // Filled | Outline | Filled Outline
    @ConfigValue @JvmField var secretClickedColor: Int = 0x66FFAA00 // ~40% gold (ARGB)
    @ConfigValue @JvmField var secretClickedLockedColor: Int = 0x66FF5555
    @ConfigValue @JvmField var secretClickedLineWidth: Double = 2.0
    @ConfigValue @JvmField var secretClickedTimeToStay: Int = 7 // seconds
    @ConfigValue @JvmField var secretClickedDepthCheck: Boolean = false // true = through walls
    @ConfigValue @JvmField var secretClickedInBoss: Boolean = false
    @ConfigValue @JvmField var secretClickedChime: Boolean = true
    @ConfigValue @JvmField var secretClickedChimeInBoss: Boolean = false
    @ConfigValue @JvmField var secretClickedSoundName: String = "Blaze Hit"
    @ConfigValue @JvmField var secretClickedVolume: Int = 100
    @ConfigValue @JvmField var secretClickedPitch: Double = 2.0

    // replaces vanilla's sidebar scoreboard; each line category can be hidden and big numbers
    // shown compact (1,234,567 -> 1.2M)
    @ConfigValue @JvmField var customScoreboardEnabled: Boolean = false
    @ConfigValue @JvmField var customScoreboardCompactNumbers: Boolean = false
    /** In dungeons, fall back to vanilla's sidebar instead of the custom one (the map info HUD already carries score). */
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
    // Synthetic extras (not real scoreboard lines) appended at the bottom, same values shown in Compact Tab.
    @ConfigValue @JvmField var sbSectionTps: Boolean = true
    @ConfigValue @JvmField var sbSectionPing: Boolean = true
    @ConfigValue @JvmField var sbSectionFps: Boolean = true
    // not on the real Hypixel sidebar — from PetHud's pet state and a 60s Hypixel-API skill poll
    @ConfigValue @JvmField var sbSectionPetExtra: Boolean = false
    @ConfigValue @JvmField var sbSectionSkills: Boolean = false
    @ConfigValue @JvmField var sbSectionBestiary: Boolean = false
    @ConfigValue @JvmField var sbSectionCollections: Boolean = false
    @ConfigValue @JvmField var sbSectionElection: Boolean = false
    @ConfigValue @JvmField var sbSectionFireSales: Boolean = false

    // ---------------- Slayer ----------------
    // No single master toggle — each of the five Slayer features below is its own switch. The
    // subsystem (scoreboard scan + entity scan) idles unless [slayerAnyEnabled] is true.

    // Mini/Boss Spawn Alert
    @ConfigValue @JvmField var slayerSpawnAlertEnabled: Boolean = false
    @ConfigValue @JvmField var slayerMiniBossAlert: Boolean = true
    @ConfigValue @JvmField var slayerBossAlert: Boolean = true
    /** Alert on-screen time, ms (shared by mini/boss/cocoon alerts). */
    @ConfigValue @JvmField var slayerAlertDurationMs: Int = 1500

    // Cocoon Alert
    @ConfigValue @JvmField var slayerCocoonAlertEnabled: Boolean = false
    @ConfigValue @JvmField var slayerCocoonAlertDurationMs: Int = 2000

    // Spawn Progress HUD
    @ConfigValue @JvmField var slayerSpawnHudEnabled: Boolean = false
    @ConfigValue @JvmField var slayerSpawnHudX: Int = 10
    @ConfigValue @JvmField var slayerSpawnHudY: Int = 140
    @ConfigValue @JvmField var slayerSpawnHudScale: Double = 1.0

    // Slayer Stats HUD
    @ConfigValue @JvmField var slayerStatsHudEnabled: Boolean = false
    @ConfigValue @JvmField var slayerStatsShowXp: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowKills: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowXpHr: Boolean = true
    @ConfigValue @JvmField var slayerStatsShowKillsHr: Boolean = true
    @ConfigValue @JvmField var slayerStatsBackground: Boolean = true
    @ConfigValue @JvmField var slayerStatsHudX: Int = 10
    @ConfigValue @JvmField var slayerStatsHudY: Int = 170
    @ConfigValue @JvmField var slayerStatsHudScale: Double = 1.0

    // Boss Timer HUD
    @ConfigValue @JvmField var slayerTimerEnabled: Boolean = false
    /** "Spawned" = timer starts when the boss spawns; "Fully Spawned" = starts once it's attackable. */
    @ConfigValue @JvmField var slayerTimerStartMode: String = "Spawned"
    @ConfigValue @JvmField var slayerTimerShowCurrent: Boolean = true
    @ConfigValue @JvmField var slayerTimerShowPb: Boolean = true
    @ConfigValue @JvmField var slayerTimerShowNewPb: Boolean = true
    /** Full-cycle line: wall-clock time from one boss kill to the next (fight + loot + walk + refill). */
    @ConfigValue @JvmField var slayerTimerShowCycle: Boolean = true
    @ConfigValue @JvmField var slayerTimerHudX: Int = 10
    @ConfigValue @JvmField var slayerTimerHudY: Int = 255
    @ConfigValue @JvmField var slayerTimerHudScale: Double = 1.0

    // Profit Tracker (drop value + coins/hr) — SkyHanni-style, prices real drops.
    // Kept per (slayer type + tier), like SkyHanni. Price source = the shared trackerPriceModeEnum.
    @ConfigValue @JvmField var slayerProfitEnabled: Boolean = false
    /** Max drop rows shown on the HUD (highest value first); the rest fold into one "N more items" row. */
    @ConfigValue @JvmField var slayerProfitLines: Int = 10
    /** Idle seconds before the tracker pauses AND rewinds its clock by this much (SkyHanni afkTimeout). */
    @ConfigValue @JvmField var slayerProfitIdleSeconds: Int = 60
    @ConfigValue @JvmField var slayerProfitBackground: Boolean = true
    @ConfigValue @JvmField var slayerProfitHudX: Int = 10
    @ConfigValue @JvmField var slayerProfitHudY: Int = 300
    @ConfigValue @JvmField var slayerProfitHudScale: Double = 1.0
    /** Which figures the tracker shows — "Total" (persisted, all-time) or "This Session" (since launch). */
    @ConfigValue @JvmField var slayerProfitDisplayMode: String = "Total"
    /** Keep right-click-hidden rows on screen (dark + struck) even when chat is closed. */
    @ConfigValue @JvmField var slayerProfitShowHidden: Boolean = false
    /** Drop rows worth less than this many coins fold into the "N more items" row (0 = show all). */
    @ConfigValue @JvmField var slayerProfitMinValue: Int = 0
    /** Count "Mob Kill Coins" (small purse gains while grinding) as a drop row + profit. */
    @ConfigValue @JvmField var slayerProfitCountKillCoins: Boolean = true

    // Boss Phases — SkyHanni-style attack/phase cues on the boss (all 6 slayers)
    @ConfigValue @JvmField var slayerPhaseEnabled: Boolean = false
    /** Draw the cue as billboarded world text above the boss. */
    @ConfigValue @JvmField var slayerPhaseWorldText: Boolean = true
    /** Big title for the one-shot cues (BOOM / PUPS / HATCHLINGS / FIRE PITS / TWINCLAWS / STEAK / BEACON). */
    @ConfigValue @JvmField var slayerPhaseTitles: Boolean = true
    /** Show the health-phase fraction (1/3 · 2/3 …) for Voidgloom / Inferno. */
    @ConfigValue @JvmField var slayerPhaseHealthSplit: Boolean = true

    /** True when any individual Slayer feature is on — gates the whole Slayer scan/track subsystem. */
    @JvmStatic
    fun slayerAnyEnabled(): Boolean =
        slayerSpawnAlertEnabled || slayerCocoonAlertEnabled || slayerSpawnHudEnabled ||
            slayerStatsHudEnabled || slayerTimerEnabled || slayerProfitEnabled || slayerPhaseEnabled

}
