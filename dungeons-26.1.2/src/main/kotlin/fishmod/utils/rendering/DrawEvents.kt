package fishmod.utils.rendering

import fishmod.utils.rendering.drawevents.SlotEvent

object DrawEvents {
    @JvmField
    var currentSlot: net.minecraft.world.inventory.Slot? = null

    @JvmField
    var INVENTORY_SLOT_AFTER = SimpleHandler<SlotEvent>()
    @JvmField
    var INVENTORY_SLOT_BEFORE = SimpleHandler<SlotEvent>()
}
