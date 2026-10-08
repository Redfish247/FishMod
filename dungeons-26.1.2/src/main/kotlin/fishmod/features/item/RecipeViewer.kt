package fishmod.features.item

import com.google.gson.JsonObject
import fishmod.features.croesus.LootIcons
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.item.component.ResolvableProfile
import org.lwjgl.glfw.GLFW
import kotlin.math.ceil

object RecipeViewer {

    data class Ing(val id: String, val count: Int)
    data class Recipe(val kind: String, val detail: String, val inputs: List<Ing?>, val grid: Boolean, val output: Ing)

    const val COIN = "SKYBLOCK_COIN"
    private val TEXTURE_RE = Regex("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"")
    private val GRID_KEYS = listOf("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")
    private val LAST_PHASE = Identifier.fromNamespaceAndPath("fishmod", "recipe_viewer_last")

    @Volatile private var index: Map<String, List<Recipe>>? = null
    private val stacks = HashMap<String, ItemStack>()

    private var holdId: String? = null
    private var holdStart = 0L
    private var lastSeen = 0L
    private var fired = false

    @JvmStatic
    fun init() {
        ItemTooltipCallback.EVENT.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, LAST_PHASE)
        ItemTooltipCallback.EVENT.register(LAST_PHASE, ItemTooltipCallback { stack, _, _, lines ->
            try { onTooltip(stack, lines) } catch (e: Exception) { FishDiag.fail("RecipeViewer.1", "recipe tooltip failed", e) }
        })
    }

    private fun onTooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (!FishSettings.recipeViewer || !Location.inSkyblock() || stack.isEmpty) return
        val mc = Minecraft.getInstance()
        val screen = mc.screen as? AbstractContainerScreen<*> ?: return
        if ((screen as HandledScreenAccessor).`fishmod$getHoveredSlot`()?.item !== stack) return
        val id = ItemUtil.getId(stack) ?: return
        NeuRepo.ensureLoaded()
        if (recipesFor(id).isEmpty() && NeuRepo.get(id) == null) return

        val now = System.currentTimeMillis()
        val shift = InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(mc.window, GLFW.GLFW_KEY_RIGHT_SHIFT)
        if (!shift || id != holdId || now - lastSeen > 200) { holdId = id; holdStart = now; fired = false }
        lastSeen = now
        if (!shift) {
            if (FishSettings.recipeViewerHint) lines.add(Component.literal("§8Hold Shift for recipe"))
            return
        }
        val total = FishSettings.recipeViewerHoldMs.coerceAtLeast(100)
        val p = ((now - holdStart).toFloat() / total).coerceIn(0f, 1f)
        val filled = (p * 20).toInt()
        lines.add(Component.literal("§7Recipe §a" + "|".repeat(filled) + "§8" + "|".repeat(20 - filled)))
        if (p >= 1f && !fired) {
            fired = true
            mc.execute { mc.setScreen(RecipeScreen(id, screen)) }
        }
    }

    fun recipesFor(id: String): List<Recipe> {
        if (!NeuRepo.isLoaded()) return emptyList()
        val idx = index ?: buildIndex().also { index = it }
        return idx[id] ?: emptyList()
    }

    private fun buildIndex(): Map<String, List<Recipe>> {
        val out = HashMap<String, MutableList<Recipe>>()
        fun add(r: Recipe) { out.getOrPut(r.output.id) { ArrayList() }.add(r) }
        for (id in NeuRepo.ids()) {
            val o = NeuRepo.get(id) ?: continue
            runCatching {
                o.getAsJsonObject("recipe")?.let { add(crafting(id, it)) }
                o.getAsJsonArray("recipes")?.forEach { el -> parse(id, o, el.asJsonObject).forEach(::add) }
            }.onFailure { FishDiag.fail("RecipeViewer.2", "bad NEU recipe in $id", it) }
        }
        return out
    }

    private fun crafting(id: String, r: JsonObject): Recipe {
        val grid = GRID_KEYS.map { k -> r.get(k)?.asString?.let(::ing) }
        val outId = r.get("overrideOutputId")?.asString ?: id
        return Recipe("Crafting", "", grid, true, Ing(outId, r.get("count")?.asInt ?: 1))
    }

    private fun parse(id: String, o: JsonObject, r: JsonObject): List<Recipe> = when (r.get("type")?.asString) {
        "crafting", null -> listOf(crafting(id, r))
        "forge" -> {
            val secs = r.get("duration")?.asLong ?: 0L
            val inputs = r.getAsJsonArray("inputs")?.mapNotNull { ing(it.asString) } ?: emptyList()
            listOf(Recipe("Forge", if (secs > 0) duration(secs) else "", inputs, false, Ing(r.get("overrideOutputId")?.asString ?: id, r.get("count")?.asInt ?: 1)))
        }
        "npc_shop" -> {
            val cost = r.getAsJsonArray("cost")?.mapNotNull { ing(it.asString) } ?: emptyList()
            val res = r.get("result")?.asString?.let(::ing)
            if (res == null) emptyList() else listOf(Recipe("Shop", clean(o.get("displayname")?.asString ?: id), cost, false, res))
        }
        "trade" -> {
            val cost = r.get("cost")?.asString?.let(::ing)
            val res = r.get("result")?.asString?.let(::ing)
            if (res == null) emptyList() else listOf(Recipe("Trade", "", listOfNotNull(cost), false, res))
        }
        "drops" -> {
            val mob = clean(r.get("name")?.asString ?: o.get("displayname")?.asString ?: id)
            r.getAsJsonArray("drops")?.mapNotNull { d ->
                val dObj = d.asJsonObject
                val drop = dObj.get("id")?.asString?.let(::ing) ?: return@mapNotNull null
                val chance = dObj.get("chance")?.asString?.let { " · $it" } ?: ""
                Recipe("Drop", mob + chance, listOf(Ing(id, 1)), false, drop)
            } ?: emptyList()
        }
        else -> emptyList()
    }

    private fun ing(s: String): Ing? {
        if (s.isBlank()) return null
        val i = s.lastIndexOf(':')
        if (i < 0) return Ing(s, 1)
        val n = s.substring(i + 1).toDoubleOrNull() ?: return Ing(s, 1)
        return Ing(s.substring(0, i), ceil(n).toInt().coerceAtLeast(1))
    }

    private fun duration(s: Long): String = when {
        s >= 3600 -> "${s / 3600}h" + if (s % 3600 >= 60) " ${(s % 3600) / 60}m" else ""
        s >= 60 -> "${s / 60}m"
        else -> "${s}s"
    }

    fun clean(s: String): String = s.replace(Regex("§."), "")

    fun displayName(id: String): String =
        if (id == COIN) "§6Coins" else NeuRepo.get(id)?.get("displayname")?.asString ?: id

    fun lore(id: String): List<String> =
        NeuRepo.get(id)?.getAsJsonArray("lore")?.map { it.asString } ?: emptyList()

    fun stackFor(id: String): ItemStack = stacks.getOrPut(id) {
        runCatching { build(id) }.onFailure { FishDiag.fail("RecipeViewer.3", "icon build failed for $id", it) }.getOrNull()
            ?: ItemStack(Items.PAPER)
    }

    private fun build(id: String): ItemStack {
        if (id == COIN) return ItemStack(Items.GOLD_NUGGET)
        val o = NeuRepo.get(id)
        val itemId = o?.get("itemid")?.asString ?: ""
        val texture = o?.get("nbttag")?.asString?.let { TEXTURE_RE.find(it)?.groupValues?.get(1) }
        val stack = when {
            itemId == "minecraft:skull" && texture != null -> head(id, texture)
            else -> LootIcons.icon(id)?.copy()
                ?: BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(itemId) ?: Identifier.withDefaultNamespace("paper")).map { ItemStack(it) }.orElse(ItemStack(Items.PAPER))
        }
        val plain = Style.EMPTY.withItalic(false)
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(displayName(id)).withStyle(plain))
        stack.set(DataComponents.LORE, ItemLore(lore(id).map { Component.literal(it).withStyle(plain) }))
        return stack
    }

    private fun head(id: String, texture: String): ItemStack {
        val stack = ItemStack(Items.PLAYER_HEAD)
        val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", texture))
        val profile = com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(id.toByteArray()), "fmrecipe", com.mojang.authlib.properties.PropertyMap(props))
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
        return stack
    }
}
