package fishmod.features.item

import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.CustomData

// Hypixel's animated-dye equipment packets drop the item uuid on your own worn armour, so customs flicker; put the last seen uuid back.
object WornUuidRestore {

    private val SLOTS = arrayOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
    private val lastItem = arrayOfNulls<Item>(SLOTS.size)
    private val lastUuid = arrayOfNulls<String>(SLOTS.size)
    private val emptyTicks = IntArray(SLOTS.size)
    // Keep the uuid through short empty gaps (world/island joins) so the first uuid-less packets after a join are fixed too.
    private const val FORGET_AFTER_TICKS = 200

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try {
                val p = mc.player ?: return@register
                if (!ItemCustomizationStore.hasAny()) return@register
                for (i in SLOTS.indices) {
                    val st = p.getItemBySlot(SLOTS[i])
                    if (st.isEmpty) {
                        if (++emptyTicks[i] > FORGET_AFTER_TICKS) { lastItem[i] = null; lastUuid[i] = null }
                        continue
                    }
                    emptyTicks[i] = 0
                    val uuid = ItemTools.getUuid(st)
                    if (uuid != null) { lastItem[i] = st.item; lastUuid[i] = uuid; continue }
                    val known = lastUuid[i] ?: continue
                    if (lastItem[i] !== st.item || ItemCustomizationStore.own(known).isEmpty()) continue
                    val tag = st.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: net.minecraft.nbt.CompoundTag()
                    tag.putString("uuid", known)
                    st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
                }
            } catch (e: Exception) { FishDiag.fail("WornUuidRestore.1", "restoring worn armour uuid failed", e) }
        }
    }
}
