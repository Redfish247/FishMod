package fishmod

import fishmod.features.fishing.RareDropKind
import fishmod.features.fishing.RareDrops
import fishmod.features.fishing.SeaCreatures
import fishmod.features.fishing.SeaRarity
import fishmod.utils.SkyblockLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Sample lines come from Feesh and are UNVERIFIED in game.
class FishingRegexTest {
    @BeforeEach fun noPrices() { RareDrops.priceFn = { 1000.0 } }

    @Test fun tableSize() = assertEquals(88, SeaCreatures.ALL.size)

    // Un-escape each regex into a sample line: it must match itself and contain its needle.
    @Test fun everyCreatureMatchesItsOwnLine() {
        for (sc in SeaCreatures.ALL) {
            val sample = sc.regex.pattern.removePrefix("^").removeSuffix("$").replace(Regex("""\\(.)"""), "$1")
            assertTrue(sample.contains(sc.needle), "needle of ${sc.name}")
            assertTrue(sc.regex.matches(sample), "regex of ${sc.name}: $sample")
            val any = SeaCreatures.match(sample, SkyblockLocation.UNKNOWN, true)
            assertNotNull(any, sc.name)
        }
    }

    @Test fun sampleCatches() {
        val jaw = SeaCreatures.match("You have angered a legendary creature... Lord Jawbus has arrived.", SkyblockLocation.CRIMSON_ISLE, false)!!
        assertEquals("Lord Jawbus", jaw.name); assertEquals(SeaRarity.MYTHIC, jaw.rarity); assertTrue(jaw.rare && jaw.boss)
        val thunder = SeaCreatures.match("You hear a massive rumble as Thunder emerges.", SkyblockLocation.CRIMSON_ISLE, false)!!
        assertEquals(5000L, thunder.immunityMs)
        assertEquals("Squid", SeaCreatures.match("A Squid appeared.", SkyblockLocation.HUB, false)!!.name)
    }

    @Test fun areaAndPlayerChatRejected() {
        assertNull(SeaCreatures.match("You hear a massive rumble as Thunder emerges.", SkyblockLocation.HUB, false))
        assertNull(SeaCreatures.match("[MVP+] Foo: A Squid appeared.", SkyblockLocation.HUB, false))
        assertNull(SeaCreatures.match("Party > Foo: Thunder", SkyblockLocation.CRIMSON_ISLE, false))
    }

    @Test fun rareDrops() {
        val vial = RareDrops.parse("RARE DROP! Radioactive Vial (+236 ✯ Magic Find)", "Me")!!
        assertEquals(RareDropKind.ITEM, vial.kind); assertEquals("RADIOACTIVE_VIAL", vial.itemId)
        assertEquals(236, vial.magicFind); assertTrue(vial.extreme); assertEquals(1000.0, vial.price)
        val plain = RareDrops.parse("RARE DROP! Magma Core", "Me")!!
        assertEquals(0, plain.magicFind); assertEquals("MAGMA_CORE", plain.itemId)
        val book = RareDrops.parse("RARE DROP! Enchanted Book (Flash 1) (+392 ✯ Magic Find)", "Me")!!
        assertEquals(RareDropKind.BOOK, book.kind); assertEquals("ENCHANTMENT_ULTIMATE_FLASH_1", book.itemId); assertEquals(392, book.magicFind)
        val pet = RareDrops.parse("PET DROP! LEGENDARY Megalodon", "Me")!!
        assertEquals(RareDropKind.PET, pet.kind); assertEquals("MEGALODON;4", pet.itemId); assertEquals(SeaRarity.LEGENDARY, pet.petRarity)
        assertEquals("Baby Yeti", RareDrops.parse("PET DROP! Baby Yeti", "Me")!!.itemName)
    }

    @Test fun dyeAndPhoenixNeedOwnName() {
        val line = "WOW! [MVP+] Me found an Aquamarine Dye #95!"
        assertEquals("DYE_AQUAMARINE", RareDrops.parse(line, "Me")!!.itemId)
        assertNull(RareDrops.parse(line, "Other"))
        assertNotNull(RareDrops.parse("Wow! [MVP+] Me found a Phoenix pet!", "Me"))
        assertNull(RareDrops.parse("Wow! [MVP+] Someone found a Phoenix pet!", "Me"))
    }

    @Test fun notDrops() {
        assertNull(RareDrops.parse("A Squid appeared.", "Me"))
        assertNull(RareDrops.parse("[MVP+] Foo: RARE DROP! Magma Core", "Me"))
        assertNull(RareDrops.parse("", "Me"))
    }
}
