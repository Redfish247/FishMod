package fishmod

import fishmod.features.general.RareDropAnimation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RareDropAnimationTest {
    @Test fun parsesDropLines() {
        assertEquals("Radioactive Vial", RareDropAnimation.dropName("RARE DROP! Radioactive Vial (+236 ✯ Magic Find)"))
        assertEquals("Scythe Blade", RareDropAnimation.dropName("VERY RARE DROP! Scythe Blade"))
        assertEquals("Flash 1", RareDropAnimation.dropName("RARE DROP! Enchanted Book (Flash 1) (+392 ✯ Magic Find)"))
        assertEquals("Megalodon", RareDropAnimation.dropName("PET DROP! LEGENDARY Megalodon"))
        assertEquals("Baby Yeti", RareDropAnimation.dropName("PET DROP! Baby Yeti"))
        assertNull(RareDropAnimation.dropName("[MVP+] Foo: RARE DROP! Magma Core"))
        assertNull(RareDropAnimation.dropName("You found a thing"))
    }

    @Test fun parsesDyeAndNumerals() {
        assertEquals("Carmine Dye", RareDropAnimation.dropName("WOW! [MVP+] Me found a Carmine Dye #12!", "Me"))
        assertNull(RareDropAnimation.dropName("WOW! [MVP+] Other found a Carmine Dye #12!", "Me"))
        assertEquals(RareDropAnimation.norm("Smite VII"), RareDropAnimation.norm("Smite 7"))
        assertEquals("smarty pants 1", RareDropAnimation.norm("Smarty Pants I"))
        assertEquals("flash 1", RareDropAnimation.norm("Flash 1"))
        assertEquals("the one 4", RareDropAnimation.norm("The One IV"))
    }

    @Test fun ignoresPlayerChatImitations() {
        for (l in listOf("Foo: RARE DROP! Warden Heart", "[MVP+] Foo: PET DROP! Baby Yeti", "Party > [MVP+] Foo: RARE DROP! Warden Heart",
            "From [VIP] Foo: WOW! Me found a Carmine Dye #1!", "Guild > Foo: PRAY TO RNGESUS DROP! Warden Heart", "Co-op > Foo [CHAMP]: RARE DROP! Magma Core"))
            assertNull(RareDropAnimation.dropName(l, "Me"), l)
    }
}
