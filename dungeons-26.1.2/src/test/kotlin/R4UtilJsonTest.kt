import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.features.achievements.AchLeaderboard
import fishmod.features.item.NeuItems
import fishmod.utils.ChatQueue
import fishmod.utils.HypixelApi
import fishmod.utils.JsonUtility
import fishmod.utils.MayorApi
import fishmod.utils.data.NeuRepo
import fishmod.utils.networth.ItemsDb
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class R4UtilJsonTest {
    private fun obj(s: String) = JsonParser.parseString(s).asJsonObject
    private fun call(target: Any?, cls: Class<*>, name: String, vararg a: Any?): Any? {
        val m = cls.declaredMethods.first { it.name == name && it.parameterCount == a.size }
        m.isAccessible = true
        return try { m.invoke(target, *a) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.cause!! }
    }
    private fun field(target: Any, name: String): Any? {
        val f = target.javaClass.getDeclaredField(name); f.isAccessible = true; return f.get(target)
    }

    @Test fun mayorMalformed() {
        for (j in listOf("{}", """{"mayor":null}""", """{"mayor":{}}""", """{"mayor":{"name":"A","perks":[]}}""",
            """{"mayor":{"name":"A","perks":[{}]}}""", """{"mayor":{"key":"paul","perks":[{"name":null}]}}""",
            """{"mayor":{"key":"x","minister":{"key":"paul","perk":null}}}""")) {
            val r = obj(j)
            assertDoesNotThrow { call(MayorApi, MayorApi.javaClass, "parseMayorLine", r) }
            assertDoesNotThrow { call(MayorApi, MayorApi.javaClass, "parsePaul", r) }
        }
        assertEquals("§7Mayor: §eCole", call(MayorApi, MayorApi.javaClass, "parseMayorLine", obj("""{"mayor":{"key":"cole"}}""")))
        assertNull(call(MayorApi, MayorApi.javaClass, "parseMayorLine", obj("""{"mayor":{}}""")))
        assertEquals(true, call(MayorApi, MayorApi.javaClass, "parsePaul",
            obj("""{"mayor":{"key":"x","minister":{"key":"Paul","perk":{"name":"EZPZ"}}}}""")))
    }

    @Test fun hypixelSelectedProfile() {
        val root = obj("""{"profiles":[{"selected":null,"members":{"u":{}}},{"selected":true,"members":{"u":{"a":1}}}]}""")
        assertDoesNotThrow { call(null, HypixelApi::class.java, "findSelectedMember", root, "u") }
        assertNull(call(null, HypixelApi::class.java, "findSelectedMember", obj("""{"profiles":[{"selected":true,"members":null}]}"""), "u"))
        assertNull(call(null, HypixelApi::class.java, "findSelectedMember", obj("""{"profiles":null}"""), "u"))
        assertNull(call(null, HypixelApi::class.java, "findSelectedMember", obj("""{"profiles":[{"members":{}}]}"""), "u"))
    }

    @Test fun hypixelUuid() {
        val f = { s: String -> call(null, HypixelApi::class.java, "parseUuid", s) }
        assertEquals("abc", f("""{"id":"a-b-c"}"""))
        assertNull(f("not json")); assertNull(f("{}")); assertNull(f("""{"id":""}"""))
        assertNull(f("""{"data":{}}"""))
    }

    @Test fun ahLeaderboardParse() {
        val l = call(AchLeaderboard, AchLeaderboard.javaClass, "parse", obj("""{"points":[{"name":"a","points":3,"secrets":1}]}"""), "points") as List<*>
        assertEquals(1, l.size)
        assertDoesNotThrow { call(AchLeaderboard, AchLeaderboard.javaClass, "parse",
            obj("""{"points":[{"name":"a","points":3,"secrets":1},{"name":"b"}]}"""), "points") }
    }

    @Test fun itemsDbIndex() {
        val idx = { s: String? -> call(ItemsDb, ItemsDb.javaClass, "index", s?.let { obj(it) }) }
        assertDoesNotThrow { idx(null) }
        assertDoesNotThrow { idx("""{"items":[null,1,{"id":{}},{"id":"A","name":"§aFoo","npc_sell_price":"x"},{"id":"B","name":null}]}""") }
        assertEquals("A", ItemsDb.idFor("Foo"))
    }

    @Test fun neuParse() {
        val p = { id: String, t: String -> call(NeuItems, NeuItems.javaClass, "parse", id, t) }
        assertNull(p("DYE_X", "garbage"))
        assertNull(p("DYE_X", """{"displayname":"§aX Dye","lore":[]}"""))
        assertNotNull(p("DYE_X", """{"displayname":"§aX Dye","lore":["§7Hex: #ff00aa"]}"""))
        assertNull(p("SWORD", """{}"""))
    }

    @Test fun jsonUtilityMissing() {
        assertTrue(JsonUtility.readSplits("/does/not/exist.json").isEmpty())
    }

    @Test fun chatQueueOrderAndDedup() {
        @Suppress("UNCHECKED_CAST") val q = field(ChatQueue, "pending") as java.util.ArrayDeque<String>
        q.clear()
        ChatQueue.enqueue("/pc a"); ChatQueue.enqueue("pc a"); ChatQueue.enqueue("  "); ChatQueue.enqueue("/")
        assertEquals(listOf("pc a"), q.toList())
        for (i in 1..9) ChatQueue.enqueue("m$i")
        assertEquals(5, q.size)
        assertEquals("m5", q.first); assertEquals("m9", q.last)
        q.clear()
    }
}
