package fishmod.utils.config.values

import config.practical.manager.ConfigValue

/**
 * Settings unique to FishMod — lives only in FishMod's jar so it always
 * loads from the correct class file even when blade-addons is also present.
 */
object FishSettings {

    @ConfigValue @JvmField var sendLagToParty: Boolean = false

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

    // Bridge Bot
    @ConfigValue @JvmField var bridgeBotEnabled: Boolean = false
    @ConfigValue @JvmField var bridgeBotName: String = ""

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
    // Meow auto-responder: replies "meow" when anyone says meow in an enabled chat.
    @ConfigValue @JvmField var chatMeow: Boolean = false
    // Compact chat: collapse identical messages seen within the last minute into one line
    // with a "(N)" count instead of repeating them.
    @ConfigValue @JvmField var chatCompact: Boolean = false

    // Compact custom tab list (replaces vanilla player list while tab is held). Opt-in.
    @ConfigValue @JvmField var compactTabEnabled: Boolean = false
    /** Panel opacity percentage (0 = fully transparent, 100 = solid). Default 70%. */
    @ConfigValue @JvmField var compactTabOpacity: Int = 70

    // Party command toggles
    @ConfigValue @JvmField var pcAllinvite: Boolean = false
    @ConfigValue @JvmField var pcPb: Boolean = false
    @ConfigValue @JvmField var pcCata: Boolean = false
    @ConfigValue @JvmField var pcRtca: Boolean = false
    @ConfigValue @JvmField var pcDprofit: Boolean = false
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

    // Dungeon Score (live S+ tracker)
    @ConfigValue @JvmField var dungeonScoreEnabled: Boolean = false
    @ConfigValue @JvmField var dungeonScoreHudX: Int = 10
    @ConfigValue @JvmField var dungeonScoreHudY: Int = 200
    @ConfigValue @JvmField var dungeonScorePaulActive: Boolean = false
    @ConfigValue @JvmField var dungeonScoreMissingMsg: Boolean = true
    @ConfigValue @JvmField var dungeonScoreShowLeft: Boolean = false
    // 270/300 score alerts — on-screen title + chat message, each toggleable, text customizable (& color codes ok).
    @ConfigValue @JvmField var score270TitleEnabled: Boolean = true
    @ConfigValue @JvmField var score270ChatEnabled: Boolean = true
    @ConfigValue @JvmField var score270Text: String = "&e&l270 Score!"
    @ConfigValue @JvmField var score300TitleEnabled: Boolean = true
    @ConfigValue @JvmField var score300ChatEnabled: Boolean = true
    @ConfigValue @JvmField var score300Text: String = "&a&l300 Score!"

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

    // Show other mod users' cosmetic nicks (your own always shows locally)
    @ConfigValue @JvmField var remoteNicksEnabled: Boolean = false

    // Show other mod users' custom item/armor cosmetics (dye, trim, model, name, stars)
    @ConfigValue @JvmField var remoteItemsEnabled: Boolean = false

    // Customizable player model size (render-only — no hitbox/attribute change). Own size shows
    // locally when enabled; Share publishes it so other mod users render you at it (and you see theirs).
    @ConfigValue @JvmField var playerSizeEnabled: Boolean = false
    @ConfigValue @JvmField var playerSizeScaleX: Double = 1.0   // 0.25–5.0 width  multiplier
    @ConfigValue @JvmField var playerSizeScaleY: Double = 1.0   // 0.25–5.0 height multiplier
    @ConfigValue @JvmField var playerSizeScaleZ: Double = 1.0   // 0.25–5.0 depth  multiplier
    @ConfigValue @JvmField var playerSizeShared: Boolean = false // publish mine + render others' sizes

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

    // Loadout Title: parse "You equipped <Name>!" (item customizer loadout switch) and show the
    // loadout name as an on-screen title.
    @ConfigValue @JvmField var loadoutTitleEnabled: Boolean = false

    // M7/F7 lever waypoints: through-walls filled box on each boss lever; disappears once flipped.
    @ConfigValue @JvmField var enableM7LeverWaypoints: Boolean = false
    @ConfigValue @JvmField var m7LeverWaypointColor: Int = 0x13FF0086 // ARGB (faint magenta)

    // Starred mob visualizer: outlines dungeon mobs whose nametag carries the gold ✯ (must be
    // killed to clear the floor) so they stand out from regular fodder mobs.
    @ConfigValue @JvmField var enableStarredMobHighlight: Boolean = false
    @ConfigValue @JvmField var starredMobHighlightColor: Int = 0x80FFAA00.toInt() // ARGB (translucent gold)

    // Name color: gradient applied to your real username
    @ConfigValue @JvmField var nickColorStart: Int = 0xFFFF5555.toInt() // red
    @ConfigValue @JvmField var nickColorEnd: Int = 0xFF5555FF.toInt()   // blue
    // Optional custom nick text (up to 18 visible chars, & color codes ok). Empty = use real IGN.
    @ConfigValue @JvmField var nickCustomName: String = ""
    // Color application mode for the nick (custom name or IGN). "GRADIENT" = Start→End across letters; "SOLID" = single Start color.
    @ConfigValue @JvmField var nickColorMode: String = "GRADIENT"

    // Your own above-head nametag (with [level] + emblem)
    @ConfigValue @JvmField var nickPreviewEnabled: Boolean = false
    @ConfigValue @JvmField var nickPreviewScale: Double = 1.0  // text size (best-effort; IF may pin it)
    @ConfigValue @JvmField var nickPreviewYOffset: Double = 0.0  // raise(+)/lower(-) the tag, blocks

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
    @ConfigValue @JvmField var dungeonScoreScale: Double = 1.0
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

    // ── TTS Voice Callouts ──────────────────────────────────────────────────────
    // Speak short alerts through the OS's built-in text-to-speech.
    @ConfigValue @JvmField var ttsEnabled: Boolean = false
    @ConfigValue @JvmField var ttsRareDrops: Boolean = true   // rare/insane drops, praise rngesus, great catch
    @ConfigValue @JvmField var ttsSlayer: Boolean = true   // slayer quest started / complete / boss slain
    @ConfigValue @JvmField var ttsFishing: Boolean = true   // spoken "Reel" when the bobber reminder fires
    @ConfigValue @JvmField var ttsRate: Int = 0      // Windows speech rate (-10..10)

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

    // ── Streamer Mode ───────────────────────────────────────────────────────────
    // Anti-snipe: §k-scramble player IGNs in Party Finder menus + your own name in chat. Optional
    // lobby tab scrambling for when you're idling in a hub.
    @ConfigValue @JvmField var streamerMode: Boolean = false
    @ConfigValue @JvmField var streamerHideTab: Boolean = false

    // ── Reputation ──────────────────────────────────────────────────────────────
    // Show a red ✘ next to flagged (net-negative rep) players in the tab list.
    @ConfigValue @JvmField var repFlagsEnabled: Boolean = false

    // ── Wardrobe Hotkeys ────────────────────────────────────────────────────────
    // Press a Wardrobe slot hotkey (bind in Options > Controls) to instantly click that
    // set/loadout in an open Wardrobe or Loadouts GUI.
    @ConfigValue @JvmField var wardrobeHotkeysEnabled: Boolean = false
    @ConfigValue @JvmField var wardrobeHotkeysAutoClose: Boolean = true

    // ── Desk-Buddy ──────────────────────────────────────────────────────────────
    // A tiny kaomoji companion that idles, sleeps when you're AFK, and dances on RNG drops.
    @ConfigValue @JvmField var deskBuddyEnabled: Boolean = false
    @ConfigValue @JvmField var deskBuddyReactToRng: Boolean = true   // dance on rare drops / praise rngesus
    @ConfigValue @JvmField var deskBuddyName: String = "Rocky"
    @ConfigValue @JvmField var deskBuddyAfkSeconds: Int = 120    // idle this long → sleep
    @ConfigValue @JvmField var deskBuddyHudX: Int = 10
    @ConfigValue @JvmField var deskBuddyHudY: Int = 440
    @ConfigValue @JvmField var deskBuddyScale: Double = 1.5

}
