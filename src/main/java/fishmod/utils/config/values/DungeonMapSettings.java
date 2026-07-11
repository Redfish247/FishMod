package fishmod.utils.config.values;

import config.practical.manager.ConfigValue;

public class DungeonMapSettings {

    @ConfigValue
    public static boolean enabled = false;

    @ConfigValue
    public static boolean showRoomNames = false;

    @ConfigValue
    public static boolean showSecretCounts = false;

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
    public static int unopenedColor = 0xff808080;

    @ConfigValue
    public static int normalDoorColor = 0xff2b2b2b;

    @ConfigValue
    public static int entranceDoorColor = 0xff148500;

    @ConfigValue
    public static int bloodDoorColor = 0xffff0000;

    @ConfigValue
    public static int witherDoorColor = 0xff1a1a1a;
}
