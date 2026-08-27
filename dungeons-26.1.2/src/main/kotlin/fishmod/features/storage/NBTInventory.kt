package fishmod.features.storage

import net.minecraft.client.Minecraft
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

/**
 * A cached storage page's items, (de)serialised through vanilla [ItemStack.OPTIONAL_CODEC] then
 * gzipped + Base64 for compact on-disk storage (NoammAddons' NBTInventory, simplified).
 */
data class NBTInventory(val stacks: List<ItemStack>) {

    val rows get() = (stacks.size + 8) / 9

    fun encode(): String {
        val ops = registryOps()
        val list = ListTag()
        for (s in stacks) {
            if (s.isEmpty) { list.add(CompoundTag()); continue }
            val t: Tag = ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).result().orElse(CompoundTag())
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
                    items.add(ItemStack.OPTIONAL_CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY))
                }
                NBTInventory(items)
            }
        }.getOrNull()

        private fun registryOps() =
            (Minecraft.getInstance().connection?.registryAccess()
                ?: Minecraft.getInstance().level?.registryAccess()
                ?: error("no registry access"))
                .createSerializationContext(NbtOps.INSTANCE)
    }
}
