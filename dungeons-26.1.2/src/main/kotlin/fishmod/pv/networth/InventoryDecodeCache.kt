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
import java.io.IOException
import java.util.function.Supplier

internal object InventoryDecodeCache {
    private val DECODE_KEEPING_EMPTY_CACHE = ThreadLocal<MutableMap<String, List<InventoryDecoder.Stack?>>>()
    private val DECODE_COMPACT_CACHE = ThreadLocal<MutableMap<String, List<InventoryDecoder.Stack>>>()

    @JvmStatic
    fun <T> withSharedDecode(work: Supplier<T>): T {
        val outer = DECODE_KEEPING_EMPTY_CACHE.get() == null
        if (outer) {
            DECODE_KEEPING_EMPTY_CACHE.set(HashMap())
            DECODE_COMPACT_CACHE.set(HashMap())
        }
        try {
            return work.get()
        } finally {
            if (outer) {
                DECODE_KEEPING_EMPTY_CACHE.remove()
                DECODE_COMPACT_CACHE.remove()
            }
        }
    }

    @JvmStatic
    fun withSharedDecode(work: Runnable) {
        withSharedDecode(Supplier { work.run() })
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decodeKeepingEmpty(encoded: String, minSlots: Int): List<InventoryDecoder.Stack?> {
        val cache = DECODE_KEEPING_EMPTY_CACHE.get()
            ?: return NbtInventoryCodec.decodeKeepingEmptyUncached(encoded, minSlots)
        val natural = cache.getOrPut(encoded) { NbtInventoryCodec.decodeKeepingEmptyUncached(encoded, 0) }
        return padSlots(natural, minSlots)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decode(encoded: String): List<InventoryDecoder.Stack> {
        val cache = DECODE_COMPACT_CACHE.get()
        cache?.get(encoded)?.let { return it }
        val decoded = if (DECODE_KEEPING_EMPTY_CACHE.get() != null) {
            compactNonNull(decodeKeepingEmpty(encoded, 0))
        } else {
            NbtInventoryCodec.decodeUncached(encoded)
        }
        cache?.put(encoded, decoded)
        return decoded
    }

    @JvmStatic
    fun decodeField(container: JsonObject?, field: String?): List<InventoryDecoder.Stack> {
        if (container == null || field == null) return emptyList()
        return decodeDataElement(container.get(field))
    }

    @JvmStatic
    fun decodeDataElement(element: JsonElement?): List<InventoryDecoder.Stack> {
        val data = extractData(element)
        if (data.isBlank()) return emptyList()
        return try {
            decode(data)
        } catch (ignored: Exception) {
            emptyList()
        }
    }

    @JvmStatic
    fun decodeFieldKeepingEmpty(container: JsonObject?, field: String?, minSlots: Int): List<InventoryDecoder.Stack?> {
        if (container == null || field == null) return emptySlots(minSlots)
        return decodeDataElementKeepingEmpty(container.get(field), minSlots)
    }

    @JvmStatic
    fun decodeDataElementKeepingEmpty(element: JsonElement?, minSlots: Int): List<InventoryDecoder.Stack?> {
        val data = extractData(element)
        if (data.isBlank()) return emptySlots(minSlots)
        return try {
            decodeKeepingEmpty(data, minSlots)
        } catch (ignored: Exception) {
            emptySlots(minSlots)
        }
    }

    @JvmStatic
    fun extractData(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonObject) {
            val data = element.asJsonObject.get("data")
            if (data != null && data.isJsonPrimitive) return data.asString
        }
        return ""
    }

    @JvmStatic
    fun padSlots(slots: List<InventoryDecoder.Stack?>?, minSlots: Int): List<InventoryDecoder.Stack?> {
        if (slots == null) return emptySlots(minSlots)
        if (minSlots <= slots.size) return slots
        val out = ArrayList<InventoryDecoder.Stack?>(minSlots)
        out.addAll(slots)
        while (out.size < minSlots) out.add(null)
        return out
    }

    @JvmStatic
    fun emptySlots(count: Int): MutableList<InventoryDecoder.Stack?> = MutableList(count) { null }

    @JvmStatic
    fun compactNonNull(slots: List<InventoryDecoder.Stack?>?): List<InventoryDecoder.Stack> {
        if (slots.isNullOrEmpty()) return emptyList()
        return slots.filterNotNullTo(ArrayList(slots.size))
    }

    @JvmStatic
    fun put(map: MutableMap<String, List<InventoryDecoder.Stack>>, key: String, stacks: List<InventoryDecoder.Stack>) {
        map[key] = stacks
    }
}
