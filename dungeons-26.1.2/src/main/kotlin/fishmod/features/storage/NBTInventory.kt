package fishmod.features.storage

import net.minecraft.client.Minecraft
import fishmod.utils.debug.FishDiag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

class NBTInventory(val stacks: List<ItemStack>) {

    val rows get() = (stacks.size + 8) / 9

    fun encode(): String {
        val ops = registryOps()
        val list = ListTag()
        for (s in stacks) {
            if (s.isEmpty) { list.add(CompoundTag()); continue }
            val res = ItemStack.OPTIONAL_CODEC.encodeStart(ops, s)
            res.error().ifPresent { err -> FishDiag.fail("NBTInventory.1", "failed to encode ${s.hoverName.string}: ${err.message()}") }
            val t: Tag = res.result().orElse(CompoundTag())
            list.add(t)
        }
        val root = CompoundTag().apply { put("i", list) }
        val baos = ByteArrayOutputStream()
        NbtIo.writeCompressed(root, baos)
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    companion object {
        fun decode(encoded: String): NBTInventory? = runCatching {
            val ops = registryOps()
            val bytes = Base64.getDecoder().decode(encoded)
            ByteArrayInputStream(bytes).use { bais ->
                val root = NbtIo.readCompressed(bais, NbtAccounter.unlimitedHeap())
                val list = root.getListOrEmpty("i")
                val items = ArrayList<ItemStack>(list.size)
                for (i in list.indices) {
                    val tag = list.getCompoundOrEmpty(i)
                    if (tag.isEmpty) { items.add(ItemStack.EMPTY); continue }
                    val parsed = ItemStack.OPTIONAL_CODEC.parse(ops, tag)
                    parsed.error().ifPresent { err -> FishDiag.fail("NBTInventory.2", "failed to decode stored item $i: ${err.message()}") }
                    items.add(parsed.result().orElse(ItemStack.EMPTY))
                }
                NBTInventory(items)
            }
        }.onFailure { FishDiag.fail("NBTInventory.3", "failed to decode storage page (${encoded.length} chars)", it) }.getOrNull()

        private fun registryOps() =
            (Minecraft.getInstance().connection?.registryAccess()
                ?: Minecraft.getInstance().level?.registryAccess()
                ?: error("no registry access"))
                .createSerializationContext(NbtOps.INSTANCE)
    }
}
