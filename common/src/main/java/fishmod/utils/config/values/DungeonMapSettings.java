package fishmod.utils.config.values;

import config.practical.manager.ConfigValue;

public class DungeonMapSettings {

    @ConfigValue
    public static boolean enabled = false;

    @ConfigValue
    public static boolean showRoomNames = true;

    @ConfigValue
    public static boolean showSecretCounts = true;

    // Predicts a color blend for undiscovered rooms once locally-observed data narrows the
    // candidates to more than one RoomType. Off by default until the local database has data.
    @ConfigValue
    public static boolean predictionLayerEnabled = true;

    @ConfigValue
    public static int normalColor = 0xff6b3a11;

    @ConfigValue
    public static int puzzleColor = 0xff750085;

    @ConfigValue
    public static int trapColor = 0xffd87f33;

    @ConfigValue
    public static int minibossColor = 0xfffedf00;

    @ConfigValue
    public static int fairyColor = 0xffe000ff;

    @ConfigValue
    public static int bloodColor = 0xffff0000;

    @ConfigValue
    public static int entranceColor = 0xff148500;

    @ConfigValue
    public static int clearedColor = 0xff00a000;

    @ConfigValue
    public static int unopenedColor = 0xff3a3a3a;

    @ConfigValue
    public static int failedColor = 0xff550000;

    @ConfigValue
    public static int normalDoorColor = 0xff2b2b2b;

    @ConfigValue
    public static int entranceDoorColor = 0xff148500;

    @ConfigValue
    public static int bloodDoorColor = 0xffff0000;

    @ConfigValue
    public static int witherDoorColor = 0xff1a1a1a;

    @ConfigValue
    public static int witherDoorOpenColor = 0xff000000;

    @ConfigValue
    public static boolean showPlayerMarkers = true;

    @ConfigValue
    public static int selfMarkerColor = 0xff55ff55;

    @ConfigValue
    public static int teammateMarkerColor = 0xffffffff;

    @ConfigValue
    public static boolean showCheckmarks = true;

    @ConfigValue
    public static float checkmarkScale = 1.0f;

    @ConfigValue
    public static boolean centerCheckmark = false;

    @ConfigValue
    public static boolean hideUnknownCheckmark = true;

    @ConfigValue
    public static boolean showPlayerNames = false;

    @ConfigValue
    public static float playerNameScale = 0.5f;

    @ConfigValue
    public static int backgroundColor = 0x00000000;

    @ConfigValue
    public static int borderColor = 0x00000000;

    @ConfigValue
    public static int borderThickness = 1;

    // Self-only: colors the wither door based on whether YOU currently hold a Wither Key —
    // no information about other players is inferred or shown.
    @ConfigValue
    public static boolean boxWitherDoors = false;

    @ConfigValue
    public static int witherKeyHeldColor = 0xff55ff55;

    @ConfigValue
    public static int witherKeyMissingColor = 0xffff5555;

    @ConfigValue
    public static boolean showExtraInfoUnderMap = false;

    @ConfigValue
    public static boolean playerClearInfoEnabled = false;
}
