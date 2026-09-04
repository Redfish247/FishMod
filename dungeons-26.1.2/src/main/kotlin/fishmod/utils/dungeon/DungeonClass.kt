package fishmod.utils.dungeon

import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.data.EntityUtil
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

enum class DungeonClass {
    ARCHER, BERSERK, HEALER, MAGE, TANK;

    companion object {
        private val PATTERN: Pattern = Pattern.compile("^\\[(Archer|Berserk|Healer|Mage|Tank)]")
        private val NAME_CLASS_PATTERN: Pattern = Pattern.compile("^\\[\\d+] (.+) \\((Archer|Berserk|Healer|Mage|Tank) ")

        /** Most reliable signal of the local player's own class — fires at the start of a run. */
        private val STATS_DOUBLED_PATTERN: Pattern =
            Pattern.compile("Your (Archer|Berserk|Healer|Mage|Tank) stats are doubled because you are the only player using this class!")

        private val SELECTED_PATTERN: Pattern =
            Pattern.compile("You have selected the (Archer|Berserk|Healer|Mage|Tank) Dungeon Class!")

        private val nameClassMap = ConcurrentHashMap<String, DungeonClass>()

        @JvmField
        var currentClass: DungeonClass? = null

        @JvmStatic
        fun init() {

            // Clear the class map when we actually load a new instance / the run ends — NOT on
            // every phase change during clear (runJustStarted() stays true the whole clear phase,
            // so that wiped everyone's class mid-run and only some refilled from the tab list).
            Events.ON_WORLD_CHANGE.register { reset(); false }
            Events.ON_RUN_END.register { reset(); false }

            ClientReceiveMessageEvents.GAME.register { message, _ ->
                val string = message.string

                // Authoritative own-class signals — always win, regardless of run state.
                val selected = SELECTED_PATTERN.matcher(string)
                if (selected.find()) {
                    currentClass = parseClass(selected.group(1))
                    return@register
                }
                val doubled = STATS_DOUBLED_PATTERN.matcher(string)
                if (doubled.find()) {
                    currentClass = parseClass(doubled.group(1))
                    return@register
                }

                if (!Phase.runStarted() && currentClass != null) return@register

                // the "[Class]" chat prefix is the SPEAKER's class, not necessarily ours — only a fallback seed
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
                    // group(1) is everything between "[lvl] " and " (Class"; it may carry rank tags
                    // ("[YOUTUBE] Future77"), so the IGN is the last whitespace-separated token.
                    val name = matcher.group(1).trim().substringAfterLast(' ')
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
        fun getClass(player: Player?): DungeonClass? {
            if (player == null) return null
            return getClass(player.name.string)
        }

        @JvmStatic
        fun getAll(): Map<String, DungeonClass> = Collections.unmodifiableMap(nameClassMap)

        @JvmStatic
        fun isTeammate(player: Player?): Boolean {
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
            Misc.addChatMessage(Component.literal("Classes"))
            nameClassMap.forEach { (name, clazz) ->
                Misc.addChatMessage(Component.literal("Name: $name" + "Class: " + clazz.name))
            }
        }
    }
}
