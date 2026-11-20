package fishmod.features.dungeon

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.features.dungeon.DungeonExtraSettings as S
import fishmod.features.item.fishmodCustomDataTag
import fishmod.utils.FishUtil
import fishmod.utils.SidebarCache
import fishmod.utils.SkyblockLocation
import fishmod.utils.debug.FishDiag
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

// Deathmite/skull glow, skull hide, teammate HP alert + HUD, class level colour in tab
object DungeonExtras {

    private const val HUD = "Party HP"
    // from SkyHanni (ScoreboardPattern.teammatesPattern)
    private val TEAMMATE = Regex("(?:§.)*\\[(\\w)] (?:§.)*(\\w{2,16}) (?:§.)*?§(.)(?:§.)*([\\d,.]+[kM]?|DEAD)")
    // from SkyHanni (DungeonApi.playerDungeonTeamPattern)
    private val TAB_TEAM = Regex("^(\\[\\d+]) (\\[[^]]+])? ?(\\S+)\\s?[^(]* \\((\\S+) ([CLXVI0-9]+)\\)$")

    class Mate(val cls: String, val name: String, val color: Char, val hp: String)

    @Volatile private var glow: Map<Int, Int> = emptyMap()
    @Volatile private var skulls: Set<Int> = emptySet()
    @Volatile var mates: List<Mate> = emptyList(); private set
    private val lastAlert = HashMap<String, Long>()
    private var tick = 0
    private var sbVersion = -1

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try { onTick(mc) } catch (t: Throwable) { FishDiag.fail("DungeonExtras.1", "dungeon extras tick failed", t) }
        }
        FishHudEditor.register(HUD, { S.partyHpHudX }, { v -> S.partyHpHudX = v }, { S.partyHpHudY }, { v -> S.partyHpHudY = v },
            100, 40, { S.partyHpHudScale }, { v -> S.partyHpHudScale = v })
        HudPreview.register(HUD) {
            val l = listOf(Mate("H", "Healer", 'a', "1,520"), Mate("T", "Tank", 'c', "310"))
            HudExample(100, HudPreview.TEXT_H * l.size) { ctx -> draw(ctx, l) }
        }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "party_hp")) { ctx, _ ->
            if (!FishHudEditor.isOpen()) render(ctx)
        }
        fishmod.utils.rendering.DrawHooks.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!S.kismetCount || stack == null || stack.isEmpty) return@register
            try { kismet(ctx, stack, x, y) } catch (t: Throwable) { FishDiag.fail("DungeonExtras.3", "kismet count draw failed", t) }
        }
    }

    // from SkyHanni (CroesusChestTracker: "Reroll Chest" item, "You already rerolled a chest!" lore)
    private fun kismet(ctx: GuiGraphicsExtractor, stack: net.minecraft.world.item.ItemStack, x: Int, y: Int) {
        if (fishmod.utils.TextStrip.plain(stack.hoverName.string).trim() != "Reroll Chest") return
        val lore = stack.get(net.minecraft.core.component.DataComponents.LORE)?.lines() ?: return
        if (lore.any { fishmod.utils.TextStrip.plain(it.string).trim() == "You already rerolled a chest!" }) return
        val inv = Minecraft.getInstance().player?.inventory ?: return
        var n = 0
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (!s.isEmpty && s.fishmodCustomDataTag()?.getString("id")?.orElse("") == "KISMET_FEATHER") n += s.count
        }
        val t = n.toString()
        val font = Minecraft.getInstance().font
        ctx.text(font, t, x + 17 - font.width(t), y + 9, if (n > 0) 0xFF55FF55.toInt() else 0xFFFF5555.toInt(), true)
    }

    @JvmStatic
    fun glowColor(e: Entity): Int {
        val g = glow
        if (g.isEmpty()) return EntityRenderState.NO_OUTLINE
        return g[e.id] ?: EntityRenderState.NO_OUTLINE
    }

    @JvmStatic
    fun hideSkull(e: Entity): Boolean = S.skullHide && e is ArmorStand && skulls.contains(e.id)

    // Rebuilds "[lvl] rank name (Class XX)" with the level coloured by tier
    @JvmStatic
    fun classTab(original: Component): Component {
        if (!S.classTabColor || !SkyblockLocation.inDungeon()) return original
        val m = TAB_TEAM.find(fishmod.utils.TextStrip.plain(original.string).trim()) ?: return original
        val (sb, rank, name, cls, lvl) = m.destructured
        val n = lvl.toIntOrNull() ?: roman(lvl)
        return Component.literal("§8$sb ${if (rank.isEmpty()) "" else "$rank "}§f$name §f(§d$cls ${color(n)}$lvl§f)")
    }

    private fun onTick(mc: Minecraft) {
        if (++tick % 5 != 0) return
        val inDungeon = SkyblockLocation.inDungeon()
        scanEntities(mc, inDungeon)
        if (!inDungeon || !(S.lowHpAlert || S.partyHpHud)) { if (mates.isNotEmpty()) mates = emptyList(); return }
        if (SidebarCache.version == sbVersion) return
        sbVersion = SidebarCache.version
        val out = ArrayList<Mate>()
        for (e in SidebarCache.entries) {
            val m = TEAMMATE.find(e.raw) ?: continue
            out += Mate(m.groupValues[1], m.groupValues[2], m.groupValues[3].firstOrNull() ?: 'f', m.groupValues[4])
        }
        mates = out
        if (S.lowHpAlert) {
            val now = System.currentTimeMillis()
            for (p in out) {
                if (p.color != 'c' || p.hp == "DEAD") continue
                if (now - (lastAlert[p.name] ?: 0L) < S.lowHpCooldownSec.coerceAtLeast(1) * 1000L) continue
                lastAlert[p.name] = now
                FishUtil.forceTitle(Component.literal("§c${p.name} §ais low"), Component.literal("§c${p.hp}❤"), 1200)
                SoundManager.play(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.6f, "lowHp", 400)
            }
        }
    }

    private fun scanEntities(mc: Minecraft, inDungeon: Boolean) {
        val level = mc.level
        val player = mc.player
        if (!inDungeon || level == null || player == null || !(S.deathmiteHighlight || S.skullHighlight || S.skullHide)) {
            if (glow.isNotEmpty()) glow = emptyMap()
            if (skulls.isNotEmpty()) skulls = emptySet()
            return
        }
        val out = HashMap<Int, Int>()
        val sk = HashSet<Int>()
        val tags = ArrayList<ArmorStand>()
        val mobs = ArrayList<LivingEntity>()
        for (e in level.entitiesForRendering()) {
            if (e.distanceToSqr(player) > 48.0 * 48.0) continue
            if (e is ArmorStand) {
                // from SkyHanni (DungeonHideItems: helmet named "Skeleton Skull")
                val head = e.getItemBySlot(EquipmentSlot.HEAD)
                if (!head.isEmpty && fishmod.utils.TextStrip.plain(head.hoverName.string) == "Skeleton Skull") {
                    sk += e.id
                    if (S.skullHighlight) out[e.id] = S.skullColor or 0xFF000000.toInt()
                } else if (S.deathmiteHighlight && e.customName?.string?.contains("Deathmite") == true) tags += e // from SkyHanni (HighlightDungeonDeathmite)
            } else if (e is LivingEntity && e !is Player && e.isAlive) mobs += e
        }
        for (s in tags) {
            val best = mobs.filter { val dx = it.x - s.x; val dz = it.z - s.z; dx * dx + dz * dz < 2.25 && s.y - it.y in -1.0..3.0 }
                .minByOrNull { it.distanceToSqr(s) } ?: continue
            out[best.id] = S.deathmiteColor or 0xFF000000.toInt()
        }
        glow = out
        skulls = sk
    }

    private fun render(ctx: GuiGraphicsExtractor) {
        if (!S.partyHpHud || !SkyblockLocation.inDungeon()) return
        val l = mates
        val mc = Minecraft.getInstance()
        if (l.isEmpty() || mc.player == null || mc.hudHidden) return
        val sc = S.partyHpHudScale.toFloat()
        ctx.pose().pushMatrix()
        try {
            ctx.pose().translate(S.partyHpHudX.toFloat(), S.partyHpHudY.toFloat())
            ctx.pose().scale(sc, sc)
            draw(ctx, l)
        } catch (e: Exception) {
            FishDiag.fail("DungeonExtras.2", "party hp hud render failed", e)
        } finally {
            ctx.pose().popMatrix()
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor, l: List<Mate>) {
        val font = Minecraft.getInstance().font
        l.forEachIndexed { i, p ->
            ctx.text(font, Component.literal("§e[${p.cls}] §f${p.name} §${p.color}${p.hp}"), 0, i * HudPreview.TEXT_H, -1, true)
        }
    }

    // Tier colours from SkyHanni (DungeonApi.getColor)
    private fun color(l: Int): String = when {
        l >= 50 -> "§c§l"; l >= 45 -> "§c"; l >= 40 -> "§6"; l >= 35 -> "§d"; l >= 30 -> "§9"
        l >= 25 -> "§b"; l >= 20 -> "§2"; l >= 15 -> "§a"; l >= 10 -> "§e"; l >= 5 -> "§f"; else -> "§7"
    }

    private fun roman(s: String): Int {
        val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100)
        var total = 0
        for (i in s.indices) {
            val c = v[s[i]] ?: return 0
            val next = if (i + 1 < s.length) v[s[i + 1]] ?: 0 else 0
            total += if (c < next) -c else c
        }
        return total
    }
}
