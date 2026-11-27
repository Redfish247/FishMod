/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fishmod.pv.data.Leveling
import java.util.Locale

internal object ProfileParseSupport {
    @JvmStatic
    fun str(element: JsonElement?): String {
        if (element == null || element.isJsonNull || !element.isJsonPrimitive) return ""
        return try {
            element.asString
        } catch (_: IllegalStateException) {
            ""
        } catch (_: ClassCastException) {
            ""
        } catch (_: UnsupportedOperationException) {
            ""
        }
    }

    @JvmStatic
    fun bool(obj: JsonObject?, key: String): Boolean {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull || !obj.get(key).isJsonPrimitive) return false
        return try {
            obj.get(key).asBoolean
        } catch (_: IllegalStateException) {
            false
        } catch (_: ClassCastException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    @JvmStatic
    fun readLong(obj: JsonObject?, key: String): Long {
        if (obj == null || !obj.has(key)) return 0L
        return Leveling.num(obj.get(key))?.let { Math.round(it).toLong() } ?: 0L
    }

    @JvmStatic
    fun readDouble(obj: JsonObject?, key: String): Double {
        if (obj == null || !obj.has(key)) return 0.0
        return Leveling.num(obj.get(key))?.toDouble() ?: 0.0
    }

    inline fun <T> lenient(fallback: T, block: () -> T): T = try {
        block()
    } catch (_: IllegalStateException) {
        fallback
    } catch (_: ClassCastException) {
        fallback
    } catch (_: NumberFormatException) {
        fallback
    } catch (_: UnsupportedOperationException) {
        fallback
    }

    @JvmStatic
    fun title(id: String): String = id.substring(0, 1).uppercase(Locale.ROOT) + id.substring(1)
}
