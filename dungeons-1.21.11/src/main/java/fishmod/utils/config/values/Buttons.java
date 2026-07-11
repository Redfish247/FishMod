package fishmod.utils.config.values;

import config.practical.manager.ConfigValue;
import fishmod.features.other.InventoryButton;

/**
 * Config + button instances for the inventory command buttons (1:1 port of blade-addons' Buttons).
 * The seven {@link InventoryButton}s sit in the empty corners of the survival inventory GUI; a button
 * only renders when its command string is non-empty. Registered with FishConfig so the commands persist.
 */
public class Buttons {

    /** Master toggle — when off, nothing renders and clicks pass through (off by default). */
    @ConfigValue
    public static boolean enableInventoryButtons = false;

    @ConfigValue
    public static String command1 = "";

    @ConfigValue
    public static String command2 = "";

    @ConfigValue
    public static String command3 = "";

    @ConfigValue
    public static String command4 = "";

    @ConfigValue
    public static String command5 = "";

    @ConfigValue
    public static String command6 = "";

    @ConfigValue
    public static String command7 = "";

    // Same fixed layout as blade-addons: 1-3 down the left of the player model, 4-5 top-right,
    // 6-7 bottom-right. Constructed on class-load so they self-register with InventoryButton.
    public static final InventoryButton button1 = new InventoryButton(77, 5, () -> command1);
    public static final InventoryButton button2 = new InventoryButton(77, 23, () -> command2);
    public static final InventoryButton button3 = new InventoryButton(77, 41, () -> command3);
    public static final InventoryButton button4 = new InventoryButton(133, 5, () -> command4);
    public static final InventoryButton button5 = new InventoryButton(151, 5, () -> command5);
    public static final InventoryButton button6 = new InventoryButton(133, 61, () -> command6);
    public static final InventoryButton button7 = new InventoryButton(151, 61, () -> command7);

    /** Touch this class so the buttons above register even before the config screen is opened. */
    public static void init() { }
}
