package fishmod.features

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import fishmod.compat.hudHidden
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import java.io.File

// Every FishUtil.forceTitle alert drawn as its own movable HUD slot (keyed by the calling feature's class) instead of the vanilla title.
object AlertTitles {
    private class Def(val key: String, val name: String, val exMain: String, val exSub: String = "")

    private val DEFS = listOf(
        Def("BurrowDetector", "Alert: Use Spade", "§eUse Spade!"),
        Def("DianaTracker", "Alert: Diana Drop", "§d§lExample Drop!", "§612.3k coins"),
        Def("ArrowsDevice", "Alert: Arrows Device", "§aDevice Complete"),
        Def("BloodSolver", "Alert: Blood Solver", "§c§lKill Mobs"),
        Def("WitherDragons", "Alert: Wither Dragon Spawn", "§c§lRED", "§cRED §aGREEN"),
        Def("NecronLbTimer", "Alert: Necron LB", "§c§lSHOOT LB!"),
        Def("StormTicks", "Alert: Storm Ticks", "§d§lSTAND ON CRUSHER!"),
        Def("InvincibilityTracker", "Alert: Invincibility Proc", "§bBonzo's Mask Procced!"),
        Def("KeyPickupAlert", "Alert: Key Pickup", "§aWither Key picked up"),
        Def("LeapAnnounce", "Alert: Leap", "§bLeaped to Player"),
        Def("RoomTimer", "Alert: Room Timer", "§aRoom done in 12.3s"),
        Def("ArrowHud", "Alert: Arrows Low", "§cOUT OF ARROWS", "§eLow arrows: §f64"),
        Def("LimboAlert", "Alert: Limbo", "§cYou are in Limbo"),
        Def("PartyJoinSound", "Alert: Party Join", "", "§aPlayer §7joined the party"),
        Def("Kuudra", "Alert: Kuudra", "§eCrate Priority", "§7Example"),
        Def("Mining", "Alert: Mining", "§6Mining Alert", "§7Example"),
        Def("PetSwapTitle", "Alert: Pet Swap", "§aPet swapped"),
        Def("Ragnarock", "Alert: Ragnarock", "§aCasted Ragnarock", "§7Strength: 123"),
        Def("RiftBurger", "Alert: Rift Burgers", "§cBurgers 3/5", "§7Highlite sells for more with 5/5"),
        Def("RiftHay", "Alert: Rift Hay", "§c§lHAY!"),
        Def("RiftLeech", "Alert: Rift Leech", "§c§lLASER!", "§7Go to the safe spot"),
        Def("RiftMobs", "Alert: Rift Bacte / Crux", "§a§lBacte!", "§7Bacte spawned"),
        Def("RiftParkour", "Alert: Rift Kill Zone", "§c§lGet back in the arena!"),
        Def("RiftVampire", "Alert: Rift Vampire", "§c§lSTEAK!"),
        Def("SlayerAlerts", "Alert: Slayer", "§c§lSLAYER BOSS", "§7Example"),
        Def("SlayerBossPhases", "Alert: Slayer Phase", "§6Phase change"),
        Def("SlayerCarryTracker", "Alert: Slayer Carry Done", "§aCarry done!", "§ePlayer §73/3"),
        Def("GoldorSection", "Alert: Goldor Section", "", "§aSection cleared"),
        Def("ExplosiveShotHud", "Alert: Explosive Shot", "§c1.2M", "§7Explosive Shot §8• §f3 enemies"),
        Def("LoadoutTitle", "Alert: Loadout Equipped", "§fLoadout 1", "§7Loadout equipped"),
        Def("Other", "Alert: Other", "§eAlert!", "§7Example"),
    )

    private class Slot(val def: Def) {
        var x = -1; var y = -1; var scale = 1.0
        var main: Component = Component.empty(); var sub: Component = Component.empty()
        var start = 0L; var stayMs = 3500
    }

    private val slots = LinkedHashMap<String, Slot>().also { m -> for (d in DEFS) m[d.key] = Slot(d) }
    val NAMES: List<String> = DEFS.map { it.name }
    private val FILE = File("config/fishmod/alert_titles.json")
    private const val W = 260
    private const val MAIN_SCALE = 3f
    private const val SUB_SCALE = 1.6f
    private const val FADE_IN = 250L
    private const val FADE_OUT = 500L
    @Volatile private var dirty = false

    @JvmStatic
    fun show(main: Component, sub: Component, stayMs: Int) {
        if (main.string.isEmpty() && sub.string.isEmpty()) return
        val s = slots[callerKey()] ?: slots["Other"]!!
        s.main = main; s.sub = sub; s.stayMs = stayMs.coerceIn(100, 120_000); s.start = System.currentTimeMillis()
    }

    private fun callerKey(): String = try {
        StackWalker.getInstance().walk { st ->
            st.map { it.className }
                .filter { !it.startsWith("fishmod.utils.FishUtil") && !it.startsWith("fishmod.features.AlertTitles") && !it.startsWith("kotlin.") && !it.startsWith("java.") }
                .findFirst().orElse("")
        }.substringAfterLast('.').substringBefore('$')
    } catch (t: Throwable) { "" }

    private fun alpha(s: Slot, now: Long): Float {
        val t = now - s.start
        return when {
            s.start == 0L || t < 0 -> 0f
            t < FADE_IN -> t / FADE_IN.toFloat()
            t < FADE_IN + s.stayMs -> 1f
            t < FADE_IN + s.stayMs + FADE_OUT -> 1f - (t - FADE_IN - s.stayMs) / FADE_OUT.toFloat()
            else -> 0f
        }
    }

    private fun boxH(hasSub: Boolean) = (9 * MAIN_SCALE).toInt() + if (hasSub) (9 * SUB_SCALE).toInt() + 4 else 0

    private fun sx(s: Slot): Int = if (s.x >= 0) s.x else ((Minecraft.getInstance().window.guiScaledWidth - W * s.scale) / 2).toInt()
    private fun sy(s: Slot): Int = if (s.y >= 0) s.y else Minecraft.getInstance().window.guiScaledHeight / 2 - 60

    // Title block at the origin, shared by the in-game draw and the editor example
    private fun render(ctx: GuiGraphicsExtractor, main: Component, sub: Component, color: Int) {
        val font = Minecraft.getInstance().font
        val pose = ctx.pose()
        if (main.string.isNotEmpty()) {
            pose.pushMatrix(); pose.translate(W / 2f, 0f); pose.scale(MAIN_SCALE, MAIN_SCALE)
            ctx.text(font, main, -font.width(main) / 2, 0, color, true)
            pose.popMatrix()
        }
        val y = 9 * MAIN_SCALE + 4
        if (sub.string.isNotEmpty()) {
            pose.pushMatrix(); pose.translate(W / 2f, if (main.string.isEmpty()) 0f else y); pose.scale(SUB_SCALE, SUB_SCALE)
            ctx.text(font, sub, -font.width(sub) / 2, 0, color, true)
            pose.popMatrix()
        }
    }

    @JvmStatic
    fun init() {
        load()
        for (s in slots.values) {
            val d = s.def
            HudPreview.register(d.name) {
                HudExample(W, boxH(d.exSub.isNotEmpty() && d.exMain.isNotEmpty())) { ctx -> render(ctx, Component.literal(d.exMain), Component.literal(d.exSub), 0xFFFFFFFF.toInt()) }
            }
            FishHudEditor.register(d.name, { sx(s) }, { v -> s.x = v; dirty = true }, { sy(s) }, { v -> s.y = v; dirty = true }, W, boxH(true),
                { s.scale }, { v -> s.scale = v; dirty = true }, { true })
        }
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "alert_titles")) { ctx, _ ->
            val mc = Minecraft.getInstance()
            if (mc.player == null || mc.hudHidden || FishHudEditor.isOpen()) return@addLast
            val now = System.currentTimeMillis()
            for (s in slots.values) {
                val a = alpha(s, now)
                if (a <= 0.02f) continue
                val color = ((a * 255).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
                val pose = ctx.pose()
                pose.pushMatrix()
                pose.translate(sx(s).toFloat(), sy(s).toFloat())
                pose.scale(s.scale.toFloat(), s.scale.toFloat())
                render(ctx, s.main, s.sub, color)
                pose.popMatrix()
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { if (dirty && ++saveTick % 40 == 0) save() }
        ClientLifecycleEvents.CLIENT_STOPPING.register { if (dirty) save() }
    }

    private var saveTick = 0

    private fun load() {
        if (!FILE.exists()) return
        FishDiag.guard("AlertTitles.1", "alert_titles.json unreadable") {
            val o = com.google.gson.JsonParser.parseString(FILE.readText()).asJsonObject
            for ((k, s) in slots) o.getAsJsonObject(k)?.let { j ->
                s.x = j.get("x")?.asInt ?: -1; s.y = j.get("y")?.asInt ?: -1; s.scale = j.get("s")?.asDouble ?: 1.0
            }
        }
    }

    private fun save() {
        dirty = false
        val o = JsonObject()
        for ((k, s) in slots) if (s.x >= 0 || s.y >= 0 || s.scale != 1.0) o.add(k, JsonObject().also { it.addProperty("x", s.x); it.addProperty("y", s.y); it.addProperty("s", s.scale) })
        val json = GsonBuilder().setPrettyPrinting().create().toJson(o)
        fishmod.utils.IoExecutor.execute { FishDiag.guard("AlertTitles.2", "alert_titles.json write failed") { fishmod.utils.SafeFiles.writeAtomic(FILE, json) } }
    }
}
