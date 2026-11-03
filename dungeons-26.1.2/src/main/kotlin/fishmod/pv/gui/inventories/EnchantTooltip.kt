/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.google.gson.JsonParser
import fishmod.features.extras.ExtrasSettings
import fishmod.pv.ProfileViewer
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import fishmod.pv.gui.PvDraw
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

/**
 * T6/T7 enchant colours matching Athen/SkyHanni max-level tables.
 * Last tier is rainbow (T7); second-to-last is gold bold (T6).
 * A 7-tier book uses VI/VII; a 5-tier book uses IV/V.
 */
object EnchantTooltip {
	private val TOKEN = Regex(
		"^(.*?)\\s+(I{1,3}|IV|VI{0,3}|IX|X{1,3}I{0,3}|1[0-2]|[1-9]|10|11|12)$",
		RegexOption.IGNORE_CASE
	)
	private val COMMA = Regex("\\s*,\\s*")
	private const val T6_GOLD = 0xFFAA00

	@Volatile
	private var maxByName: Map<String, Int>? = null

	@Volatile
	private var ultimateByName: Map<String, Boolean> = mapOf()

	@JvmStatic
	fun colorize(line: Component?): Component {
		if (line == null) {
			return Component.empty()
		}
		val plain = line.string
		if (plain.isNullOrBlank() || !looksLikeEnchantLine(plain)) {
			return line
		}
		val maxes = ensureLoaded()
		val parts = splitParts(plain)
		val out = Component.empty().setStyle(Style.EMPTY.withItalic(false))
		var any = false
		for ((i, raw) in parts.withIndex()) {
			if (i > 0) {
				out.append(Component.literal(", ").withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)))
			}
			val part = raw.trim()
			val match = TOKEN.matchEntire(part)
			if (match == null) {
				out.append(Component.literal(part).withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)))
				continue
			}
			val name = match.groupValues[1].trim()
			val level = parseLevel(match.groupValues[2])
			val max = maxes[name.lowercase(Locale.ROOT).trim()]
			if (max == null || max < 2 || level <= 0) {
				out.append(Component.literal(part).withStyle(Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)))
				continue
			}
			any = true
			val ultimate = ultimateByName[name.lowercase(Locale.ROOT).trim()] == true
			val label = name + " " + match.groupValues[2].uppercase(Locale.ROOT)
			if (ExtrasSettings.enchantColors) {
				val s = ExtrasSettings
				out.append(when {
					level >= max -> tier(label, s.enchantMaxColor, s.enchantMaxChroma, s.enchantBold)
					level == max - 1 -> tier(label, s.enchantHighColor, s.enchantHighChroma, s.enchantBold)
					ultimate -> tier(label, s.enchantUltColor, s.enchantUltChroma, true)
					else -> tier(label, s.enchantNormalColor, s.enchantNormalChroma, false)
				})
			} else if (level >= max) {
				out.append(PvDraw.chromaText(label, true, 0f))
			} else if (level == max - 1) {
				out.append(PvDraw.styled(label, T6_GOLD, true))
			} else if (ultimate) {
				out.append(
					Component.literal(label).withStyle(
						Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false)
					)
				)
			} else {
				out.append(
					Component.literal(label).withStyle(
						Style.EMPTY.withColor(ChatFormatting.BLUE).withItalic(false)
					)
				)
			}
		}
		return if (any) out else line
	}

	private fun tier(label: String, argb: Int, chroma: Boolean, bold: Boolean): Component =
		if (chroma) PvDraw.chromaText(label, bold, 0f) // one shared hue so all enchants stay in sync
		else Component.literal(label).withStyle(Style.EMPTY.withColor(argb and 0xFFFFFF).withBold(bold).withItalic(false))

	/** Recolours enchant lines in every item tooltip when Enchant Colors is on. */
	@JvmStatic
	fun init() {
		ItemTooltipCallback.EVENT.register(ItemTooltipCallback { _, _, _, lines ->
			if (!ExtrasSettings.enchantColors) return@ItemTooltipCallback
			for (i in 1 until lines.size) lines[i] = colorize(lines[i])
		})
	}

	// Java String.split semantics: trailing empty parts dropped.
	private fun splitParts(plain: String): List<String> = plain.split(COMMA).dropLastWhile { it.isEmpty() }

	private fun looksLikeEnchantLine(plain: String): Boolean =
		TOKEN.matches((splitParts(plain).firstOrNull() ?: "").trim())

	private fun parseLevel(raw: String?): Int {
		if (raw == null) {
			return 0
		}
		return when (val token = raw.trim().uppercase(Locale.ROOT)) {
			"I" -> 1
			"II" -> 2
			"III" -> 3
			"IV" -> 4
			"V" -> 5
			"VI" -> 6
			"VII" -> 7
			"VIII" -> 8
			"IX" -> 9
			"X" -> 10
			"XI" -> 11
			"XII" -> 12
			else -> token.toIntOrNull() ?: 0
		}
	}

	private fun ensureLoaded(): Map<String, Int> {
		maxByName?.let { return it }
		val maxes = HashMap<String, Int>()
		val ultimates = HashMap<String, Boolean>()
		try {
			load(maxes, ultimates)
		} finally {
			// Publish only fully built maps (ultimate first: callers gate on maxByName).
			ultimateByName = ultimates
			maxByName = maxes
		}
		return maxes
	}

	private fun load(maxByName: MutableMap<String, Int>, ultimateByName: MutableMap<String, Boolean>) {
		val client: Minecraft? = Minecraft.getInstance()
		val id = ProfileViewer.id("data/enchants.json")
		try {
			var input: InputStream? = null
			if (client != null) {
				val resource = client.resourceManager.getResource(id)
				if (resource.isPresent) {
					input = resource.get().open()
				}
			}
			if (input == null) {
				input = ProfileViewer::class.java.getResourceAsStream("/assets/fishmod/pv/data/enchants.json")
			}
			if (input == null) {
				return
			}
			input.use { stream ->
				InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
					val rootEl = JsonParser.parseReader(reader)
					if (rootEl == null || !rootEl.isJsonObject) {
						return
					}
					for ((key, value) in rootEl.asJsonObject.entrySet()) {
						val obj = if (value != null && value.isJsonObject) value.asJsonObject else continue
						val max = if (obj.has("max")) obj.get("max").asInt else 0
						val ultimate = obj.has("ultimate") && obj.get("ultimate").asBoolean
						put(maxByName, ultimateByName, key, max, ultimate)
						if (obj.has("lore") && obj.get("lore").isJsonPrimitive) {
							put(maxByName, ultimateByName, obj.get("lore").asString, max, ultimate)
						}
						if (obj.has("nbt") && obj.get("nbt").isJsonPrimitive) {
							put(maxByName, ultimateByName, obj.get("nbt").asString, max, ultimate)
						}
					}
				}
			}
		} catch (exception: Exception) {
			ProfileViewer.LOGGER.warn("Failed to load enchant max levels", exception)
		}
	}

	private fun put(
		maxByName: MutableMap<String, Int>,
		ultimateByName: MutableMap<String, Boolean>,
		key: String?,
		max: Int,
		ultimate: Boolean
	) {
		if (key.isNullOrBlank() || max < 1) {
			return
		}
		val id = key.lowercase(Locale.ROOT).trim()
		maxByName[id] = max
		maxByName[id.replace('_', ' ')] = max
		ultimateByName[id] = ultimate
		ultimateByName[id.replace('_', ' ')] = ultimate
	}
}
