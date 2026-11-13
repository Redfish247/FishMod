/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import fishmod.pv.ProfileViewer
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.PetLoreResolver
import fishmod.pv.data.PetSnapshot
import fishmod.pv.data.TrophySkulls
import fishmod.pv.gui.PvDraw
import fishmod.pv.neu.NeuRepoCache
import fishmod.pv.neu.SkyBlockPackCache
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ItemLore

object SkyBlockItemFactory {
	private val FAILED_ICON_IDS: MutableSet<String> = ConcurrentHashMap.newKeySet()
	private val PET_LEVEL = Regex("(?i)\\[Lvl\\s*(\\d+)\\]")
	private val LVL_PLACEHOLDER = Regex("(?i)\\[Lvl\\s*\\{?LVL\\}?\\]\\s*")
	private const val HOTM_TAB_SKULL_VALUE =
		"ewogICJ0aW1lc3RhbXAiIDogMTYxODk5OTIyNDk2OSwKICAicHJvZmlsZUlkIiA6ICI1NjY3NWIyMjMyZjA0ZWUwODkxNzllOWM5MjA2Y2ZlOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVJbmRyYSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS83NDIxM2RjNmRjNGIxNjQxZGVmZDMzM2Y0YTQ3MzJjYzcxNGRkNjc3NzE4ZmExMGYxNDBhNjkzOWMxMmFhMzJiIgogICAgfQogIH0KfQ=="

	private val FLAWLESS_GEM_SKULLS: Map<String, String> = mapOf(
		"JADE" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTY1NzA0NywKICAicHJvZmlsZUlkIiA6ICIxYWZhZjc2NWI1ZGY0NjA3YmY3ZjY1ZGYzYWIwODhhOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMb3lfQmxvb2RBbmdlbCIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mODlmNzVlMGIwMDM3OGE1ODNkYmJhNzI4ZGNkYzZlOTM0NmYzMWRkNjAxZDQ0OGYzZDYwNjE1Yzc0NjVjYzNlIgogICAgfQogIH0KfQ==",
		"AMBER" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTE3NjQ5NiwKICAicHJvZmlsZUlkIiA6ICJiNWRkZTVmODJlYjM0OTkzYmMwN2Q0MGFiNWY2ODYyMyIsCiAgInByb2ZpbGVOYW1lIiA6ICJsdXhlbWFuIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkY2U2MmY3MGFjMDQ2Yjg4MTExM2M2Y2Y4NjI5ODc3Mjc3NzRlMjY1ODg1NTAxYzlhMjQ1YjE4MGRiMDhjMGQiCiAgICB9CiAgfQp9",
		"AMETHYST" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NTQ2NjY1MiwKICAicHJvZmlsZUlkIiA6ICI2MWVhMDkyM2FhNDQ0OTEwYmNlZjViZmQ2ZDNjMGQ1NyIsCiAgInByb2ZpbGVOYW1lIiA6ICJUaGVEYXJ0aEZhdGhlciIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kMzYyMzUyMWM4MTExYWQyOWU5ZGNmN2FjYzU2MDg1YTlhYjA3ZGE3MzJkMTUxODk3NmFlZTYxZDBiM2UzYmQ2IgogICAgfQogIH0KfQ==",
		"SAPPHIRE" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjIzMjc2OSwKICAicHJvZmlsZUlkIiA6ICI5MWZlMTk2ODdjOTA0NjU2YWExZmMwNTk4NmRkM2ZlNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJoaGphYnJpcyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85NTdjZmE5Yzc1YmE1ODQ2NDVlZTJhZjZkOTg2N2Q3NjdkZGVhNDY2N2NkZmM3MmRjMTA2MWRkMTk3NWNhN2QwIgogICAgfQogIH0KfQ==",
		"TOPAZ" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjQxMjI5MSwKICAicHJvZmlsZUlkIiA6ICI1N2IzZGZiNWY4YTY0OWUyOGI1NDRlNGZmYzYzMjU2ZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJYaWthcm8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDEwOTY0ZjNjNDc5YWQ3ZDlhZmFmNjhhNDJjYWI3YzEwN2QyZDg4NGY1NzVjYWUyZjA3MGVjNmY5MzViM2JlIgogICAgfQogIH0KfQ==",
		"JASPER" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4NjAzNDI0NywKICAicHJvZmlsZUlkIiA6ICJiMGQ3MzJmZTAwZjc0MDdlOWU3Zjc0NjMwMWNkOThjYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPUHBscyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9mZjk5M2QzYTQzZDQwNTk3YjQ3NDQ4NTk3NjE2MGQwY2Y1MmFjNjRkMTU3MzA3ZDNiMWM5NDFkYjIyNGQwYWM2IgogICAgfQogIH0KfQ==",
		"RUBY" to "ewogICJ0aW1lc3RhbXAiIDogMTYxODA4MzU3MzU0NywKICAicHJvZmlsZUlkIiA6ICJmZDQ3Y2I4YjgzNjQ0YmY3YWIyYmUxODZkYjI1ZmMwZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDEyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzkyNmEyNDhmYmJjMDZjZjA2ZTJjOTIwZWNhMWNhYzhhMmM5NjE2NGQzMjYwNDk0YmVkMTQyZDU1MzAyNmNjNiIKICAgIH0KICB9Cn0=",
		"AQUAMARINE" to "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MDU2MjAxNiwKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNb2phbmdIYXNBU3R1ZGlvIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2QzNzY5OWJkZjhjYjQzZWZmMTQ3ZDljZTE5YjE0ODAyZjliYzg4YTIxZTYzYWQyOGI2Njk1MzBlOGEzYzBiNTciCiAgICB9CiAgfQp9",
		"CITRINE" to "ewogICJ0aW1lc3RhbXAiIDogMTcwODQ3NzA2NTU2NywKICAicHJvZmlsZUlkIiA6ICJhYWZmMDUwYTExOTk0NzM1YjEyNDVlNDk0MGFlZjY4NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJMYXN0SW1tb3J0YWwiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjA2YjRmNjNkM2QzYTM5Yzk4NTY1YWY0ODU4YTUxMzViZTc3NGFkNjcyZWIyMzZiYjY1YWRmYzhjYjM0MjVlOCIKICAgIH0KICB9Cn0=",
		"ONYX" to "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MTg1MDQxOSwKICAicHJvZmlsZUlkIiA6ICI0NmNhODkyZTY4ODA0YThmYjFkYzkwYjg0ZTY5ZjVmZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJPbG8xNjA2IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzFjYTliMjg0MWY3MWJhNmZjNGQ2ZDJlOTdlODBjMTgwMWYzNmNhMzk1OGU1YTlhODFhNGY4Nzg1ZjY0MzQzNjciCiAgICB9CiAgfQp9",
		"OPAL" to "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA0MjA1OTQ4NywKICAicHJvZmlsZUlkIiA6ICI1ZTdmY2RjYTU5YzI0NjkwODAwNjg4OTNkODU1ODM3NCIsCiAgInByb2ZpbGVOYW1lIiA6ICJKYWVsbGFyaSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS81ZDE1ZWQ3MGU3MjAwNDBhZDczMTFlNjkzNTlkZmRmNWUxMTRlYWRkMmE0YzFmOTcxYTk1MDEzNDFhNDUyNjRiIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=",
		"PERIDOT" to "ewogICJ0aW1lc3RhbXAiIDogMTcwNzY0MjI2OTYzMSwKICAicHJvZmlsZUlkIiA6ICIxZDIyYmUxYmQ2YTM0NWQ0OTI0Nzc4YmM4YWFlMjYzMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJNQVJTX0hYIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE4ODRhOGRjYjcxMjgzNDFjZTA5Y2IzNjE2YzExYzNlNDZmODBkMDgzYTZmMmRiYjRkYmUwYmI5MzIzMThiMDkiCiAgICB9CiAgfQp9"
	)

	@JvmStatic
	fun clearCache() {
		ItemStackCache.clear()
		SkyBlockItemIconCache.clear()
	}

	@JvmStatic
	fun customIcon(skyblockId: String?): Identifier? {
		if (skyblockId.isNullOrBlank()) {
			return null
		}
		return SkyBlockItemIconCache.getOrRequest(itemModel(skyblockId))
	}

	@JvmStatic
	fun customIconModel(skyblockId: String?, itemModel: String?): Identifier? {
		if (itemModel.isNullOrBlank()) {
			return customIcon(skyblockId)
		}
		if (!skyblockId.isNullOrBlank()) {
			ItemStackCache.rememberItemModel(skyblockId.uppercase(Locale.ROOT), itemModel)
		}
		return SkyBlockItemIconCache.getOrRequest(itemModel)
	}

	@JvmStatic
	fun customIconSize(skyblockId: String?): Int {
		if (skyblockId == null) {
			return 16
		}
		return SkyBlockItemIconCache.textureSize(ItemStackCache.getItemModel(skyblockId.uppercase(Locale.ROOT)))
	}

	@JvmStatic
	fun itemModel(skyblockId: String?): String? {
		if (skyblockId.isNullOrBlank()) {
			return null
		}
		val key = skyblockId.uppercase(Locale.ROOT)
		ItemStackCache.getItemModel(key)?.let { return it }
		ItemStackCache.rememberItemModel(key, HypixelItemResolver.resolveItemModel(key))
		return ItemStackCache.getItemModel(key)
	}

	private fun snapshotIds(snapshot: InventorySnapshot): MutableSet<String> {
		val ids = HashSet<String>()
		collectIds(snapshot.inventory, ids)
		snapshot.enderChest.forEach { collectIds(it, ids) }
		snapshot.backpacks.forEach { collectIds(it, ids) }
		snapshot.wardrobe.forEach { collectIds(it, ids) }
		snapshot.equipmentWardrobe.forEach { collectIds(it, ids) }
		snapshot.loadouts.forEach { collectLoadoutIds(it, ids) }
		snapshot.sacks.forEach { collectIds(it, ids) }
		collectIds(snapshot.fishingBag, ids)
		collectIds(snapshot.potionBag, ids)
		collectIds(snapshot.quiver, ids)
		snapshot.accessoryBag.forEach { collectIds(it, ids) }
		collectIds(snapshot.timePocket, ids)
		collectIds(snapshot.personalVault, ids)
		collectIds(snapshot.carnivalMasks, ids)
		collectIds(snapshot.candyBag, ids)
		return ids
	}

	@JvmStatic
	fun warmBlocking(snapshot: InventorySnapshot?) {
		NeuRepoCache.ensureLoadedBlocking()
		SkyBlockPackCache.ensureReadyBlocking()
		prefetch(snapshot)
		if (snapshot == null) {
			return
		}
		for (id in snapshotIds(snapshot)) {
			for (candidate in NeuItemResolver.candidates(id)) {
				if (NeuRepoCache.get(candidate) == null) {
					NeuRepoCache.getOrFetch(candidate)
				}
			}
			baseStack(id)
			customIcon(id)
		}
	}

	@JvmStatic
	fun warmAsync(snapshot: InventorySnapshot?) {
		prefetch(snapshot)
		HypixelApiClient.parseExecutor().execute {
			try {
				warmBlocking(snapshot)
			} catch (ignored: Exception) {
			}
		}
	}

	@JvmStatic
	fun warmPetsAsync(pets: PetSnapshot?) {
		prefetchPets(pets)
		HypixelApiClient.parseExecutor().execute {
			try {
				warmPetsBlocking(pets)
			} catch (ignored: Exception) {
			}
		}
	}

	@JvmStatic
	fun prefetchPets(pets: PetSnapshot?) {
		if (pets == null || pets.isEmpty()) {
			return
		}
		val ids = HashSet<String>()
		for (pet in pets.pets) {
			if (pet.neuId.isNotBlank()) {
				ids.add(pet.neuId)
			}
			if (pet.hasHeldItem()) {
				ids.add(pet.heldItem)
			}
		}
		prefetchIds(ids)
	}

	@JvmStatic
	fun prefetchIds(ids: Collection<String?>?) {
		if (ids.isNullOrEmpty()) {
			return
		}
		SkyBlockItemIconCache.ensurePack()
		val neuIds = HashSet<String>()
		for (id in ids) {
			if (id.isNullOrBlank()) {
				continue
			}
			neuIds.addAll(NeuItemResolver.candidates(id))
		}
		NeuRepoCache.prefetch(neuIds)
	}

	private fun warmPetsBlocking(pets: PetSnapshot?) {
		if (pets == null || pets.isEmpty()) {
			return
		}
		for (pet in pets.pets) {
			if (pet.neuId.isNotBlank()) {
				baseStack(pet.neuId)
				customIcon(pet.neuId)
			}
			if (pet.hasHeldItem()) {
				baseStack(pet.heldItem)
				customIcon(pet.heldItem)
			}
		}
	}

	@JvmStatic
	fun prefetch(snapshot: InventorySnapshot?) {
		if (snapshot == null) {
			return
		}
		val ids = snapshotIds(snapshot)
		ids.add("JUMBO_BACKPACK")
		ids.add("POCKET_SACK_IN_A_SACK")
		ids.add("LARGE_POTION_BAG")
		ids.add("LARGE_TALISMAN_BAG")
		ids.addAll(NeuRepoCache.sackItemIds())
		SkyBlockItemIconCache.ensurePack()
		val neuIds = HashSet<String>()
		for (id in ids) {
			neuIds.addAll(NeuItemResolver.candidates(id))
		}
		NeuRepoCache.prefetch(neuIds)
	}

	private fun collectIds(page: InventorySnapshot.Page?, ids: MutableSet<String>) {
		if (page == null) {
			return
		}
		for (slot in page.slots) {
			collectSlot(slot, ids)
		}
	}

	private fun collectSlot(slot: InventorySnapshot.Slot?, ids: MutableSet<String>) {
		val id = slot?.id
		if (slot != null && !slot.isEmpty() && id != null) {
			ids.add(id)
		}
	}

	private fun collectLoadoutIds(loadout: InventorySnapshot.Loadout?, ids: MutableSet<String>) {
		if (loadout == null) {
			return
		}
		loadout.equipment.forEach { collectSlot(it, ids) }
		loadout.armor.forEach { collectSlot(it, ids) }
		collectSlot(loadout.pet, ids)
	}

	@JvmStatic
	fun iconStack(skyblockId: String?): ItemStack {
		if (skyblockId.isNullOrBlank()) {
			return ItemStack.EMPTY
		}
		val key = skyblockId.uppercase(Locale.ROOT)
		if ("RUBY_VEILSHROOM" == key) {
			val veil = baseStack("VEILSHROOM")
			if (!veil.isEmpty && !veil.`is`(Items.PAPER)) {
				val copy = veil.copy()
				applySkyBlockIdentity(copy, key)
				return copy
			}
			val mushroom = ItemStack(Items.RED_MUSHROOM)
			applySkyBlockIdentity(mushroom, key)
			return mushroom
		}
		NeuRepoCache.prefetch(NeuItemResolver.candidates(skyblockId))
		val stack = baseStack(skyblockId).copy()
		applySkyBlockIdentity(stack, key)
		return stack
	}

	@JvmStatic
	fun hotmTabIcon(): ItemStack {
		val sky = iconStack("MINING_2_PORTAL")
		if (!sky.isEmpty && sky.`is`(Items.PLAYER_HEAD)) {
			return sky
		}
		val head = ItemStack(Items.PLAYER_HEAD)
		SkullTextureApplier.applyValue(head, HOTM_TAB_SKULL_VALUE, null)
		return head
	}

	@JvmStatic
	fun gemstoneHead(gem: String?): ItemStack {
		if (gem.isNullOrBlank()) {
			return ItemStack.EMPTY
		}
		val key = gem.trim().uppercase(Locale.ROOT).removeSuffix("_CRYSTAL").removeSuffix("_GEM")
		val value = FLAWLESS_GEM_SKULLS[key] ?: return ItemStack.EMPTY
		val head = ItemStack(Items.PLAYER_HEAD)
		SkullTextureApplier.applyValue(head, value, null)
		return head
	}

	@JvmStatic
	fun texturedHead(value: String?): ItemStack {
		if (value.isNullOrBlank()) {
			return ItemStack.EMPTY
		}
		val head = ItemStack(Items.PLAYER_HEAD)
		SkullTextureApplier.applyValue(head, value, null)
		return head
	}

	@JvmStatic
	fun trophySkullStack(id: String?): ItemStack {
		if (id.isNullOrBlank()) {
			return ItemStack.EMPTY
		}
		val value = TrophySkulls.value(id)
		if (value.isNullOrBlank()) {
			return ItemStack(Items.PLAYER_HEAD)
		}
		return texturedHead(value)
	}

	private fun styledName(name: String): Component =
		ItemTooltipEnricher.legacyText(name).copy().withStyle { it.withItalic(false) }

	private fun loreComponent(line: String?): Component =
		if (line.isNullOrBlank()) Component.empty() else EnchantTooltip.colorize(ItemTooltipEnricher.legacyText(line))

	@JvmStatic
	fun toStack(slot: InventorySnapshot.Slot?): ItemStack {
		if (slot == null || slot.isEmpty()) {
			return ItemStack.EMPTY
		}
		var stack = baseStack(slot.id).copy()
		val displayCount = if (slot.count > 64) 1 else maxOf(1, minOf(64, slot.count))
		stack.count = displayCount

		if (!slot.skullValue.isNullOrBlank()) {
			if (!stack.`is`(Items.PLAYER_HEAD)) {
				stack = ItemStack(Items.PLAYER_HEAD, stack.count)
			}
			SkullTextureApplier.applyValue(stack, slot.skullValue, slot.skullSignature)
		}
		val dye = slot.dyeColor
		if (dye != null && LegacyMaterialMap.isLeather(stack.item)) {
			stack.set(DataComponents.DYED_COLOR, DyedItemColor(dye and 0xFFFFFF))
		}

		var name = slot.displayName
		if (name.isNullOrBlank()) {
			val neu = NeuItemResolver.neuItem(slot.id)
			if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
				name = neu.get("displayname").asString.replace(LVL_PLACEHOLDER, "").trim()
			}
		}
		if (name.isNullOrBlank()) {
			val def = HypixelItemResolver.hypixelDef(slot.id)
			if (def != null && def.has("name") && def.get("name").isJsonPrimitive) {
				name = def.get("name").asString
				if (name != null && !name.contains("§") && def.has("tier") && def.get("tier").isJsonPrimitive) {
					name = ItemTextFormat.tierColorPrefix(def.get("tier").asString) + name
				}
			}
		}
		if (name.isNullOrBlank()) {
			name = ItemTextFormat.prettyId(NeuItemResolver.canonicalId(slot.id))
		}
		stack.set(DataComponents.CUSTOM_NAME, styledName(name))

		val loreLines = ArrayList<Component>()
		val lore = slot.lore
		if (!lore.isNullOrEmpty()) {
			for (raw in lore) {
				loreLines.add(loreComponent(ItemTooltipEnricher.cleanLoreLine(raw)))
			}
		} else {
			val petResolved = resolveAuctionPetIfNeeded(slot)
			val petLore = petResolved?.lore
			if (petResolved != null && !petLore.isNullOrEmpty()) {
				val petName = petResolved.displayName
				if (!petName.isNullOrBlank()) {
					stack.set(DataComponents.CUSTOM_NAME, styledName(petName))
				}
				for (raw in petLore) {
					loreLines.add(loreComponent(ItemTooltipEnricher.cleanLoreLine(raw)))
				}
			} else {
				val neu = NeuItemResolver.neuItem(slot.id)
				if (neu != null && neu.has("lore") && neu.get("lore").isJsonArray) {
					for (el in neu.getAsJsonArray("lore")) {
						if (el.isJsonPrimitive) {
							loreLines.add(loreComponent(el.asString))
						}
					}
				}
			}
		}
		ItemTooltipEnricher.applyRecombobulatorMarkers(loreLines, slot)
		if (loreLines.isNotEmpty()) {
			stack.set(DataComponents.LORE, ItemLore(loreLines))
		}
		applySkyBlockIdentity(stack, slot.id)
		return stack
	}

	@JvmStatic
	fun auctionPetSlot(tag: String?, displayName: String?, tier: String?): InventorySnapshot.Slot? {
		if (tag.isNullOrBlank()) {
			return null
		}
		val key = tag.uppercase(Locale.ROOT)
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null
		}
		val type = key.substring(4)
		if (type.isBlank()) {
			return null
		}
		var rarity = normalizeTier(tier)
		if (rarity.isBlank()) {
			rarity = normalizeTier(resolveTier(key))
		}
		if (rarity.isBlank()) {
			rarity = "LEGENDARY"
		}
		val tierIndex = petTierIndex(rarity)
		val neuId = "$type;$tierIndex"
		val level = parsePetLevel(displayName)
		val pretty = type.replace('_', ' ')
		val pet = PetSnapshot.Entry(
			type,
			pretty,
			rarity,
			tierIndex,
			neuId,
			level,
			maxOf(100, level),
			0.0,
			0.0,
			0.0,
			0.0,
			0f,
			false,
			"",
			0,
			"",
			"",
			0.0
		)
		var name: String? = PetLoreResolver.displayNameFor(pet)
		if (name.isNullOrBlank()) {
			name = if (displayName.isNullOrBlank()) pretty else displayName
		}
		return InventorySnapshot.Slot(neuId, 1, PetLoreResolver.loreFor(pet), name, null, null, null)
	}

	private fun resolveAuctionPetIfNeeded(slot: InventorySnapshot.Slot?): InventorySnapshot.Slot? {
		val id = slot?.id ?: return null
		val key = id.uppercase(Locale.ROOT)
		if (!key.startsWith("PET_") || key.startsWith("PET_ITEM_")) {
			return null
		}
		return auctionPetSlot(key, slot.displayName, "")
	}

	private fun petTierIndex(tier: String?): Int {
		if (tier == null) {
			return 3
		}
		return when (normalizeTier(tier)) {
			"COMMON" -> 0
			"UNCOMMON" -> 1
			"RARE" -> 2
			"EPIC" -> 3
			"LEGENDARY" -> 4
			"MYTHIC" -> 5
			else -> 4
		}
	}

	private fun parsePetLevel(displayName: String?): Int {
		if (displayName.isNullOrBlank()) {
			return 1
		}
		val level = PET_LEVEL.find(displayName)?.groupValues?.get(1)?.toIntOrNull() ?: return 1
		return maxOf(1, level)
	}

	@JvmStatic
	fun tooltipLines(slot: InventorySnapshot.Slot?, rendered: ItemStack): MutableList<Component> =
		ItemTooltipEnricher.tooltipLines(slot, rendered, true)

	@JvmStatic
	fun tooltipLines(
		slot: InventorySnapshot.Slot?,
		rendered: ItemStack,
		includeEstimatedValue: Boolean
	): MutableList<Component> = ItemTooltipEnricher.tooltipLines(slot, rendered, includeEstimatedValue)

	@JvmStatic
	fun applySkyBlockIdentity(stack: ItemStack?, skyblockId: String?) {
		if (stack == null || stack.isEmpty || skyblockId.isNullOrBlank()) {
			return
		}
		val id = skyblockId.uppercase(Locale.ROOT)
		CustomData.update(DataComponents.CUSTOM_DATA, stack) { tag ->
			tag.putString("id", id)
			val extra = CompoundTag()
			extra.putString("id", id)
			tag.put("ExtraAttributes", extra)
		}
	}

	private fun baseStack(skyblockId: String?): ItemStack {
		if (skyblockId.isNullOrBlank()) {
			return ItemStack(Items.PAPER)
		}
		return try {
			resolveBaseStack(skyblockId)
		} catch (exception: RuntimeException) {
			// Bad repo / API data must not crash a GUI frame; icons are rebuilt every frame, so warn once per id.
			if (FAILED_ICON_IDS.add(skyblockId)) {
				ProfileViewer.LOGGER.warn("[FishPV] Icon fallback for {}: {}", skyblockId, exception.toString())
			}
			ItemStack(Items.PAPER)
		}
	}

	private fun resolveBaseStack(skyblockId: String): ItemStack {
		val key = skyblockId.uppercase(Locale.ROOT)
		for (candidate in NeuItemResolver.candidates(skyblockId)) {
			val cached = ItemStackCache.getBase(candidate)
			if (cached != null) {
				ItemStackCache.copyItemModel(candidate, key)
				return cached
			}
			val built = NeuItemResolver.buildFromNeu(candidate)
			if (built != null) {
				ItemStackCache.putBase(candidate, built.copy())
				if (key != candidate) {
					ItemStackCache.putBase(key, built.copy())
				}
				ItemStackCache.copyItemModel(candidate, key)
				return built
			}
		}
		// Don't cache Hypixel fallbacks - NEU may still be downloading.
		return HypixelItemResolver.buildFromHypixel(key)
	}

	@JvmStatic
	fun tierArgb(tier: String?): Int = when (normalizeTier(tier)) {
		"COMMON" -> 0xFFFFFFFF.toInt()
		"UNCOMMON" -> 0xFF55FF55.toInt()
		"RARE" -> 0xFF5555FF.toInt()
		"EPIC" -> 0xFFAA00AA.toInt()
		"LEGENDARY" -> 0xFFFFAA00.toInt()
		"MYTHIC" -> 0xFFFF55FF.toInt()
		"DIVINE" -> 0xFF55FFFF.toInt()
		"SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555.toInt()
		"ULTIMATE" -> 0xFFAA0000.toInt()
		else -> PvDraw.COLOR_TEXT
	}

	@JvmStatic
	fun tierArgbFromFormattedName(formattedName: String?): Int =
		tierArgb(ItemTextFormat.tierFromFormattingPrefix(formattedName))

	@JvmStatic
	fun neuTier(skyblockId: String?): String {
		val neu = NeuItemResolver.neuItem(skyblockId) ?: return ""
		if (neu.has("tier") && neu.get("tier").isJsonPrimitive) {
			val tier = normalizeTier(neu.get("tier").asString)
			if (tier.isNotBlank()) {
				return tier
			}
		}
		return NeuItemResolver.tierFromNeuItem(neu)
	}

	/**
	 * Resolve rarity from Hypixel items definitions, then NEU.
	 * Cofl pet tags (`PET_JELLYFISH`) omit rarity - do not guess from `TYPE;n` NEU files.
	 */
	@JvmStatic
	fun resolveTier(skyblockId: String?): String {
		if (skyblockId.isNullOrBlank()) {
			return ""
		}
		val key = skyblockId.uppercase(Locale.ROOT)
		val petAuctionTag = key.startsWith("PET_") && !key.startsWith("PET_ITEM_")
		val hypixelTier = HypixelItemResolver.hypixelTier(skyblockId)
		if (hypixelTier.isNotBlank()) {
			return hypixelTier
		}
		if (!petAuctionTag) {
			val neu = neuTier(skyblockId)
			if (neu.isNotBlank()) {
				return neu
			}
		}
		return ""
	}

	@JvmStatic
	fun normalizeTier(tier: String?): String {
		if (tier.isNullOrBlank()) {
			return ""
		}
		val t = ItemTextFormat.stripFormatting(tier).trim().uppercase(Locale.ROOT).replace(' ', '_')
		if (t.startsWith("VERY_SPECIAL")) {
			return "VERY_SPECIAL"
		}
		return when (t) {
			"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE" -> t
			else -> NeuItemResolver.RARITY_WORD.find(t)?.groupValues?.get(1)?.uppercase(Locale.ROOT)?.replace(' ', '_') ?: ""
		}
	}

	@JvmStatic
	fun plainDisplayName(skyblockId: String?): String {
		if (skyblockId.isNullOrBlank()) {
			return ""
		}
		val neu = NeuItemResolver.neuItem(skyblockId)
		if (neu != null && neu.has("displayname") && neu.get("displayname").isJsonPrimitive) {
			val name: String? = neu.get("displayname").asString
			if (!name.isNullOrBlank()) {
				return ItemTextFormat.stripFormatting(name.replace(LVL_PLACEHOLDER, "").trim())
			}
		}
		val hypixelName = HypixelItemResolver.hypixelDisplayName(skyblockId)
		if (!hypixelName.isNullOrBlank()) {
			return ItemTextFormat.stripFormatting(hypixelName)
		}
		return ItemTextFormat.prettyId(NeuItemResolver.canonicalId(skyblockId))
	}

	@JvmStatic
	fun isTexturedPlayerHead(stack: ItemStack?): Boolean = SkullTextureApplier.isTexturedPlayerHead(stack)

	@JvmStatic
	fun legacyLine(text: String?): Component = ItemTooltipEnricher.legacyLine(text)
}
