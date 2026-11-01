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

    @ConfigValue
    @JvmField
    var name1: String = ""

    @ConfigValue
    @JvmField
    var icon1: String = ""

    @ConfigValue
    @JvmField
    var name2: String = ""

    @ConfigValue
    @JvmField
    var icon2: String = ""

    @ConfigValue
    @JvmField
    var name3: String = ""

    @ConfigValue
    @JvmField
    var icon3: String = ""

    @ConfigValue
    @JvmField
    var name4: String = ""

    @ConfigValue
    @JvmField
    var icon4: String = ""

    @ConfigValue
    @JvmField
    var name5: String = ""

    @ConfigValue
    @JvmField
    var icon5: String = ""

    @ConfigValue
    @JvmField
    var name6: String = ""

    @ConfigValue
    @JvmField
    var icon6: String = ""

    @ConfigValue
    @JvmField
    var name7: String = ""

    @ConfigValue
    @JvmField
    var icon7: String = ""

    // "x,y;x,y;..." per button, relative to the inventory corner; empty = default spot
    @ConfigValue
    @JvmField
    var layout: String = ""

    @ConfigValue
    @JvmField
    var showEquipment: Boolean = false

    @JvmField
    val button1: InventoryButton = InventoryButton(0, 77, 5, { command1 }, { name1 }, { icon1 })
    @JvmField
    val button2: InventoryButton = InventoryButton(1, 77, 23, { command2 }, { name2 }, { icon2 })
    @JvmField
    val button3: InventoryButton = InventoryButton(2, 77, 41, { command3 }, { name3 }, { icon3 })
    @JvmField
    val button4: InventoryButton = InventoryButton(3, 133, 5, { command4 }, { name4 }, { icon4 })
    @JvmField
    val button5: InventoryButton = InventoryButton(4, 151, 5, { command5 }, { name5 }, { icon5 })
    @JvmField
    val button6: InventoryButton = InventoryButton(5, 133, 61, { command6 }, { name6 }, { icon6 })
    @JvmField
    val button7: InventoryButton = InventoryButton(6, 151, 61, { command7 }, { name7 }, { icon7 })

    @JvmStatic
    fun init() { }
}
