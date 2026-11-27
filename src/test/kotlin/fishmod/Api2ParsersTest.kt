package fishmod

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class Api2ParsersTest {
    private fun call(cls: String, name: String, vararg args: Any?): Any? {
        val c = Class.forName(cls)
        val m = c.declaredMethods.first { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }
        return try { m.invoke(null, *args) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.targetException }
    }
    private fun obj(s: String): JsonObject = JsonParser.parseString(s).asJsonObject
    private val junk = listOf("null", "5", "\"x\"", "[]", "{}", "true", "[null]", "[[1]]", "{\"a\":null}", "{\"costs\":5}", "{\"costs\":[null,\"x\",{},[],2]}")

    @Test fun essenceCostsBadTierKeepsRest() {
        @Suppress("UNCHECKED_CAST")
        val r = call("fishmod.pv.dungeons.EssenceShopCosts", "parse", obj("""{"s":{"P":{"costs":[1,null,"x",{},4]},"Q":{"costs":5},"R":null}}""")) as Map<String, IntArray>
        assertArrayEquals(intArrayOf(1, 0, 0, 0, 4), r["p"])
    }
    @Test fun essenceCostsJunk() {
        for (j in junk) call("fishmod.pv.dungeons.EssenceShopCosts", "parse", obj("""{"s":{"p":$j},"t":$j}"""))
    }
    @Test fun gardenCostsJunk() {
        for (j in junk) for (k in listOf("chip_sowdust", "crop_upgrade_copper", "composter_rare_items", "plots", "plot_costs", "composter_upgrades"))
            call("fishmod.pv.data.GardenCosts", "parse", obj("""{"$k":$j}"""))
        call("fishmod.pv.data.GardenCosts", "parse", obj("""{"plots":{"a":{"number":"x","x":null},"b":5},"plot_costs":{"t":[{"item":"A","amount":"z"},null,5]}}"""))
    }
    @Test fun chocolateFactoryJunk() {
        for (j in junk) for (k in listOf("prestige_chocolate", "hitman_slot_cost", "factions"))
            call("fishmod.pv.data.ChocolateFactoryData", "parse", obj("""{"$k":$j}"""))
        call("fishmod.pv.data.ChocolateFactoryData", "parse", obj("""{"factions":{"":{"a":[null,1]},"x":{"c":null,"d":"e"}},"prestige_chocolate":{"1":"x","z":2,"3":null}}"""))
    }
    @Test fun bestiaryHelpers() {
        val c = "fishmod.pv.data.BestiaryData"
        for (j in junk) {
            call(c, "parseBracketLadders", obj("""{"a":$j,"b":[1,"x",null,{},2.5,-3]}"""))
            call(c, "parseIcon", JsonParser.parseString(j))
            call(c, "parseFamilies", "cat", JsonParser.parseString(j), HashMap<String, Any>())
        }
        call(c, "parseFamilies", "cat", JsonParser.parseString("""[null,5,{"name":5,"mobs":"x"},{"mobs":[null]}]"""), HashMap<String, Any>())
        call(c, "parseFamilies", "cat", JsonParser.parseString("""{"a":null,"b":5,"c":{"name":null,"brackets":"x","mobs":{"d":null}}}"""), HashMap<String, Any>())
        for (s in listOf(null, "", "§", "§l", "x§")) { call(c, "prettyId", s) }
    }
    @Test fun collectionRewardsText() {
        val c = "fishmod.pv.gui.collections.page.CollectionRewards"
        val f: java.util.function.Function<String, String> = (java.util.function.Function<String, String?> { null }) as java.util.function.Function<String, String>
        for (s in listOf(null, "", " ", "§", "SkyBlock XP", "+5 Coins", "Enchanted  Recipe", " Minion Recipes", "Recipe", "I Minion", "Sharpness Book Recipe", "Pet Recipe", "XIIX Book Recipe", "Potion Recipe"))
            call(c, "parse", s, f)
        val throwing = java.util.function.Function<String, String?> { null } as java.util.function.Function<String, String>
        call(c, "parse", "Bad Book Recipe", throwing, java.util.function.Predicate<String> { true })
    }
    @Test fun eventsSnapshotBingo() {
        val u = java.util.UUID.randomUUID()
        val e = fishmod.pv.data.EventsSnapshot.empty()
        for (j in junk) {
            e.withBingoResources(obj("""{"events":$j,"goals":$j,"success":$j}"""))
            e.withBingoHistory(obj("""{"events":$j,"success":$j,"player":$j}"""))
            e.withBingoResources(obj("""{"goals":[$j,{"id":$j,"name":$j,"progress":$j,"requiredAmount":$j}]}"""))
            e.withBingoHistory(obj("""{"events":[$j,{"key":$j,"points":$j,"completed_goals":$j}]}"""))
            call("fishmod.pv.data.EventsSnapshot", "parseBingoProfile", obj("""{"profiles":$j}"""), u)
            call("fishmod.pv.data.EventsSnapshot", "parseBingoProfile", obj("""{"profiles":[$j]}"""), u)
            call("fishmod.pv.data.EventsSnapshot", "parseChocobits", obj("""{"a":$j,"1":$j}"""))
        }
    }
}
