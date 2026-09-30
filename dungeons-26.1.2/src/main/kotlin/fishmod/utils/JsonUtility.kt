package fishmod.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import fishmod.utils.debug.Debug
import fishmod.utils.debug.FishDiag
import fishmod.utils.dungeon.Split
import java.io.InputStreamReader

object JsonUtility {

    @JvmStatic
    fun readSplits(path: String): HashMap<String, ArrayList<Split>> {
        try {
            JsonUtility::class.java.getResourceAsStream(path).use { stream ->
                if (stream == null) {
                    FishDiag.fail("JsonUtility.1", "bundled splits resource missing: $path")
                    return HashMap()
                }
                InputStreamReader(stream).use { reader ->
                    val element = JsonParser.parseReader(reader)
                    return parseSplits(element)
                }
            }
        } catch (e: Exception) {
            FishDiag.fail("JsonUtility.2", "failed to parse splits from $path", e)
            Debug.LOGGER.error("Failed to parse splits from $path", e)
        }

        return HashMap()
    }

    private fun parseSplits(jsonElement: JsonElement): HashMap<String, ArrayList<Split>> {
        val floors = HashMap<String, ArrayList<Split>>()
        val obj = jsonElement.asJsonObject

        for (floorEntry in obj.entrySet()) {
            val splits = ArrayList<Split>()

            val floorName = floorEntry.key
            val dialoguesArray = floorEntry.value.asJsonArray

            for (dialogueElement in dialoguesArray) {
                val dialogueObj = dialogueElement.asJsonObject

                if (!FishDiag.check(dialogueObj.has("name") && dialogueObj.has("start") && dialogueObj.has("end") && dialogueObj.has("color"), "JsonUtility.3") { "split in $floorName missing fields: $dialogueObj" }) continue
                val color = dialogueObj.get("color").asInt
                val name = dialogueObj.get("name").asString
                val start = dialogueObj.get("start").asString
                val end = dialogueObj.get("end").asString
                val avg = if (dialogueObj.has("avg")) dialogueObj.get("avg").asDouble else 0.0

                splits.add(Split(name, start, end, color, avg))
            }

            floors[floorName] = splits
        }
        return floors
    }
}
