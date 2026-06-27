package fishmod.utils.config.values;

import config.practical.manager.ConfigValue;

/**
 * Settings unique to FishMod — lives only in FishMod's jar so it always
 * loads from the correct class file even when blade-addons is also present.
 */
public class FishSettings {

    @ConfigValue
    public static boolean sendLagToParty = false;

    @ConfigValue
    public static boolean showPuzzles = false;

    @ConfigValue
    public static boolean deathMessageEnabled = false;

    @ConfigValue
    public static String deathMessageTemplate = "{name} died like a bum";

    @ConfigValue
    public static boolean deathMessageToParty = false;

    /** Class XP gained per run when playing as that class — used for .rtca / .crtc */
    @ConfigValue
    public static int rtcaClassXpPerRun = 424200;
    /** Class XP gained per run for the classes you're NOT playing (passive) — used for .rtca */
    @ConfigValue public static int rtcaClassPassiveXpPerRun = 106050;
    @ConfigValue public static int rtcCataXpPerRun  = 509040;
    /** Hypixel's first-5-runs-of-the-day daily bonus (+40% XP). Toggle off after you've used them. */
    @ConfigValue public static boolean rtcaIncludeDailyBonus = false;

    @ConfigValue
    public static boolean warpMapHudEnabled = false;

    @ConfigValue
    public static int warpMapDotColor = 0xFFDB3737;

    // Splits HUD (standalone position)
    @ConfigValue public static int     splitsHudX               = 5;
    @ConfigValue public static int     splitsHudY               = 10;

    // Soulflow HUD
    @ConfigValue public static boolean soulflowHudEnabled      = false;
    @ConfigValue public static int     soulflowWarningThreshold = 1000;
    @ConfigValue public static boolean soulflowMissingNotifier  = false;
    @ConfigValue public static int     soulflowHudX             = 10;
    @ConfigValue public static int     soulflowHudY             = 60;
    @ConfigValue public static int     soulflowHudColor         = 0xFF55FFFF; // Aqua §b

    // FishMod GUI
    @ConfigValue public static String fmguiScale = "Normal"; // Normal | 1.5x | 2x

    // Pet XP multipliers (see Hypixel wiki — Pets/Pet XP).
    // Pet XP gained = skill XP × (1 + taming×0.01) × (1 + beastmaster%/100) × (1 + petItem%/100) × extraMult.
    /** Taming level — adds +1% pet XP per level (max 60 = +60%). */
    @ConfigValue public static int petXpTamingLevel       = 0;
    /** Beastmaster Crest bonus % (Coal=10, Iron=20, Gold=30, Diamond=40, Bronze pre-promote=2…). */
    @ConfigValue public static int petXpBeastmasterBonus  = 0;
    /** Pet item XP bonus % — items like "All Skills XP Boost". 0 = none. */
    @ConfigValue public static int petXpPetItemBonus      = 0;
    /** Booster cookie active (+20% skill XP, which becomes +20% pet XP for matching pets). */
    @ConfigValue public static boolean petXpBoosterCookie = false;
    /** When true, the four pet XP multipliers above are auto-detected from the Hypixel API every ~60s. */
    @ConfigValue public static boolean petXpAutoDetect    = false;

    // Pet HUD
    @ConfigValue public static boolean petHudEnabled    = false;
    @ConfigValue public static boolean petHudShowLevel  = false;
    @ConfigValue public static boolean petHudFadeIdle   = false;
    @ConfigValue public static int     petHudFadeMs     = 5000;
    @ConfigValue public static int     petHudX          = 10;
    @ConfigValue public static int     petHudY          = 80;
    @ConfigValue public static int     petHudColor      = 0xFFFFD580;

    // Cooldown overlay (per-item ability cooldowns drawn on hotbar / inventory slots)
    @ConfigValue public static boolean cooldownOverlayEnabled = false;
    @ConfigValue public static boolean cooldownShowText       = false;
    @ConfigValue public static boolean cooldownShowBar        = false;
    @ConfigValue public static boolean cooldownOnlyUnder3s    = false;

    // Bridge Bot
    @ConfigValue public static boolean bridgeBotEnabled = false;
    @ConfigValue public static String  bridgeBotName    = "";

    // Slayer XP tracker
    @ConfigValue public static boolean fireFreezeTimerEnabled = false;
    @ConfigValue public static boolean skillTrackerEnabled = false;
    @ConfigValue public static int     skillTrackerHudX    = 10;
    @ConfigValue public static int     skillTrackerHudY    = 360;
    @ConfigValue public static double  skillTrackerScale   = 1.0;

    @ConfigValue public static boolean slayerXpEnabled = false;
    @ConfigValue public static int     slayerXpHudX    = 10;
    @ConfigValue public static int     slayerXpHudY        = 80;

    // Powder tracker
    @ConfigValue public static boolean powderTrackerEnabled = false;
    @ConfigValue public static int     powderTrackerHudX    = 10;
    @ConfigValue public static int     powderTrackerHudY    = 100;

    // Session stats HUD
    @ConfigValue public static boolean sessionStatsEnabled      = false;
    @ConfigValue public static boolean sessionStatsInDungeon    = false;
    @ConfigValue public static boolean sessionStatsInDungeonHub = false;
    @ConfigValue public static boolean sessionStatsResetOnRelog = false;
    @ConfigValue public static int     sessionStatsHudX         = 10;
    @ConfigValue public static int     sessionStatsHudY         = 120;

    // Chat-channel compatibility — when on, dot-commands (.pb, .rtca, etc.) work in these
    // channels in addition to party chat, and replies go back in the same channel.
    @ConfigValue public static boolean chatParty   = false;
    @ConfigValue public static boolean chatGuild   = false;
    @ConfigValue public static boolean chatOfficer = false;
    @ConfigValue public static boolean chatPrivate = false;
    @ConfigValue public static boolean chatAll     = false; // opt-in (false-positive risk)
    // Meow auto-responder: replies "meow" when anyone says meow in an enabled chat.
    @ConfigValue public static boolean chatMeow    = false;

    // Compact custom tab list (replaces vanilla player list while tab is held). Opt-in.
    @ConfigValue public static boolean compactTabEnabled = false;
    /** Panel opacity percentage (0 = fully transparent, 100 = solid). Default 70%. */
    @ConfigValue public static int     compactTabOpacity = 70;

    // Party command toggles
    @ConfigValue public static boolean pcAllinvite  = false;
    @ConfigValue public static boolean pcPb         = false;
    @ConfigValue public static boolean pcCata       = false;
    @ConfigValue public static boolean pcRtca       = false;
    @ConfigValue public static boolean pcDprofit    = false;
    @ConfigValue public static boolean pcRtc        = false;
    @ConfigValue public static boolean pcCrtc       = false;
    @ConfigValue public static boolean pcHelp       = false;
    @ConfigValue public static boolean pcNw         = false;
    @ConfigValue public static boolean pcBank       = false;
    @ConfigValue public static boolean pcPowder     = false;
    @ConfigValue public static boolean pcLevel      = false;
    @ConfigValue public static boolean pcFarming    = false;
    @ConfigValue public static boolean pcVisitor    = false;
    @ConfigValue public static boolean pcNuc        = false;
    @ConfigValue public static boolean pcWorm       = false; // .worm / .scatha (Worm + Scatha bestiary)

    // Smart copy-chat: right-click a chat line to copy the whole message (joins wrapped lines,
    // strips ---- / ▬▬▬ dividers).
    @ConfigValue public static boolean smartCopyChat = false;

    // Mod chat prefix — shown as "<prefix> > <message>" on FishMod's chat output (max 10 chars).
    @ConfigValue public static boolean modPrefixEnabled = false;
    @ConfigValue public static String modPrefix = "FM";

    // Dungeon Score (live S+ tracker)
    @ConfigValue public static boolean dungeonScoreEnabled = false;
    @ConfigValue public static int     dungeonScoreHudX    = 10;
    @ConfigValue public static int     dungeonScoreHudY    = 200;
    @ConfigValue public static boolean dungeonScorePaulActive = false;

    // Farming coin/hr tracker
    @ConfigValue public static boolean farmingTrackerEnabled = false;
    @ConfigValue public static int     farmingTrackerHudX    = 10;
    @ConfigValue public static int     farmingTrackerHudY    = 240;

    // Harvest Feast tracker
    @ConfigValue public static boolean harvestFeastEnabled = false;
    @ConfigValue public static int     harvestFeastHudX    = 10;
    @ConfigValue public static int     harvestFeastHudY    = 280;
    @ConfigValue public static double  harvestFeastScale   = 1.0;

    // Mining coin/hr tracker
    @ConfigValue public static boolean miningTrackerEnabled = false;
    @ConfigValue public static int     miningTrackerHudX    = 10;
    @ConfigValue public static int     miningTrackerHudY    = 320;
    @ConfigValue public static double  miningTrackerScale   = 1.0;

    // Show other mod users' cosmetic nicks (your own always shows locally)
    @ConfigValue public static boolean remoteNicksEnabled = false;

    // Show other mod users' custom item/armor cosmetics (dye, trim, model, name, stars)
    @ConfigValue public static boolean remoteItemsEnabled = false;

    // Customizable player model size (render-only — no hitbox/attribute change). Own size shows
    // locally when enabled; Share publishes it so other mod users render you at it (and you see theirs).
    @ConfigValue public static boolean playerSizeEnabled = false;
    @ConfigValue public static double  playerSizeScaleX  = 1.0;   // 0.25–5.0 width  multiplier
    @ConfigValue public static double  playerSizeScaleY  = 1.0;   // 0.25–5.0 height multiplier
    @ConfigValue public static double  playerSizeScaleZ  = 1.0;   // 0.25–5.0 depth  multiplier
    @ConfigValue public static boolean playerSizeShared  = false; // publish mine + render others' sizes

    // Chat filter: hide selected categories of Hypixel chat spam. Master gate + per-category toggles.
    @ConfigValue public static boolean chatFilterEnabled       = false;
    @ConfigValue public static boolean cfKillCombo             = true;  // "+15 Kill Combo"
    @ConfigValue public static boolean cfBossMessages          = false; // "[BOSS] Wither King: ..."
    @ConfigValue public static boolean cfFriendJoinLeave       = false; // "Friend > X joined./left."
    @ConfigValue public static boolean cfBazaar                = false; // "[Bazaar] Executing instant buy..."
    @ConfigValue public static boolean cfWarping               = false; // "Warping..."

    // Explosive Shot: parse "Your Explosive Shot hit N enemy/enemies for D damage." and show the
    // per-enemy damage (D / N) as an on-screen title.
    @ConfigValue public static boolean explosiveShotEnabled    = false;

    // M7/F7 lever waypoints: through-walls filled box on each boss lever; disappears once flipped.
    @ConfigValue public static boolean enableM7LeverWaypoints  = false;
    @ConfigValue public static int     m7LeverWaypointColor    = 0x13FF0086; // ARGB (faint magenta)

    // Name color: gradient applied to your real username
    @ConfigValue public static int nickColorStart = 0xFFFF5555; // red
    @ConfigValue public static int nickColorEnd   = 0xFF5555FF; // blue
    // Optional custom nick text (up to 18 visible chars, & color codes ok). Empty = use real IGN.
    @ConfigValue public static String nickCustomName = "";
    // Color application mode for the nick (custom name or IGN). "GRADIENT" = Start→End across letters; "SOLID" = single Start color.
    @ConfigValue public static String nickColorMode = "GRADIENT";

    // Your own above-head nametag (with [level] + emblem)
    @ConfigValue public static boolean nickPreviewEnabled = false;
    @ConfigValue public static double  nickPreviewScale   = 1.0;  // text size (best-effort; IF may pin it)
    @ConfigValue public static double  nickPreviewYOffset = 0.0;  // raise(+)/lower(-) the tag, blocks

    // Trophy Frogs tab tracker
    @ConfigValue public static boolean trophyFrogEnabled = false;
    @ConfigValue public static int     trophyFrogHudX    = 10;
    @ConfigValue public static int     trophyFrogHudY    = 60;
    @ConfigValue public static double  trophyFrogHudScale = 1.0;

    public enum PriceMode {
        INSTASELL,  // bazaar buyPrice  (default)
        SELL_OFFER, // bazaar sellPrice
        NPC_SELL    // items API npc_sell_price
    }
    @ConfigValue public static PriceMode trackerPriceModeEnum = PriceMode.INSTASELL;
    // Per-tracker price mode (Slayer XP doesn't have one — it tracks XP, not items)
    @ConfigValue public static PriceMode powderPriceMode       = PriceMode.INSTASELL;
    @ConfigValue public static PriceMode farmingPriceMode      = PriceMode.INSTASELL;
    @ConfigValue public static PriceMode harvestFeastPriceMode = PriceMode.INSTASELL;
    @ConfigValue public static PriceMode miningPriceMode       = PriceMode.INSTASELL;
    // Legacy int kept for any save-file backcompat; not used by code anymore.
    @ConfigValue public static int trackerPriceMode = 0;
    @ConfigValue public static boolean pcCorpse = false;

    // Cooldown overlay extras
    @ConfigValue public static boolean cooldownInInventory = false;

    // Per-HUD scale (1.0 = default). Adjusted via scroll wheel in HUD editor.
    @ConfigValue public static double sessionStatsScale  = 1.0;
    @ConfigValue public static double powderTrackerScale = 1.0;
    @ConfigValue public static double slayerXpScale      = 1.0;
    @ConfigValue public static double farmingTrackerScale = 1.0;
    @ConfigValue public static double dungeonScoreScale  = 1.0;
    @ConfigValue public static double petHudScale         = 1.0;
    @ConfigValue public static double soulflowHudScale    = 1.0;
    @ConfigValue public static boolean pcSecrets    = false;
    @ConfigValue public static boolean pcRuns       = false;
    @ConfigValue public static boolean pcJoinFloor  = false;
    @ConfigValue public static boolean pcFps        = false;
    @ConfigValue public static boolean pcTps        = false;
    @ConfigValue public static boolean pcPing       = false;
    @ConfigValue public static boolean pcDisband    = false;
    @ConfigValue public static boolean pcMp         = false;
    @ConfigValue public static boolean pcCollection = false;

    // Chat-triggered party actions (.kick / .warp / .transfer / .promote).
    // pcPartyActionsMode: "off" | "self" | "whitelist" | "everyone".
    // Default off for safety — only enable when you trust the party.
    @ConfigValue public static boolean pcPartyActions          = false;
    @ConfigValue public static String  pcPartyActionsMode      = "self";
    @ConfigValue public static String  pcPartyActionsWhitelist = "";

    // Manual loot/profit tracker (in-inventory panel, Dungeon Hub only)
    @ConfigValue public static boolean lootTrackerEnabled = false;
    @ConfigValue public static int     lootTrackerX = -1; // -1 = auto-anchor beside the inventory
    @ConfigValue public static int     lootTrackerY = -1;

    // Simon Says (F7 Goldor) tracker
    @ConfigValue public static boolean simonSaysEnabled    = false;
    @ConfigValue public static boolean simonSaysHudEnabled = false;
    @ConfigValue public static boolean simonSaysPartyChat  = false;
    @ConfigValue public static boolean simonSaysFailEnabled = false;
    @ConfigValue public static String  simonSaysFailMessage = "Simon Says: FAILED!";
    @ConfigValue public static int     simonSaysHudX       = 10;
    @ConfigValue public static int     simonSaysHudY       = 360;
    @ConfigValue public static double  simonSaysHudScale   = 1.0;

    // Daily/Weekly/Monthly Challenges
    @ConfigValue public static boolean challengesEnabled            = false;
    @ConfigValue public static boolean challengeHudEnabled          = false;
    @ConfigValue public static int     challengeHudX                = 10;
    @ConfigValue public static int     challengeHudY                = 400;
    @ConfigValue public static double  challengeHudScale            = 1.0;
    @ConfigValue public static int     challengeAfkMinutes          = 3;
    @ConfigValue public static boolean challengeLeaderboardEnabled  = false;
    /** Optional override for the /challenges/* worker base URL. Empty = default proxy. */
    @ConfigValue public static String  challengeWorkerOverride      = "";

    // Class Colored Boots — recolor your dungeon boots (leather dye) by your detected class.
    @ConfigValue public static boolean classColoredBootsEnabled = false;

    // ── Fishing ───────────────────────────────────────────────────────────────
    // Bobber Reminder: after a fish bites, count down; once the reminder delay passes without
    // reeling, flash a customizable "!!!" alert (+ optional sound); if the catch window closes
    // unreeled, show the "missed it" text. All drawn in one small HUD.
    @ConfigValue public static boolean fishingTimerEnabled    = false;
    /** Seconds after a bite before the "!!!" reel reminder flashes (countdown shows until then). */
    @ConfigValue public static int     fishingReminderDelay   = 3;
    /** Customizable reel-now reminder text. */
    @ConfigValue public static String  fishingReminderText    = "§c§l!!! REEL !!!";
    /** Shown briefly when the catch window closes without a reel. */
    @ConfigValue public static String  fishingMissedText      = "§7Missed it...";
    /** Play a ping when the reminder fires. */
    @ConfigValue public static boolean fishingReminderSound   = true;
    @ConfigValue public static int     fishingTimerHudX       = 10;
    @ConfigValue public static int     fishingTimerHudY       = 140;
    @ConfigValue public static double  fishingTimerScale      = 1.5;

    // Sea Creature Tracker: per-creature session counts + creatures/hr, with an optional
    // title+sound alert when a rare creature surfaces.
    @ConfigValue public static boolean seaCreatureEnabled     = false;
    @ConfigValue public static boolean seaCreatureRareAlert   = true;
    @ConfigValue public static int     seaCreatureHudX        = 10;
    @ConfigValue public static int     seaCreatureHudY        = 160;
    @ConfigValue public static double  seaCreatureScale       = 1.0;

    // Trophy Fish tab tracker (Crimson Isle) — same shape as Trophy Frogs.
    @ConfigValue public static boolean trophyFishEnabled      = false;
    @ConfigValue public static int     trophyFishHudX         = 10;
    @ConfigValue public static int     trophyFishHudY         = 200;
    @ConfigValue public static double  trophyFishHudScale     = 1.0;

    // ── Slayer ────────────────────────────────────────────────────────────────
    // Slayer Alerts: title + sound on miniboss spawn, boss spawn, and boss slain.
    @ConfigValue public static boolean slayerAlertsEnabled    = false;
    @ConfigValue public static boolean slayerAlertMiniboss    = true;
    @ConfigValue public static boolean slayerAlertBossSpawn   = true;
    @ConfigValue public static boolean slayerAlertBossSlain   = true;
    @ConfigValue public static boolean slayerAlertSound       = true;

    // Slayer Drop Tracker: session counter for RARE / VERY RARE / CRAZY RARE / PRAISE drops.
    @ConfigValue public static boolean slayerDropsEnabled     = false;
    @ConfigValue public static int     slayerDropsHudX        = 10;
    @ConfigValue public static int     slayerDropsHudY        = 240;
    @ConfigValue public static double  slayerDropsScale       = 1.0;

    // ── Location Ping ───────────────────────────────────────────────────────────
    // Press the ping key (default middle mouse, rebindable in Options > Controls) to drop a
    // through-walls waypoint where you're looking.
    @ConfigValue public static boolean pingEnabled         = true;
    @ConfigValue public static boolean pingSound           = true;
    @ConfigValue public static boolean pingAnnounceParty   = false;  // also post coords to party chat
    @ConfigValue public static boolean pingShareEnabled    = false;  // show/share pings with other FishMod users
    @ConfigValue public static int     pingColor           = 0xFF55FFFF; // ARGB (aqua)
    @ConfigValue public static int     pingDurationSeconds = 8;

    // ── Desk-Buddy ──────────────────────────────────────────────────────────────
    // A tiny kaomoji companion that idles, sleeps when you're AFK, and dances on RNG drops.
    @ConfigValue public static boolean deskBuddyEnabled       = false;
    @ConfigValue public static boolean deskBuddyReactToRng    = true;   // dance on rare drops / praise rngesus
    @ConfigValue public static String  deskBuddyName          = "Rocky";
    @ConfigValue public static int     deskBuddyAfkSeconds    = 120;    // idle this long → sleep
    @ConfigValue public static int     deskBuddyHudX          = 10;
    @ConfigValue public static int     deskBuddyHudY          = 440;
    @ConfigValue public static double  deskBuddyScale         = 1.5;

}
