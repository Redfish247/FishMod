package fishmod.features.dungeon

import fishmod.utils.Misc
import fishmod.utils.config.values.Dungeons
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import net.minecraft.network.chat.Component
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

object DupeClassDetector {

    private val announced: MutableSet<DungeonClass> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun init() {

        Events.ON_PHASE_CHANGE.register {
            if (Phase.runJustStarted()) {
                announced.clear()
            }
            false
        }

        Events.ON_RUN_END.register {
            announced.clear()
            false
        }

        Events.ON_PLAYER_ENTRY.register { _ ->
            if (!Dungeons.detectDuplicateClass) return@register false
            checkForDupes()
            false
        }
    }

    private fun checkForDupes() {
        val byClass: MutableMap<DungeonClass, MutableList<String>> = EnumMap(DungeonClass::class.java)

        DungeonClass.getAll().forEach { name, dungeonClass ->
            if (dungeonClass == null) return@forEach
            if (dungeonClass == DungeonClass.MAGE && Dungeons.ignoreDupeMage) return@forEach
            byClass.computeIfAbsent(dungeonClass) { ArrayList() }.add(name)
        }

        byClass.forEach { (dungeonClass, names) ->
            if (names.size < 2) return@forEach
            if (!announced.add(dungeonClass)) return@forEach

            Misc.addChatMessage(
                Component.literal(
                    "§c§lDupe Class Detected §8> §f" + names.size +
                        " " + capitalize(dungeonClass.name) + " §7(" + joinNames(names) + ")"
                )
            )

            if (Dungeons.dupeClassPartyChat) {
                Misc.executeCommand(
                    "pc Dupe Class Detected > " + names.size +
                        " " + capitalize(dungeonClass.name) + " (" + joinNames(names) + ")"
                )
            }
        }
    }

    private fun joinNames(names: List<String>): String {
        if (names.size == 1) return names[0]
        if (names.size == 2) return names[0] + " & " + names[1]
        return names.subList(0, names.size - 1).joinToString(", ") + " & " + names[names.size - 1]
    }

    private fun capitalize(name: String): String {
        return name[0] + name.substring(1).lowercase()
    }
}
