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
            f.writeText("""{"notifiyMelody":true,"hideStatusOverLay":true,"copyChat":true,"chatCopy":false,"other":5}""")
            ConfigKeyMigration.run(f.path)
            val o = JsonParser.parseString(f.readText()).asJsonObject
            assertEquals(true, o["melodyAlert"].asBoolean)
            assertEquals(true, o["hideEffectHud"].asBoolean)
            assertEquals(false, o["chatCopy"].asBoolean)
            assertEquals(5, o["other"].asInt)
            assertFalse(o.has("notifiyMelody") || o.has("copyChat"))
        } finally { f.delete() }
    }
}
