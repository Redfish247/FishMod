/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories;

import com.google.gson.JsonObject;
import fishmod.pv.BetterPV;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.data.InventorySnapshot;
import fishmod.pv.data.PetLoreResolver;
import fishmod.pv.data.PetSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.neu.NeuRepoCache;
import fishmod.pv.neu.SkyBlockPackCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;

/**
 * Public item-stack / tooltip facade. Stack building and lore enrichment live in package helpers:
 * {@link NeuItemResolver}, {@link HypixelItemResolver}, {@link SkullTextureApplier},
 * {@link LegacyMaterialMap}, {@link ItemTooltipEnricher}, {@link ItemStackCache}.
 */
public final class SkyBlockItemFactory {
	private static final Set<String> FAILED_ICON_IDS = ConcurrentHashMap.newKeySet();
	private static final Pattern PET_LEVEL = Pattern.compile("(?i)\\[Lvl\\s*(\\d+)\\]");
	private static final Pattern LVL_PLACEHOLDER = Pattern.compile("(?i)\\[Lvl\\s*\\{?LVL\\}?\\]\\s*");
	/** NEU {@code MINING_2_PORTAL} skull Value (HotM / mining menu stand-in). */
	private static final String HOTM_TAB_SKULL_VALUE =
		"ewogICJ0aW1lc3RhbXAiIDogMTYxODk5OTIyNDk2OSwKICAicHJvZmlsZUlkIiA6ICI1NjY3NWIyMjMyZjA0ZWUwODkxNzllOWM5MjA2Y2ZlOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVJbmRyYSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83NDIxM2RjNmRjNGIxNjQxZGVmZDMzM2Y0YTQ3MzJjYzcxNGRkNjc3NzE4ZmExMGYxNDBhNjkzOWMxMmFhMzJiIgogICAgfQogIH0KfQ==";

	/**
	 * Classic Flawless gemstone player-head textures (pre-item-model NEU).
	 * Modern gems are paper; these skull Values still render the familiar 3D cubes.
	 */
	private static final Map<String, String> FLAWLESS_GEM_SKULLS = Map.ofEntries(
		Map.entry("JADE", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTY1NzA0NywKICAicHJvZmlsZUlkIiA6ICIxYWZhZjc2NWI1ZGY0NjA3YmY3ZjY1ZGYzYWIwODhhOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMb3lfQmxvb2RBbmdlbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mODlmNzVlMGIwMDM3OGE1ODNkYmJhNzI4ZGNkYzZlOTM0NmYzMWRkNjAxZDQ0OGYzZDYwNjE1Yzc0NjVjYzNlIgogICAgfQogIH0KfQ=="),
		Map.entry("AMBER", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTE3NjQ5NiwKICAicHJvZmlsZUlkIiA6ICJiNWRkZTVmODJlYjM0OTkzYmMwN2Q0MGFiNWY2ODYyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdXhlbWFuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkY2U2MmY3MGFjMDQ2Yjg4MTExM2M2Y2Y4NjI5ODc3Mjc3NzRlMjY1ODg1NTAxYzlhMjQ1YjE4MGRiMDhjMGQiCiAgICB9CiAgfQp9"),
		Map.entry("AMETHYST", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTQ2NjY1MiwKICAicHJvZmlsZUlkIiA6ICI2MWVhMDkyM2FhNDQ0OTEwYmNlZjViZmQ2ZDNjMGQ1NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVEYXJ0aEZhdGhlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMzYyMzUyMWM4MTExYWQyOWU5ZGNmN2FjYzU2MDg1YTlhYjA3ZGE3MzJkMTUxODk3NmFlZTYxZDBiM2UzYmQ2IgogICAgfQogIH0KfQ=="),
		Map.entry("SAPPHIRE", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjIzMjc2OSwKICAicHJvZmlsZUlkIiA6ICI5MWZlMTk2ODdjOTA0NjU2YWExZmMwNTk4NmRkM2ZlNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJoaGphYnJpcyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85NTdjZmE5Yzc1YmE1ODQ2NDVlZTJhZjZkOTg2N2Q3NjdkZGVhNDY2N2NkZmM3MmRjMTA2MWRkMTk3NWNhN2QwIgogICAgfQogIH0KfQ=="),
		Map.entry("TOPAZ", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjQxMjI5MSwKICAicHJvZmlsZUlkIiA6ICI1N2IzZGZiNWY4YTY0OWUyOGI1NDRlNGZmYzYzMjU2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJYaWthcm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDEwOTY0ZjNjNDc5YWQ3ZDlhZmFmNjhhNDJjYWI3YzEwN2QyZDg4NGY1NzVjYWUyZjA3MGVjNmY5MzViM2JlIgogICAgfQogIH0KfQ=="),
		Map.entry("JASPER", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjAzNDI0NywKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZjk5M2QzYTQzZDQwNTk3YjQ3NDQ4NTk3NjE2MGQwY2Y1MmFjNjRkMTU3MzA3ZDNiMWM5NDFkYjIyNGQwYWM2IgogICAgfQogIH0KfQ=="),
		Map.entry("RUBY", "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4MzU3MzU0NywKICAicHJvZmlsZUlkIiA6ICJmZDQ3Y2I4YjgzNjQ0YmY3YWIyYmUxODZkYjI1ZmMwZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDEyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkyNmEyNDhmYmJjMDZjZjA2ZTJjOTIwZWNhMWNhYzhhMmM5NjE2NGQzMjYwNDk0YmVkMTQyZDU1MzAyNmNjNiIKICAgIH0KICB9Cn0="),
		Map.entry("AQUAMARINE", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MDU2MjAxNiwKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNb2phbmdIYXNBU3R1ZGlvIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2QzNzY5OWJkZjhjYjQzZWZmMTQ3ZDljZTE5YjE0ODAyZjliYzg4YTIxZTYzYWQyOGI2Njk1MzBlOGEzYzBiNTciCiAgICB9CiAgfQp9"),
		Map.entry("CITRINE", "ewogICJ0aW1lc3RhbXAiIDogMTcwODQ3NzA2NTU2NywKICAicHJvZmlsZUlkIiA6ICJhYWZmMDUwYTExOTk0NzM1YjEyNDVlNDk0MGFlZjY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXN0SW1tb3J0YWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjA2YjRmNjNkM2QzYTM5Yzk4NTY1YWY0ODU4YTUxMzViZTc3NGFkNjcyZWIyMzZiYjY1YWRmYzhjYjM0MjVlOCIKICAgIH0KICB9Cn0="),
		Map.entry("ONYX", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MTg1MDQxOSwKICAicHJvZmlsZUlkIiA6ICI0NmNhODkyZTY4ODA0YThmYjFkYzkwYjg0ZTY5ZjVmZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPbG8xNjA2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFjYTliMjg0MWY3MWJhNmZjNGQ2ZDJlOTdlODBjMTgwMWYzNmNhMzk1OGU1YTlhODFhNGY4Nzg1ZjY0MzQzNjciCiAgICB9CiAgfQp9"),
		Map.entry("OPAL", "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA0MjA1OTQ4NywKICAicHJvZmlsZUlkIiA6ICI1ZTdmY2RjYTU5YzI0NjkwODAwNjg4OTNkODU1ODM3NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJKYWVsbGFyaSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81ZDE1ZWQ3MGU3MjAwNDBhZDczMTFlNjkzNTlkZmRmNWUxMTRlYWRkMmE0YzFmOTcxYTk1MDEzNDFhNDUyNjRiIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0="),
		Map.entry("PERIDOT", "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MjI2OTYzMSwKICAicHJvZmlsZUlkIiA6ICIxZDIyYmUxYmQ2YTM0NWQ0OTI0Nzc4YmM4YWFlMjYzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNQVJTX0hYIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE4ODRhOGRjYjcxMjgzNDFjZTA5Y2IzNjE2YzExYzNlNDZmODBkMDgzYTZmMmRiYjRkYmUwYmI5MzIzMThiMDkiCiAgICB9CiAgfQp9")
	);

	private SkyBlockItemFactory() {
	}

	public static void clearCache() {
		ItemStackCache.clear();
		SkyBlockItemIconCache.clear();
	}

	public static Identifier customIcon(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		String model = ItemStackCache.getItemModel(key);
		if (model == null) {
			ItemStackCache.rememberItemModel(key, HypixelItemResolver.resolveItemModel(key));
			model = ItemStackCache.getItemModel(key);
		}
		return SkyBlockItemIconCache.getOrRequest(model);
	}

	public static Identifier customIconModel(String skyblockId, String itemModel) {
		if (itemModel == null || itemModel.isBlank()) {
			return customIcon(skyblockId);
		}
		if (skyblockId != null && !skyblockId.isBlank()) {
			ItemStackCache.rememberItemModel(skyblockId.toUpperCase(Locale.ROOT), itemModel);
		}
		return SkyBlockItemIconCache.getOrRequest(itemModel);
	}

	public static int customIconSize(String skyblockId) {
		if (skyblockId == null) {
			return 16;
		}
		String model = ItemStackCache.getItemModel(skyblockId.toUpperCase(Locale.ROOT));
		return SkyBlockItemIconCache.textureSize(model);
	}

	/** Hypixel {@code item_model} string for a SkyBlock id, if known. */
	public static String itemModel(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return null;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		String model = ItemStackCache.getItemModel(key);
		if (model == null) {
			ItemStackCache.rememberItemModel(key, HypixelItemResolver.resolveItemModel(key));
			model = ItemStackCache.getItemModel(key);
		}
		return model;
	}

	/** Blocking warm on a worker thread so the first inventory paint already has textures. */
	public static void warmBlocking(InventorySnapshot snapshot) {
		NeuRepoCache.ensureLoadedBlocking();
		SkyBlockPackCache.ensureReadyBlocking();
		prefetch(snapshot);
		if (snapshot == null) {
			return;
		}
		Set<String> ids = new HashSet<>();
		collectIds(snapshot.inventory(), ids);
		for (InventorySnapshot.Page page : snapshot.enderChest()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.backpacks()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.wardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.equipmentWardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Loadout loadout : snapshot.loadouts()) collectLoadoutIds(loadout, ids);
		for (InventorySnapshot.Page page : snapshot.sacks()) collectIds(page, ids);
		collectIds(snapshot.fishingBag(), ids);
		collectIds(snapshot.potionBag(), ids);
		collectIds(snapshot.quiver(), ids);
		for (InventorySnapshot.Page page : snapshot.accessoryBag()) collectIds(page, ids);
		collectIds(snapshot.timePocket(), ids);
		collectIds(snapshot.personalVault(), ids);
		collectIds(snapshot.carnivalMasks(), ids);
		collectIds(snapshot.candyBag(), ids);
		for (String id : ids) {
			for (String candidate : NeuItemResolver.candidates(id)) {
				if (NeuRepoCache.get(candidate) == null) {
					NeuRepoCache.getOrFetch(candidate);
				}
			}
			baseStack(id);
			customIcon(id);
		}
	}

	/** Prefetch + warm stacks without delaying the profile UI. */
	public static void warmAsync(InventorySnapshot snapshot) {
		prefetch(snapshot);
		HypixelApiClient.parseExecutor().execute(() -> {
			try {
				warmBlocking(snapshot);
			} catch (Exception ignored) {
			}
		});
	}

	/** Prefetch pet skull / held-item icons in the background. */
	public static void warmPetsAsync(PetSnapshot pets) {
		prefetchPets(pets);
		HypixelApiClient.parseExecutor().execute(() -> {
			try {
				warmPetsBlocking(pets);
			} catch (Exception ignored) {
			}
		});
	}

	public static void prefetchPets(PetSnapshot pets) {
		if (pets == null || pets.isEmpty()) {
			return;
		}
		Set<String> ids = new HashSet<>();
		for (PetSnapshot.Entry pet : pets.pets()) {
			if (pet.neuId() != null && !pet.neuId().isBlank()) {
				ids.add(pet.neuId());
			}
			if (pet.hasHeldItem()) {
				ids.add(pet.heldItem());
			}
		}
		prefetchIds(ids);
	}

	/** Prefetch NEU defs for arbitrary SkyBlock item ids (collections / minions). */
	public static void prefetchIds(Collection<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return;
		}
		SkyBlockItemIconCache.ensurePack();
		Set<String> neuIds = new HashSet<>();
		for (String id : ids) {
			if (id == null || id.isBlank()) {
				continue;
			}
			neuIds.addAll(NeuItemResolver.candidates(id));
		}
		NeuRepoCache.prefetch(neuIds);
	}

	private static void warmPetsBlocking(PetSnapshot pets) {
		if (pets == null || pets.isEmpty()) {
			return;
		}
		for (PetSnapshot.Entry pet : pets.pets()) {
			if (pet.neuId() != null && !pet.neuId().isBlank()) {
				baseStack(pet.neuId());
				customIcon(pet.neuId());
			}
			if (pet.hasHeldItem()) {
				baseStack(pet.heldItem());
				customIcon(pet.heldItem());
			}
		}
	}

	public static void prefetch(InventorySnapshot snapshot) {
		if (snapshot == null) {
			return;
		}
		Set<String> ids = new HashSet<>();
		collectIds(snapshot.inventory(), ids);
		for (InventorySnapshot.Page page : snapshot.enderChest()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.backpacks()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.wardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Page page : snapshot.equipmentWardrobe()) collectIds(page, ids);
		for (InventorySnapshot.Loadout loadout : snapshot.loadouts()) collectLoadoutIds(loadout, ids);
		for (InventorySnapshot.Page page : snapshot.sacks()) collectIds(page, ids);
		collectIds(snapshot.fishingBag(), ids);
		collectIds(snapshot.potionBag(), ids);
		collectIds(snapshot.quiver(), ids);
		for (InventorySnapshot.Page page : snapshot.accessoryBag()) collectIds(page, ids);
		collectIds(snapshot.timePocket(), ids);
		collectIds(snapshot.personalVault(), ids);
		collectIds(snapshot.carnivalMasks(), ids);
		collectIds(snapshot.candyBag(), ids);
		// Inventory pane button skulls (backpack / sacks / bags).
		ids.add("JUMBO_BACKPACK");
		ids.add("POCKET_SACK_IN_A_SACK");
		ids.add("LARGE_POTION_BAG");
		ids.add("LARGE_TALISMAN_BAG");
		ids.addAll(NeuRepoCache.sackItemIds());
		SkyBlockItemIconCache.ensurePack();
		Set<String> neuIds = new HashSet<>();
		for (String id : ids) {
			neuIds.addAll(NeuItemResolver.candidates(id));
		}
		NeuRepoCache.prefetch(neuIds);
	}

	private static void collectIds(InventorySnapshot.Page page, Set<String> ids) {
		if (page == null || page.slots() == null) {
			return;
		}
		for (InventorySnapshot.Slot slot : page.slots()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
	}

	private static void collectLoadoutIds(InventorySnapshot.Loadout loadout, Set<String> ids) {
		if (loadout == null) {
			return;
		}
		for (InventorySnapshot.Slot slot : loadout.equipment()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
		for (InventorySnapshot.Slot slot : loadout.armor()) {
			if (slot != null && !slot.isEmpty() && slot.id() != null) {
				ids.add(slot.id());
			}
		}
		if (loadout.pet() != null && !loadout.pet().isEmpty() && loadout.pet().id() != null) {
			ids.add(loadout.pet().id());
		}
	}

	public static ItemStack iconStack(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return ItemStack.EMPTY;
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		if ("RUBY_VEILSHROOM".equals(key)) {
			ItemStack veil = baseStack("VEILSHROOM");
			if (!veil.isEmpty() && !veil.is(Items.PAPER)) {
				ItemStack copy = veil.copy();
				applySkyBlockIdentity(copy, key);
				return copy;
			}
			ItemStack mushroom = new ItemStack(Items.RED_MUSHROOM);
			applySkyBlockIdentity(mushroom, key);
			return mushroom;
		}
		NeuRepoCache.prefetch(NeuItemResolver.candidates(skyblockId));
		ItemStack stack = baseStack(skyblockId).copy();
		applySkyBlockIdentity(stack, key);
		return stack;
	}

	/**
	 * Heart of the Mountain tab icon - SkyBlock mining skull (Deep Caverns / HotM menu style head).
	 * Token of the Mountain is not in the items API; this is the closest NEU mining portal skull.
	 */
	public static ItemStack hotmTabIcon() {
		ItemStack sky = iconStack("MINING_2_PORTAL");
		if (sky != null && !sky.isEmpty() && sky.is(Items.PLAYER_HEAD)) {
			return sky;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		SkullTextureApplier.applyValue(head, HOTM_TAB_SKULL_VALUE, null);
		return head;
	}

	public static ItemStack gemstoneHead(String gem) {
		if (gem == null || gem.isBlank()) {
			return ItemStack.EMPTY;
		}
		String key = gem.trim().toUpperCase(Locale.ROOT);
		if (key.endsWith("_CRYSTAL")) {
			key = key.substring(0, key.length() - "_CRYSTAL".length());
		}
		if (key.endsWith("_GEM")) {
			key = key.substring(0, key.length() - "_GEM".length());
		}
		String value = FLAWLESS_GEM_SKULLS.get(key);
		if (value == null) {
			return ItemStack.EMPTY;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		SkullTextureApplier.applyValue(head, value, null);
		return head;
	}

	public static ItemStack texturedHead(String value) {
		if (value == null || value.isBlank()) {
			return ItemStack.EMPTY;
		}
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		SkullTextureApplier.applyValue(head, value, null);
		return head;
	}

	public static ItemStack trophySkullStack(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		String value = fishmod.pv.data.TrophySkulls.value(id);
		if (value == null || value.isBlank()) {
			return new ItemStack(Items.PLAYER_HEAD);
		}
		return texturedHead(value);
	}

	public static ItemStack toStack(InventorySnapshot.Slot slot) {
		if (slot == null || slot.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack base = baseStack(slot.id());
		ItemStack stack = base.copy();
		// Sack totals (and other huge counts) show as a single icon; quantity goes in the tooltip.
		int displayCount = slot.count() > 64 ? 1 : Math.max(1, Math.min(64, slot.count()));
		stack.setCount(displayCount);

		// Prefer live inventory NBT (custom dyes / skulls) over NEU defaults.
		if (slot.skullValue() != null && !slot.skullValue().isBlank()) {
			if (!stack.is(Items.PLAYER_HEAD)) {
				stack = new ItemStack(Items.PLAYER_HEAD, stack.getCount());
			}
			SkullTextureApplier.applyValue(stack, slot.skullValue(), slot.skullSignature());
		}
		if (slot.dyeColor() != null && LegacyMaterialMap.isLeather(stack.getItem())) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(slot.dyeColor() & 0xFFFFFF));
		}

		String name = slot.displayName();
		if (name == null || name.isBlank()) {
			JsonObject neu = NeuItemResolver.neuItem(slot.id());
			if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
				name = neu.get("displayname").getAsString();
				// Pets ship as "[Lvl {LVL}] Name" - strip the level placeholder for UI labels.
				name = LVL_PLACEHOLDER.matcher(name).replaceAll("").trim();
			}
		}
		if (name == null || name.isBlank()) {
			JsonObject def = HypixelItemResolver.hypixelDef(slot.id());
			if (def != null && def.has("name") && def.get("name").isJsonPrimitive()) {
				name = def.get("name").getAsString();
				if (name != null && !name.contains("§") && def.has("tier") && def.get("tier").isJsonPrimitive()) {
					name = ItemTextFormat.tierColorPrefix(def.get("tier").getAsString()) + name;
				}
			}
		}
		if (name == null || name.isBlank()) {
			name = ItemTextFormat.prettyId(NeuItemResolver.canonicalId(slot.id()));
		}
		stack.set(DataComponents.CUSTOM_NAME, ItemTooltipEnricher.legacyText(name).copy().withStyle(style -> style.withItalic(false)));

		List<Component> loreLines = new ArrayList<>();
		if (slot.lore() != null && !slot.lore().isEmpty()) {
			for (String raw : slot.lore()) {
				String cleaned = ItemTooltipEnricher.cleanLoreLine(raw);
				loreLines.add(cleaned.isBlank() ? Component.empty() : EnchantTooltip.colorize(ItemTooltipEnricher.legacyText(cleaned)));
			}
		} else {
			// Auction PET_* tags often ship with empty lore - resolve NEU pet text.
			InventorySnapshot.Slot petResolved = resolveAuctionPetIfNeeded(slot);
			if (petResolved != null && petResolved.lore() != null && !petResolved.lore().isEmpty()) {
				if (petResolved.displayName() != null && !petResolved.displayName().isBlank()) {
					name = petResolved.displayName();
					stack.set(DataComponents.CUSTOM_NAME, ItemTooltipEnricher.legacyText(name).copy().withStyle(style -> style.withItalic(false)));
				}
				for (String raw : petResolved.lore()) {
					String cleaned = ItemTooltipEnricher.cleanLoreLine(raw);
					loreLines.add(cleaned.isBlank() ? Component.empty() : EnchantTooltip.colorize(ItemTooltipEnricher.legacyText(cleaned)));
				}
			} else {
				JsonObject neu = NeuItemResolver.neuItem(slot.id());
				if (neu != null && neu.has("lore") && neu.get("lore").isJsonArray()) {
					for (var el : neu.getAsJsonArray("lore")) {
						if (el.isJsonPrimitive()) {
							String line = el.getAsString();
							loreLines.add(line == null || line.isBlank()
								? Component.empty()
								: EnchantTooltip.colorize(ItemTooltipEnricher.legacyText(line)));
						}
					}
				}
			}
		}
		ItemTooltipEnricher.applyRecombobulatorMarkers(loreLines, slot);
		if (!loreLines.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(loreLines));
		}
		applySkyBlockIdentity(stack, slot.id());
		return stack;
	}

	/**
	 * Cofl / AH {@code PET_JELLYFISH} → resolved NEU pet slot with PetLoreResolver lore.
	 */
	public static InventorySnapshot.Slot auctionPetSlot(String tag, String displayName, String tier) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		String key = tag.toUpperCase(Locale.ROOT);
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null;
		}
		String type = key.substring(4);
		if (type.isBlank()) {
			return null;
		}
		String rarity = normalizeTier(tier);
		if (rarity.isBlank()) {
			rarity = normalizeTier(resolveTier(key));
		}
		if (rarity.isBlank()) {
			rarity = "LEGENDARY";
		}
		int tierIndex = petTierIndex(rarity);
		String neuId = type + ";" + tierIndex;
		int level = parsePetLevel(displayName);
		String pretty = type.replace('_', ' ');
		PetSnapshot.Entry pet = new PetSnapshot.Entry(
			type,
			pretty,
			rarity,
			tierIndex,
			neuId,
			level,
			Math.max(100, level),
			0,
			0,
			0,
			0,
			0f,
			false,
			"",
			0,
			"",
			"",
			0
		);
		String name = PetLoreResolver.displayNameFor(pet);
		if (name == null || name.isBlank()) {
			name = displayName == null || displayName.isBlank() ? pretty : displayName;
		}
		return new InventorySnapshot.Slot(neuId, 1, PetLoreResolver.loreFor(pet), name, null, null, null);
	}

	private static InventorySnapshot.Slot resolveAuctionPetIfNeeded(InventorySnapshot.Slot slot) {
		if (slot == null || slot.id() == null) {
			return null;
		}
		String key = slot.id().toUpperCase(Locale.ROOT);
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null;
		}
		return auctionPetSlot(key, slot.displayName(), "");
	}

	private static int petTierIndex(String tier) {
		if (tier == null) {
			return 3;
		}
		return switch (normalizeTier(tier)) {
			case "COMMON" -> 0;
			case "UNCOMMON" -> 1;
			case "RARE" -> 2;
			case "EPIC" -> 3;
			case "LEGENDARY" -> 4;
			case "MYTHIC" -> 5;
			default -> 4;
		};
	}

	private static int parsePetLevel(String displayName) {
		if (displayName == null || displayName.isBlank()) {
			return 1;
		}
		Matcher m = PET_LEVEL.matcher(displayName);
		if (m.find()) {
			try {
				return Math.max(1, Integer.parseInt(m.group(1)));
			} catch (NumberFormatException ignored) {
			}
		}
		return 1;
	}

	public static List<Component> tooltipLines(InventorySnapshot.Slot slot, ItemStack rendered) {
		return ItemTooltipEnricher.tooltipLines(slot, rendered, true);
	}

	public static List<Component> tooltipLines(
		InventorySnapshot.Slot slot,
		ItemStack rendered,
		boolean includeEstimatedValue
	) {
		return ItemTooltipEnricher.tooltipLines(slot, rendered, includeEstimatedValue);
	}

	/**
	 * Stamp SkyBlock id onto {@code custom_data} so CIT packs (FurfSky) can match.
	 */
	public static void applySkyBlockIdentity(ItemStack stack, String skyblockId) {
		if (stack == null || stack.isEmpty() || skyblockId == null || skyblockId.isBlank()) {
			return;
		}
		String id = skyblockId.toUpperCase(Locale.ROOT);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putString("id", id);
			CompoundTag extra = new CompoundTag();
			extra.putString("id", id);
			tag.put("ExtraAttributes", extra);
		});
	}

	private static ItemStack baseStack(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return new ItemStack(Items.PAPER);
		}
		try {
			return resolveBaseStack(skyblockId);
		} catch (RuntimeException exception) {
			// Bad repo / API data must not crash a GUI frame; icons are rebuilt every frame, so warn once per id.
			if (FAILED_ICON_IDS.add(skyblockId)) {
				BetterPV.LOGGER.warn("[BetterPV] Icon fallback for {}: {}", skyblockId, exception.toString());
			}
			return new ItemStack(Items.PAPER);
		}
	}

	private static ItemStack resolveBaseStack(String skyblockId) {
		String key = skyblockId.toUpperCase(Locale.ROOT);
		for (String candidate : NeuItemResolver.candidates(skyblockId)) {
			ItemStack cached = ItemStackCache.getBase(candidate);
			if (cached != null) {
				ItemStackCache.copyItemModel(candidate, key);
				return cached;
			}
			ItemStack built = NeuItemResolver.buildFromNeu(candidate);
			if (built != null) {
				ItemStackCache.putBase(candidate, built.copy());
				if (!key.equals(candidate)) {
					ItemStackCache.putBase(key, built.copy());
				}
				ItemStackCache.copyItemModel(candidate, key);
				return built;
			}
		}
		// Don't cache Hypixel fallbacks - NEU may still be downloading.
		return HypixelItemResolver.buildFromHypixel(key);
	}

	public static int tierArgb(String tier) {
		String key = normalizeTier(tier);
		if (key.isBlank()) {
			return PvDraw.COLOR_TEXT;
		}
		return switch (key) {
			case "COMMON" -> 0xFFFFFFFF;
			case "UNCOMMON" -> 0xFF55FF55;
			case "RARE" -> 0xFF5555FF;
			case "EPIC" -> 0xFFAA00AA;
			case "LEGENDARY" -> 0xFFFFAA00;
			case "MYTHIC" -> 0xFFFF55FF;
			case "DIVINE" -> 0xFF55FFFF;
			case "SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555;
			case "ULTIMATE" -> 0xFFAA0000;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	public static int tierArgbFromFormattedName(String formattedName) {
		return tierArgb(ItemTextFormat.tierFromFormattingPrefix(formattedName));
	}

	public static String neuTier(String skyblockId) {
		JsonObject neu = NeuItemResolver.neuItem(skyblockId);
		if (neu == null) {
			return "";
		}
		if (neu.has("tier") && neu.get("tier").isJsonPrimitive()) {
			String tier = normalizeTier(neu.get("tier").getAsString());
			if (!tier.isBlank()) {
				return tier;
			}
		}
		return NeuItemResolver.tierFromNeuItem(neu);
	}

	/**
	 * Resolve rarity from Hypixel items definitions, then NEU.
	 * Cofl pet tags ({@code PET_JELLYFISH}) omit rarity - do not guess from {@code TYPE;n} NEU files.
	 */
	public static String resolveTier(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return "";
		}
		String key = skyblockId.toUpperCase(Locale.ROOT);
		boolean petAuctionTag = key.startsWith("PET_") && !key.startsWith("PET_ITEM_");
		String hypixelTier = HypixelItemResolver.hypixelTier(skyblockId);
		if (!hypixelTier.isBlank()) {
			return hypixelTier;
		}
		if (!petAuctionTag) {
			String neu = neuTier(skyblockId);
			if (!neu.isBlank()) {
				return neu;
			}
		}
		return "";
	}

	public static String normalizeTier(String tier) {
		if (tier == null || tier.isBlank()) {
			return "";
		}
		String t = ItemTextFormat.stripFormatting(tier).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (t.startsWith("VERY_SPECIAL")) {
			return "VERY_SPECIAL";
		}
		return switch (t) {
			case "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE" -> t;
			default -> {
				Matcher m = NeuItemResolver.RARITY_WORD.matcher(t);
				yield m.find() ? m.group(1).toUpperCase(Locale.ROOT).replace(' ', '_') : "";
			}
		};
	}

	public static String plainDisplayName(String skyblockId) {
		if (skyblockId == null || skyblockId.isBlank()) {
			return "";
		}
		JsonObject neu = NeuItemResolver.neuItem(skyblockId);
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive()) {
			String name = neu.get("displayname").getAsString();
			if (name != null && !name.isBlank()) {
				name = LVL_PLACEHOLDER.matcher(name).replaceAll("").trim();
				return ItemTextFormat.stripFormatting(name);
			}
		}
		String hypixelName = HypixelItemResolver.hypixelDisplayName(skyblockId);
		if (hypixelName != null && !hypixelName.isBlank()) {
			return ItemTextFormat.stripFormatting(hypixelName);
		}
		return ItemTextFormat.prettyId(NeuItemResolver.canonicalId(skyblockId));
	}

	/** True when a player head has a non-blank textures property (not a Steve placeholder). */
	public static boolean isTexturedPlayerHead(ItemStack stack) {
		return SkullTextureApplier.isTexturedPlayerHead(stack);
	}

	public static Component legacyLine(String text) {
		return ItemTooltipEnricher.legacyLine(text);
	}
}
