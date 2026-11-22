/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.Locale
import java.util.UUID

class SearchHistory private constructor(entries: List<Entry>) {
    private val entries: List<Entry> = entries.toList()

    @JvmRecord
    data class Entry(val uuid: UUID?, val name: String) {
        fun samePlayer(otherUuid: UUID?, otherName: String): Boolean {
            if (uuid != null && otherUuid != null) return uuid == otherUuid
            return name.equals(otherName, ignoreCase = true)
        }
    }

    fun entries(): List<Entry> = entries

    fun record(uuid: UUID?, name: String?): SearchHistory {
        val cleaned = name?.trim() ?: ""
        if (!NAME.matches(cleaned)) return this
        val next = ArrayList<Entry>(MAX)
        next.add(Entry(uuid, cleaned))
        for (entry in entries) {
            if (next.size >= MAX) break
            if (!entry.samePlayer(uuid, cleaned)) next.add(entry)
        }
        return SearchHistory(next)
    }

    fun filter(query: String?): List<Entry> {
        val q = query?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (q.isEmpty()) return entries
        return entries.filter { it.name.lowercase(Locale.ROOT).contains(q) }
    }

    fun toJson(): JsonArray {
        val array = JsonArray()
        for (entry in entries) {
            val obj = JsonObject()
            entry.uuid?.let { obj.addProperty("uuid", it.toString()) }
            obj.addProperty("name", entry.name)
            array.add(obj)
        }
        return array
    }

    companion object {
        const val MAX = 3
        private val NAME = Regex("[A-Za-z0-9_]{1,16}")
        private val EMPTY = SearchHistory(emptyList())

        @JvmStatic
        fun empty(): SearchHistory = EMPTY

        @JvmStatic
        fun fromJson(element: JsonElement?): SearchHistory {
            if (element == null || !element.isJsonArray) return EMPTY
            val array = element.asJsonArray
            var history = EMPTY
            for (i in array.size() - 1 downTo 0) {
                val item = array.get(i)
                if (!item.isJsonObject) continue
                val obj = item.asJsonObject
                history = history.record(parseUuid(string(obj, "uuid")), string(obj, "name"))
            }
            return history
        }

        private fun string(obj: JsonObject, key: String): String {
            val value = obj.get(key)
            return if (value != null && value.isJsonPrimitive) value.asString else ""
        }

        private fun parseUuid(raw: String?): UUID? {
            if (raw.isNullOrBlank()) return null
            return try {
                UUID.fromString(raw.trim())
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
