/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.Locale

/** Museum donation inventory decoding. */
internal object MuseumInventoryDecoder {
    @JvmStatic
    fun parseMuseum(museumMember: JsonObject?): List<InventoryDecoder.Stack> {
        if (museumMember == null) return emptyList()
        val out = ArrayList<InventoryDecoder.Stack>()
        InventoryDecodeSupport.obj(museumMember.get("items"))?.let { items ->
            for ((_, raw) in items.entrySet()) {
                val value = InventoryDecodeSupport.obj(raw) ?: continue
                val borrowing = value.get("borrowing")
                if (borrowing != null && borrowing.isJsonPrimitive && borrowing.asBoolean) continue
                val nested = InventoryDecodeSupport.obj(value.get("items"))
                if (nested != null && nested.has("data")) {
                    out.addAll(InventoryDecodeCache.decodeDataElement(nested))
                }
            }
        }
        specialArray(museumMember)?.forEach { element ->
            val nested = InventoryDecodeSupport.obj(InventoryDecodeSupport.obj(element)?.get("items"))
            if (nested != null) out.addAll(InventoryDecodeCache.decodeDataElement(nested))
        }
        return out
    }

    @JvmStatic
    fun parseMuseumById(museumMember: JsonObject?): MutableMap<String, InventoryDecoder.Stack> {
        val out = LinkedHashMap<String, InventoryDecoder.Stack>()
        if (museumMember == null) return out
        InventoryDecodeSupport.obj(museumMember.get("items"))?.let { items ->
            for ((key, raw) in items.entrySet()) {
                val value = InventoryDecodeSupport.obj(raw) ?: continue
                val nested = InventoryDecodeSupport.obj(value.get("items"))
                if (nested != null && nested.has("data")) {
                    InventoryDecodeCache.decodeDataElement(nested).firstOrNull()?.let {
                        out[key.uppercase(Locale.ROOT)] = it
                    }
                }
            }
        }
        specialArray(museumMember)?.forEachIndexed { i, element ->
            val value = InventoryDecodeSupport.obj(element) ?: return@forEachIndexed
            InventoryDecodeCache.decodeDataElement(value.get("items")).forEachIndexed { j, stack ->
                out[specialKey(i, j)] = stack
            }
        }
        return out
    }

    private fun specialArray(museumMember: JsonObject): JsonArray? {
        val special = museumMember.get("special")
        return if (special != null && special.isJsonArray) special.asJsonArray else null
    }

    @JvmStatic
    fun specialKey(index: Int, stack: Int): String = if (stack == 0) "SPECIAL:$index" else "SPECIAL:$index:$stack"
}
