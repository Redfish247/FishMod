/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.data

import com.google.gson.JsonObject
import fishmod.pv.util.LegacyChatFormatting
import java.util.Locale
import net.minecraft.ChatFormatting

class GuildStatus private constructor(
	state: State?,
	name: String?,
	tag: String?,
	tagColor: String?,
	rank: String?,
	joinedMs: Long,
	members: Int,
	description: String?,
	exp: Long,
	createdMs: Long,
	error: String?
) {
	enum class State {
		IDLE,
		LOADING,
		READY,
		NONE,
		ERROR
	}

	@get:JvmName("state") val state: State = state ?: State.IDLE
	@get:JvmName("name") val name: String = name ?: ""
	@get:JvmName("tag") val tag: String = tag ?: ""
	@get:JvmName("tagColor") val tagColor: String = tagColor ?: ""
	@get:JvmName("rank") val rank: String = rank ?: ""
	@get:JvmName("joinedMs") val joinedMs: Long = maxOf(0L, joinedMs)
	@get:JvmName("members") val members: Int = maxOf(0, members)
	@get:JvmName("description") val description: String = description ?: ""
	@get:JvmName("exp") val exp: Long = maxOf(0L, exp)
	@get:JvmName("createdMs") val createdMs: Long = maxOf(0L, createdMs)
	@get:JvmName("error") val error: String = error ?: ""

	fun buttonLabel(): String = when (state) {
		State.IDLE -> "Load Guild"
		State.LOADING -> "..."
		State.READY -> if (tag.isBlank()) name else "$name [$tag]"
		State.NONE -> "No Guild"
		State.ERROR -> "Guild"
	}

	fun tagRgb(): Int {
		var fmt: ChatFormatting? = null
		if (tagColor.isNotBlank()) {
			try {
				fmt = ChatFormatting.valueOf(tagColor.uppercase(Locale.ROOT))
			} catch (ignored: IllegalArgumentException) {
			}
		}
		val rgb = LegacyChatFormatting.rgb(fmt) ?: return ACCENT
		return 0xFF000000.toInt() or rgb
	}

	companion object {
		private const val ACCENT = 0xFF5B8CFF.toInt()

		@JvmStatic
		fun idle(): GuildStatus = GuildStatus(State.IDLE, "", "", "", "", 0L, 0, "", 0L, 0L, "")

		@JvmStatic
		fun loading(): GuildStatus = GuildStatus(State.LOADING, "", "", "", "", 0L, 0, "", 0L, 0L, "")

		@JvmStatic
		fun none(): GuildStatus = GuildStatus(State.NONE, "", "", "", "", 0L, 0, "", 0L, 0L, "")

		@JvmStatic
		fun error(message: String?): GuildStatus = GuildStatus(State.ERROR, "", "", "", "", 0L, 0, "", 0L, 0L, message)

		@JvmStatic
		fun fromHypixel(root: JsonObject?, undashedUuid: String?): GuildStatus {
			if (root == null) {
				return error("Guild unavailable")
			}
			val guild = Leveling.obj(root.get("guild")) ?: return none()
			val name = str(guild, "name")
			if (name.isBlank()) {
				return none()
			}
			var rank = ""
			var joined = 0L
			val members = if (guild.has("members") && guild.get("members").isJsonArray) guild.getAsJsonArray("members") else null
			val count = members?.size() ?: 0
			val want = undashedUuid?.replace("-", "")?.lowercase(Locale.ROOT) ?: ""
			if (members != null && want.isNotBlank()) {
				for (el in members) {
					if (!el.isJsonObject) {
						continue
					}
					val m = el.asJsonObject
					val uuid = str(m, "uuid").replace("-", "").lowercase(Locale.ROOT)
					if (want != uuid) {
						continue
					}
					rank = str(m, "rank")
					joined = longOf(m, "joined")
					break
				}
			}
			return GuildStatus(
				State.READY,
				name,
				str(guild, "tag"),
				str(guild, "tagColor"),
				rank,
				joined,
				count,
				str(guild, "description"),
				longOf(guild, "exp"),
				longOf(guild, "created"),
				""
			)
		}

		private fun str(obj: JsonObject?, key: String?): String {
			if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
				return ""
			}
			return try {
				obj.get(key).asString ?: ""
			} catch (ignored: IllegalStateException) {
				""
			} catch (ignored: ClassCastException) {
				""
			} catch (ignored: NumberFormatException) {
				""
			} catch (ignored: UnsupportedOperationException) {
				""
			}
		}

		private fun longOf(obj: JsonObject?, key: String?): Long {
			if (obj == null || key == null || !obj.has(key) || !obj.get(key).isJsonPrimitive) {
				return 0L
			}
			return try {
				maxOf(0L, obj.get(key).asDouble.toLong())
			} catch (ignored: IllegalStateException) {
				0L
			} catch (ignored: ClassCastException) {
				0L
			} catch (ignored: NumberFormatException) {
				0L
			} catch (ignored: UnsupportedOperationException) {
				0L
			}
		}
	}
}
