package fishmod.pv.networth

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ApiInventoryDecoderTest {
    private fun ok(m: String, f: () -> Unit) = try { f() } catch (e: Throwable) { fail("$m: $e", e) }
    private val bad = listOf("null", "5", "\"x\"", "\"!!notbase64!!\"", "\"H4sIAAAA\"", "\"\"", "[]", "{}", "true", "{\"data\":5}", "{\"data\":\"AAAA\",\"type\":0}", "[1,2]")
    private val keys = listOf("inventory", "inv_contents", "inv_armor", "ender_chest_contents", "bag_contents", "wardrobe_contents",
        "equipment_contents", "sacks_counts", "backpack_contents", "backpack_icons", "personal_vault_contents", "item_data", "quiver", "talisman_bag")

    @Test fun itemBytes() {
        for (b in bad) {
            val e = JsonParser.parseString(b)
            ok("stacks $b") { InventoryDecoder.stacksFromItemBytes(e) }
            ok("slot $b") { InventoryDecoder.slotFromItemBytes(e) }
        }
        ok("null") { InventoryDecoder.stacksFromItemBytes(null) }
    }

    @Test fun memberWrongTypes() {
        for (b in bad) for (k in keys) {
            val m = JsonParser.parseString("{\"inventory\":{\"$k\":$b,\"bag_contents\":$b},\"$k\":$b}").asJsonObject
            ok("home $k=$b") { InventoryDecoder.parseHomeGear(m) }
            ok("acc $k=$b") { InventoryDecoder.accessoryCandidates(m) }
            ok("cat $k=$b") { InventoryDecoder.parseCategories(m, m) }
            ok("ui $k=$b") { InventoryDecoder.parseUi(m) }
            ok("accinfo $k=$b") { InventoryDecoder.parseAccessoryInfo(m) }
            ok("rift $k=$b") { InventoryDecoder.parseRiftUi(m) }
            ok("ward $k=$b") { InventoryDecoder.wardrobePresets(m) }
            ok("armor $k=$b") { InventoryDecoder.readArmorSlots(m) }
            ok("museum $k=$b") { InventoryDecoder.parseMuseum(m) }
            ok("toolkit $k=$b") { InventoryDecoder.parseToolkit(m) }
        }
    }
}
