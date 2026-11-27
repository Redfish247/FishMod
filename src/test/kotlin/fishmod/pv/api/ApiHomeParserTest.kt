package fishmod.pv.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.data.EventsSnapshot
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.dungeons.ClassLevelQuery
import java.util.UUID
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiHomeParserTest {
    private fun ok(m: String, f: () -> Unit) = try { f() } catch (e: Throwable) { fail("$m: $e", e) }
    private fun obj(s: String): JsonObject = JsonParser.parseString(s).asJsonObject
    private val uuid = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val u = "11111111222233334444555555555555"

    private val bad = listOf("null", "5", "\"x\"", "[]", "{}", "true", "[1,2]", "{\"a\":null}")

    @Test fun homeCoreMalformedRoots() {
        for (r in listOf("{}", "{\"profiles\":null}", "{\"profiles\":5}", "{\"profiles\":[]}", "{\"profiles\":[null]}",
            "{\"profiles\":[5,\"x\"]}", "{\"profiles\":[{}]}", "{\"profiles\":[{\"members\":5}]}",
            "{\"profiles\":[{\"members\":{}}]}", "{\"profiles\":[{\"members\":{\"$u\":null}}]}",
            "{\"profiles\":[{\"members\":{\"$u\":5}}]}", "{\"profiles\":[{\"members\":{\"$u\":{}}}]}")) {
            ok("") { ProfileHomeParser.parseHomeCore("x", uuid, obj(r)) }
        }
    }

    @Test fun homeCoreFieldsWrongTypes() {
        for (b in bad) for (k in listOf("player_data", "leveling", "slayer", "profile", "currencies", "inventory",
            "experience", "events", "garden_player_data", "bestiary", "dungeons", "rift", "pets_data", "collection")) {
            val r = "{\"profiles\":[{\"profile_id\":\"p\",\"cute_name\":$b,\"banking\":$b,\"members\":{\"$u\":{\"$k\":$b,\"player_stats\":$b}}}]}"
            ok("$k=$b") { ProfileHomeParser.parseHomeCore("x", uuid, obj(r)) }
        }
    }

    @Test fun gardenAndEventsWrongTypes() {
        for (b in bad) for (k in listOf("garden_player_data", "events", "player_data", "experience", "player_stats", "jacobs_contest")) {
            val m = obj("{\"$k\":$b}")
            ok("garden $k=$b") { GardenSnapshot.fromMember(m) }
            ok("events $k=$b") { EventsSnapshot.fromMember(m, obj("{}"), uuid) }
        }
        ok("") { GardenSnapshot.fromMember(null) }
        ok("") { EventsSnapshot.fromMember(null, null, uuid) }
    }

    @Test fun classLevelQuery() {
        assertNull(ClassLevelQuery.parse(null))
        assertNull(ClassLevelQuery.parse("  "))
        assertNull(ClassLevelQuery.parse("99999999999 mage"))
        assertNull(ClassLevelQuery.parse("mage"))
        assertNotNull(ClassLevelQuery.parse("mage 50"))
        assertNotNull(ClassLevelQuery.parse("50 mage"))
        assertNotNull(ClassLevelQuery.parse("ca 50"))
        assertNotNull(ClassLevelQuery.parse("0 mage"))
        for (s in listOf("mage 0", "mage 999", "avg 0", "class average 100", "\u0000", "mage  50  ")) {
            ok(s) { ClassLevelQuery.parse(s) }
        }
    }
}
