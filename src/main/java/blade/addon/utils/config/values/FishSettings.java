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

    @ConfigValue
    public static String hypixelApiKey = "";

    /** Class XP gained per run when playing as that class — used for .rtca */
    @ConfigValue
    public static int rtcaClassXpPerRun = 500000;

    @ConfigValue
    public static boolean warpMapHudEnabled = true;

    @ConfigValue
    public static int warpMapDotColor = 0xFFDB3737;

    @ConfigValue
    public static boolean autoAcceptPartyInvite = false;

    // Party command toggles
    @ConfigValue public static boolean pcAllinvite  = true;
    @ConfigValue public static boolean pcPb         = true;
    @ConfigValue public static boolean pcCata       = true;
    @ConfigValue public static boolean pcRtca       = true;
    @ConfigValue public static boolean pcJoinFloor  = true;
    @ConfigValue public static boolean pcFps        = true;
    @ConfigValue public static boolean pcTps        = true;
    @ConfigValue public static boolean pcPing       = true;
}
