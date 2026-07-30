package fishmod.utils.dungeon

import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.data.EntityUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

enum class DungeonClass {
    ARCHER, BERSERK, HEALER, MAGE, TANK;

    companion object {
        private val PATTERN: Pattern = Pattern.compile("^\\[(Archer|Berserk|Healer|Mage|Tank)]")
        private val NAME_CLASS_PATTERN: Pattern = Pattern.compile("^\\[\\d+] (.+) \\((Archer|Berserk|Healer|Mage|Tank) ")

        /** Matches the "stats are doubled" message: the most reliable signal of the local player's own class. */
        private val STATS_DOUBLED_PATTERN: Pattern =
            Pattern.compile("Your (Archer|Berserk|Healer|Mage|Tank) stats are doubled because you are the only player using this class!")

        private val nameClassMap = ConcurrentHashMap<String, DungeonClass>()

        @JvmField
        var currentClass: DungeonClass? = null

        @JvmStatic
        fun init() {

            Events.ON_PHASE_CHANGE.register {
                if (Phase.runJustStarted()) {
                    reset()
                }
                false
            }

            Events.ON_RUN_END.register {
                reset()
                false
            }

            ClientReceiveMessageEvents.GAME.register { message, _ ->
                val string = message.string

                // Authoritative own-class signal — always wins, regardless of run state.
                val doubled = STATS_DOUBLED_PATTERN.matcher(string)
                if (doubled.find()) {
                    currentClass = parseClass(doubled.group(1))
                    return@register
                }

                if (!Phase.runStarted() && currentClass != null) return@register

                // Fallback only: the dungeon chat "[Class]" prefix is the SPEAKER's class, not necessarily
                // ours — only use it to seed currentClass when nothing more reliable has set it yet.
                val matcher = PATTERN.matcher(string)
                if (matcher.find() && currentClass == null) {
                    currentClass = parseClass(matcher.group(1))
                }
            }

            Events.ON_PLAYER_ENTRY.register { receivedEntry ->
                if (receivedEntry == null) return@register false
                val text = receivedEntry.displayName() ?: return@register false
                val string = text.string
                val matcher = NAME_CLASS_PATTERN.matcher(string)

                if (matcher.find()) {
                    val name = matcher.group(1).replace(Regex(" .+"), "")
                    val className = parseClass(matcher.group(2))
                    if (className == null) return@register false

                    if (EntityUtil.isClientPlayer(name)) {
                        currentClass = className
                    }

                    nameClassMap[name] = className
                    return@register false
                }

                false
            }
        }

        private fun parseClass(name: String): DungeonClass? {
            return try {
                valueOf(name.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        @JvmStatic
        fun getClass(playerName: String?): DungeonClass? {
            if (playerName == null) return null
            return nameClassMap[playerName]
        }

        @JvmStatic
        fun getClass(player: PlayerEntity?): DungeonClass? {
            if (player == null) return null
            return getClass(player.name.string)
        }

        @JvmStatic
        fun getAll(): Map<String, DungeonClass> = Collections.unmodifiableMap(nameClassMap)

        @JvmStatic
        fun isTeammate(player: PlayerEntity?): Boolean {
            if (player == null || EntityUtil.isClientPlayer(player)) return false
            val name = player.name.string
            return nameClassMap.containsKey(name)
        }

        @JvmStatic
        fun getColor(dungeonClass: DungeonClass?): Int {
            if (dungeonClass == null) return -0x1

            return when (dungeonClass) {
                ARCHER -> Dungeons.archerColor
                BERSERK -> Dungeons.berserkColor
                HEALER -> Dungeons.healerColor
                MAGE -> Dungeons.mageColor
                TANK -> Dungeons.tankColor
            }
        }

        @JvmStatic
        fun getChar(dungeonClass: DungeonClass?): String {
            if (dungeonClass == null) return "?"

            return when (dungeonClass) {
                ARCHER -> "A"
                BERSERK -> "B"
                HEALER -> "H"
                MAGE -> "M"
                TANK -> "T"
            }
        }

        @JvmStatic
        fun getColor(name: String?): Int = getColor(getClass(name))

        @JvmStatic
        fun isClass(dungeonClass: DungeonClass?): Boolean = currentClass == dungeonClass

        @JvmStatic
        fun isArchTeam(): Boolean = currentClass == ARCHER || currentClass == TANK

        @JvmStatic
        fun isBersTeam(): Boolean = currentClass == BERSERK || currentClass == MAGE

        private fun reset() {
            currentClass = null
            nameClassMap.clear()
        }

        @JvmStatic
        fun printClasses() {
            Misc.addChatMessage(Text.literal("Classes"))
            nameClassMap.forEach { (name, clazz) ->
                Misc.addChatMessage(Text.literal("Name: $name" + "Class: " + clazz.name))
            }
        }
    }
}
