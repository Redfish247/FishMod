package blade.addon.utils.config.values;

import config.practical.manager.ConfigValue;

/**
 * Settings unique to FishMod — lives only in FishMod's jar so it always
 * loads from the correct class file even when blade-addons is also present.
 */
public class FishSettings {

    @ConfigValue
    public static boolean sendLagToParty = true;

    @ConfigValue
    public static boolean showPuzzles = true;

    @ConfigValue
    public static boolean deathMessageEnabled = true;

    @ConfigValue
    public static String deathMessageTemplate = "{name} died like a bum";

    @ConfigValue
    public static boolean deathMessageToParty = false;

    /** Class XP gained per run when playing as that class — used for .rtca */
    @ConfigValue
    public static int rtcaClassXpPerRun = 403000;
    @ConfigValue public static int rtcCataXpPerRun  = 490000;
    /** Hypixel's first-5-runs-of-the-day daily bonus (+40% XP). Toggle off after you've used them. */
    @ConfigValue public static boolean rtcaIncludeDailyBonus = true;

    @ConfigValue
    public static boolean warpMapHudEnabled = true;

    @ConfigValue
    public static int warpMapDotColor = 0xFFDB3737;

    // Splits HUD (standalone position)
    @ConfigValue public static int     splitsHudX               = 5;
    @ConfigValue public static int     splitsHudY               = 10;

    // Soulflow HUD
    @ConfigValue public static boolean soulflowHudEnabled      = true;
    @ConfigValue public static int     soulflowWarningThreshold = 1000;
    @ConfigValue public static boolean soulflowMissingNotifier  = true;
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
    @ConfigValue public static boolean petXpAutoDetect    = true;

    // Pet HUD
    @ConfigValue public static boolean petHudEnabled    = false;
    @ConfigValue public static boolean petHudShowLevel  = true;
    @ConfigValue public static boolean petHudFadeIdle   = true;
    @ConfigValue public static int     petHudFadeMs     = 5000;
    @ConfigValue public static int     petHudX          = 10;
    @ConfigValue public static int     petHudY          = 80;
    @ConfigValue public static int     petHudColor      = 0xFFFFD580;

    // Cooldown overlay (per-item ability cooldowns drawn on hotbar / inventory slots)
    @ConfigValue public static boolean cooldownOverlayEnabled = false;
    @ConfigValue public static boolean cooldownShowText       = true;
    @ConfigValue public static boolean cooldownShowBar        = false;
    @ConfigValue public static boolean cooldownOnlyUnder3s    = false;

    // Bridge Bot
    @ConfigValue public static boolean bridgeBotEnabled = false;
    @ConfigValue public static String  bridgeBotName    = "";

    // Slayer XP tracker
    @ConfigValue public static boolean fireFreezeTimerEnabled = true;
    @ConfigValue public static boolean skillTrackerEnabled = true;
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
    @ConfigValue public static boolean sessionStatsEnabled      = true;
    @ConfigValue public static boolean sessionStatsInDungeon    = true;
    @ConfigValue public static boolean sessionStatsInDungeonHub = false;
    @ConfigValue public static boolean sessionStatsResetOnRelog = false;
    @ConfigValue public static int     sessionStatsHudX         = 10;
    @ConfigValue public static int     sessionStatsHudY         = 120;

    // Chat-channel compatibility — when on, dot-commands (.pb, .rtca, etc.) work in these
    // channels in addition to party chat, and replies go back in the same channel.
    @ConfigValue public static boolean chatGuild   = true;
    @ConfigValue public static boolean chatOfficer = true;
    @ConfigValue public static boolean chatPrivate = true;
    @ConfigValue public static boolean chatAll     = false; // opt-in (false-positive risk)
    // Meow auto-responder: replies "meow" when anyone says meow in an enabled chat.
    @ConfigValue public static boolean chatMeow    = true;

    // Party command toggles
    @ConfigValue public static boolean pcAllinvite  = true;
    @ConfigValue public static boolean pcPb         = true;
    @ConfigValue public static boolean pcCata       = true;
    @ConfigValue public static boolean pcRtca       = true;
    @ConfigValue public static boolean pcDprofit    = true;
    @ConfigValue public static boolean pcRtc        = true;
    @ConfigValue public static boolean pcHelp       = true;
    @ConfigValue public static boolean pcNw         = true;
    @ConfigValue public static boolean pcBank       = true;

    // Dungeon Score (live S+ tracker)
    @ConfigValue public static boolean dungeonScoreEnabled = true;
    @ConfigValue public static int     dungeonScoreHudX    = 10;
    @ConfigValue public static int     dungeonScoreHudY    = 200;
    @ConfigValue public static boolean dungeonScorePaulActive = false;

    // Farming coin/hr tracker
    @ConfigValue public static boolean farmingTrackerEnabled = true;
    @ConfigValue public static int     farmingTrackerHudX    = 10;
    @ConfigValue public static int     farmingTrackerHudY    = 240;

    // Harvest Feast tracker
    @ConfigValue public static boolean harvestFeastEnabled = true;
    @ConfigValue public static int     harvestFeastHudX    = 10;
    @ConfigValue public static int     harvestFeastHudY    = 280;
    @ConfigValue public static double  harvestFeastScale   = 1.0;

    // Mining coin/hr tracker
    @ConfigValue public static boolean miningTrackerEnabled = true;
    @ConfigValue public static int     miningTrackerHudX    = 10;
    @ConfigValue public static int     miningTrackerHudY    = 320;
    @ConfigValue public static double  miningTrackerScale   = 1.0;

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
    @ConfigValue public static boolean pcCorpse = true;

    // Cooldown overlay extras
    @ConfigValue public static boolean cooldownInInventory = true;

    // Per-HUD scale (1.0 = default). Adjusted via scroll wheel in HUD editor.
    @ConfigValue public static double sessionStatsScale  = 1.0;
    @ConfigValue public static double powderTrackerScale = 1.0;
    @ConfigValue public static double slayerXpScale      = 1.0;
    @ConfigValue public static double farmingTrackerScale = 1.0;
    @ConfigValue public static double dungeonScoreScale  = 1.0;
    @ConfigValue public static double petHudScale         = 1.0;
    @ConfigValue public static double soulflowHudScale    = 1.0;
    @ConfigValue public static double croesusOverlayScale = 1.0;
    @ConfigValue public static boolean pcSecrets    = true;
    @ConfigValue public static boolean pcRuns       = true;
    @ConfigValue public static boolean pcJoinFloor  = true;
    @ConfigValue public static boolean pcFps        = true;
    @ConfigValue public static boolean pcTps        = true;
    @ConfigValue public static boolean pcPing       = true;
    @ConfigValue public static boolean pcDisband    = false;
    @ConfigValue public static boolean pcMp         = true;
    @ConfigValue public static boolean pcCollection = true;

    // Chat-triggered party actions (.kick / .warp / .transfer / .promote).
    // pcPartyActionsMode: "off" | "self" | "whitelist" | "everyone".
    // Default off for safety — only enable when you trust the party.
    @ConfigValue public static boolean pcPartyActions          = false;
    @ConfigValue public static String  pcPartyActionsMode      = "self";
    @ConfigValue public static String  pcPartyActionsWhitelist = "";

    // Croesus chest drop overlay
    @ConfigValue public static boolean croesusOverlayEnabled     = true;
    @ConfigValue public static boolean croesusOverlayHideInDungeon = true;
    @ConfigValue public static int     croesusOverlayX           = 10;
    @ConfigValue public static int     croesusOverlayY           = 160;

    // Simon Says (F7 Goldor) tracker
    @ConfigValue public static boolean simonSaysEnabled    = true;
    @ConfigValue public static boolean simonSaysHudEnabled = true;
    @ConfigValue public static boolean simonSaysPartyChat  = false;
    @ConfigValue public static int     simonSaysHudX       = 10;
    @ConfigValue public static int     simonSaysHudY       = 360;
    @ConfigValue public static double  simonSaysHudScale   = 1.0;

}
