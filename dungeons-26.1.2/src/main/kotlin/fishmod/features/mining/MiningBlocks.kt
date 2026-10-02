package fishmod.features.mining

import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.StainedGlassBlock
import net.minecraft.world.level.block.StainedGlassPaneBlock
import net.minecraft.world.level.block.state.BlockState

// Hypixel ore -> vanilla block mapping (SkyHanni OreBlock is the reference)
enum class Gem(val display: String, val dye: DyeColor, val code: String, val rgb: Int) {
    RUBY("Ruby", DyeColor.RED, "§c", 0xFF5555),
    AMBER("Amber", DyeColor.ORANGE, "§6", 0xFFAA00),
    AMETHYST("Amethyst", DyeColor.PURPLE, "§5", 0xAA00AA),
    JADE("Jade", DyeColor.LIME, "§a", 0x55FF55),
    SAPPHIRE("Sapphire", DyeColor.LIGHT_BLUE, "§b", 0x55FFFF),
    TOPAZ("Topaz", DyeColor.YELLOW, "§e", 0xFFFF55),
    JASPER("Jasper", DyeColor.MAGENTA, "§d", 0xFF55FF),
    OPAL("Opal", DyeColor.WHITE, "§f", 0xFFFFFF),
    AQUAMARINE("Aquamarine", DyeColor.BLUE, "§9", 0x5555FF),
    CITRINE("Citrine", DyeColor.BROWN, "§6", 0x8B5A2B),
    ONYX("Onyx", DyeColor.BLACK, "§8", 0x555555),
    PERIDOT("Peridot", DyeColor.GREEN, "§2", 0x00AA00);

    companion object {
        fun byName(s: String) = entries.firstOrNull { it.display.equals(s, true) }
        fun of(state: BlockState): Gem? {
            val dye = when (val b = state.block) {
                is StainedGlassBlock -> b.color
                is StainedGlassPaneBlock -> b.color
                else -> return null
            }
            return entries.firstOrNull { it.dye == dye }
        }
    }
}

// Pity multipliers from SkyHanni MineshaftPityDisplay.PityBlock (max counter 2000)
enum class PityBlock(val display: String, val weight: Int) {
    MITHRIL("Mithril", 2), GEMSTONE("Low Tier Gemstone", 8), HIGH_GEMSTONE("High Tier Gemstone", 10),
    GLACITE("Glacite", 4), TUNGSTEN("Tungsten", 4), UMBER("Umber", 4), TITANIUM("Titanium", 8);
}

object MiningBlocks {

    private val MITHRIL = setOf<Block>(Blocks.GRAY_WOOL, Blocks.CYAN_TERRACOTTA, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
        Blocks.DARK_PRISMARINE, Blocks.LIGHT_BLUE_WOOL)
    private val UMBER = setOf<Block>(Blocks.TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.SMOOTH_RED_SANDSTONE)
    private val TUNGSTEN = setOf<Block>(Blocks.INFESTED_COBBLESTONE, Blocks.CLAY, Blocks.COBBLESTONE)
    private val HIGH_GEMS = setOf(Gem.AQUAMARINE, Gem.CITRINE, Gem.ONYX, Gem.PERIDOT)

    fun pity(state: BlockState): PityBlock? {
        val b = state.block
        Gem.of(state)?.let { return if (it in HIGH_GEMS) PityBlock.HIGH_GEMSTONE else PityBlock.GEMSTONE }
        return when {
            b in MITHRIL -> PityBlock.MITHRIL
            b == Blocks.PACKED_ICE -> PityBlock.GLACITE
            b in TUNGSTEN && b != Blocks.COBBLESTONE -> PityBlock.TUNGSTEN
            b in UMBER -> PityBlock.UMBER
            b == Blocks.POLISHED_DIORITE -> PityBlock.TITANIUM
            else -> null
        }
    }

    // Short material name for overlays
    fun material(state: BlockState): String {
        val b = state.block
        Gem.of(state)?.let { return it.display }
        return when {
            b in MITHRIL -> "Mithril"
            b == Blocks.POLISHED_DIORITE -> "Titanium"
            b == Blocks.PACKED_ICE -> "Glacite"
            b in UMBER -> "Umber"
            b in TUNGSTEN -> "Tungsten"
            else -> "Hard Stone"
        }
    }

    // Skyblocker PickobulusHelper: what Pickobulus can break outside the Hollows/Glacite
    val CONVERT_INTO_BEDROCK = setOf<Block>(
        Blocks.STONE, Blocks.COBBLESTONE, Blocks.POLISHED_DIORITE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS,
        Blocks.DARK_PRISMARINE, Blocks.CYAN_TERRACOTTA, Blocks.LIGHT_BLUE_WOOL, Blocks.GRAY_WOOL, Blocks.LAPIS_BLOCK,
        Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.REDSTONE_BLOCK,
        Blocks.COAL_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.LAPIS_ORE,
        Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK,
        Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.END_STONE,
    )
    val TUNNEL_BREAKABLE = setOf<Block>(
        Blocks.PACKED_ICE, Blocks.POLISHED_DIORITE, Blocks.INFESTED_STONE, Blocks.LIGHT_GRAY_CARPET, Blocks.PRISMARINE,
        Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE, Blocks.LIGHT_BLUE_WOOL, Blocks.GRAY_WOOL, Blocks.CYAN_TERRACOTTA,
        Blocks.BROWN_TERRACOTTA, Blocks.TERRACOTTA, Blocks.SMOOTH_RED_SANDSTONE, Blocks.INFESTED_COBBLESTONE, Blocks.CLAY,
    )
}
