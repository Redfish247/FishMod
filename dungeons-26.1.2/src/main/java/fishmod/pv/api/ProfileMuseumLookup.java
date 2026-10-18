/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.api;

import com.google.gson.JsonObject;
import fishmod.pv.data.Leveling;

/** Resolves museum member JSON from Hypixel museum responses. */
final class ProfileMuseumLookup {
	private ProfileMuseumLookup() {
	}

	static JsonObject findMuseumMember(JsonObject museumRoot, String profileId, String undashed) {
		if (museumRoot == null) {
			return null;
		}
		JsonObject members = Leveling.obj(museumRoot.get("members"));
		if (members != null) {
			JsonObject direct = findMember(members, undashed);
			if (direct != null) {
				return direct;
			}
		}
		if (profileId != null && museumRoot.has(profileId) && museumRoot.get(profileId).isJsonObject()) {
			JsonObject profileMuseum = museumRoot.getAsJsonObject(profileId);
			JsonObject nestedMembers = Leveling.obj(profileMuseum.get("members"));
			if (nestedMembers != null) {
				return findMember(nestedMembers, undashed);
			}
			return profileMuseum;
		}
		return findMember(museumRoot, undashed);
	}

	static JsonObject findMember(JsonObject members, String undashed) {
		if (members == null) {
			return null;
		}
		if (members.has(undashed) && members.get(undashed).isJsonObject()) {
			return members.getAsJsonObject(undashed);
		}
		for (var entry : members.entrySet()) {
			if (entry.getKey() != null && entry.getKey().replace("-", "").equalsIgnoreCase(undashed)
				&& entry.getValue().isJsonObject()) {
				return entry.getValue().getAsJsonObject();
			}
		}
		return null;
	}
}
