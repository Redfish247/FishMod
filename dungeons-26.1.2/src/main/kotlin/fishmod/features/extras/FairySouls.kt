package fishmod.features.extras
import fishmod.utils.events.chatPlain
import fishmod.compat.cam

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import fishmod.utils.IoExecutor
import fishmod.utils.SkyblockLocation
import fishmod.utils.data.NeuRepo
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import fishmod.features.extras.ExtrasSettings as S

object FairySouls {
    private val PATH = FabricLoader.getInstance().configDir.resolve("FishMod").resolve("fairy-souls-found.json")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val found = ConcurrentHashMap<String, MutableSet<String>>()

    fun init() {
        load()
        FishEvents.chat.register { text ->
            if (S.fairySoulWaypoints) onChat(text.chatPlain)
            false
        }
        RenderHooks.NO_DEPTH_FILLED.register { ctx, ps, vc -> if (S.fairySoulWaypoints && SkyblockLocation.inSkyblock()) render(ctx, ps, vc) }
    }

    private fun souls(mode: String): List<Pair<String, Vec3>> {
        val arr = NeuRepo.fairySouls?.getAsJsonArray(mode) ?: run { NeuRepo.ensureLoaded(); return emptyList() }
        return arr.mapNotNull { e ->
            val p = e.asString.split(',').mapNotNull { it.trim().toIntOrNull() }
            if (p.size == 3) e.asString to Vec3(p[0] + 0.5, p[1].toDouble(), p[2] + 0.5) else null
        }
    }

    private fun onChat(s: String) {
        if (!s.startsWith("SOUL! You found a Fairy Soul!") && !s.startsWith("You have already found that Fairy Soul!")) return
        val mode = SkyblockLocation.currentMode
        val p = Minecraft.getInstance().player ?: return
        val done = found[mode] ?: emptySet()
        val near = souls(mode).filter { it.first !in done }.minByOrNull { it.second.distanceTo(p.position()) } ?: return
        if (near.second.distanceTo(p.position()) > 8.0) return
        found.getOrPut(mode) { ConcurrentHashMap.newKeySet() }.add(near.first)
        save()
    }

    fun resetIsland() { found.remove(SkyblockLocation.currentMode); save() }

    private fun render(ctx: net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext, ps: com.mojang.blaze3d.vertex.PoseStack, vc: com.mojang.blaze3d.vertex.VertexConsumer) {
        val mode = SkyblockLocation.currentMode
        if (mode.isEmpty()) return
        val done = found[mode] ?: emptySet()
        val eye = Minecraft.getInstance().cam.position()
        val argb = S.fairySoulColor
        val rgb = argb and 0xFFFFFF
        for ((key, pos) in souls(mode)) {
            if (key in done) continue
            val center = pos.add(0.0, 0.5, 0.0)
            val d = center.distanceTo(eye)
            val (c, k) = FishDraw.pullIn(center, eye)
            FishDraw.fillBox(ps, vc, AABB.ofSize(c, k, k, k), (0x66 shl 24) or rgb)
            if (S.fairySoulBeam) FishDraw.fillBox(ps, vc, AABB.ofSize(c.add(0.0, k * 100.5, 0.0), k * 0.4, k * 200, k * 0.4), (0x40 shl 24) or rgb)
            if (S.fairySoulText) {
                val (tp, pk) = FishDraw.pullIn(Vec3(pos.x, pos.y + 1.5 + d / 25.0, pos.z), eye)
                val px = (maxOf(0.035, d * 0.0035) * pk).toFloat()
                FishDraw.renderSeeThroughText(ctx, ps, Component.literal("Fairy Soul §7(${d.toInt()}m)"), tp, px, argb or (0xFF shl 24), true)
            }
        }
    }

    private fun load() {
        try {
            if (!Files.exists(PATH)) return
            JsonParser.parseString(Files.readString(PATH)).asJsonObject.entrySet().forEach { (k, v) ->
                found[k] = ConcurrentHashMap.newKeySet<String>().apply { v.asJsonArray.forEach { add(it.asString) } }
            }
        } catch (e: java.io.IOException) { FishDiag.fail("FairySouls.1", "fairy soul save unreadable", e) }
        catch (e: RuntimeException) { found.clear(); fishmod.utils.SafeFiles.quarantine(PATH, e) }
    }

    private fun save() {
        val snap = found.mapValues { it.value.toList() }
        IoExecutor.execute { fishmod.utils.SafeFiles.writeAtomic(PATH, gson.toJson(snap)) }
    }
}
