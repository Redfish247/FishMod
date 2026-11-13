/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MuseumCache {
    const val REFRESH_COOLDOWN_MS: Long = 30L * 60L * 1000L

    class Entry(museumMember: JsonObject?, fetchedAtMs: Long, itemCount: Int) {
        @get:JvmName("museumMember") val museumMember: JsonObject = museumMember ?: JsonObject()
        @get:JvmName("fetchedAtMs") val fetchedAtMs: Long = fetchedAtMs
        @get:JvmName("itemCount") val itemCount: Int = maxOf(0, itemCount)
    }

    private val CACHE = ConcurrentHashMap<String, Entry>()
    private val NEXT_REFRESH_AT_MS = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun key(uuid: UUID?, profileId: String?): String {
        val u = uuid?.toString()?.replace("-", "")?.lowercase() ?: ""
        val p = profileId?.trim()?.lowercase() ?: ""
        return "$u|$p"
    }

    @JvmStatic
    fun get(uuid: UUID?, profileId: String?): Entry? = CACHE[key(uuid, profileId)]

    @JvmStatic
    fun has(uuid: UUID?, profileId: String?): Boolean = get(uuid, profileId) != null

    @JvmStatic
    fun put(uuid: UUID?, profileId: String?, museumMember: JsonObject?, itemCount: Int) {
        val k = key(uuid, profileId)
        CACHE[k] = Entry(museumMember, System.currentTimeMillis(), itemCount)
        NEXT_REFRESH_AT_MS[k] = System.currentTimeMillis() + REFRESH_COOLDOWN_MS
    }

    @JvmStatic
    fun invalidate(uuid: UUID?, profileId: String?) {
        CACHE.remove(key(uuid, profileId))
    }

    @JvmStatic
    fun canRefresh(uuid: UUID?, profileId: String?): Boolean {
        val next = NEXT_REFRESH_AT_MS[key(uuid, profileId)]
        return next == null || System.currentTimeMillis() >= next
    }

    @JvmStatic
    fun refreshReadyInMs(uuid: UUID?, profileId: String?): Long {
        val next = NEXT_REFRESH_AT_MS[key(uuid, profileId)] ?: return 0L
        return maxOf(0L, next - System.currentTimeMillis())
    }

    @JvmStatic
    fun clearAll() {
        CACHE.clear()
        NEXT_REFRESH_AT_MS.clear()
    }
}
