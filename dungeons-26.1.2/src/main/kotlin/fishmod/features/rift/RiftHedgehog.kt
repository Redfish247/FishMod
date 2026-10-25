package fishmod.features.rift

import fishmod.utils.Misc
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

// Deadgehog (Black Lagoon) state is not in the nametag: /fm rift ents dumps entity data to find it
object RiftHedgehog {

    private fun colors(c: Component): String {
        val out = linkedSetOf<String>()
        c.visit({ style, s -> if (s.isNotBlank()) out += (style.color?.serialize() ?: "none"); java.util.Optional.empty<Unit>() }, net.minecraft.network.chat.Style.EMPTY)
        return out.joinToString(",")
    }

    private fun headTex(s: net.minecraft.world.item.ItemStack): String =
        s.get(net.minecraft.core.component.DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value()?.let { "(tex ${it.takeLast(24)} len=${it.length})" }.orEmpty()

    private fun dump() {
        val mc = Minecraft.getInstance(); val me = mc.player ?: return
        val ents = mc.level?.getEntities(me, me.boundingBox.inflate(10.0)) { it !== me && EntityType.getKey(it.type).path != "arrow" }.orEmpty()
            .sortedBy { it.distanceToSqr(me) }.take(25)
        Misc.addChatMessage(Component.literal("§6[Rift ents] ${ents.size} entities (10 blocks)"))
        for (e in ents) {
            val name = e.customName?.let { "'${it.string}' col=${colors(it)}" } ?: "-"
            val eq = (e as? LivingEntity)?.let { l ->
                EquipmentSlot.entries.mapNotNull { s -> l.getItemBySlot(s).takeUnless { it.isEmpty }?.let { "${s.name.take(2)}=${it.item}${headTex(it)}" } }.joinToString(",")
            }.orEmpty()
            val fx = (e as? LivingEntity)?.activeEffects?.joinToString(",") { it.effect.unwrapKey().map { k -> k.identifier().path }.orElse("?") }.orEmpty()
            Misc.addChatMessage(Component.literal(
                "§7${EntityType.getKey(e.type).path} #${e.id} d=${"%.1f".format(Math.sqrt(e.distanceToSqr(me)))} $name${if (e is Player) " player='${e.name.string}' col=${colors(e.displayName ?: e.name)} team=${e.team?.name}" else ""} inv=${e.isInvisible} glow=${e.isCurrentlyGlowing} pose=${e.pose.name} fire=${e.isOnFire} ride=${e.vehicle?.id} eq=[$eq] fx=[$fx]"))
        }
    }

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { d, _ ->
            d.register(ClientCommands.literal("fm").then(ClientCommands.literal("rift").then(
                ClientCommands.literal("ents").executes { dump(); 1 })))
        }
    }
}
