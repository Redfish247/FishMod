package fishmod.features.mining

import fishmod.utils.events.chatRaw
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import fishmod.features.mining.MiningSettings as S

// Ability-ready title, Pickobulus preview (Skyblocker algorithm), Maniac Miner block counter, SkyMall perk
object MiningAbilities {

    private val ABILITIES = setOf("Pickobulus", "Mining Speed Boost", "Maniac Miner", "Gemstone Infusion", "Sheer Force",
        "Anomalous Desire", "Vein Seeker", "Hazardous Miner")
    private val READY = Regex("""^(.+?) is now available!$""")
    private val USED = Regex("""You used your (.+?) Pickaxe Ability!""")
    private val SKYMALL = Regex("""^New buff: (.+?)\.?$""")

    private var pickBlocks: List<BlockPos> = emptyList()
    private var pickDrops: Map<String, Int> = emptyMap()
    private var pickError = ""
    private var maniacStart = 0L
    private var maniacEnd = 0L
    private var maniacBlocks = 0

    fun init() {
        FishEvents.chat.register { text ->
            val s = Mining.strip(text.chatRaw).trim()
            READY.find(s)?.let { m ->
                val ab = m.groupValues[1]
                if ((S.miningAbilities && S.miningAbilityTitle) && ab in ABILITIES)
                    Mining.title(S.miningAbilityTitleText.replace("{ability}", ab), "", S.miningAbilityTitleMs, S.miningAbilitySound)
            }
            USED.find(s)?.let { m ->
                if (m.groupValues[1] == "Maniac Miner") { maniacStart = System.currentTimeMillis(); maniacEnd = 0L; maniacBlocks = 0 }
            }
            if (s.contains("Maniac Miner") && (s.contains("expired") || s.contains("ended") || s.contains("ran out"))) endManiac()
            SKYMALL.find(s)?.let { S.miningSkyMallPerk = it.groupValues[1] }
            false
        }
        Mining.breakListeners += Mining.BreakListener { _, _, _ -> if (maniacActive()) maniacBlocks++ }
        ClientTickEvents.END_CLIENT_TICK.register { tickPickobulus() }

        RenderHooks.NO_DEPTH_FILLED.register { _, ps, vc ->
            if (!(S.miningAbilities && S.miningPickobulus) || pickBlocks.isEmpty()) return@register
            for (p in pickBlocks) {
                val b = AABB(p)
                if (S.miningPickobulusOpacity > 0) FishDraw.fillBox(ps, vc, b, Mining.alpha(S.miningPickobulusColor, S.miningPickobulusOpacity))
                Mining.outline(ps, vc, b, Mining.alpha(S.miningPickobulusColor, 100), 1.5f)
            }
        }

        MiningHuds.reg("Pickobulus", "mining_pickobulus", 140, 60,
            { S.miningPickobulusHudX }, { S.miningPickobulusHudX = it }, { S.miningPickobulusHudY }, { S.miningPickobulusHudY = it },
            { S.miningPickobulusHudScale }, { S.miningPickobulusHudScale = it },
            { (S.miningAbilities && S.miningPickobulus) && S.miningPickobulusHud && (pickBlocks.isNotEmpty() || pickError.isNotEmpty()) }, { (S.miningAbilities && S.miningPickobulus) && S.miningPickobulusHud },
            example = { pickLines(37, mapOf("Mithril" to 24, "Titanium" to 6, "Stone" to 7), "") }) {
            pickLines(pickBlocks.size, pickDrops, pickError)
        }
        MiningHuds.reg("Maniac Miner", "mining_maniac", 120, 30,
            { S.miningManiacHudX }, { S.miningManiacHudX = it }, { S.miningManiacHudY }, { S.miningManiacHudY = it },
            { S.miningManiacHudScale }, { S.miningManiacHudScale = it },
            { (S.miningAbilities && S.miningManiac) && maniacStart > 0 && (maniacActive() || System.currentTimeMillis() - maniacEnd < 10_000) }, { (S.miningAbilities && S.miningManiac) },
            example = { maniacLines(true, 64, 12) }) {
            val active = maniacActive()
            maniacLines(active, maniacBlocks, ((if (active) System.currentTimeMillis() else maniacEnd) - maniacStart) / 1000)
        }
        MiningHuds.reg("SkyMall", "mining_skymall", 160, 12,
            { S.miningSkyMallHudX }, { S.miningSkyMallHudX = it }, { S.miningSkyMallHudY }, { S.miningSkyMallHudY = it },
            { S.miningSkyMallHudScale }, { S.miningSkyMallHudScale = it },
            { (S.miningAbilities && S.miningSkyMall) && S.miningSkyMallPerk.isNotEmpty() && (Mining.inMiningIsland() || fishmod.features.FishHudEditor.isOpen()) }, { (S.miningAbilities && S.miningSkyMall) },
            example = { skyMallLines(S.miningSkyMallPerk.ifEmpty { "Example perk text" }) }) {
            skyMallLines(S.miningSkyMallPerk)
        }
    }

    private fun pickLines(blocks: Int, drops: Map<String, Int>, error: String): List<String> =
        if (error.isNotEmpty()) listOf("§b§lPickobulus", error)
        else listOf("§b§lPickobulus §7($blocks blocks)") + drops.entries.sortedByDescending { it.value }.map { "§7${it.key}: §f${it.value}" }

    private fun skyMallLines(perk: String) = listOf("§bSkyMall: §f$perk")

    private fun maniacLines(active: Boolean, blocks: Int, secs: Long): List<String> =
        listOf("§c§lManiac Miner" + if (active) "" else " §7(ended)", "§7Blocks: §f$blocks §8(${secs}s)")

    private fun maniacActive(): Boolean {
        if (maniacStart == 0L || maniacEnd != 0L) return false
        if (System.currentTimeMillis() - maniacStart > 30_000) { endManiac(); return false }
        return true
    }

    private fun endManiac() { if (maniacStart > 0 && maniacEnd == 0L) maniacEnd = System.currentTimeMillis() }

    private fun holdingPickobulus(): Boolean {
        val st = Minecraft.getInstance().player?.mainHandItem ?: return false
        return st.get(DataComponents.LORE)?.lines()?.any { it.string.contains("Pickobulus") } == true
    }

    private fun tickPickobulus() {
        pickBlocks = emptyList(); pickError = ""
        if (!(S.miningAbilities && S.miningPickobulus) || !Mining.inMiningIsland()) return
        val mc = Minecraft.getInstance()
        val p = mc.player ?: return
        val level = mc.level ?: return
        Mining.tabLine("Pickobulus:")?.let { if (it != "Pickobulus: Available") { pickError = "§cOn cooldown: " + it.removePrefix("Pickobulus: "); return } }
        if (!holdingPickobulus()) return
        // Eye height + 0.53625 offset per Skyblocker
        val start = p.position().add(0.0, p.eyeHeight + 0.53625, 0.0)
        val hit = level.clip(ClipContext(start, start.add(p.forward.scale(20.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p))
        if (hit.type != HitResult.Type.BLOCK) { pickError = "§7Not looking at a block"; return }
        compute(hit.blockPos)
    }

    private fun compute(pos: BlockPos) {
        val level = Minecraft.getInstance().level ?: return
        val grid = Array(8) { i -> Array(8) { j -> Array<BlockState>(8) { k -> level.getBlockState(pos.offset(i - 4, j - 4, k - 4)) } } }
        val out = ArrayList<BlockPos>()
        val drops = HashMap<String, Int>()
        val tunnels = Mining.inTunnels(); val shaft = Mining.inShaft(); val hollows = Mining.inHollows()
        val done = Array(8) { Array(8) { BooleanArray(8) } }
        var changed = true
        while (changed) { changed = false
        for (i in 1..6) for (j in 1..6) for (k in 1..6) {
            if (done[i][j][k]) continue
            val st = grid[i][j][k]
            if (st.isAir || st.`is`(Blocks.BEDROCK)) continue
            val exposed = grid[i - 1][j][k].isAir || grid[i + 1][j][k].isAir || grid[i][j - 1][k].isAir ||
                grid[i][j + 1][k].isAir || grid[i][j][k - 1].isAir || grid[i][j][k + 1].isAir
            if (!exposed) continue
            val gem = Gem.of(st) != null
            val breaks = when {
                tunnels || st.`is`(Blocks.PACKED_ICE) -> st.block in MiningBlocks.TUNNEL_BREAKABLE || gem
                hollows || shaft -> true
                else -> st.block in MiningBlocks.CONVERT_INTO_BEDROCK || gem
            }
            if (!breaks) continue
            done[i][j][k] = true; changed = true
            if (hollows || shaft || st.`is`(Blocks.PACKED_ICE) || (tunnels && gem)) grid[i][j][k] = Blocks.AIR.defaultBlockState()
            out += pos.offset(i - 4, j - 4, k - 4)
            val name = MiningBlocks.material(st)
            drops[name] = (drops[name] ?: 0) + 1
        } }
        pickBlocks = out; pickDrops = drops
    }
}
