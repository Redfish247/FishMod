package fishmod.features.slayer

import fishmod.compat.hudHidden
import fishmod.features.FishHudEditor
import fishmod.features.HudExample
import fishmod.features.HudPreview
import fishmod.features.slayer.EndAlertSettings as S
import fishmod.utils.FishUtil
import fishmod.utils.SkyblockLocation
import fishmod.utils.TabListCache
import fishmod.utils.TextStrip
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.animal.wolf.Wolf
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

// Egg sacs, Yang glyph tracer, Broodmother countdown, no-quest warning, zealot/dragon counter
object SlayerMore {

    // from NoFrills (EggHitsDisplay)
    private val EGG_SAC = Regex("^\\d+s \\d+/\\d+$")
    // from SkyHanni (TabWidget.BROODMOTHER, BroodmotherFeatures stage durations)
    private val BROOD = Regex("^Broodmother: (\\w+)!?$")
    private val BROOD_SEC = mapOf("Slain" to 150, "Dormant" to 120, "Soon" to 90, "Awakening" to 60, "Imminent" to 30, "Alive" to 0)
    // from Skyblocker (TheEnd) and SkyHanni (DragonFeatures)
    private const val SPECIAL_ZEALOT = "A special Zealot has spawned nearby!"
    private val DRAGON_SPAWN = Regex("^☬ The (\\w+) Dragon has spawned!$")

    private var tick = 0
    @Volatile private var eggs: List<ArmorStand> = emptyList()
    private var glyphStand: Vec3? = null
    @Volatile private var glyph: BlockPos? = null
    private var broodStage: String? = null
    private var broodAt = 0L
    private var tabVersion = -1
    private var warned = false
    private val noQuestKills = ArrayDeque<Long>()
    private val deadSeen = HashSet<Int>()
    private val zealotIds = HashSet<Int>()
    private val hitIds = HashSet<Int>()
    private var zealots = 0
    private var sinceEye = 0
    private var eyes = 0
    private var dragons = 0
    private var lastDragon: String? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            try { onTick(mc) } catch (t: Throwable) { FishDiag.fail("SlayerMore.1", "slayer extras tick failed", t) }
        }
        RenderHooks.GIZMO.register { _ ->
            try { render3d() } catch (t: Throwable) { FishDiag.fail("SlayerMore.2", "slayer extras render failed", t) }
        }
        FishEvents.worldChange.register {
            eggs = emptyList(); glyph = null; glyphStand = null; broodStage = null; zealotIds.clear(); hitIds.clear(); deadSeen.clear()
            noQuestKills.clear(); warned = false; false
        }
        FishEvents.chat.register { text ->
            if (S.zealotHud) {
                val s = TextStrip.plain(text.chatPlain).trim()
                if (s == SPECIAL_ZEALOT) { eyes++; sinceEye = 0 }
                else DRAGON_SPAWN.find(s)?.let { dragons++; lastDragon = it.groupValues[1] }
            }
            false
        }
        AttackEntityCallback.EVENT.register { _, _, _, entity, _ ->
            if ((S.zealotHud || S.noQuestWarning) && entity is LivingEntity) {
                hitIds.add(entity.id)
                // swings add ids on every island, so drop the ones that no longer exist
                if (hitIds.size > 400) Minecraft.getInstance().level?.let { lv -> hitIds.retainAll { lv.getEntity(it) != null } }
            }
            InteractionResult.PASS
        }
        hud("Broodmother", { S.broodmotherHudX }, { S.broodmotherHudX = it }, { S.broodmotherHudY }, { S.broodmotherHudY = it },
            { S.broodmotherHudScale }, { S.broodmotherHudScale = it }, { S.broodmotherHud && SkyblockLocation.`in`(SkyblockLocation.SPIDERS_DEN) }, ::broodLines,
            listOf("§4Broodmother: §6Soon §b≤1:12"))
        hud("Zealot Counter", { S.zealotHudX }, { S.zealotHudX = it }, { S.zealotHudY }, { S.zealotHudY = it },
            { S.zealotHudScale }, { S.zealotHudScale = it }, { S.zealotHud && SkyblockLocation.`in`(SkyblockLocation.THE_END) }, ::zealotLines,
            listOf("§5Zealots: §f412 §7(§f87 §7since eye)", "§5Eyes: §f3", "§5Dragons: §f2 §7(Young)"))
    }

    private fun isZealot(e: LivingEntity): Boolean {
        // from Skyblocker (TheEnd.isZealot: nametag stand containing "zealot")
        if (e.name.string.contains("zealot", true)) return true
        val level = Minecraft.getInstance().level ?: return false
        return level.getEntitiesOfClass(ArmorStand::class.java, e.boundingBox.inflate(1.0, 2.0, 1.0)) {
            it.customName?.string?.contains("zealot", true) == true
        }.isNotEmpty()
    }

    private fun onTick(mc: Minecraft) {
        val level = mc.level ?: return
        val player = mc.player ?: return
        if ((S.zealotHud || S.noQuestWarning) && tick % 2 == 0) scanDeaths(level, player)
        if (++tick % 5 != 0) return
        val type = SlayerManager.type
        val boss = SlayerManager.bossEntity

        if (S.eggSacHighlight && type == SlayerType.TARANTULA) {
            eggs = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(32.0)) {
                it.hasCustomName() && EGG_SAC.matches(TextStrip.plain(it.customName!!.string).trim())
            }
        } else if (eggs.isNotEmpty()) eggs = emptyList()

        if (S.yangGlyphTracer && type == SlayerType.VOIDGLOOM && boss != null) {
            // from NoFrills (BeaconTracer): beacon-helmet stand near boss, then beacon block where it lands
            level.getEntitiesOfClass(ArmorStand::class.java, boss.boundingBox.inflate(4.0, 3.0, 4.0)) {
                it.getItemBySlot(EquipmentSlot.HEAD).`is`(Items.BEACON)
            }.firstOrNull()?.let { glyphStand = it.position() }
            val g = glyph
            if (g != null && !level.getBlockState(g).`is`(Blocks.BEACON)) glyph = null
            val st = glyphStand
            if (glyph == null && st != null) {
                val c = BlockPos.containing(st)
                for (p in BlockPos.betweenClosed(c.offset(-4, -3, -4), c.offset(4, 3, 4))) {
                    if (level.getBlockState(p).`is`(Blocks.BEACON)) { glyph = p.immutable(); glyphStand = null; break }
                }
            }
        } else { glyph = null; glyphStand = null }

        if (S.broodmotherHud && TabListCache.version != tabVersion) {
            tabVersion = TabListCache.version
            for (e in TabListCache.entries) {
                val st = BROOD.find(e.stripped.trim())?.groupValues?.get(1) ?: continue
                if (st != broodStage) {
                    val sec = BROOD_SEC[st]
                    if (sec != null) broodAt = System.currentTimeMillis() + sec * 1000L
                    if (st == "Alive" && broodStage != null) FishUtil.forceTitle(Component.literal("§4§lBROODMOTHER!"), Component.literal("§7Has spawned"), 2500)
                    broodStage = st
                }
                break
            }
        }

        if (type != null) { warned = false; noQuestKills.clear() }
    }

    // Counts kills from the death state, so ranged/AOE kills and special zealots all count
    private fun scanDeaths(level: net.minecraft.client.multiplayer.ClientLevel, player: net.minecraft.world.entity.player.Player) {
        val loc = SkyblockLocation.getCurrentLocation()
        val inEnd = loc == SkyblockLocation.THE_END
        val noQuest = S.noQuestWarning && SlayerManager.type == null && !warned
        if (!(S.zealotHud && inEnd) && !noQuest) return
        val now = System.currentTimeMillis()
        for (e in level.getEntitiesOfClass(LivingEntity::class.java, player.boundingBox.inflate(24.0)) { it !== player }) {
            val id = e.id
            if (!e.isDeadOrDying) {
                if (S.zealotHud && inEnd && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.type).path == "enderman" && id !in zealotIds && isZealot(e)) zealotIds.add(id)
                continue
            }
            if (!deadSeen.add(id)) continue
            val mine = id in hitIds || e.distanceToSqr(player) <= 100.0
            if (!mine) continue
            if (id in zealotIds) { zealots++; sinceEye++ }
            if (noQuest && isSlayerAreaMob(e, loc)) {
                noQuestKills.addLast(now)
                while (noQuestKills.isNotEmpty() && now - noQuestKills.first() > 60_000L) noQuestKills.removeFirst()
                if (noQuestKills.size >= 3) {
                    warned = true; noQuestKills.clear()
                    FishUtil.forceTitle(Component.literal("§c§lNO SLAYER QUEST"), Component.literal("§7You're killing slayer mobs with no quest"), 2500)
                }
            }
        }
        if (deadSeen.size > 400) { deadSeen.retainAll { level.getEntity(it) != null }; zealotIds.retainAll { level.getEntity(it) != null }; hitIds.retainAll { level.getEntity(it) != null } }
    }

    // Mob families each slayer quest counts, by island (SlayerManager area rules)
    private fun isSlayerAreaMob(e: LivingEntity, loc: SkyblockLocation?): Boolean = when (loc) {
        SkyblockLocation.HUB -> e is Zombie || e is Wolf
        SkyblockLocation.THE_PARK -> e is Wolf
        SkyblockLocation.SPIDERS_DEN -> e is Spider
        SkyblockLocation.THE_END -> net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.type).path == "enderman"
        SkyblockLocation.CRIMSON_ISLE -> e is Blaze
        else -> false
    }

    private fun render3d() {
        if (S.eggSacHighlight) for (e in eggs) {
            if (e.isRemoved) continue
            val p = e.position()
            val c = S.eggSacColor
            FishDraw.gizmoBox(AABB(p.x - 0.5, p.y, p.z - 0.5, p.x + 0.5, p.y + 1.0, p.z + 0.5), (0x40 shl 24) or (c and 0xFFFFFF), c, true)
        }
        val g = glyph
        if (S.yangGlyphTracer && g != null) {
            val c = S.yangGlyphColor
            val box = AABB(g)
            FishDraw.gizmoBox(box, (0x50 shl 24) or (c and 0xFFFFFF), c, true)
            FishDraw.gizmoLine(FishDraw.cameraLineStart(0.2), box.center, c, 2f, true)
        }
    }

    private fun broodLines(): List<String> {
        val st = broodStage ?: return emptyList()
        if (st == "Alive") return listOf("§4Broodmother: §c§lAlive!")
        val left = ((broodAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return listOf("§4Broodmother: §6$st §b≤${left / 60}:${"%02d".format(left % 60)}")
    }

    private fun zealotLines(): List<String> = listOf(
        "§5Zealots: §f$zealots §7(§f$sinceEye §7since eye)",
        "§5Eyes: §f$eyes",
        "§5Dragons: §f$dragons" + (lastDragon?.let { " §7($it)" } ?: "")
    )

    private fun hud(name: String, gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit, gs: () -> Double, ss: (Double) -> Unit,
                    on: () -> Boolean, lines: () -> List<String>, example: List<String>) {
        FishHudEditor.register(name, java.util.function.IntSupplier { gx() }, java.util.function.IntConsumer { sx(it) },
            java.util.function.IntSupplier { gy() }, java.util.function.IntConsumer { sy(it) }, 130, HudPreview.TEXT_H * example.size,
            java.util.function.DoubleSupplier { gs() }, java.util.function.DoubleConsumer { ss(it) })
        HudPreview.register(name) { HudExample(130, HudPreview.TEXT_H * example.size) { ctx -> draw(ctx, example) } }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", name.lowercase().replace(' ', '_'))) { ctx, _ ->
            if (FishHudEditor.isOpen() || !on()) return@addLast
            val mc = Minecraft.getInstance()
            if (mc.player == null || mc.hudHidden) return@addLast
            val l = lines()
            if (l.isEmpty()) return@addLast
            val sc = gs().toFloat()
            ctx.pose().pushMatrix()
            try {
                ctx.pose().translate(gx().toFloat(), gy().toFloat())
                ctx.pose().scale(sc, sc)
                draw(ctx, l)
            } catch (e: Exception) {
                FishDiag.fail("SlayerMore.3", "$name hud render failed", e)
            } finally {
                ctx.pose().popMatrix()
            }
        }
    }

    private fun draw(ctx: GuiGraphicsExtractor, l: List<String>) {
        val font = Minecraft.getInstance().font
        l.forEachIndexed { i, s -> ctx.text(font, Component.literal(s), 0, i * HudPreview.TEXT_H, -1, true) }
    }
}
