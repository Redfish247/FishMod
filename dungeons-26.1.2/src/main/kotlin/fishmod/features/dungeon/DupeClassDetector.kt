package fishmod.features.dungeon

import fishmod.utils.FishUtil
import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.DungeonSettings
import fishmod.utils.dungeon.DungeonRole
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.events.FishEvents
import net.minecraft.network.chat.Component
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap

object DupeClassDetector {

    private val announced: MutableSet<DungeonRole> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun init() {

        FishEvents.ON_PHASE_CHANGE.register {
            if (BossPhase.runJustStarted()) {
                announced.clear()
            }
            false
        }

        FishEvents.ON_RUN_END.register {
            announced.clear()
            false
        }

        FishEvents.ON_PLAYER_ENTRY.register { _ ->
            if (!DungeonSettings.dupeClassAlert) return@register false
            try { checkForDupes() } catch (e: Exception) { FishDiag.fail("DupeClassDetector.1", "dupe class check failed (${DungeonRole.getAll().size} players)", e) }
            false
        }
    }

    private fun checkForDupes() {
        val byClass: MutableMap<DungeonRole, MutableList<String>> = EnumMap(DungeonRole::class.java)

        DungeonRole.getAll().forEach { name, dungeonClass ->
            if (dungeonClass == null) { FishDiag.fail("DupeClassDetector.2", "player $name has null dungeon class"); return@forEach }
            if (dungeonClass == DungeonRole.MAGE && DungeonSettings.dupeClassSkipMage) return@forEach
            byClass.computeIfAbsent(dungeonClass) { ArrayList() }.add(name)
        }

        byClass.forEach { (dungeonClass, names) ->
            if (names.size < 2) return@forEach
            if (!announced.add(dungeonClass)) return@forEach

            FishUtil.addChatMessage(
                Component.literal(
                    "§c§lDupe Class Detected §8> §f" + names.size +
                        " " + capitalize(dungeonClass.name) + " §7(" + joinNames(names) + ")"
                )
            )

            if (DungeonSettings.dupeClassPartyChat) {
                fishmod.utils.ChatQueue.enqueue(
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
