package fishmod.utils.config.values

import fishmod.shaded.practicalconfig.manager.ConfigValue

object InvButtonSettings {

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

    // list of InvButton as JSON; old command/name/icon/layout fields are only read once to migrate
    @ConfigValue
    @JvmField
    var buttonsJson: String = ""

    @ConfigValue
    @JvmField
    var showEquipment: Boolean = false

    // last equipment seen in /stats, kept until it changes
    @ConfigValue
    @JvmField
    var equipmentCache: String = ""


    @JvmStatic
    fun init() { }
}
