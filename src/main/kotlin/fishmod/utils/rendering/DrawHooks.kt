package fishmod.utils.rendering

import fishmod.utils.rendering.drawevents.SlotDrawHook

object DrawHooks {
    @JvmField
    var currentSlot: net.minecraft.world.inventory.Slot? = null

    @JvmField
    var INVENTORY_SLOT_AFTER = SimpleHandler<SlotDrawHook>()
    @JvmField
    var INVENTORY_SLOT_BEFORE = SimpleHandler<SlotDrawHook>()
}
