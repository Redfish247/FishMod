package fishmod.features.dungeon

import fishmod.features.FishHudEditor
import fishmod.features.PetHud
import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import fishmod.utils.rendering.DrawEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

object InvincibilityTracker {

    enum class Type(
        val regex: Regex,
        val maxActive: Int,
        val maxCooldown: Int,
        val label: String,
        val ids: Set<String>,
        val show: () -> Boolean,
    ) {
        SPIRIT(Regex("^Second Wind Activated! Your Spirit Mask saved your life!$"), 30, 600, "Spirit",
            setOf("SPIRIT_MASK", "STARRED_SPIRIT_MASK"), { FishSettings.invincShowSpirit }),
        BONZO(Regex("^Your (?:. )?Bonzo's Mask saved your life!$"), 60, 3600, "Bonzo",
            setOf("BONZO_MASK", "STARRED_BONZO_MASK"), { FishSettings.invincShowBonzo }),
        PHOENIX(Regex("^Your Phoenix Pet saved you from certain death!$"), 80, 1200, "Phoenix",
            emptySet(), { FishSettings.invincShowPhoenix });

        @JvmField var active = 0
        @JvmField var cooldown = 0
        @JvmField var icon: ItemStack? = null

        fun proc() { active = maxActive; cooldown = maxCooldown }
        fun tick() { if (cooldown > 0) cooldown--; if (active > 0) active-- }
        fun reset() { active = 0; cooldown = 0 }
    }

    private const val NAME = "Invincibility Timer"
    private const val LINE_H = 10
    private const val ICON_LINE_H = 18
    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.invincHudX }, { v -> FishSettings.invincHudX = v },
            { FishSettings.invincHudY }, { v -> FishSettings.invincHudY = v },
            80, 40,
            { FishSettings.invincScale }, { v -> FishSettings.invincScale = v }
        )

        Events.ON_GAME_MESSAGE.register { text ->
            if (Dungeons.displayInvincibilityTimer) {
                val s = COLOR.replace(text.string, "").trim()
                Type.entries.firstOrNull { it.regex.matches(s) }?.let { t ->
                    t.proc()
                    procTitle(t)
                    if (FishSettings.invincAnnounce) {
                        fishmod.utils.ChatQueue.enqueue("pc ${t.label} Procced!")
                    }
                }
            }
            false
        }
        Events.ON_SERVER_TICK.register { Type.entries.forEach { it.tick() }; learnIcons(); false }
        Events.ON_WORLD_CHANGE.register { Type.entries.forEach { it.reset() }; false }

        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!Dungeons.displayInvincibilityTimer || !FishSettings.invincShowCooldown) return@register
            drawSlotBar(ctx, stack, x, y)
        }
    }

    private fun procTitle(t: Type) {
        if (!FishSettings.invincProcTitle) return
        val (text, color) = when (t) {
            Type.SPIRIT -> FishSettings.invincProcSpiritText to FishSettings.invincProcSpiritColor
            Type.BONZO -> FishSettings.invincProcBonzoText to FishSettings.invincProcBonzoColor
            Type.PHOENIX -> FishSettings.invincProcPhoenixText to FishSettings.invincProcPhoenixColor
        }
        fishmod.utils.Misc.forceTitle(
            net.minecraft.network.chat.Component.literal(text.ifBlank { "${t.label} Procced!" }).withColor(color and 0xFFFFFF),
            net.minecraft.network.chat.Component.empty(), FishSettings.invincProcTitleMs
        )
        if (FishSettings.invincProcSound) fishmod.utils.sound.SoundManager.play(
            fishmod.utils.sound.SoundManager.preset(FishSettings.invincProcSoundName),
            FishSettings.invincProcVolume.coerceIn(0, 500) / 100f,
            FishSettings.invincProcPitch.toFloat().coerceIn(0f, 2f),
            "invincProc", 0
        )
    }

    private var iconTick = 0

    private fun storedTexture(t: Type): String = when (t) {
        Type.SPIRIT -> FishSettings.invincIconSpirit
        Type.BONZO -> FishSettings.invincIconBonzo
        Type.PHOENIX -> FishSettings.invincIconPhoenix
    }

    private fun storeTexture(t: Type, tex: String) {
        if (storedTexture(t) == tex) return
        when (t) {
            Type.SPIRIT -> FishSettings.invincIconSpirit = tex
            Type.BONZO -> FishSettings.invincIconBonzo = tex
            Type.PHOENIX -> FishSettings.invincIconPhoenix = tex
        }
        t.icon = null
        runCatching { fishmod.utils.config.FishConfig.manager.save() }
    }

    private fun texture(stack: ItemStack): String? =
        stack.get(net.minecraft.core.component.DataComponents.PROFILE)?.partialProfile()?.properties()?.get("textures")?.firstOrNull()?.value()

    private fun icon(t: Type): ItemStack? {
        t.icon?.let { return it }
        val tex = storedTexture(t).ifEmpty { return null }
        val props = com.google.common.collect.ImmutableMultimap.of("textures", com.mojang.authlib.properties.Property("textures", tex))
        val profile = com.mojang.authlib.GameProfile(java.util.UUID(0L, t.ordinal.toLong()), "fmicon", com.mojang.authlib.properties.PropertyMap(props))
        return ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD).also {
            it.set(net.minecraft.core.component.DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(profile))
            t.icon = it
        }
    }

    private fun learnIcons() {
        if (!FishSettings.invincIcons || ++iconTick < 20) return
        iconTick = 0
        val mc = Minecraft.getInstance()
        val allKnown = Type.entries.all { storedTexture(it).isNotEmpty() }
        if (allKnown && mc.screen !is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) return
        if (!fishmod.utils.Location.inSkyblock()) return
        val player = mc.player ?: return
        val inv = player.inventory
        val stacks = (0 until inv.containerSize).map { inv.getItem(it) } + player.getItemBySlot(EquipmentSlot.HEAD)
        for (st in stacks) {
            if (st.isEmpty) continue
            val id = ItemUtil.getId(st) ?: continue
            val t = Type.entries.firstOrNull { id in it.ids } ?: continue
            texture(st)?.let { storeTexture(t, it) }
        }
        val screen = mc.screen as? net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*> ?: return
        if (!fishmod.utils.ScreenTitle.plain(screen).contains("Pets")) return
        for (slot in screen.menu.slots) {
            val st = slot.item
            if (st.isEmpty || !st.hoverName.string.contains("Phoenix")) continue
            texture(st)?.let { storeTexture(Type.PHOENIX, it) }
            break
        }
    }

    private fun visible(t: Type): Boolean {
        if (!t.show()) return false
        return when (FishSettings.invincShowWhen) {
            "Always" -> true
            "Active" -> t.active > 0
            "Cooldown" -> t.cooldown > 0
            else -> t.active > 0 || t.cooldown > 0
        }
    }

    private fun equipped(t: Type): Boolean {
        return when (t) {
            Type.PHOENIX -> PetHud.activePetName()?.contains("Phoenix", ignoreCase = true) == true
            else -> {
                val helmet = Minecraft.getInstance().player?.getItemBySlot(EquipmentSlot.HEAD) ?: return false
                ItemUtil.getId(helmet) in t.ids
            }
        }
    }

    private fun labelColor(t: Type): String = if (equipped(t)) "§e" else "§7"

    private fun stateColor(t: Type): String {
        if (!Dungeons.useStatusColorForInvincibility) return "§7"
        return when {
            t.active > 0 -> "§6"
            t.cooldown > 0 -> "§c"
            else -> "§a"
        }
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!Dungeons.displayInvincibilityTimer || !Location.inDungeon()) return
        if (FishSettings.invincShowInBoss && !Phase.inBoss()) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return

        val shown = Type.entries.filter { visible(it) }
        if (shown.isEmpty()) return

        val sc = FishSettings.invincScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.invincHudX.toFloat(), FishSettings.invincHudY.toFloat())
        ctx.pose().scale(sc, sc)
        var y = 0
        for (t in shown) {
            val c = stateColor(t)
            val value = when {
                t.active > 0 || t.cooldown > 0 ->
                    if (Dungeons.InvincibilityDuration)
                        String.format("%.1fs", (if (t.active > 0) t.active else t.cooldown) / 20f)
                    else "●"
                else -> "✔"
            }
            val ic = if (FishSettings.invincIcons) icon(t) else null
            if (ic != null) {
                ctx.item(ic, 0, y)
                ctx.text(mc.font, "$c$value", 18, y + 4, -1, true)
                y += ICON_LINE_H
            } else {
                ctx.text(mc.font, "${labelColor(t)}${t.label} $c$value", 0, y, -1, true)
                y += LINE_H
            }
        }
        ctx.pose().popMatrix()
    }

    private fun drawSlotBar(ctx: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int) {
        if (stack == null || stack.isEmpty) return
        val id = ItemUtil.getId(stack) ?: return
        val t = Type.entries.firstOrNull { id in it.ids } ?: return
        if (t.cooldown <= 0) return
        val frac = t.cooldown.toFloat() / t.maxCooldown
        val w = (13 * (1f - frac)).toInt().coerceIn(0, 13)
        ctx.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000.toInt())
        val col = if (frac > 0.5f) 0xFFFF5555.toInt() else 0xFF55FF55.toInt()
        ctx.fill(x + 2, y + 13, x + 2 + w, y + 14, col)
    }
}
