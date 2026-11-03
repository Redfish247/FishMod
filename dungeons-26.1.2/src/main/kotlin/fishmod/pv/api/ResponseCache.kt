/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fishmod.pv.ProfileViewer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Collections
import java.util.Optional

/** Five-minute cache for proxy responses: in memory, mirrored to disk so a restart still hits. */
internal object ResponseCache {
    private const val TTL_MS = 5L * 60L * 1000L
    private const val MAX_MEMORY = 24
    private val DIR: Path = ProfileViewer.DIR.resolve("response-cache")

    private class Cached(val json: JsonObject, val storedAtMs: Long)

    private val MEMORY: MutableMap<String, Cached> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Cached>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?): Boolean = size > MAX_MEMORY
        }
    )

    @JvmStatic
    fun get(key: String): Optional<JsonObject> {
        val now = System.currentTimeMillis()
        MEMORY[key]?.let { hit ->
            if (now - hit.storedAtMs < TTL_MS) return Optional.of(hit.json.deepCopy())
            MEMORY.remove(key)
        }
        val file = fileFor(key)
        try {
            if (Files.isRegularFile(file)) {
                val stored = Files.getLastModifiedTime(file).toMillis()
                if (now - stored < TTL_MS) {
                    Files.newBufferedReader(file, StandardCharsets.UTF_8).use { reader ->
                        val json = JsonParser.parseReader(reader).asJsonObject
                        MEMORY[key] = Cached(json, stored)
                        return Optional.of(json.deepCopy())
                    }
                }
                Files.deleteIfExists(file)
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.debug("Response cache read failed for {}", key, exception)
        } catch (exception: RuntimeException) {
            ProfileViewer.LOGGER.debug("Response cache read failed for {}", key, exception)
        }
        return Optional.empty()
    }

    @JvmStatic
    fun put(key: String, original: JsonObject) {
        // Own copy: caller mutates its object while the disk write serializes on another thread.
        val json = original.deepCopy()
        MEMORY[key] = Cached(json, System.currentTimeMillis())
        HypixelApiClient.networkExecutor().execute {
            try {
                Files.createDirectories(DIR)
                val file = fileFor(key)
                val tmp = file.resolveSibling("${file.fileName}.tmp")
                Files.writeString(tmp, json.toString(), StandardCharsets.UTF_8)
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
            } catch (exception: IOException) {
                ProfileViewer.LOGGER.debug("Response cache write failed for {}", key, exception)
            } catch (exception: RuntimeException) {
                ProfileViewer.LOGGER.debug("Response cache write failed for {}", key, exception)
            }
        }
    }

    private fun fileFor(key: String): Path {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray(StandardCharsets.UTF_8))
        return DIR.resolve(digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}
