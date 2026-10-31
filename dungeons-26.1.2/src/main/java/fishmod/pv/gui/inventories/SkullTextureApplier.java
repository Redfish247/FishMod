/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.google.common.collect.ImmutableListMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Applies NEU / Hypixel skull Value (+ optional Signature) onto player heads. */
final class SkullTextureApplier {
	private static final Pattern SKULL_VALUE = Pattern.compile(
		"Value\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);
	private static final Pattern SKULL_SIGNATURE = Pattern.compile(
		"Signature\\s*:\\s*\"([^\"]+)\"",
		Pattern.CASE_INSENSITIVE
	);

	private SkullTextureApplier() {
	}

	static void applyFromNbt(ItemStack stack, String nbt) {
		if (stack == null || !stack.is(Items.PLAYER_HEAD) || nbt == null || !nbt.contains("Value")) {
			return;
		}
		Matcher valueMatcher = SKULL_VALUE.matcher(nbt);
		if (!valueMatcher.find()) {
			return;
		}
		String value = valueMatcher.group(1).replaceAll("\\s+", "");
		String signature = null;
		Matcher signatureMatcher = SKULL_SIGNATURE.matcher(nbt);
		if (signatureMatcher.find()) {
			signature = signatureMatcher.group(1).replaceAll("\\s+", "");
		}
		applyValue(stack, value, signature);
	}

	static void applyValue(ItemStack stack, String value, String signature) {
		if (stack == null || value == null || value.isBlank()) {
			return;
		}
		String padded = padBase64(cleanValue(value));
		if (padded == null || padded.isEmpty()) {
			return;
		}
		// Prefer unsigned textures first: NEU/Hypixel signatures are often rejected by the client
		// skin pipeline, which leaves a bare Steve head even when PROFILE is set incorrectly.
		if (tryApplyProfile(stack, padded, null)) {
			return;
		}
		if (signature != null && !signature.isBlank() && tryApplyProfile(stack, padded, signature)) {
			return;
		}
	}

	private static boolean tryApplyProfile(ItemStack stack, String paddedValue, String signature) {
		// Resolved profile first: an unresolved CODEC profile (fake id) makes the client look it up and lose the texture.
		try {
			UUID uuid = UUID.nameUUIDFromBytes(("betterpv:" + paddedValue).getBytes());
			Property textures = signature == null || signature.isBlank()
				? new Property("textures", paddedValue)
				: new Property("textures", paddedValue, signature);
			PropertyMap properties = new PropertyMap(ImmutableListMultimap.of("textures", textures));
			GameProfile profile = new GameProfile(uuid, "betterpv", properties);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			if (isTexturedPlayerHead(stack)) {
				return true;
			}
		} catch (Exception ignored) {
		}
		try {
			CompoundTag propTag = new CompoundTag();
			propTag.putString("name", "textures");
			propTag.putString("value", paddedValue);
			if (signature != null && !signature.isBlank()) {
				propTag.putString("signature", signature);
			}
			ListTag propsList = new ListTag();
			propsList.add(propTag);
			CompoundTag profileTag = new CompoundTag();
			profileTag.putIntArray("id", new int[] {0, 0, 0, 1});
			profileTag.put("properties", propsList);
			var parsed = ResolvableProfile.CODEC.parse(NbtOps.INSTANCE, profileTag).result();
			if (parsed.isPresent()) {
				stack.set(DataComponents.PROFILE, parsed.get());
				if (isTexturedPlayerHead(stack)) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		try {
			UUID uuid = UUID.nameUUIDFromBytes(("betterpv:" + paddedValue).getBytes());
			Property textures = signature == null || signature.isBlank()
				? new Property("textures", paddedValue)
				: new Property("textures", paddedValue, signature);
			PropertyMap properties = new PropertyMap(ImmutableListMultimap.of("textures", textures));
			GameProfile profile = new GameProfile(uuid, "betterpv", properties);
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
			return isTexturedPlayerHead(stack);
		} catch (Exception ignored) {
			return false;
		}
	}

	/** True when a player head has a non-blank textures property (not a Steve placeholder). */
	static boolean isTexturedPlayerHead(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.is(Items.PLAYER_HEAD)) {
			return false;
		}
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) {
			return false;
		}
		try {
			var props = profile.partialProfile().properties().get("textures");
			if (props == null || props.isEmpty()) {
				return false;
			}
			for (Property prop : props) {
				if (prop != null && prop.value() != null && !prop.value().isBlank()) {
					return true;
				}
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	// Some NEU values carry a stray '=' after complete base64 or a `", "signature` tail.
	private static String cleanValue(String value) {
		String v = value.replaceAll("\\s+", "");
		int quote = v.indexOf('"');
		if (quote >= 0) {
			v = v.substring(0, quote);
		}
		int end = v.length();
		while (end > 0 && v.charAt(end - 1) == '=') {
			end--;
		}
		return v.substring(0, end);
	}

	static String padBase64(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		int pad = (4 - (value.length() % 4)) % 4;
		return pad == 0 ? value : value + "=".repeat(pad);
	}
}
