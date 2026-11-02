package fishmod.utils.dungeon

import fishmod.utils.FishUtil
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

enum class DungeonRole {
    ARCHER, BERSERK, HEALER, MAGE, TANK;

    companion object {
        private val PATTERN: Pattern = Pattern.compile("^\\[(Archer|Berserk|Healer|Mage|Tank)]")
        private val NAME_CLASS_PATTERN: Pattern = Pattern.compile("^\\[\\d+] (?:\\[[^\\]]+] )*(\\w{1,16})\\b.*?\\((Archer|Berserk|Healer|Mage|Tank) ")
        private val STATS_DOUBLED_PATTERN: Pattern =
            Pattern.compile("Your (Archer|Berserk|Healer|Mage|Tank) stats are doubled because you are the only player using this class!")

        private val SELECTED_PATTERN: Pattern =
            Pattern.compile("You have selected the (Archer|Berserk|Healer|Mage|Tank) Dungeon Class!")

        private val nameClassMap = ConcurrentHashMap<String, DungeonRole>()

        @JvmField
        var currentClass: DungeonRole? = null

        @JvmStatic
        fun init() {

            FishEvents.ON_WORLD_CHANGE.register { reset(); false }
            FishEvents.ON_RUN_END.register { reset(); false }

            ClientReceiveMessageEvents.GAME.register { message, _ ->
                try {
                    onGameMessage(message)
                } catch (t: Throwable) {
                    FishDiag.fail("DungeonClass.1", "class detection chat handler", t)
                }
            }

            FishEvents.ON_PLAYER_ENTRY.register { receivedEntry ->
                if (receivedEntry == null || !fishmod.utils.SkyblockLocation.inDungeon()) return@register false
                val text = receivedEntry.displayName() ?: return@register false
                val string = fishmod.utils.FishConstants.STRIP_COLOR_REGEX.replace(text.string, "").trim()
                val matcher = NAME_CLASS_PATTERN.matcher(string)

                if (matcher.find()) {
                    val name = matcher.group(1)
                    val className = parseClass(matcher.group(2))
                    if (className == null) return@register false

                    if (EntityTools.isClientPlayer(name)) {
                        currentClass = className
                    }

                    nameClassMap[name] = className
                    FishDiag.check(nameClassMap.size <= 5, "DungeonClass.2") { "more than 5 classed players in tab: ${nameClassMap.keys}" }
                    return@register false
                }

                false
            }
        }

        private fun onGameMessage(message: Component) {
                val string = message.string

                val selected = SELECTED_PATTERN.matcher(string)
                if (selected.find()) {
                    currentClass = parseClass(selected.group(1))
                    return
                }
                val doubled = STATS_DOUBLED_PATTERN.matcher(string)
                if (doubled.find()) {
                    currentClass = parseClass(doubled.group(1))
                    return
                }

                if (!BossPhase.runStarted() && currentClass != null) return

                val matcher = PATTERN.matcher(string)
                if (matcher.find() && currentClass == null) {
                    currentClass = parseClass(matcher.group(1))
                }
        }

        private fun parseClass(name: String): DungeonRole? {
            return try {
                valueOf(name.uppercase())
            } catch (e: IllegalArgumentException) {
                FishDiag.fail("DungeonClass.3", "unknown dungeon class name '$name'", e)
                null
            }
        }

        @JvmStatic
        fun getClass(playerName: String?): DungeonRole? {
            if (playerName == null) return null
            return nameClassMap[playerName]
        }

        @JvmStatic
        fun getClass(player: Player?): DungeonRole? {
            if (player == null) return null
            return getClass(player.name.string)
        }

        @JvmStatic
        fun getAll(): Map<String, DungeonRole> = Collections.unmodifiableMap(nameClassMap)

        @JvmStatic
        fun isTeammate(player: Player?): Boolean {
            if (player == null || EntityTools.isClientPlayer(player)) return false
            val name = player.name.string
            return nameClassMap.containsKey(name)
        }

        @JvmStatic
        fun getColor(dungeonClass: DungeonRole?): Int {
            if (dungeonClass == null) return -0x1

            return when (dungeonClass) {
                ARCHER -> DungeonSettings.archerColor
                BERSERK -> DungeonSettings.berserkColor
                HEALER -> DungeonSettings.healerColor
                MAGE -> DungeonSettings.mageColor
                TANK -> DungeonSettings.tankColor
            }
        }

        @JvmStatic
        fun getChar(dungeonClass: DungeonRole?): String {
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
        fun isClass(dungeonClass: DungeonRole?): Boolean = currentClass == dungeonClass

        private fun reset() {
            currentClass = null
            nameClassMap.clear()
        }

        @JvmStatic
        fun printClasses() {
            FishUtil.addChatMessage(Component.literal("Classes"))
            nameClassMap.forEach { (name, clazz) ->
                FishUtil.addChatMessage(Component.literal("Name: $name" + "Class: " + clazz.name))
            }
        }
    }
}
