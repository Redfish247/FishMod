package fishmod.features.fishing

import fishmod.features.fishing.FishingAlertSettings as S
import fishmod.utils.ChatQueue
import fishmod.utils.FishUtil
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component

// Opt-in sharing of big catches. Everything off by default; hotspot sharing is click-only, never automatic.
object FishingShare {
    val bigMobs: List<SeaCreature> by lazy { SeaCreatures.ALL.filter { it.rare }.sortedBy { it.name } }

    private fun checked(): Set<String> = S.shareCreatures.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun isChecked(name: String): Boolean = name in checked()

    fun setChecked(name: String, on: Boolean) {
        val set = checked().toMutableSet()
        if (on) set.add(name) else set.remove(name)
        S.shareCreatures = set.joinToString(",")
    }

    private fun coords(): String {
        val p = Minecraft.getInstance().player ?: return ""
        return "x: ${p.blockX}, y: ${p.blockY}, z: ${p.blockZ}"
    }

    fun onCatch(e: SeaCreatureCaught) {
        if (!S.shareEnabled || !e.creature.rare || !isChecked(e.creature.name)) return
        val name = e.creature.name
        if (S.shareParty) ChatQueue.enqueue("pc Caught a $name!${if (e.count > 1) " (x${e.count})" else ""}")
        if (S.shareWorldBossAll && e.creature.boss) ChatQueue.enqueue("ac $name | ${e.location.label} | ${coords()}")
    }

    fun onDrop(d: RareDrop) {
        if (!S.shareEnabled || !S.shareDrops || !S.shareParty) return
        if (d.price < S.shareDropMinPrice.coerceAtLeast(1)) return
        ChatQueue.enqueue("pc Rare drop: ${d.itemName} (${FishingAlertText.fmtCoins(d.price)})")
    }

    fun offerHotspot(perk: String, zone: String?) {
        if (!S.shareEnabled || !S.shareHotspot) return
        val where = Fishing.location().label
        val msg = "$where | $perk Hotspot${if (zone.isNullOrBlank()) "" else " at $zone"} | ${coords()}"
        val line = Component.literal("§b§lFM §8> §7Share hotspot: ")
            .append(button("§a[PARTY]", "/pchat $msg"))
            .append(Component.literal(" "))
            .append(button("§e[ALL]", "/achat $msg"))
        FishUtil.addChatMessage(line)
    }

    private fun button(label: String, cmd: String): Component =
        Component.literal(label).withStyle { it.withClickEvent(ClickEvent.RunCommand(cmd)) }
}
