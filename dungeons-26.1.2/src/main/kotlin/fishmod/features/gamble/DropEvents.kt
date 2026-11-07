package fishmod.features.gamble

import com.google.gson.JsonObject

// Stub for POST /drop; nothing calls it until real Hypixel chat samples exist
object DropEvents {

    // TODO: wire chat detection once real RNG/dye drop messages are captured; never guess strings
    @Suppress("unused")
    fun report(kind: String, name: String, rarity: String) {
        if (!GambleSettings.gambleEnabled) return
        val body = JsonObject().apply {
            addProperty("kind", kind); addProperty("name", name); addProperty("rarity", rarity)
        }
        GambleApi.call("POST", "/drop", body) { }
    }
}
