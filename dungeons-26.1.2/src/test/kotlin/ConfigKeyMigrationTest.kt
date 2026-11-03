import com.google.gson.JsonParser
import fishmod.utils.config.ConfigKeyMigration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class ConfigKeyMigrationTest {
    @Test fun movesOldKeysAndKeepsNewOnes() {
        val f = File.createTempFile("fmcfg", ".json")
        try {
            f.writeText("""{"notifiyMelody":true,"hideStatusOverLay":true,"copyChat":true,"chatCopy":false,"other":5,"timerType":"DIFFRENCE","tickTimer":{"x":0.4,"y":0.5,"scale":1.0,"info":"Tick Timer"}}""")
            ConfigKeyMigration.run(f.path)
            val o = JsonParser.parseString(f.readText()).asJsonObject
            assertEquals(true, o["melodyAlert"].asBoolean)
            assertEquals(true, o["hideEffectHud"].asBoolean)
            assertEquals(false, o["chatCopy"].asBoolean)
            assertEquals(5, o["other"].asInt)
            assertEquals("GAP", o["splitClock"].asString)
            assertEquals("Boss Tick Clock", o["tickTimer"].asJsonObject["info"].asString)
            assertFalse(o.has("notifiyMelody") || o.has("copyChat"))
        } finally { f.delete() }
    }
}
