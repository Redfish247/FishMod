package fishmod.utils.rendering

import fishmod.utils.rendering.drawevents.SlotEvent

object DrawEvents {
    @JvmField
    var INVENTORY_SLOT_AFTER = DrawHandler<SlotEvent>()
    @JvmField
    var INVENTORY_SLOT_BEFORE = DrawHandler<SlotEvent>()
}
