package fishmod.utils

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.RunSplit

object FishJson {

    // run_splits.json: { "m7": [ { label, rgb "#RRGGBB", from, to, avgSec }, ... ] }, floor keys come back upper-case
    @JvmStatic
    fun readSplits(path: String): HashMap<String, ArrayList<RunSplit>> {
        val text = FishJson::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
        if (text == null) {
            FishDiag.fail("FishJson.1", "bundled splits resource missing: $path")
            return HashMap()
        }
        val out = HashMap<String, ArrayList<RunSplit>>()
        try {
            for ((floor, rows) in JsonParser.parseString(text).asJsonObject.entrySet()) {
                out[floor.uppercase()] = rows.asJsonArray.mapNotNullTo(ArrayList()) { splitOf(floor, it.asJsonObject) }
            }
        } catch (e: Exception) {
            FishDiag.fail("FishJson.2", "failed to parse splits from $path", e)
        }
        return out
    }

    private fun splitOf(floor: String, o: JsonObject): RunSplit? {
        val ok = listOf("label", "rgb", "from", "to").all(o::has)
        if (!FishDiag.check(ok, "FishJson.3") { "split in $floor missing fields: $o" }) return null
        val rgb = o["rgb"].asString.removePrefix("#").toInt(16)
        val avg = o["avgSec"]?.asDouble ?: 0.0
        return RunSplit(o["label"].asString, o["from"].asString, o["to"].asString, rgb, avg)
    }
}
