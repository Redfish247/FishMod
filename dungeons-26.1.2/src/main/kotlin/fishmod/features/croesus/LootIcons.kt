package fishmod.features.croesus

import fishmod.utils.networth.ItemsDb
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile

// SkyBlock id -> display ItemStack, built from the Hypixel items DB (material + skull skin)
object LootIcons {

    private val cache = HashMap<String, ItemStack?>()

    private val LEGACY = mapOf(
        "SKULL_ITEM" to "player_head", "WOOD_SWORD" to "wooden_sword", "WOOD_AXE" to "wooden_axe",
        "WOOD_PICKAXE" to "wooden_pickaxe", "WOOD_SPADE" to "wooden_shovel", "WOOD_HOE" to "wooden_hoe",
        "GOLD_SWORD" to "golden_sword", "GOLD_AXE" to "golden_axe", "GOLD_PICKAXE" to "golden_pickaxe",
        "GOLD_SPADE" to "golden_shovel", "GOLD_HOE" to "golden_hoe", "GOLD_HELMET" to "golden_helmet",
        "GOLD_CHESTPLATE" to "golden_chestplate", "GOLD_LEGGINGS" to "golden_leggings", "GOLD_BOOTS" to "golden_boots",
        "IRON_SPADE" to "iron_shovel", "STONE_SPADE" to "stone_shovel", "DIAMOND_SPADE" to "diamond_shovel",
        "RAW_FISH" to "cod", "COOKED_FISH" to "cooked_cod", "EMPTY_MAP" to "map", "RED_ROSE" to "poppy",
        "YELLOW_FLOWER" to "dandelion", "WATCH" to "clock", "EXP_BOTTLE" to "experience_bottle",
        "SULPHUR" to "gunpowder", "EYE_OF_ENDER" to "ender_eye", "CARROT_STICK" to "carrot_on_a_stick",
        "CARROT_ITEM" to "carrot", "POTATO_ITEM" to "potato", "NETHER_STALK" to "nether_wart",
        "MONSTER_EGG" to "zombie_spawn_egg", "FIREWORK" to "firework_rocket", "FIREWORK_CHARGE" to "firework_star",
        "ENDER_STONE" to "end_stone", "WATER_LILY" to "lily_pad", "SPECKLED_MELON" to "glistering_melon_slice",
        "MELON" to "melon_slice", "MELON_BLOCK" to "melon", "GRILLED_PORK" to "cooked_porkchop", "PORK" to "porkchop",
        "RAW_CHICKEN" to "chicken", "RAW_BEEF" to "beef", "SNOW_BALL" to "snowball", "WEB" to "cobweb",
        "WOOL" to "white_wool", "CARPET" to "white_carpet", "STAINED_CLAY" to "white_terracotta",
        "HARD_CLAY" to "terracotta", "STAINED_GLASS" to "white_stained_glass",
        "STAINED_GLASS_PANE" to "white_stained_glass_pane", "THIN_GLASS" to "glass_pane", "LOG" to "oak_log",
        "LOG_2" to "acacia_log", "WOOD" to "oak_planks", "LEAVES" to "oak_leaves", "LEAVES_2" to "acacia_leaves",
        "SAPLING" to "oak_sapling", "STEP" to "smooth_stone_slab", "WOOD_STEP" to "oak_slab",
        "DOUBLE_PLANT" to "sunflower", "LONG_GRASS" to "short_grass", "SMOOTH_BRICK" to "stone_bricks",
        "IRON_BARDING" to "iron_horse_armor", "GOLD_BARDING" to "golden_horse_armor",
        "DIAMOND_BARDING" to "diamond_horse_armor", "GOLD_RECORD" to "music_disc_13",
        "GREEN_RECORD" to "music_disc_cat", "RECORD_3" to "music_disc_blocks", "RECORD_4" to "music_disc_chirp",
        "RECORD_5" to "music_disc_far", "RECORD_6" to "music_disc_mall", "RECORD_7" to "music_disc_mellohi",
        "RECORD_8" to "music_disc_stal", "RECORD_9" to "music_disc_strad", "RECORD_10" to "music_disc_ward",
        "RECORD_11" to "music_disc_11", "RECORD_12" to "music_disc_wait", "IRON_PLATE" to "heavy_weighted_pressure_plate",
        "GOLD_PLATE" to "light_weighted_pressure_plate", "REDSTONE_LAMP_OFF" to "redstone_lamp",
        "REDSTONE_TORCH_ON" to "redstone_torch", "REDSTONE_COMPARATOR" to "comparator", "DIODE" to "repeater",
        "MYCEL" to "mycelium", "HUGE_MUSHROOM_1" to "brown_mushroom_block", "HUGE_MUSHROOM_2" to "red_mushroom_block",
        "NETHER_BRICK_ITEM" to "nether_brick", "NETHER_BRICK" to "nether_bricks", "CLAY_BRICK" to "brick",
        "BRICK" to "bricks", "WORKBENCH" to "crafting_table", "ENCHANTMENT_TABLE" to "enchanting_table",
        "ENDER_PORTAL_FRAME" to "end_portal_frame", "MOB_SPAWNER" to "spawner", "PISTON_BASE" to "piston",
        "PISTON_STICKY_BASE" to "sticky_piston", "BOOK_AND_QUILL" to "writable_book", "LEASH" to "lead",
        "STORAGE_MINECART" to "chest_minecart", "POWERED_MINECART" to "furnace_minecart",
        "EXPLOSIVE_MINECART" to "tnt_minecart", "BOAT" to "oak_boat", "RAILS" to "rail", "COMMAND" to "command_block",
        "FENCE" to "oak_fence", "FENCE_GATE" to "oak_fence_gate", "IRON_FENCE" to "iron_bars",
        "NETHER_FENCE" to "nether_brick_fence", "TRAP_DOOR" to "oak_trapdoor", "WOOD_DOOR" to "oak_door",
        "WOOD_STAIRS" to "oak_stairs", "WOOD_BUTTON" to "oak_button", "WOOD_PLATE" to "oak_pressure_plate",
        "STONE_PLATE" to "stone_pressure_plate", "COBBLE_WALL" to "cobblestone_wall", "SMOOTH_STAIRS" to "stone_brick_stairs",
        "BIRCH_WOOD_STAIRS" to "birch_stairs", "SPRUCE_WOOD_STAIRS" to "spruce_stairs", "JUNGLE_WOOD_STAIRS" to "jungle_stairs",
        "SPRUCE_DOOR_ITEM" to "spruce_door", "BIRCH_DOOR_ITEM" to "birch_door", "JUNGLE_DOOR_ITEM" to "jungle_door",
        "ACACIA_DOOR_ITEM" to "acacia_door", "DARK_OAK_DOOR_ITEM" to "dark_oak_door", "FLOWER_POT_ITEM" to "flower_pot",
        "CAULDRON_ITEM" to "cauldron", "BREWING_STAND_ITEM" to "brewing_stand", "BED" to "red_bed",
        "BANNER" to "white_banner", "SEEDS" to "wheat_seeds", "MUSHROOM_SOUP" to "mushroom_stew",
        "GRASS" to "grass_block", "QUARTZ" to "quartz", "QUARTZ_ORE" to "nether_quartz_ore",
    )

    @JvmStatic
    fun icon(id: String?): ItemStack? {
        if (id.isNullOrEmpty()) return null
        if (id.startsWith("ENCHANTMENT_")) return cache.getOrPut(id) { ItemStack(Items.ENCHANTED_BOOK) }
        cache[id]?.let { return it }
        if (!ItemsDb.isLoaded()) { ItemsDb.ensureLoaded(); return null }
        val built = runCatching { build(id) }.getOrNull()
        cache[id] = built
        return built
    }

    private fun build(id: String): ItemStack? {
        val o = ItemsDb.get(id) ?: return null
        val material = o.get("material")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val dur = o.get("durability")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        val item = itemFor(material, dur) ?: return null
        val stack = ItemStack(item)
        if (item == Items.PLAYER_HEAD) {
            val skin = o.getAsJsonObject("skin")?.get("value")?.asString ?: return null
            val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", skin))
            val profile = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(id.toByteArray()), "fmloot", com.mojang.authlib.properties.PropertyMap(props))
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        }
        o.get("color")?.takeIf { it.isJsonPrimitive }?.asString?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
            ?.takeIf { it.size == 3 }?.let { (r, g, b) -> stack.set(DataComponents.DYED_COLOR, DyedItemColor((r shl 16) or (g shl 8) or b)) }
        if (o.get("glowing")?.asBoolean == true) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        return stack
    }

    private fun itemFor(material: String, dur: Int): Item? {
        val path = if (material == "INK_SACK") DyeColor.byId(15 - dur.coerceIn(0, 15)).serializedName + "_dye"
            else LEGACY[material] ?: material.lowercase()
        val key = Identifier.tryParse("minecraft:$path") ?: return null
        return BuiltInRegistries.ITEM.getOptional(key).orElse(null)?.takeIf { it != Items.AIR }
    }
}
