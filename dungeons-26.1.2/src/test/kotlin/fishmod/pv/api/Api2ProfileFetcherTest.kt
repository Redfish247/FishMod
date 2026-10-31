package fishmod.pv.api

import com.google.gson.JsonParser
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Api2ProfileFetcherTest {
    private val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val u = "11111111222233334444555555555555"
    private val bad = listOf("null", "5", "\"x\"", "[]", "{}", "[null]", "{\"a\":null}", "true")

    private fun run(json: String, pid: String = "p") {
        try { ProfileFetcher.parseByProfileId("x", uuid, JsonParser.parseString(json).asJsonObject, pid) }
        catch (e: Throwable) { fail("$json: $e", e) }
    }

    @Test fun profileFieldsWrongTypes() {
        for (b in bad) {
            run("""{"profiles":[{"profile_id":"p","cute_name":$b,"members":{"$u":{}}}]}""")
            run("""{"profiles":[{"profile_id":$b,"cute_name":"A","members":{"$u":{}}}]}""")
            run("""{"profiles":[{"profile_id":"p","cute_name":"A","members":$b}]}""")
            run("""{"profiles":[{"profile_id":"p","members":{"$u":$b}}]}""")
            run("""{"profiles":[{"profile_id":"p","game_mode":$b,"banking":$b,"community_upgrades":$b,"members":{"$u":{"player_data":$b,"profile":$b}}}]}""")
            run("""{"profiles":$b}""")
            run("""{"profiles":[$b]}""")
            run("""{"profiles":[{"profile_id":"p","members":{"$u":{}, "zz":$b}}]}""")
        }
    }
}
