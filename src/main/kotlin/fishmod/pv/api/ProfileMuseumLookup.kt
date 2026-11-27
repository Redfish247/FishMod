/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonObject
import fishmod.pv.data.Leveling

internal object ProfileMuseumLookup {
    @JvmStatic
    fun findMuseumMember(museumRoot: JsonObject?, profileId: String?, undashed: String): JsonObject? {
        if (museumRoot == null) return null
        Leveling.obj(museumRoot.get("members"))?.let { members ->
            findMember(members, undashed)?.let { return it }
        }
        if (profileId != null && museumRoot.has(profileId) && museumRoot.get(profileId).isJsonObject) {
            val profileMuseum = museumRoot.getAsJsonObject(profileId)
            val nestedMembers = Leveling.obj(profileMuseum.get("members"))
            return if (nestedMembers != null) findMember(nestedMembers, undashed) else profileMuseum
        }
        return findMember(museumRoot, undashed)
    }

    @JvmStatic
    fun findMember(members: JsonObject?, undashed: String): JsonObject? {
        if (members == null) return null
        if (members.has(undashed) && members.get(undashed).isJsonObject) {
            return members.getAsJsonObject(undashed)
        }
        for ((key, value) in members.entrySet()) {
            if (key != null && key.replace("-", "").equals(undashed, ignoreCase = true) && value.isJsonObject) {
                return value.asJsonObject
            }
        }
        return null
    }
}
