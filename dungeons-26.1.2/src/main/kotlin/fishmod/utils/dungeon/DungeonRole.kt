package fishmod.utils.dungeon

import fishmod.utils.FishConstants
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.data.EntityTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

// Dungeon class per party member, read from the tab list; our own class also from chat.
enum class DungeonRole(val letter: String, private val colorSetting: () -> Int) {
    ARCHER("A", { DungeonSettings.roleColorArcher }),
    BERSERK("B", { DungeonSettings.roleColorBerserk }),
    HEALER("H", { DungeonSettings.roleColorHealer }),
    MAGE("M", { DungeonSettings.roleColorMage }),
    TANK("T", { DungeonSettings.roleColorTank });

    val color: Int get() = colorSetting()

    companion object {
        private const val ROLES = "(Archer|Berserk|Healer|Mage|Tank)"
        // own class from chat: picked in the lobby, solo-class buff, or the "[Mage]" style prefix
        private val OWN_ROLE_LINES = listOf(
            Regex("You have selected the $ROLES Dungeon Class!"),
            Regex("Your $ROLES stats are doubled because you are the only player using this class!")
        )
        private val CHAT_PREFIX = Regex("^\\[$ROLES]")
        // tab row: "[42] [MVP+] Name ♲ (Mage XL)"
        private val TAB_ROW = Regex("^\\[\\d+] (?:\\[[^\\]]+] )*(\\w{1,16})\\b.*?\\($ROLES ")

        private val byPlayer = ConcurrentHashMap<String, DungeonRole>()

        @JvmField
        var currentClass: DungeonRole? = null

        @JvmStatic
        fun init() {
            FishEvents.worldChange.register { reset(); false }
            FishEvents.runEnd.register { reset(); false }
            ClientReceiveMessageEvents.GAME.register { message, _ ->
                FishDiag.guard("DungeonRole.1", "class detection chat handler") { readOwnRole(message.string) }
            }
            FishEvents.tabEntry.register { entry ->
                if (entry != null && SkyblockLocation.inDungeon()) entry.displayName()?.let { readTabRow(it.string) }
                false
            }
        }

        private fun readTabRow(raw: String) {
            val m = TAB_ROW.find(FishConstants.STRIP_COLOR_REGEX.replace(raw, "").trim()) ?: return
            val name = m.groupValues[1]
            val role = parse(m.groupValues[2]) ?: return
            if (EntityTools.isClientPlayer(name)) currentClass = role
            byPlayer[name] = role
            FishDiag.check(byPlayer.size <= 5, "DungeonRole.2") { "more than 5 classed players in tab: ${byPlayer.keys}" }
        }

        private fun readOwnRole(line: String) {
            for (r in OWN_ROLE_LINES) {
                r.find(line)?.let { currentClass = parse(it.groupValues[1]); return }
            }
            // the chat prefix only fills a gap, it never overrides mid-run
            if (currentClass == null) CHAT_PREFIX.find(line)?.let { currentClass = parse(it.groupValues[1]) }
        }

        private fun parse(name: String): DungeonRole? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: null.also { FishDiag.fail("DungeonRole.3", "unknown dungeon class name '$name'") }

        @JvmStatic
        fun getClass(playerName: String?): DungeonRole? = playerName?.let { byPlayer[it] }

        @JvmStatic
        fun getClass(player: Player?): DungeonRole? = player?.let { getClass(it.name.string) }

        @JvmStatic
        fun getAll(): Map<String, DungeonRole> = Collections.unmodifiableMap(byPlayer)

        @JvmStatic
        fun isTeammate(player: Player?): Boolean =
            player != null && !EntityTools.isClientPlayer(player) && byPlayer.containsKey(player.name.string)

        @JvmStatic
        fun getColor(dungeonClass: DungeonRole?): Int = dungeonClass?.color ?: -0x1

        @JvmStatic
        fun getChar(dungeonClass: DungeonRole?): String = dungeonClass?.letter ?: "?"

        @JvmStatic
        fun getColor(name: String?): Int = getColor(getClass(name))

        @JvmStatic
        fun isClass(dungeonClass: DungeonRole?): Boolean = currentClass == dungeonClass

        private fun reset() {
            currentClass = null
            byPlayer.clear()
        }

        @JvmStatic
        fun printClasses() {
            FishUtil.addChatMessage(Component.literal("§7Party roles:"))
            byPlayer.forEach { (name, role) -> FishUtil.addChatMessage(Component.literal(" §f$name §7- §b${role.name.lowercase()}")) }
        }
    }
}
