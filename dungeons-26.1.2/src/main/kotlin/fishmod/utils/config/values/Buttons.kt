package fishmod.utils.config.values

import config.practical.manager.ConfigValue
import fishmod.features.other.InventoryButton

/**
 * Config + button instances for the inventory command buttons (1:1 port of blade-addons' Buttons).
 * The seven [InventoryButton]s sit in the empty corners of the survival inventory GUI; a button
 * only renders when its command string is non-empty. Registered with FishConfig so the commands persist.
 */
object Buttons {

    /** Master toggle — when off, nothing renders and clicks pass through (off by default). */
    @ConfigValue
    @JvmField
    var enableInventoryButtons: Boolean = false

    @ConfigValue
    @JvmField
    var command1: String = ""

    @ConfigValue
    @JvmField
    var command2: String = ""

    @ConfigValue
    @JvmField
    var command3: String = ""

    @ConfigValue
    @JvmField
    var command4: String = ""

    @ConfigValue
    @JvmField
    var command5: String = ""

    @ConfigValue
    @JvmField
    var command6: String = ""

    @ConfigValue
    @JvmField
    var command7: String = ""

    // Same fixed layout as blade-addons: 1-3 down the left of the player model, 4-5 top-right,
    // 6-7 bottom-right. Constructed on class-load so they self-register with InventoryButton.
    @JvmField
    val button1: InventoryButton = InventoryButton(77, 5) { command1 }
    @JvmField
    val button2: InventoryButton = InventoryButton(77, 23) { command2 }
    @JvmField
    val button3: InventoryButton = InventoryButton(77, 41) { command3 }
    @JvmField
    val button4: InventoryButton = InventoryButton(133, 5) { command4 }
    @JvmField
    val button5: InventoryButton = InventoryButton(151, 5) { command5 }
    @JvmField
    val button6: InventoryButton = InventoryButton(133, 61) { command6 }
    @JvmField
    val button7: InventoryButton = InventoryButton(151, 61) { command7 }

    /** Touch this class so the buttons above register even before the config screen is opened. */
    @JvmStatic
    fun init() { }
}
