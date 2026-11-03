/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.networth

import java.util.Locale
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NumericTag

object NbtAttrs {
    @JvmStatic
    fun string(tag: CompoundTag?, key: String): String? {
        if (tag == null || !tag.contains(key)) return null
        return try {
            tag.getString(key).orElse(null)
        } catch (ignored: Throwable) {
            val text = tag.get(key)?.toString() ?: return null
            if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) text.substring(1, text.length - 1) else text
        }
    }

    @JvmStatic
    fun intValue(tag: CompoundTag?, key: String, fallback: Int): Int {
        if (tag == null || !tag.contains(key)) return fallback
        return try {
            tag.getIntOr(key, fallback)
        } catch (ignored: Throwable) {
            val value = tag.get(key)
            if (value is NumericTag) value.intValue() else string(tag, key)?.toIntOrNull() ?: fallback
        }
    }

    @JvmStatic
    fun longValue(tag: CompoundTag?, key: String, fallback: Long): Long {
        if (tag == null || !tag.contains(key)) return fallback
        return try {
            tag.getLongOr(key, fallback)
        } catch (ignored: Throwable) {
            val value = tag.get(key)
            if (value is NumericTag) value.longValue() else string(tag, key)?.toLongOrNull() ?: fallback
        }
    }

    @JvmStatic
    fun doubleValue(tag: CompoundTag?, key: String, fallback: Double): Double {
        if (tag == null || !tag.contains(key)) return fallback
        return try {
            tag.getDoubleOr(key, fallback)
        } catch (ignored: Throwable) {
            (tag.get(key) as? NumericTag)?.doubleValue() ?: fallback
        }
    }

    @JvmStatic
    fun has(tag: CompoundTag?, key: String): Boolean = tag != null && tag.contains(key)

    @JvmStatic
    fun compound(tag: CompoundTag?, key: String): CompoundTag? {
        if (tag == null || !tag.contains(key)) return null
        return tag.get(key) as? CompoundTag
    }

    /** Numeric entries keyed lowercase so Hypixel ids like `ultimate_chimera` / `rend` match. */
    @JvmStatic
    fun intMap(tag: CompoundTag?, key: String): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        val map = compound(tag, key) ?: return out
        for (entryKey in map.keySet()) {
            out[entryKey.lowercase(Locale.ROOT)] = intValue(map, entryKey, 0)
        }
        return out
    }

    @JvmStatic
    fun stringList(tag: CompoundTag?, key: String): List<String> {
        val out = ArrayList<String>()
        if (tag == null || !tag.contains(key)) return out
        when (val value = tag.get(key)) {
            is ListTag -> for (child in value) {
                var text = child.toString()
                if (text.startsWith("\"") && text.endsWith("\"")) text = text.substring(1, text.length - 1)
                out.add(text)
            }
            is CompoundTag -> for (entryKey in value.keySet()) {
                string(value, entryKey)?.let(out::add)
            }
            else -> {}
        }
        return out
    }

    @JvmStatic
    fun stringListValues(tag: CompoundTag?, key: String): List<String> = stringList(tag, key)
}
