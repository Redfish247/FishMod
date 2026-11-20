package fishmod.features.inventory

import fishmod.features.extras.*

import fishmod.features.hud.fishmodCustomDataTag
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import fishmod.features.extras.ExtrasSettings as S

// Lists the items set in a Personal Compactor / Deletor in its tooltip
object PersonalCompactorTooltip {
    // from SkyHanni (PersonalCompactorOverlay): id pattern, slot keys and slot counts
    private val ID = Regex("""^PERSONAL_(COMPACTOR|DELETOR)_(\d+)$""")
    private val SLOTS = mapOf(7000 to 28, 6000 to 21, 5000 to 14, 4000 to 7)

    fun init() {
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines ->
            if (!S.compactorTooltip) return@ItemTooltipCallback
            try {
                val m = ID.find(ItemTools.getId(stack) ?: return@ItemTooltipCallback) ?: return@ItemTooltipCallback
                val prefix = if (m.groupValues[1] == "COMPACTOR") "personal_compact_" else "personal_deletor_"
                val tag = stack.fishmodCustomDataTag() ?: return@ItemTooltipCallback
                val slots = SLOTS[m.groupValues[2].toInt()] ?: return@ItemTooltipCallback
                val ids = (0 until slots).mapNotNull { tag.getStringOr(prefix + it, "").ifEmpty { null } }
                lines.add(Component.literal("§7Set items (" + ids.size + "/" + slots + "):"))
                ids.forEach { id -> lines.add(Component.literal("§8- §f" + pretty(id))) }
            } catch (e: Exception) {
                FishDiag.fail("PersonalCompactorTooltip.1", "compactor tooltip failed", e)
            }
        })
    }

    private fun pretty(id: String) = id.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
}
