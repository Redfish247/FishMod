/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/** Legacy NEU skull texture Values for trophy fish/frogs (pre-ItemModel paper). */
object TrophySkulls {
    @Volatile
    private var loaded = false
    private val VALUES = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            load()
            loaded = true
        }
    }

    @JvmStatic
    fun value(skyblockId: String?): String? {
        ensureLoaded()
        if (skyblockId.isNullOrBlank()) return null
        return VALUES[skyblockId.uppercase()]
    }

    private fun load() {
        try {
            val stream = TrophySkulls::class.java.getResourceAsStream("/assets/fishmod/pv/data/trophy_skulls.json")
            if (stream == null) {
                ProfileViewer.LOGGER.warn("Missing trophy_skulls.json")
                return
            }
            stream.use { input ->
                val root = JsonParser.parseReader(InputStreamReader(input, StandardCharsets.UTF_8))
                if (root == null || !root.isJsonObject) return
                for ((key, value) in root.asJsonObject.entrySet()) {
                    if (key == null || value == null || !value.isJsonPrimitive) continue
                    val v = value.asString
                    if (!v.isNullOrBlank()) VALUES[key.uppercase()] = v
                }
            }
        } catch (ex: IOException) {
            ProfileViewer.LOGGER.warn("Failed to load trophy_skulls.json", ex)
        } catch (ex: RuntimeException) {
            if (!SoftDataFailure.isSoft(ex)) throw ex
            ProfileViewer.LOGGER.warn("Failed to load trophy_skulls.json", ex)
        }
    }
}
