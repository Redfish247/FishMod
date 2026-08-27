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

    @ConfigValue @JvmField var warpMapHudEnabled: Boolean = false

    @ConfigValue @JvmField var warpMapDotColor: Int = 0xFFDB3737.toInt()

    // Splits HUD (standalone position)
    @ConfigValue @JvmField var splitsHudX: Int = 5
    @ConfigValue @JvmField var splitsHudY: Int = 10

    // Soulflow HUD
    @ConfigValue @JvmField var soulflowHudEnabled: Boolean = false
    @ConfigValue @JvmField var soulflowWarningThreshold: Int = 1000
    @ConfigValue @JvmField var soulflowMissingNotifier: Boolean = false
    @ConfigValue @JvmField var soulflowHudX: Int = 10
    @ConfigValue @JvmField var soulflowHudY: Int = 60
    @ConfigValue @JvmField var soulflowHudColor: Int = 0xFF55FFFF.toInt() // Aqua §b

    // FishMod GUI
    @ConfigValue @JvmField var fmguiScale: String = "Normal" // Normal | 1.5x | 2x
    /** Comma-separated column names, left-to-right, saved from drag-reordering the /fm screen's tabs. */
    @ConfigValue @JvmField var fmColumnOrder: String = ""

    // Pet XP multipliers (see Hypixel wiki — Pets/Pet XP).
    // Pet XP gained = skill XP × (1 + taming×0.01) × (1 + beastmaster%/100) × (1 + petItem%/100) × extraMult.
    /** Taming level — adds +1% pet XP per level (max 60 = +60%). */
    @ConfigValue @JvmField var petXpTamingLevel: Int = 0
    /** Beastmaster Crest bonus % (Coal=10, Iron=20, Gold=30, Diamond=40, Bronze pre-promote=2…). */
    @ConfigValue @JvmField var petXpBeastmasterBonus: Int = 0
    /** Pet item XP bonus % — items like "All Skills XP Boost". 0 = none. */
    @ConfigValue @JvmField var petXpPetItemBonus: Int = 0
    /** Booster cookie active (+20% skill XP, which becomes +20% pet XP for matching pets). */
    @ConfigValue @JvmField var petXpBoosterCookie: Boolean = false
    /** When true, the four pet XP multipliers above are auto-detected from the Hypixel API every ~60s. */
    @ConfigValue @JvmField var petXpAutoDetect: Boolean = false

    // Pet HUD
    @ConfigValue @JvmField var petHudEnabled: Boolean = false
    @ConfigValue @JvmField var petHudShowLevel: Boolean = false
    @ConfigValue @JvmField var petHudShowRarity: Boolean = true
    @ConfigValue @JvmField var petHudFadeIdle: Boolean = false
    @ConfigValue @JvmField var petHudFadeMs: Int = 5000
    @ConfigValue @JvmField var petHudX: Int = 10
    @ConfigValue @JvmField var petHudY: Int = 80
    @ConfigValue @JvmField var petHudColor: Int = 0xFFFFD580.toInt()

    // Cooldown overlay (per-item ability cooldowns drawn on hotbar / inventory slots)
    @ConfigValue @JvmField var cooldownOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var cooldownShowText: Boolean = false
    @ConfigValue @JvmField var cooldownShowBar: Boolean = false
    @ConfigValue @JvmField var cooldownOnlyUnder3s: Boolean = false

    // Catacombs/class overflow levels — drawn on the real Hypixel level-up menu items past the level-50 cap
    @ConfigValue @JvmField var catacombsOverflowEnabled: Boolean = false

    // Slayer XP tracker
    @ConfigValue @JvmField var fireFreezeTimerEnabled: Boolean = false
    @ConfigValue @JvmField var skillTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var skillTrackerHudX: Int = 10
    @ConfigValue @JvmField var skillTrackerHudY: Int = 360
    @ConfigValue @JvmField var skillTrackerScale: Double = 1.0

    @ConfigValue @JvmField var slayerXpEnabled: Boolean = false
    @ConfigValue @JvmField var slayerXpHudX: Int = 10
    @ConfigValue @JvmField var slayerXpHudY: Int = 80

    // Powder tracker
    @ConfigValue @JvmField var powderTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var powderTrackerHudX: Int = 10
    @ConfigValue @JvmField var powderTrackerHudY: Int = 100

    // Session stats HUD
    @ConfigValue @JvmField var sessionStatsEnabled: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeon: Boolean = false
    @ConfigValue @JvmField var sessionStatsInDungeonHub: Boolean = false
    @ConfigValue @JvmField var sessionStatsResetOnRelog: Boolean = false
    @ConfigValue @JvmField var sessionStatsHudX: Int = 10
    @ConfigValue @JvmField var sessionStatsHudY: Int = 120

    // Chat-channel compatibility — when on, dot-commands (.pb, .rtca, etc.) work in these
    // channels in addition to party chat, and replies go back in the same channel.
    @ConfigValue @JvmField var chatParty: Boolean = false
    @ConfigValue @JvmField var chatGuild: Boolean = false
    @ConfigValue @JvmField var chatOfficer: Boolean = false
    @ConfigValue @JvmField var chatPrivate: Boolean = false
    @ConfigValue @JvmField var chatAll: Boolean = false // opt-in (false-positive risk)
    // Party Finder join-request stats: whispers you get while this is on print the sender's
    // MP/PB/Cata/Gear to your own chat (local-only, nothing sent back to them).
    @ConfigValue @JvmField var pfStatsEnabled: Boolean = false
    // Compact chat: collapse identical messages seen within the last minute into one line
    // with a "(N)" count instead of repeating them.
    @ConfigValue @JvmField var chatCompact: Boolean = false

    // Guild bridge bot: reformat "Guild > BotName: Player » msg" into a clean
    // "Guild > [Bridge] Player: msg" line and hide the raw bot message.
    @ConfigValue @JvmField var bridgeBotEnabled: Boolean = false
    @ConfigValue @JvmField var bridgeBotName: String = ""

    // Compact custom tab list (replaces vanilla player list while tab is held). Opt-in.
    @ConfigValue @JvmField var compactTabEnabled: Boolean = false
    /** Panel opacity percentage (0 = fully transparent, 100 = solid). Default 70%. */
    @ConfigValue @JvmField var compactTabOpacity: Int = 70
    /** Master switch for the SERVER/TPS/FPS/PING stat strip; when off only the player columns render. */
    @ConfigValue @JvmField var compactTabStatBarEnabled: Boolean = true
    /** Where the SERVER/TPS/FPS/PING stat strip sits relative to the player columns: TOP/BOTTOM/LEFT/RIGHT. */
    @ConfigValue @JvmField var compactTabStatBarPosition: String = "TOP"

    // Party command toggles
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

    // Smart copy-chat: right-click a chat line to copy the whole message (joins wrapped lines,
    // strips ---- / ▬▬▬ dividers).
    @ConfigValue @JvmField var smartCopyChat: Boolean = false

    // Mod chat prefix — shown as "<prefix> > <message>" on FishMod's chat output (max 10 chars).
    @ConfigValue @JvmField var modPrefixEnabled: Boolean = false
    @ConfigValue @JvmField var modPrefix: String = "FM"

    // Farming coin/hr tracker
    @ConfigValue @JvmField var farmingTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var farmingTrackerHudX: Int = 10
    @ConfigValue @JvmField var farmingTrackerHudY: Int = 240

    // Harvest Feast tracker
    @ConfigValue @JvmField var harvestFeastEnabled: Boolean = false
    @ConfigValue @JvmField var harvestFeastHudX: Int = 10
    @ConfigValue @JvmField var harvestFeastHudY: Int = 280
    @ConfigValue @JvmField var harvestFeastScale: Double = 1.0

    // Mining coin/hr tracker
    @ConfigValue @JvmField var miningTrackerEnabled: Boolean = false
    @ConfigValue @JvmField var miningTrackerHudX: Int = 10
    @ConfigValue @JvmField var miningTrackerHudY: Int = 320
    @ConfigValue @JvmField var miningTrackerScale: Double = 1.0

    @ConfigValue @JvmField var remoteNicksEnabled: Boolean = false

    // Render-only player model size; Share publishes it so other mod users render you at it too.
    @ConfigValue @JvmField var playerSizeEnabled: Boolean = false
    @ConfigValue @JvmField var playerSizeScaleX: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleY: Double = 1.0
    @ConfigValue @JvmField var playerSizeScaleZ: Double = 1.0
    @ConfigValue @JvmField var playerSizeShared: Boolean = false

    // Chat filter: hide selected categories of Hypixel chat spam. Master gate + per-category toggles.
    @ConfigValue @JvmField var chatFilterEnabled: Boolean = false
    @ConfigValue @JvmField var cfKillCombo: Boolean = true  // "+15 Kill Combo"
    @ConfigValue @JvmField var cfBossMessages: Boolean = false // "[BOSS] Wither King: ..."
    @ConfigValue @JvmField var cfFriendJoinLeave: Boolean = false // "Friend > X joined./left."
    @ConfigValue @JvmField var cfBazaar: Boolean = false // "[Bazaar] Executing instant buy..."
    @ConfigValue @JvmField var cfWarping: Boolean = false // "Warping..."

    // Explosive Shot: parse "Your Explosive Shot hit N enemy/enemies for D damage." and show the
    // per-enemy damage (D / N) as an on-screen title.
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
    @ConfigValue @JvmField var nickPreviewScale: Double = 1.0
    @ConfigValue @JvmField var nickPreviewYOffset: Double = 0.0

    // M7/F7 lever waypoints: through-walls filled box on each boss lever; disappears once flipped.
    @ConfigValue @JvmField var enableM7LeverWaypoints: Boolean = false
    @ConfigValue @JvmField var m7LeverWaypointColor: Int = 0x13FF0086 // ARGB (faint magenta)

    // Starred mob visualizer: outlines dungeon mobs whose nametag carries the gold ✯ (must be
    // killed to clear the floor) so they stand out from regular fodder mobs.
    @ConfigValue @JvmField var enableStarredMobHighlight: Boolean = false
    @ConfigValue @JvmField var starredMobHighlightColor: Int = 0x80FFAA00.toInt() // ARGB (translucent gold)

    // Trophy Frogs tab tracker
    @ConfigValue @JvmField var trophyFrogEnabled: Boolean = false
    @ConfigValue @JvmField var trophyFrogHudX: Int = 10
    @ConfigValue @JvmField var trophyFrogHudY: Int = 60
    @ConfigValue @JvmField var trophyFrogHudScale: Double = 1.0

    enum class PriceMode {
        INSTASELL,  // bazaar buyPrice  (default)
        SELL_OFFER, // bazaar sellPrice
        NPC_SELL    // items API npc_sell_price
    }
    @ConfigValue @JvmField var trackerPriceModeEnum: PriceMode = PriceMode.INSTASELL
    // Per-tracker price mode (Slayer XP doesn't have one — it tracks XP, not items)
    @ConfigValue @JvmField var powderPriceMode: PriceMode = PriceMode.INSTASELL
    @ConfigValue @JvmField var farmingPriceMode: PriceMode = PriceMode.INSTASELL
    @ConfigValue @JvmField var harvestFeastPriceMode: PriceMode = PriceMode.INSTASELL
    @ConfigValue @JvmField var miningPriceMode: PriceMode = PriceMode.INSTASELL
    // Legacy int kept for any save-file backcompat; not used by code anymore.
    @ConfigValue @JvmField var trackerPriceMode: Int = 0
    @ConfigValue @JvmField var pcCorpse: Boolean = false

    // Cooldown overlay extras
    @ConfigValue @JvmField var cooldownInInventory: Boolean = false

    // Per-HUD scale (1.0 = default). Adjusted via scroll wheel in HUD editor.
    @ConfigValue @JvmField var sessionStatsScale: Double = 1.0
    @ConfigValue @JvmField var powderTrackerScale: Double = 1.0
    @ConfigValue @JvmField var slayerXpScale: Double = 1.0
    @ConfigValue @JvmField var farmingTrackerScale: Double = 1.0
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

    // Chat-triggered party actions: .kick, .warp/.w, .transfer/.pt/.ptme, .promote, .demote.
    // Each has its own on/off toggle. pcPartyActionsMode governs who besides yourself can trigger
    // them: "off" (nobody, not even you) | "self" (only you) | "whitelist" (you + listed names) |
    // "blacklist" (everyone except listed names) | "everyone" (any party member, no filter).
    // Manage the lists in-game with /fmcmd whitelist|blacklist add|remove|list.
    // Default off for safety — only enable when you trust the party.
    @ConfigValue @JvmField var pcActionKick: Boolean = false
    @ConfigValue @JvmField var pcActionWarp: Boolean = false
    @ConfigValue @JvmField var pcActionTransfer: Boolean = false
    @ConfigValue @JvmField var pcActionPromote: Boolean = false
    @ConfigValue @JvmField var pcActionDemote: Boolean = false
    @ConfigValue @JvmField var pcPartyActionsMode: String = "self"
    @ConfigValue @JvmField var pcPartyActionsWhitelist: String = ""
    @ConfigValue @JvmField var pcPartyActionsBlacklist: String = ""

    // Manual loot/profit tracker (in-inventory panel, Dungeon Hub only)
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


    // Daily/Weekly/Monthly Challenges
    @ConfigValue @JvmField var challengesEnabled: Boolean = false
    @ConfigValue @JvmField var challengeHudEnabled: Boolean = false
    @ConfigValue @JvmField var challengeHudX: Int = 10
    @ConfigValue @JvmField var challengeHudY: Int = 400
    @ConfigValue @JvmField var challengeHudScale: Double = 1.0
    @ConfigValue @JvmField var challengeAfkMinutes: Int = 3
    @ConfigValue @JvmField var challengeLeaderboardEnabled: Boolean = false
    /** Optional override for the /challenges worker base URL. Empty = default proxy. */
    @ConfigValue @JvmField var challengeWorkerOverride: String = ""

    // PB Pace — live delta vs your personal-best splits during a dungeon run.
    @ConfigValue @JvmField var pbPaceEnabled: Boolean = false
    @ConfigValue @JvmField var pbPaceHudX: Int = 10
    @ConfigValue @JvmField var pbPaceHudY: Int = 300
    @ConfigValue @JvmField var pbPaceScale: Double = 1.0

    // Class Colored Boots — recolor your dungeon boots (leather dye) by your detected class.
    @ConfigValue @JvmField var classColoredBootsEnabled: Boolean = false

    // ── Fishing ───────────────────────────────────────────────────────────────
    // Bobber Reminder: after a fish bites, count down; once the reminder delay passes without
    // reeling, flash a customizable "!!!" alert (+ optional sound); if the catch window closes
    // unreeled, show the "missed it" text. All drawn in one small HUD.
    @ConfigValue @JvmField var fishingTimerEnabled: Boolean = false
    /** Seconds after a bite before the "!!!" reel reminder flashes (countdown shows until then). */
    @ConfigValue @JvmField var fishingReminderDelay: Int = 3
    /** Customizable reel-now reminder text. */
    @ConfigValue @JvmField var fishingReminderText: String = "§c§l!!! REEL !!!"
    /** Shown briefly when the catch window closes without a reel. */
    @ConfigValue @JvmField var fishingMissedText: String = "§7Missed it..."
    /** Play a ping when the reminder fires. */
    @ConfigValue @JvmField var fishingReminderSound: Boolean = true
    @ConfigValue @JvmField var fishingTimerHudX: Int = 10
    @ConfigValue @JvmField var fishingTimerHudY: Int = 140
    @ConfigValue @JvmField var fishingTimerScale: Double = 1.5

    // Sea Creature Tracker: per-creature session counts + creatures/hr, with an optional
    // title+sound alert when a rare creature surfaces.
    @ConfigValue @JvmField var seaCreatureEnabled: Boolean = false
    @ConfigValue @JvmField var seaCreatureRareAlert: Boolean = true
    @ConfigValue @JvmField var seaCreatureHudX: Int = 10
    @ConfigValue @JvmField var seaCreatureHudY: Int = 160
    @ConfigValue @JvmField var seaCreatureScale: Double = 1.0

    // Trophy Fish tab tracker (Crimson Isle) — same shape as Trophy Frogs.
    @ConfigValue @JvmField var trophyFishEnabled: Boolean = false
    @ConfigValue @JvmField var trophyFishHudX: Int = 10
    @ConfigValue @JvmField var trophyFishHudY: Int = 200
    @ConfigValue @JvmField var trophyFishHudScale: Double = 1.0

    // ── Slayer ────────────────────────────────────────────────────────────────
    // Slayer Alerts: title + sound on miniboss spawn, boss spawn, and boss slain.
    @ConfigValue @JvmField var slayerAlertsEnabled: Boolean = false
    @ConfigValue @JvmField var slayerAlertMiniboss: Boolean = true
    @ConfigValue @JvmField var slayerAlertBossSpawn: Boolean = true
    @ConfigValue @JvmField var slayerAlertBossSlain: Boolean = true
    @ConfigValue @JvmField var slayerAlertSound: Boolean = true

    // Slayer Drop Tracker: session counter for RARE / VERY RARE / CRAZY RARE / PRAISE drops.
    @ConfigValue @JvmField var slayerDropsEnabled: Boolean = false
    @ConfigValue @JvmField var slayerDropsHudX: Int = 10
    @ConfigValue @JvmField var slayerDropsHudY: Int = 240
    @ConfigValue @JvmField var slayerDropsScale: Double = 1.0

    // ── Location Ping ───────────────────────────────────────────────────────────
    // Press the ping key (default middle mouse, rebindable in Options > Controls) to drop a
    // through-walls waypoint where you're looking.
    @ConfigValue @JvmField var pingEnabled: Boolean = true
    @ConfigValue @JvmField var pingSound: Boolean = true
    @ConfigValue @JvmField var pingAnnounceParty: Boolean = false  // also post coords to party chat
    @ConfigValue @JvmField var pingShareEnabled: Boolean = false  // show/share pings with other FishMod users
    @ConfigValue @JvmField var pingFromChat: Boolean = true   // render a waypoint from coords posted in chat
    @ConfigValue @JvmField var pingColor: Int = 0xFF55FFFF.toInt() // ARGB (aqua)
    @ConfigValue @JvmField var pingDurationSeconds: Int = 8

    // ── Wardrobe Hotkeys ────────────────────────────────────────────────────────
    // Press a Wardrobe slot hotkey (bind in Options > Controls) to instantly click that
    // set/loadout in an open Wardrobe or Loadouts GUI.
    @ConfigValue @JvmField var wardrobeHotkeysEnabled: Boolean = false
    @ConfigValue @JvmField var wardrobeHotkeysAutoClose: Boolean = true

    // Loadout Title: parse "You equipped <Name>!" (item-customizer loadout switch) and flash the
    // loadout name as an on-screen title.
    @ConfigValue @JvmField var loadoutTitleEnabled: Boolean = false

    // Auto Sprint — keep sprinting while holding forward (only sets the flag, never clears it).
    @ConfigValue @JvmField var autoSprintEnabled: Boolean = false

    // Sound Manager — master gate + volume (0-100%) for every FishMod feature cue routed through
    // fishmod.utils.sound.SoundManager.
    @ConfigValue @JvmField var soundMasterEnabled: Boolean = true
    @ConfigValue @JvmField var soundMasterVolume: Int = 100

    // Puzzle solver framework (fishmod.features.dungeon.puzzles).
    @ConfigValue @JvmField var puzzleSolversEnabled: Boolean = false
    @ConfigValue @JvmField var puzzleSolverStyle: String = "Filled Outline" // Filled | Outline | Filled Outline
    // Three Weirdos
    @ConfigValue @JvmField var weirdosSolver: Boolean = true
    @ConfigValue @JvmField var weirdosCorrectColor: Int = 0xB355FF55.toInt()
    @ConfigValue @JvmField var weirdosWrongColor: Int = 0xB3FF5555.toInt()
    // Blaze (Lower/Higher)
    @ConfigValue @JvmField var blazeSolver: Boolean = true
    @ConfigValue @JvmField var blazeFirstColor: Int = 0xC055FF55.toInt()
    @ConfigValue @JvmField var blazeSecondColor: Int = 0xC0FFAA00.toInt()
    @ConfigValue @JvmField var blazeOtherColor: Int = 0x66FFFFFF
    @ConfigValue @JvmField var blazeLine: Boolean = true
    @ConfigValue @JvmField var blazeLineCount: Int = 1
    // Quiz (Oruo trivia)
    @ConfigValue @JvmField var quizSolver: Boolean = true
    @ConfigValue @JvmField var quizColor: Int = 0xC055FF55.toInt()
    // Water Board
    @ConfigValue @JvmField var waterSolver: Boolean = true
    @ConfigValue @JvmField var waterOptimized: Boolean = false
    @ConfigValue @JvmField var waterFirstColor: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var waterSecondColor: Int = 0xC0FFAA00.toInt()
    // Creeper Beams
    @ConfigValue @JvmField var beamsSolver: Boolean = true
    @ConfigValue @JvmField var beamsTracer: Boolean = false
    // Teleport Maze
    @ConfigValue @JvmField var tpMazeSolver: Boolean = true
    @ConfigValue @JvmField var tpMazeNextColor: Int = 0x8055FF55.toInt()
    @ConfigValue @JvmField var tpMazeVisitedColor: Int = 0x80FF5555.toInt()
    // Tic Tac Toe
    @ConfigValue @JvmField var tttSolver: Boolean = true
    @ConfigValue @JvmField var tttColor: Int = 0x9955FF55.toInt()
    // Boulder
    @ConfigValue @JvmField var boulderSolver: Boolean = true
    @ConfigValue @JvmField var boulderShowAll: Boolean = true
    @ConfigValue @JvmField var boulderColor: Int = 0x9955FF55.toInt()
    // Ice Fill
    @ConfigValue @JvmField var iceFillSolver: Boolean = true
    @ConfigValue @JvmField var iceFillColor: Int = 0xFFFF55FF.toInt()

    // ── Arrow Align (F7 P3 device) ─────────────────────────────────────────────
    @ConfigValue @JvmField var arrowAlignEnabled: Boolean = false

    // ── Time Changer (client-side world time) ──────────────────────────────────
    @ConfigValue @JvmField var timeChangerEnabled: Boolean = false
    @ConfigValue @JvmField var timeChangerMode: String = "Day"

    // ── Arrow Hit Sound ───────────────────────────────────────────────────────
    @ConfigValue @JvmField var arrowHitSoundEnabled: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundSuppress: Boolean = false
    @ConfigValue @JvmField var arrowHitSoundName: String = "Note: Harp"
    @ConfigValue @JvmField var arrowHitSoundVolume: Int = 100
    @ConfigValue @JvmField var arrowHitSoundPitch: Double = 1.4

    // ── Block Overlay ─────────────────────────────────────────────────────────
    @ConfigValue @JvmField var blockOverlayEnabled: Boolean = false
    @ConfigValue @JvmField var blockOverlayMode: Int = 2 // 0 outline, 1 fill, 2 filled outline
    @ConfigValue @JvmField var blockOverlayFillColor: Int = 0x40FFFFFF
    @ConfigValue @JvmField var blockOverlayOutlineColor: Int = 0xFFFFFFFF.toInt()
    /** Extra multiplier (0-100%) on the fill alpha, on top of the fill colour's own alpha. */
    @ConfigValue @JvmField var blockOverlayOpacity: Int = 100
    @ConfigValue @JvmField var blockOverlayPhase: Boolean = false

    // ── Wither ESP (F7) ──────────────────────────────────────────────────────
    @ConfigValue @JvmField var witherEspEnabled: Boolean = false
    @ConfigValue @JvmField var witherEspMaxorColor: Int = 0xFF5804A4.toInt()
    @ConfigValue @JvmField var witherEspStormColor: Int = 0xFF00D0FF.toInt()
    @ConfigValue @JvmField var witherEspGoldorColor: Int = 0xFFFFFFFF.toInt()
    @ConfigValue @JvmField var witherEspNecronColor: Int = 0xFFFF0000.toInt()

    // ── M7 Relics HUD (enable flags are Floor7.enableRelicStartTimer / renderRelicHighlight) ──
    @ConfigValue @JvmField var relicTimerHudX: Int = 10
    @ConfigValue @JvmField var relicTimerHudY: Int = 180
    @ConfigValue @JvmField var relicTimerScale: Double = 1.0

    // ── Item Quality Tooltip ─────────────────────────────────────────────────
    @ConfigValue @JvmField var itemQualityTooltip: Boolean = false

    // ── Gyro Helper ─────────────────────────────────────────────────────────
    @ConfigValue @JvmField var gyroHelperEnabled: Boolean = false
    @ConfigValue @JvmField var gyroBoxColor: Int = 0xFF55FFFF.toInt()
    @ConfigValue @JvmField var gyroRingColor: Int = 0xFF55FFFF.toInt()

    // ── Mage Beam ──────────────────────────────────────────────────────────
    @ConfigValue @JvmField var mageBeamEnabled: Boolean = false
    @ConfigValue @JvmField var mageBeamColor: Int = 0xFFAA0000.toInt()
    @ConfigValue @JvmField var mageBeamDurationTicks: Int = 40
    @ConfigValue @JvmField var mageBeamHideParticles: Boolean = true
    @ConfigValue @JvmField var mageBeamDepth: Boolean = true

    // ── Spring Boots ───────────────────────────────────────────────────────
    @ConfigValue @JvmField var springBootsEnabled: Boolean = false
    @ConfigValue @JvmField var springBootsShowBlocks: Boolean = false
    @ConfigValue @JvmField var springBootsBox: Boolean = true
    @ConfigValue @JvmField var springBootsBoxColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var springBootsHudX: Int = 10
    @ConfigValue @JvmField var springBootsHudY: Int = 200
    @ConfigValue @JvmField var springBootsScale: Double = 1.0

    // ── Terracotta Timer (F6) ────────────────────────────────────────────────
    @ConfigValue @JvmField var terracottaTimerEnabled: Boolean = false

    // ── Wither Dragons (M7 P5) ───────────────────────────────────────────────
    @ConfigValue @JvmField var witherDragonsEnabled: Boolean = false
    @ConfigValue @JvmField var witherDragonsTimer: Boolean = true
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
    // priority
    @ConfigValue @JvmField var witherDragonsPriority: Boolean = false
    @ConfigValue @JvmField var witherDragonsNormalPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsEasyPower: Double = 0.0
    @ConfigValue @JvmField var witherDragonsSoloDebuff: Int = 0        // 0 Tank, 1 Healer
    @ConfigValue @JvmField var witherDragonsSoloDebuffAll: Boolean = true

    // ── Leap Counter ─────────────────────────────────────────────────────────

    // ── Auto GFS (refill dungeon consumables from your own sacks) ─────────────
    @ConfigValue @JvmField var autoGfsEnabled: Boolean = false
    @ConfigValue @JvmField var autoGfsDelaySec: Int = 20
    @ConfigValue @JvmField var autoGfsPearls: Boolean = true
    @ConfigValue @JvmField var autoGfsTnt: Boolean = false
    @ConfigValue @JvmField var autoGfsLeaps: Boolean = false
    @ConfigValue @JvmField var autoGfsJerry: Boolean = false

    // ── Tac Timer ────────────────────────────────────────────────────────────
    @ConfigValue @JvmField var tacTimerEnabled: Boolean = false
    @ConfigValue @JvmField var tacTimerReverse: Boolean = false
    @ConfigValue @JvmField var tacTimerPrefix: Boolean = true
    @ConfigValue @JvmField var tacTimerSuffix: Boolean = false
    @ConfigValue @JvmField var tacTimerWaypoint: Boolean = false
    @ConfigValue @JvmField var tacTimerColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var tacTimerHudX: Int = 10
    @ConfigValue @JvmField var tacTimerHudY: Int = 180
    @ConfigValue @JvmField var tacTimerScale: Double = 1.0

    // ── Dungeon Abilities ────────────────────────────────────────────────────
    @ConfigValue @JvmField var dungeonAbilitiesEnabled: Boolean = false

    // ── Slot Binds ───────────────────────────────────────────────────────────
    @ConfigValue @JvmField var slotBindsEnabled: Boolean = false
    @ConfigValue @JvmField var slotBindsShow: Boolean = true
    @ConfigValue @JvmField var slotBindsBorder: Boolean = true
    @ConfigValue @JvmField var slotBindsLine: Boolean = true
    @ConfigValue @JvmField var slotBindsHoverOnly: Boolean = false
    @ConfigValue @JvmField var slotBindsColor: Int = 0xFFFF55FF.toInt()

    // ── Camera Tweaks ─────────────────────────────────────────────────────────
    @ConfigValue @JvmField var cameraTweaksEnabled: Boolean = false
    @ConfigValue @JvmField var cameraCustomFov: Boolean = false
    @ConfigValue @JvmField var cameraFov: Int = 110
    @ConfigValue @JvmField var cameraFullBright: Boolean = false
    @ConfigValue @JvmField var cameraNoBlindness: Boolean = false
    @ConfigValue @JvmField var cameraNoNausea: Boolean = false

    // ── F7 Terminal Solver (Odin TerminalSolver port) ──────────────────────────
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

    // Warp Cooldown HUD (revives Dungeons.enableWarpCooldown). Cooldown starts on the party's
    // "entered <floor> Catacombs" chat line (Odin logic); 30s is Hypixel's real re-entry gate.
    @ConfigValue @JvmField var warpCooldownSeconds: Int = 30
    @ConfigValue @JvmField var warpCooldownColor: Int = 0xFF55FF55.toInt()
    @ConfigValue @JvmField var warpAnnounceKick: Boolean = false
    @ConfigValue @JvmField var warpKickText: String = "Kicked!"
    @ConfigValue @JvmField var warpCooldownHudX: Int = 10
    @ConfigValue @JvmField var warpCooldownHudY: Int = 160
    @ConfigValue @JvmField var warpCooldownScale: Double = 1.0

    // ── Blessing Display (Odin BlessingDisplay port; reads the tab footer) ────────
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

    // ── Invincibility Timer (Odin InvincibilityTimer port) ──────────────────────
    // enable is Dungeons.displayInvincibilityTimer; Dungeons.InvincibilityDuration = show "X.Xs"
    // vs a plain icon; Dungeons.useStatusColorForInvincibility = gold/red/green by state.
    @ConfigValue @JvmField var invincAnnounce: Boolean = true
    @ConfigValue @JvmField var invincShowCooldown: Boolean = true
    @ConfigValue @JvmField var invincShowWhen: String = "Any" // Always | Any | Active | Cooldown
    @ConfigValue @JvmField var invincShowInBoss: Boolean = false
    @ConfigValue @JvmField var invincShowSpirit: Boolean = true
    @ConfigValue @JvmField var invincShowBonzo: Boolean = true
    @ConfigValue @JvmField var invincShowPhoenix: Boolean = true
    @ConfigValue @JvmField var invincEquippedMaskColor: Int = 0xFFAA00AA.toInt()
    @ConfigValue @JvmField var invincHudX: Int = 10
    @ConfigValue @JvmField var invincHudY: Int = 140
    @ConfigValue @JvmField var invincScale: Double = 1.0

    // ── Key Notifier (revives Dungeons.enableKeyNotifier) ───────────────────────
    @ConfigValue @JvmField var keyNotifierTitle: Boolean = true
    @ConfigValue @JvmField var keyNotifierChat: Boolean = false
    @ConfigValue @JvmField var keyNotifierSound: Boolean = true

    // ── Leap Messages (revives Dungeons.enableLeapMessages) ─────────────────────
    @ConfigValue @JvmField var leapMessagesTitle: Boolean = true
    /** Send the leap message to party chat (/pc). */
    @ConfigValue @JvmField var leapMessagesParty: Boolean = false
    @ConfigValue @JvmField var leapMessagesSound: Boolean = true
    /** {name} = the Spirit-Leap target. */
    @ConfigValue @JvmField var leapMessagesText: String = "&b&lLEAP &r&7-> &f{name}"

    // ── Auto Sprint ─────────────────────────────────────────────────────────────
    @ConfigValue @JvmField var autoSprintDungeonOnly: Boolean = false

    // ── Auto Requeue delay (ms after the "> EXTRA STATS <" line) ───────────────
    @ConfigValue @JvmField var autoRequeueDelayMs: Int = 2000

    // ── Secret Clicked (Odin SecretClicked port) ───────────────────────────────
    @ConfigValue @JvmField var secretClickedEnabled: Boolean = false
    @ConfigValue @JvmField var secretClickedBoxes: Boolean = true
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

    // ── Custom Scoreboard ────────────────────────────────────────────────────────
    // Replaces vanilla's sidebar scoreboard with one where each line category can be hidden
    // and big numbers (Purse/Bank/Bits/etc) can be shown compact (1,234,567 -> 1.2M).
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
    // API/HUD-backed extras -- not on the real Hypixel sidebar, sourced from PetHud's already-tracked
    // pet state and a 60s Hypixel-API skill-level poll (see fishmod.features.scoreboard.SkillLevels).
    @ConfigValue @JvmField var sbSectionPetExtra: Boolean = false
    @ConfigValue @JvmField var sbSectionSkills: Boolean = false
    @ConfigValue @JvmField var sbSectionBestiary: Boolean = false
    @ConfigValue @JvmField var sbSectionCollections: Boolean = false
    @ConfigValue @JvmField var sbSectionElection: Boolean = false
    @ConfigValue @JvmField var sbSectionFireSales: Boolean = false

}
