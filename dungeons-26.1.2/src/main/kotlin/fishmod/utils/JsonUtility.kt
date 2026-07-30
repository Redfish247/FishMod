package fishmod.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import fishmod.Bladeaddons
import fishmod.utils.debug.Debug
import fishmod.utils.dungeon.Split
import java.io.IOException
import java.io.InputStreamReader

object JsonUtility {

    @JvmStatic
    fun readSplits(path: String): HashMap<String, ArrayList<Split>> {
        try {
            Bladeaddons::class.java.getResourceAsStream(path).use { stream ->
                if (stream == null) return HashMap()
                InputStreamReader(stream).use { reader ->
                    val element = JsonParser.parseReader(reader)
                    return parseSplits(element)
                }
            }
        } catch (e: IOException) {
            Debug.LOGGER.error("Failed to parse a split")
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
