package fishmod

import fishmod.features.fishing.FishingAlertText
import fishmod.features.fishing.FishingAlertText.TrophyRarity
import fishmod.features.fishing.SeaCreatures
import fishmod.utils.SkyblockLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Sample lines come from Feesh and are UNVERIFIED in game.
class FishingAlertTextTest {
    @Test fun trophyFish() {
        val t = FishingAlertText.trophy("⛃ TROPHY FISH! You caught a Sulphur Skitter BRONZE!")!!
        assertEquals("Sulphur Skitter", t.name)
        assertEquals(TrophyRarity.BRONZE, t.rarity)
        assertFalse(t.frog)
    }

    @Test fun trophyFrogAn() {
        val t = FishingAlertText.trophy("⛃ TROPHY FROG! You caught an Obfuscated 1 DIAMOND!")!!
        assertTrue(t.frog)
        assertEquals(TrophyRarity.DIAMOND, t.rarity)
    }

    @Test fun trophyRejectsOthers() {
        assertNull(FishingAlertText.trophy("⛃ GOOD CATCH! You caught a Rusty Coin!"))
        assertNull(FishingAlertText.trophy("Party > Bob: TROPHY FISH! You caught a Gusher GOLD!"))
        assertFalse(FishingAlertText.isTrophyLine("hello"))
    }

    @Test fun discovery() {
        assertEquals("Gusher", FishingAlertText.newDiscovery("NEW DISCOVERY: Gusher", true))
        assertNull(FishingAlertText.newDiscovery("NEW DISCOVERY: Gusher", false))
        assertNotNull(FishingAlertText.newDiscovery("NEW DISCOVERY: Obfuscated-1", false))
        assertNull(FishingAlertText.newDiscovery("Something else", true))
    }

    @Test fun wormhole() {
        assertTrue(FishingAlertText.isWormholeGone("Your Wormhole closed up..."))
        assertFalse(FishingAlertText.isWormholeGone("Your Wormhole closed up"))
    }

    @Test fun hotspotName() {
        assertTrue(FishingAlertText.isHotspotName("HOTSPOT"))
        assertFalse(FishingAlertText.isHotspotName("HOTSPOTS"))
    }

    @Test fun seconds() {
        assertEquals(62, FishingAlertText.parseSeconds("1m 02s"))
        assertEquals(59, FishingAlertText.parseSeconds("59s"))
        assertEquals(3720, FishingAlertText.parseSeconds("1h 2m"))
        assertNull(FishingAlertText.parseSeconds("soon"))
    }

    @Test fun timerDeployables() {
        assertEquals("Umberella" to 25, FishingAlertText.timerDeployable("Umberella 25s"))
        assertEquals("Mana Flux" to 90, FishingAlertText.timerDeployable("Mana Flux 90s"))
        assertNull(FishingAlertText.timerDeployable("Zombie 20s"))
        assertNull(FishingAlertText.timerDeployable("Umberella"))
    }

    @Test fun bait() {
        assertEquals(1234, FishingAlertText.baitRemaining("Bait Remaining: 1,234"))
        assertNull(FishingAlertText.baitRemaining("Something: 5"))
    }

    @Test fun compactLines() {
        assertEquals("§b§lDOUBLE HOOK!", FishingAlertText.compactDoubleHook("It's a Double Hook! Woot woot!"))
        assertNull(FishingAlertText.compactDoubleHook("Hook line"))
        val sc = SeaCreatures.match("The Water Hydra has come to test your strength.", SkyblockLocation.HUB, true)!!
        assertEquals("§7A §6Water Hydra §7has spawned!", FishingAlertText.compactSpawn(sc))
    }

    @Test fun coins() {
        assertEquals("1.5M", FishingAlertText.fmtCoins(1_500_000.0))
        assertEquals("250k", FishingAlertText.fmtCoins(250_000.0))
    }
}
