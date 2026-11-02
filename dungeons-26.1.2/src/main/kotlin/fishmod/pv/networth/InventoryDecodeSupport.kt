/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.data.InventorySnapshot
import java.util.Locale

/** Shared JSON and UI helpers for inventory decoding. */
internal object InventoryDecodeSupport {
    private val NON_DIGITS = Regex("\\D+")

    @JvmStatic
    fun obj(element: JsonElement?): JsonObject? =
        if (element != null && element.isJsonObject) element.asJsonObject else null

    @JvmStatic
    fun jsonInt(obj: JsonObject?, key: String): Int? {
        val value = obj?.get(key) ?: return null
        if (value.isJsonNull || !value.isJsonPrimitive) return null
        return try {
            value.asInt
        } catch (ignored: Exception) {
            null
        }
    }

    @JvmStatic
    fun tryParseInt(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return value.trim().toIntOrNull() ?: value.replace(NON_DIGITS, "").takeIf { it.isNotEmpty() }?.toIntOrNull()
    }

    @JvmStatic
    fun compareKeys(a: String, b: String): Int {
        val ai = tryParseInt(a)
        val bi = tryParseInt(b)
        if (ai != null && bi != null) return ai.compareTo(bi)
        return a.compareTo(b, ignoreCase = true)
    }

    @JvmStatic
    fun normalizeUuid(uuid: String?): String = uuid?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""

    @JvmStatic
    fun toUiSlots(stacks: List<InventoryDecoder.Stack?>): MutableList<InventorySnapshot.Slot?> =
        stacks.mapTo(ArrayList(stacks.size)) { toUiSlot(it) }

    @JvmStatic
    fun toUiSlot(stack: InventoryDecoder.Stack?): InventorySnapshot.Slot? {
        if (stack == null) return null
        return InventorySnapshot.Slot(
            stack.id,
            stack.count,
            stack.lore,
            stack.displayName,
            stack.dyeColor,
            stack.skullValue,
            stack.skullSignature,
            stack.extraAttributes,
            stack.soulbound,
        )
    }

    @JvmStatic
    fun chunkPages(
        slots: List<InventorySnapshot.Slot?>,
        pageSize: Int,
        columns: Int,
        titlePrefix: String,
    ): List<InventorySnapshot.Page> {
        if (slots.isEmpty()) return listOf(InventorySnapshot.emptyPage(titlePrefix, columns))
        val totalPages = (slots.size + pageSize - 1) / pageSize
        val pages = ArrayList<InventorySnapshot.Page>()
        for (start in slots.indices step pageSize) {
            val end = minOf(slots.size, start + pageSize)
            val pageIndex = start / pageSize + 1
            val title = if (totalPages <= 1) titlePrefix else "$titlePrefix $pageIndex"
            pages.add(InventorySnapshot.Page(title, slots.subList(start, end), columns))
        }
        return pages
    }

    @JvmStatic
    fun prettyWords(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.lowercase(Locale.ROOT).replace('-', '_').split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}
