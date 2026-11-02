/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import com.google.common.collect.ImmutableListMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

/** Applies NEU / Hypixel skull Value (+ optional Signature) onto player heads. */
object SkullTextureApplier {
	private val SKULL_VALUE = Regex("Value\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
	private val SKULL_SIGNATURE = Regex("Signature\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
	private val WHITESPACE = Regex("\\s+")

	@JvmStatic
	fun applyFromNbt(stack: ItemStack?, nbt: String?) {
		if (stack == null || !stack.`is`(Items.PLAYER_HEAD) || nbt == null || !nbt.contains("Value")) {
			return
		}
		val valueMatch = SKULL_VALUE.find(nbt) ?: return
		val value = valueMatch.groupValues[1].replace(WHITESPACE, "")
		val signature = SKULL_SIGNATURE.find(nbt)?.groupValues?.get(1)?.replace(WHITESPACE, "")
		applyValue(stack, value, signature)
	}

	@JvmStatic
	fun applyValue(stack: ItemStack?, value: String?, signature: String?) {
		if (stack == null || value.isNullOrBlank()) {
			return
		}
		val padded = padBase64(cleanValue(value))
		if (padded.isNullOrEmpty()) {
			return
		}
		// Prefer unsigned textures first: NEU/Hypixel signatures are often rejected by the client
		// skin pipeline, which leaves a bare Steve head even when PROFILE is set incorrectly.
		if (tryApplyProfile(stack, padded, null)) {
			return
		}
		if (!signature.isNullOrBlank()) {
			tryApplyProfile(stack, padded, signature)
		}
	}

	private fun resolvedProfile(paddedValue: String, signature: String?): ResolvableProfile {
		val uuid = UUID.nameUUIDFromBytes("fishpv:$paddedValue".toByteArray())
		val textures = if (signature.isNullOrBlank()) {
			Property("textures", paddedValue)
		} else {
			Property("textures", paddedValue, signature)
		}
		val properties = PropertyMap(ImmutableListMultimap.of("textures", textures))
		return ResolvableProfile.createResolved(GameProfile(uuid, "fishpv", properties))
	}

	private fun tryApplyProfile(stack: ItemStack, paddedValue: String, signature: String?): Boolean {
		// Resolved profile first: an unresolved CODEC profile (fake id) makes the client look it up and lose the texture.
		try {
			stack.set(DataComponents.PROFILE, resolvedProfile(paddedValue, signature))
			if (isTexturedPlayerHead(stack)) {
				return true
			}
		} catch (ignored: Exception) {
		}
		try {
			val propTag = CompoundTag()
			propTag.putString("name", "textures")
			propTag.putString("value", paddedValue)
			if (!signature.isNullOrBlank()) {
				propTag.putString("signature", signature)
			}
			val propsList = ListTag()
			propsList.add(propTag)
			val profileTag = CompoundTag()
			profileTag.putIntArray("id", intArrayOf(0, 0, 0, 1))
			profileTag.put("properties", propsList)
			val parsed = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, profileTag).result()
			if (parsed.isPresent) {
				stack.set(DataComponents.PROFILE, parsed.get())
				if (isTexturedPlayerHead(stack)) {
					return true
				}
			}
		} catch (ignored: Exception) {
		}
		return try {
			stack.set(DataComponents.PROFILE, resolvedProfile(paddedValue, signature))
			isTexturedPlayerHead(stack)
		} catch (ignored: Exception) {
			false
		}
	}

	/** True when a player head has a non-blank textures property (not a Steve placeholder). */
	@JvmStatic
	fun isTexturedPlayerHead(stack: ItemStack?): Boolean {
		if (stack == null || stack.isEmpty || !stack.`is`(Items.PLAYER_HEAD)) {
			return false
		}
		val profile = stack.get(DataComponents.PROFILE) ?: return false
		try {
			val props = profile.partialProfile().properties().get("textures")
			if (props == null || props.isEmpty()) {
				return false
			}
			for (prop in props) {
				if (prop != null && !prop.value().isNullOrBlank()) {
					return true
				}
			}
		} catch (ignored: Exception) {
		}
		return false
	}

	// Some NEU values carry a stray '=' after complete base64 or a `", "signature` tail.
	private fun cleanValue(value: String): String {
		var v = value.replace(WHITESPACE, "")
		val quote = v.indexOf('"')
		if (quote >= 0) {
			v = v.substring(0, quote)
		}
		return v.trimEnd('=')
	}

	@JvmStatic
	fun padBase64(value: String?): String? {
		if (value.isNullOrEmpty()) {
			return value
		}
		val pad = (4 - (value.length % 4)) % 4
		return if (pad == 0) value else value + "=".repeat(pad)
	}
}
