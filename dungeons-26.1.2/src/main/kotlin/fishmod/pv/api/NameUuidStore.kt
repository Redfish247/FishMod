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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Name to UUID lookups kept across sessions so repeat /pv targets skip Mojang. */
internal object NameUuidStore {
    // Mojang holds a changed name for 37 days before anyone else can claim it, so a
    // shorter TTL can never map a name to the wrong account.
    private val TTL_MS = TimeUnit.DAYS.toMillis(30)
    private const val MAX_ENTRIES = 2000
    private val FILE: Path = ProfileViewer.DIR.resolve("name-uuid-cache.json")

    private class Entry(val uuid: UUID, val name: String, val savedAtMs: Long) {
        fun fresh(now: Long): Boolean = now - savedAtMs < TTL_MS
    }

    private val ENTRIES = ConcurrentHashMap<String, Entry>()
    private val SAVE_SCHEDULED = AtomicBoolean()

    @Volatile
    private var loaded = false

    @JvmRecord
    data class Stored(val value: HypixelApiClient.UuidName, val savedAtMs: Long)

    @JvmStatic
    fun get(name: String?): Stored? {
        if (name.isNullOrBlank()) return null
        ensureLoaded()
        val entry = ENTRIES[name.lowercase(Locale.ROOT)]
        if (entry == null || !entry.fresh(System.currentTimeMillis())) return null
        return Stored(HypixelApiClient.UuidName(entry.uuid, entry.name), entry.savedAtMs)
    }

    // savedAtMs is when the mapping was last confirmed, not when it was copied here,
    // so an entry taken from the server cache can't outlive the 37 day hold.
    @JvmStatic
    fun put(name: String?, uuid: UUID?, resolvedName: String?, savedAtMs: Long) {
        if (name.isNullOrBlank() || uuid == null) return
        ensureLoaded()
        val now = System.currentTimeMillis()
        val entry = Entry(uuid, if (resolvedName.isNullOrBlank()) name else resolvedName, minOf(savedAtMs, now))
        if (!entry.fresh(now)) return
        ENTRIES[name.lowercase(Locale.ROOT)] = entry
        ENTRIES[entry.name.lowercase(Locale.ROOT)] = entry
        forgetOtherNames(uuid, entry.name)
        scheduleSave()
    }

    @JvmStatic
    fun forget(name: String?) {
        if (name.isNullOrBlank()) return
        ensureLoaded()
        if (ENTRIES.remove(name.lowercase(Locale.ROOT)) != null) scheduleSave()
    }

    // Drops names this account no longer has once its current name is confirmed.
    @JvmStatic
    fun forgetOtherNames(uuid: UUID?, currentName: String?) {
        if (uuid == null || currentName == null) return
        ensureLoaded()
        val current = currentName.lowercase(Locale.ROOT)
        val removed = ENTRIES.entries.removeIf { uuid == it.value.uuid && it.key != current }
        if (removed) scheduleSave()
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!Files.isRegularFile(FILE)) return
        val now = System.currentTimeMillis()
        try {
            val root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).asJsonObject
            for ((key, element) in root.entrySet()) {
                if (!element.isJsonObject) continue
                val value = element.asJsonObject
                val uuid = HypixelApiClient.parseUndashedUuid(if (value.has("uuid")) value.get("uuid").asString else null)
                val name = if (value.has("name")) value.get("name").asString else null
                val savedAt = if (value.has("savedAt")) value.get("savedAt").asLong else 0L
                if (uuid != null && name != null) {
                    val entry = Entry(uuid, name, savedAt)
                    if (entry.fresh(now)) ENTRIES[key] = entry
                }
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Could not read name cache {}", FILE, exception)
        } catch (exception: RuntimeException) {
            ProfileViewer.LOGGER.warn("Could not read name cache {}", FILE, exception)
        }
    }

    private fun scheduleSave() {
        if (SAVE_SCHEDULED.compareAndSet(false, true)) {
            CompletableFuture.runAsync({ save() }, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS))
        }
    }

    private fun save() {
        SAVE_SCHEDULED.set(false)
        val now = System.currentTimeMillis()
        val live = ArrayList<Map.Entry<String, Entry>>()
        for (e in ENTRIES.entries) {
            if (e.value.fresh(now)) live.add(e) else ENTRIES.remove(e.key, e.value)
        }
        live.sortByDescending { it.value.savedAtMs }
        val root = JsonObject()
        for ((i, e) in live.withIndex()) {
            if (i >= MAX_ENTRIES) {
                ENTRIES.remove(e.key, e.value)
                continue
            }
            val value = JsonObject()
            value.addProperty("uuid", HypixelApiClient.undashed(e.value.uuid))
            value.addProperty("name", e.value.name)
            value.addProperty("savedAt", e.value.savedAtMs)
            root.add(e.key, value)
        }
        try {
            Files.createDirectories(FILE.parent)
            val tmp = FILE.resolveSibling("${FILE.fileName}.tmp")
            Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8)
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (exception: IOException) {
            ProfileViewer.LOGGER.warn("Could not save name cache {}", FILE, exception)
        }
    }
}
