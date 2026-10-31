import fishmod.features.slayers.SlayerType
import fishmod.utils.data.Roman
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DungeonParsersTest {
    @Test fun romanBasics() {
        assertEquals(4, Roman.toInt("IV"))
        assertEquals(5, Roman.toInt(" v "))
        assertEquals(0, Roman.toInt(""))
        assertEquals(0, Roman.toInt(null))
        assertEquals(0, Roman.toInt("IIX?"))
    }

    @Test fun slayerCategory() {
        assertEquals(SlayerType.REVENANT to 5, SlayerType.parseCategory("Revenant Horror V"))
        assertEquals(SlayerType.INFERNO to 4, SlayerType.parseCategory("Inferno Demonlord IV"))
        assertNull(SlayerType.parseCategory(""))
        assertNull(SlayerType.parseCategory("Revenant Horror"))
        assertNull(SlayerType.parseCategory("Revenant Horror VI"))
        assertNull(SlayerType.parseCategory("Foo Bar III"))
    }

    private fun progress(s: String): fishmod.features.slayers.SlayerManager.SpawnProgress {
        val c = fishmod.features.slayers.SlayerManager::class.java
        val m = c.getDeclaredMethod("parseProgress", String::class.java)
        m.isAccessible = true
        return m.invoke(c.getField("INSTANCE").get(null), s) as fishmod.features.slayers.SlayerManager.SpawnProgress
    }

    @Test fun slayerProgress() {
        assertEquals(50.0, progress("(1,200/2,400) Combat XP").percent!!, 1e-9)
        val k = progress("(1.2k/2.4k) Combat XP")
        assertEquals(1200.0, k.current!!, 1e-9)
        assertEquals(50.0, k.percent!!, 1e-9)
        assertEquals(42.0, progress("42% complete").percent!!, 1e-9)
    }
}
