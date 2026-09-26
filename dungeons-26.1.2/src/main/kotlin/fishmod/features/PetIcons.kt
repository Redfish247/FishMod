package fishmod.features

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import java.io.File

private val BRACKET_RE = Regex("""\[[^]]*]""")

// Pet head icons, learned from the /pets menu (pet name -> skin texture) and saved to disk.
object PetIcons {

    private const val FILE_PATH = "config/fishmod-pet-icons.json"
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val LEVEL = Regex("""\[Lvl\s*\d+]""")
    private val PETS_TITLE = Regex("""^(\(\d+/\d+\)\s*)?Pets.*""")

    private var textures: MutableMap<String, String> = HashMap()
    private val stacks = HashMap<String, ItemStack>()
    private var tick = 0

    init { load() }

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> if (++tick >= 10) { tick = 0; learn(mc) } }
    }

    // "[275✦] Golden Dragon" / "★ Ender Dragon" -> "golden dragon"
    @JvmStatic
    fun key(name: String): String =
        name.replace(BRACKET_RE, "").replace("✦", "").replace("★", "").trim().lowercase()

    @JvmStatic
    fun icon(petName: String?): ItemStack? {
        val k = key(petName ?: return null)
        stacks[k]?.let { return it }
        val tex = textures[k] ?: return null
        val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", tex))
        val profile = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(k.toByteArray()), "fmpet", com.mojang.authlib.properties.PropertyMap(props))
        return ItemStack(Items.PLAYER_HEAD).also {
            it.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
            stacks[k] = it
        }
    }

    private fun learn(mc: Minecraft) {
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if (!PETS_TITLE.matches(COLOR.replace(screen.title.string, "").trim())) return
        var changed = false
        for (slot in screen.menu.slots) {
            val st = slot.item
            if (st.isEmpty) continue
            val name = COLOR.replace(st.hoverName.string, "")
            val m = LEVEL.find(name) ?: continue
            val k = key(name.substring(m.range.last + 1))
            if (k.isEmpty()) continue
            val tex = st.get(DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value() ?: continue
            if (textures[k] != tex) { textures[k] = tex; stacks.remove(k); changed = true }
        }
        if (changed) save()
    }

    private fun load() {
        val f = File(FILE_PATH)
        if (!f.exists()) return
        try {
            val type = object : TypeToken<MutableMap<String, String>>() {}.type
            f.reader().use { r -> GSON.fromJson<MutableMap<String, String>>(r, type)?.let { textures = it } }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[PetIcons] load failed: {}", e.toString())
        }
    }

    private fun save() {
        try {
            val f = File(FILE_PATH)
            f.parentFile?.mkdirs()
            f.writer().use { w -> GSON.toJson(textures, w) }
        } catch (e: Exception) {
            fishmod.utils.debug.Debug.LOGGER.warn("[PetIcons] save failed: {}", e.toString())
        }
    }
}
