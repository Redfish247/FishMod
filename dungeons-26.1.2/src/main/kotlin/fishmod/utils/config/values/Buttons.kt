package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue
import fishmod.features.other.InventoryButton

object Buttons {

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

    @JvmStatic
    fun init() { }
}
