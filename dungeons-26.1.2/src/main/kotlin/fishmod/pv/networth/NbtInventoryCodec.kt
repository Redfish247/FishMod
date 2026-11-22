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
import com.google.gson.JsonParser
import fishmod.pv.gui.SkyBlockSymbols
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Base64
import java.util.Locale
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

internal object NbtInventoryCodec {
    private val EDGE_QUOTES = Regex("^\"|\"$")

    @JvmStatic
    @Throws(IOException::class)
    fun decodeKeepingEmptyUncached(encoded: String, minSlots: Int): List<InventoryDecoder.Stack?> {
        val root = readRoot(encoded) ?: return emptyList()
        val items = root.get("i") as? ListTag ?: return listOfNotNull(fromBareAttributes(root))
        val out = ArrayList<InventoryDecoder.Stack?>()
        for (i in 0 until maxOf(minSlots, items.size)) {
            val compound = if (i < items.size) items.get(i) as? CompoundTag else null
            out.add(if (compound == null || compound.isEmpty) null else fromSlot(compound))
        }
        return out
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decodeUncached(encoded: String): List<InventoryDecoder.Stack> {
        val root = readRoot(encoded) ?: return emptyList()
        val items = root.get("i") as? ListTag ?: return listOfNotNull(fromBareAttributes(root))
        val out = ArrayList<InventoryDecoder.Stack>()
        for (child in items) {
            val compound = child as? CompoundTag ?: continue
            if (compound.isEmpty) continue
            fromSlot(compound)?.let(out::add)
        }
        return out
    }

    private fun readRoot(encoded: String): CompoundTag? {
        val bytes = Base64.getDecoder().decode(encoded)
        return ByteArrayInputStream(bytes).use { NbtIo.readCompressed(it, NbtAccounter.unlimitedHeap()) }
    }

    @JvmStatic
    fun fromBareAttributes(root: CompoundTag): InventoryDecoder.Stack? {
        val id = string(root, "id")
        if (id.isNullOrBlank()) return null
        return InventoryDecoder.Stack(id, 1, root, emptyList(), false)
    }

    @JvmStatic
    fun fromSlot(slot: CompoundTag): InventoryDecoder.Stack? {
        val tag = compound(slot.get("tag")) ?: slot
        val attrs = compound(tag.get("ExtraAttributes")) ?: compound(tag.get("extra_attributes")) ?: return null
        val id = string(attrs, "id") ?: string(attrs, "ID")
        if (id.isNullOrBlank()) return null
        val count = maxOf(1, intOr(slot, "Count", intOr(slot, "count", 1)))
        val display = compound(tag.get("display"))
        val lore = ArrayList<String>()
        var displayName: String? = null
        var dyeColor: Int? = null
        if (display != null) {
            displayName = cleanJsonText(tagText(display.get("Name")))
            val loreTag = display.get("Lore")
            if (loreTag is ListTag) {
                for (line in loreTag) lore.add(cleanJsonText(tagText(line)))
            }
            if (display.contains("color")) {
                val color = intOr(display, "color", Int.MIN_VALUE)
                if (color != Int.MIN_VALUE) dyeColor = color and 0xFFFFFF
            }
        }
        var skullValue: String? = null
        var skullSignature: String? = null
        val skullOwner = compound(tag.get("SkullOwner")) ?: compound(tag.get("skull_owner"))
        if (skullOwner != null) {
            val props = compound(skullOwner.get("Properties")) ?: compound(skullOwner.get("properties"))
            val textures = props?.get("textures")
            if (textures is ListTag && !textures.isEmpty()) {
                val first = compound(textures.get(0))
                if (first != null) {
                    skullValue = string(first, "Value") ?: string(first, "value")
                    skullSignature = string(first, "Signature") ?: string(first, "signature")
                }
            }
        }
        val soulbound = attrs.contains("donated_museum") || lore.any { it.contains("Soulbound") }
        return InventoryDecoder.Stack(id, count, attrs, lore, soulbound, displayName, dyeColor, skullValue, skullSignature)
    }

    @JvmStatic
    fun tagText(tag: Tag?): String {
        if (tag == null) return ""
        if (tag is StringTag) {
            try {
                return tag.value()
            } catch (ignored: Throwable) {
                try {
                    return tag.toString().replace(EDGE_QUOTES, "")
                } catch (ignored2: Throwable) {
                }
            }
        }
        val raw = tag.toString()
        if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return unescapeSnbtString(raw.substring(1, raw.length - 1))
        }
        return raw
    }

    @JvmStatic
    fun unescapeSnbtString(value: String): String = value
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\u00a7", "§")
        .replace("\\u00A7", "§")

    @JvmStatic
    fun cleanJsonText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var line = raw.trim()
        if (line.startsWith("\"") && line.endsWith("\"") && line.length >= 2) {
            line = unescapeSnbtString(line.substring(1, line.length - 1))
        }
        if ((line.startsWith("{") || line.startsWith("[")) && line.contains("text")) {
            try {
                val flattened = flattenJsonText(JsonParser.parseString(line), JsonStyle())
                if (flattened.isNotEmpty()) return SkyBlockSymbols.replace(flattened)
            } catch (ignored: Exception) {
            }
            val out = StringBuilder()
            var idx = 0
            while (true) {
                val textIdx = line.indexOf("\"text\"", idx)
                if (textIdx < 0) break
                val colon = line.indexOf(':', textIdx)
                val firstQuote = line.indexOf('"', colon + 1)
                var secondQuote = if (firstQuote >= 0) line.indexOf('"', firstQuote + 1) else -1
                while (secondQuote > firstQuote && line[secondQuote - 1] == '\\') {
                    secondQuote = line.indexOf('"', secondQuote + 1)
                }
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    out.append(unescapeSnbtString(line.substring(firstQuote + 1, secondQuote)))
                }
                idx = if (secondQuote > 0) secondQuote + 1 else textIdx + 6
            }
            if (out.isNotEmpty()) return SkyBlockSymbols.replace(out.toString())
        }
        return SkyBlockSymbols.replace(line.replace("\\u00a7", "§").replace("\\u00A7", "§"))
    }

    @JvmStatic
    fun flattenJsonText(element: JsonElement?, parent: JsonStyle): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (element.isJsonArray) return element.asJsonArray.joinToString("") { flattenJsonText(it, parent) }
        if (!element.isJsonObject) return ""
        val obj = element.asJsonObject
        val style = parent.child(obj)
        val out = StringBuilder()
        val text = obj.get("text")
        if (text != null && text.isJsonPrimitive) {
            val value = text.asString
            if (!value.isNullOrEmpty()) out.append(style.toLegacyPrefix()).append(value)
        }
        val extra = obj.get("extra")
        if (extra != null && extra.isJsonArray) {
            for (child in extra.asJsonArray) out.append(flattenJsonText(child, style))
        }
        return out.toString()
    }

    class JsonStyle private constructor(
        private val color: Char,
        private val bold: Boolean,
        private val italic: Boolean,
        private val underlined: Boolean,
        private val strikethrough: Boolean,
        private val obfuscated: Boolean,
    ) {
        constructor() : this(' ', false, false, false, false, false)

        fun child(obj: JsonObject): JsonStyle {
            val colorEl = obj.get("color")
            val nextColor = if (colorEl != null && colorEl.isJsonPrimitive) namedColorCode(colorEl.asString) else color
            return JsonStyle(
                nextColor,
                boolOr(obj, "bold", bold),
                boolOr(obj, "italic", italic),
                boolOr(obj, "underlined", underlined),
                boolOr(obj, "strikethrough", strikethrough),
                boolOr(obj, "obfuscated", obfuscated),
            )
        }

        fun toLegacyPrefix(): String = buildString {
            if (color != ' ') append('§').append(color) else append("§r")
            if (bold) append("§l")
            if (italic) append("§o")
            if (underlined) append("§n")
            if (strikethrough) append("§m")
            if (obfuscated) append("§k")
        }

        private companion object {
            val PALETTE = arrayOf(
                intArrayOf('0'.code, 0, 0, 0), intArrayOf('1'.code, 0, 0, 170), intArrayOf('2'.code, 0, 170, 0), intArrayOf('3'.code, 0, 170, 170),
                intArrayOf('4'.code, 170, 0, 0), intArrayOf('5'.code, 170, 0, 170), intArrayOf('6'.code, 255, 170, 0), intArrayOf('7'.code, 170, 170, 170),
                intArrayOf('8'.code, 85, 85, 85), intArrayOf('9'.code, 85, 85, 255), intArrayOf('a'.code, 85, 255, 85), intArrayOf('b'.code, 85, 255, 255),
                intArrayOf('c'.code, 255, 85, 85), intArrayOf('d'.code, 255, 85, 255), intArrayOf('e'.code, 255, 255, 85), intArrayOf('f'.code, 255, 255, 255),
            )

            fun boolOr(obj: JsonObject, key: String, fallback: Boolean): Boolean {
                val value = obj.get(key)
                if (value == null || !value.isJsonPrimitive) return fallback
                return try {
                    value.asBoolean
                } catch (ignored: Exception) {
                    fallback
                }
            }

            fun namedColorCode(name: String?): Char {
                if (name.isNullOrBlank()) return 'f'
                val key = name.trim().lowercase(Locale.ROOT)
                if (key.startsWith("#") && key.length == 7) return nearestLegacyColor(key)
                return when (key) {
                    "black" -> '0'
                    "dark_blue" -> '1'
                    "dark_green" -> '2'
                    "dark_aqua" -> '3'
                    "dark_red" -> '4'
                    "dark_purple" -> '5'
                    "gold" -> '6'
                    "gray", "grey" -> '7'
                    "dark_gray", "dark_grey" -> '8'
                    "blue" -> '9'
                    "green" -> 'a'
                    "aqua" -> 'b'
                    "red" -> 'c'
                    "light_purple", "pink" -> 'd'
                    "yellow" -> 'e'
                    else -> 'f'
                }
            }

            fun nearestLegacyColor(hex: String): Char {
                val rgb = hex.substring(1).toIntOrNull(16) ?: return 'f'
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                var best = 'f'
                var bestDist = Int.MAX_VALUE
                for (entry in PALETTE) {
                    val dr = r - entry[1]
                    val dg = g - entry[2]
                    val db = b - entry[3]
                    val dist = dr * dr + dg * dg + db * db
                    if (dist < bestDist) {
                        bestDist = dist
                        best = entry[0].toChar()
                    }
                }
                return best
            }
        }
    }

    @JvmStatic
    fun compound(tag: Tag?): CompoundTag? = tag as? CompoundTag

    @JvmStatic
    fun string(tag: CompoundTag?, key: String): String? {
        if (tag == null || !tag.contains(key)) return null
        return try {
            tag.getString(key).orElse(null)
        } catch (ignored: Throwable) {
            tag.get(key)?.toString()?.replace(EDGE_QUOTES, "")
        }
    }

    @JvmStatic
    fun intOr(tag: CompoundTag?, key: String, fallback: Int): Int {
        if (tag == null || !tag.contains(key)) return fallback
        return try {
            tag.getIntOr(key, fallback)
        } catch (ignored: Throwable) {
            try {
                tag.getByteOr(key, fallback.toByte()).toInt()
            } catch (ignored2: Throwable) {
                fallback
            }
        }
    }
}
