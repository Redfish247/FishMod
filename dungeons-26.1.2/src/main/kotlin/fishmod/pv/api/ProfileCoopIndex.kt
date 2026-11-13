/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import fishmod.pv.data.Leveling
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object ProfileCoopIndex {
    private val COOP_MEMBER_NAMES = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun selectedProfile(root: JsonObject): JsonObject? {
        val profiles = if (root.has("profiles") && root.get("profiles").isJsonArray) root.getAsJsonArray("profiles") else return null
        var best: JsonObject? = null
        for (element in profiles) {
            if (!element.isJsonObject) continue
            val profile = element.asJsonObject
            val selected = ProfileParseSupport.bool(profile, "selected")
            if (selected || best == null) {
                best = profile
                if (selected) break
            }
        }
        return best
    }

    @JvmStatic
    fun pickProfile(profiles: JsonArray?, preferredProfileId: String?): JsonObject? {
        if (profiles == null) return null
        var preferred: JsonObject? = null
        var selected: JsonObject? = null
        var first: JsonObject? = null
        for (element in profiles) {
            if (!element.isJsonObject) continue
            val profile = element.asJsonObject
            if (first == null) first = profile
            val id = ProfileParseSupport.str(profile.get("profile_id"))
            if (preferredProfileId != null && preferredProfileId == id) preferred = profile
            if (ProfileParseSupport.bool(profile, "selected")) selected = profile
        }
        return preferred ?: selected ?: first
    }

    @JvmStatic
    fun listProfileChoices(
        profiles: JsonArray?,
        activeProfileId: String?,
        viewedUuidUndashed: String?
    ): List<ProfileFetcher.ProfileChoice> {
        if (profiles == null || profiles.isEmpty) return listOf()
        val viewed = viewedUuidUndashed?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
        val out = ArrayList<ProfileFetcher.ProfileChoice>(profiles.size())
        for (element in profiles) {
            if (!element.isJsonObject) continue
            val profile = element.asJsonObject
            val id = ProfileParseSupport.str(profile.get("profile_id"))
            val cute = if (profile.has("cute_name")) ProfileParseSupport.str(profile.get("cute_name")) else "Unknown"
            val hypixelSelected = ProfileParseSupport.bool(profile, "selected")
            val selected = if (!activeProfileId.isNullOrBlank()) activeProfileId == id else hypixelSelected
            var mode = ""
            if (profile.has("game_mode") && profile.get("game_mode").isJsonPrimitive && !profile.get("game_mode").isJsonNull) {
                mode = profile.get("game_mode").asString
            }
            var created = 0L
            if (profile.has("created_at") && profile.get("created_at").isJsonPrimitive) {
                created = ProfileParseSupport.lenient(0L) { profile.get("created_at").asLong }
            }
            out.add(ProfileFetcher.ProfileChoice(cute, id, selected, mode, created, parseCoopSummary(profile, viewed)))
        }
        if (activeProfileId.isNullOrBlank() && out.none { it.selected } && out.isNotEmpty()) {
            val first = out[0]
            out[0] = ProfileFetcher.ProfileChoice(first.cuteName, first.profileId, true, first.gameMode, first.createdAtMs, first.coop)
        }
        return out
    }

    @JvmStatic
    fun parseCoopSummary(profile: JsonObject?, viewedUuidUndashed: String?): ProfileFetcher.CoopSummary {
        val members = Leveling.obj(profile?.get("members"))
        if (members == null || members.isEmpty) return ProfileFetcher.CoopSummary.solo()
        val current = ArrayList<ProfileFetcher.CoopMemberRef>()
        val former = ArrayList<ProfileFetcher.CoopMemberRef>()
        for ((key, value) in members.entrySet()) {
            if (value == null || !value.isJsonObject) continue
            val uuid = key?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
            if (uuid.isBlank()) continue
            val memberObj = value.asJsonObject
            val ref = ProfileFetcher.CoopMemberRef(uuid, shortCoopUuid(uuid), coopJoinedMs(memberObj))
            if (isDeletedCoopMember(memberObj)) {
                former.add(ref)
            } else if (uuid != viewedUuidUndashed) {
                current.add(ref)
            }
        }
        current.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fallbackName })
        former.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.fallbackName })
        return ProfileFetcher.CoopSummary(current.size, former.size, current.toList(), former.toList())
    }

    private fun coopJoinedMs(memberObj: JsonObject): Long {
        val profileNode = Leveling.obj(memberObj.get("profile"))
        val invite = Leveling.obj(profileNode?.get("coop_invitation")) ?: return 0L
        val confirmed = invite.get("confirmed")
        if (confirmed != null && confirmed.isJsonPrimitive && !confirmed.asBoolean) return 0L
        val ts = invite.get("confirmed_timestamp")
        if (ts == null || !ts.isJsonPrimitive || !ts.asJsonPrimitive.isNumber) return 0L
        return ts.asLong
    }

    @JvmStatic
    fun isDeletedCoopMember(memberObj: JsonObject): Boolean {
        val profileNode = Leveling.obj(memberObj.get("profile")) ?: return false
        val notice = profileNode.get("deletion_notice")
        return notice != null && !notice.isJsonNull
    }

    @JvmStatic
    fun shortCoopUuid(uuid: String?): String {
        if (uuid == null) return "?"
        return if (uuid.length < 8) uuid else uuid.substring(0, 8)
    }

    @JvmStatic
    fun coopMemberDisplayName(member: ProfileFetcher.CoopMemberRef?): String {
        if (member == null) return "?"
        val resolved = COOP_MEMBER_NAMES[member.uuid]
        return if (!resolved.isNullOrBlank()) resolved else member.fallbackName
    }

    @JvmStatic
    fun coopNameResolved(member: ProfileFetcher.CoopMemberRef?): Boolean {
        if (member == null || member.uuid.isBlank()) return false
        val name = coopMemberDisplayName(member)
        if (name.isBlank() || name == "?") return false
        return name != shortCoopUuid(member.uuid)
    }

    @JvmStatic
    fun warmCoopMemberNames(
        choices: List<ProfileFetcher.ProfileChoice>?,
        viewedUuidUndashed: String?,
        viewedName: String?,
        onResolved: Runnable?
    ) {
        if (choices.isNullOrEmpty()) return
        val viewed = viewedUuidUndashed?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
        if (viewed.isNotBlank() && !viewedName.isNullOrBlank()) {
            COOP_MEMBER_NAMES[viewed] = viewedName
        }
        val pending = HashSet<String>()
        for (choice in choices) {
            collectCoopNameLookups(choice.coop.currentMembers, pending)
            collectCoopNameLookups(choice.coop.formerMembers, pending)
        }
        for (uuidKey in pending) {
            val uuid = HypixelApiClient.parseUndashedUuid(uuidKey) ?: continue
            HypixelApiClient.resolveName(uuid).thenAccept { opt ->
                opt.ifPresent { id ->
                    COOP_MEMBER_NAMES[uuidKey] = id.name
                    onResolved?.run()
                }
            }
        }
    }

    @JvmStatic
    fun collectCoopNameLookups(members: List<ProfileFetcher.CoopMemberRef>, pending: MutableSet<String>) {
        for (member in members) {
            if (member.uuid.isBlank()) continue
            if (COOP_MEMBER_NAMES.containsKey(member.uuid)) continue
            if (member.fallbackName != shortCoopUuid(member.uuid)) {
                COOP_MEMBER_NAMES[member.uuid] = member.fallbackName
                continue
            }
            pending.add(member.uuid)
        }
    }
}
