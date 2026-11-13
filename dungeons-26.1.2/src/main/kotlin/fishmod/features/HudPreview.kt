package fishmod.features

import fishmod.shaded.practicalconfig.hud.HUDComponent
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** One static HUD example: drawn at (0,0) at scale 1 by the HUD's own render code; w/h is its real in-game size. */
class HudExample(val w: Int, val h: Int, val draw: (GuiGraphicsExtractor) -> Unit)

/** HUD editor previews: each HUD registers an example built from its real render path fed with example data. */
object HudPreview {
    const val LINE_H = 10
    const val TEXT_H = 9

    /** True only while the editor draws an example, so a render that reads live state can use example data instead. */
    @JvmField
    var active = false

    private val makers = HashMap<String, () -> HudExample?>()

    @JvmStatic
    fun register(name: String, make: () -> HudExample?) {
        makers[name] = make
    }

    @JvmStatic
    fun has(name: String) = makers.containsKey(name)

    private val ALIASES: Map<String, List<String>> = mapOf(
        "Charmed Visitor HUD" to listOf("Charmed Visitor"), "Composter HUD" to listOf("Composter"), "Crop Milestone HUD" to listOf("Crop Milestone"),
        "Jacob's Contest HUD" to listOf("Jacob's Contest"), "Mooshroom Cow Perk HUD" to listOf("Mooshroom Cow Perk"), "Pest Timer HUD" to listOf("Pest Timer"),
        "Pet HUD" to listOf("Pet"), "Rare Crop Tracker" to listOf("Rare Crops"), "Greenhouse Status" to listOf("Greenhouse"),
        "Hoe Level Overflow" to listOf("Hoe Level"), "Garden Level Overflow" to listOf("Garden Level"),
        "Farming Profit Tracker" to listOf("Farming Profit"), "Mining Profit Tracker" to listOf("Mining Profit"),
        "Mineshaft Pity Display" to listOf("Mineshaft Pity"), "Fossil Excavator Solver" to listOf("Fossil Solver"), "Log Book Stats" to listOf("Log Book"),
        "Mouse Sensitivity Reducer" to listOf("Sensitivity"), "Puzzle Overlay" to listOf("Puzzles"), "Puzzle Solvers" to listOf("Puzzles", "Quiz Timer"),
        "Crown of Avarice Counter" to listOf("Crown of Avarice"), "Crown of Avarice Tracker" to listOf("Crown of Avarice"),
        "Diana Tracker" to listOf("Diana Loot Tracker", "Diana Mob Tracker", "Diana Stats", "Diana Magic Find"),
        "Fishing Trackers" to listOf("Fishing Area Tracker", "Fishing Efficiency", "Fishing Profit", "Fishing Sea Creatures", "Barn Fishing Timer", "Fishing Festival"),
        "Kuudra Chests" to listOf("Kuudra Chest Counter", "Kuudra Chest Value", "Kuudra Profit"),
        "Kuudra Boss" to listOf("Kuudra Health", "Kuudra Backbone"), "Supply Helper" to listOf("Kuudra Supplies"), "Build Helper" to listOf("Kuudra Build", "Kuudra Fresh Timers"),
        "Mining Abilities" to listOf("Maniac Miner", "SkyMall", "Pickobulus"),
        "RNG Meter HUD" to listOf("Slayer RNG Meter"), "Spawn Progress HUD" to listOf("Slayer Spawn"), "Slayer Stats HUD" to listOf("Slayer Stats"),
        "Carry Tracker HUD" to listOf("Slayer Carries"), "Profit Tracker" to listOf("Slayer Profit"), "Boss Timer" to listOf("Slayer Boss Timer"),
        "Fishing Alerts" to listOf("Fishing Creature Alert", "Fishing Rare Drop Alert", "Fishing Hotspot Alert", "Fishing Wormhole Alert",
            "Fishing Trophy Alert", "Fishing Deployable Alert", "Fishing Bait Alert"),
        "Rift Guide Overlay" to listOf("Rift Guide"),
        "Burger Warning" to listOf("Alert: Rift Burgers"), "Leech Supreme Helper" to listOf("Alert: Rift Leech"),
        "Bacte Helper" to listOf("Alert: Rift Bacte / Crux"), "Crux Helper" to listOf("Alert: Rift Bacte / Crux"),
        "Colosseum Kill Zone Warning" to listOf("Alert: Rift Kill Zone"), "Half-Eaten Cave Hay Warning" to listOf("Alert: Rift Hay"),
        "Vampire Slayer Helper" to listOf("Alert: Rift Vampire"),
        "Blessing Display" to listOf("Blessings"), "Performance HUD" to listOf("Performance", "Clock"), "Soulflow HUD" to listOf("Soulflow"),
        "Ragnarock" to listOf("Rag Timer"), "M7 Relics" to listOf("Relic Countdown"), "Wither Dragons" to listOf("Wither Dragon Timer"),
        "Experiment Profit Tracker" to listOf("Experiment Profit"), "Enable Map" to listOf("Dungeon Map", "Dungeon Map Info"),
        "Mythos Mob HP" to listOf("Diana Mythos HP"), "No Shuriken Overlay" to listOf("Diana No Shuriken"),
        "Kuudra Fight Extras" to listOf("Kuudra Backbone"),
        "Pest Spawn Title" to listOf("Garden Titles"), "Visitor Arrival Alert" to listOf("Garden Titles"),
        "Boss Tick Clocks" to listOf("Boss Tick Clock", "Storm Kill Time", "Py Tick Timer", "Necron LB Timer", "Necron Drag Timer", "Necron Drop Lava/Platform",
            "LB Release Timer", "Storm Crush Banner", "Pillar Blast Timer", "Terminals Open In"),
        "Crystal Respawn" to listOf("Crystal Respawn", "Crystal Not Placed"),
    )

    @JvmStatic
    fun namesFor(feature: String): List<String> {
        if (makers.containsKey(feature)) return listOf(feature)
        return (ALIASES[feature] ?: return emptyList()).filter { makers.containsKey(it) }
    }

    @JvmStatic
    fun example(name: String): HudExample? {
        val m = makers[name] ?: return null
        return try { m() } catch (t: Throwable) { FishDiag.fail("HudPreview.1", "HUD example '$name' build failed", t); null }
    }

    @JvmStatic
    fun draw(ex: HudExample, ctx: GuiGraphicsExtractor, name: String) {
        active = true
        try { ex.draw(ctx) } catch (t: Throwable) { FishDiag.fail("HudPreview.2", "HUD example '$name' draw failed", t) } finally { active = false }
    }

    private fun font() = Minecraft.getInstance().font

    @JvmStatic
    fun textWidth(lines: List<String>, right: List<String>? = null, gap: Int = 6): Int {
        val f = font()
        if (right != null) return lines.indices.maxOfOrNull { f.width(lines[it]) + gap + f.width(right.getOrElse(it) { "" }) } ?: 0
        return lines.maxOfOrNull { f.width(it) } ?: 0
    }

    @JvmStatic
    fun textHeight(lines: Int, lineH: Int = LINE_H) = if (lines <= 0) 0 else (lines - 1) * lineH + TEXT_H

    /** The shared text-block draw (white shadowed text at origin) used by the in-game HUDs and their examples. */
    @JvmStatic
    @JvmOverloads
    fun drawText(ctx: GuiGraphicsExtractor, lines: List<String>, lineH: Int = LINE_H, w: Int = 0, center: Boolean = false, color: Int = -1) {
        val f = font()
        lines.forEachIndexed { i, s -> ctx.text(f, s, if (center) (w - f.width(s)) / 2 else 0, i * lineH, color, true) }
    }

    /** A plain text example: size from the text itself (minW applies when centered, like the component box). */
    @JvmStatic
    @JvmOverloads
    fun text(lines: List<String>, lineH: Int = LINE_H, center: Boolean = false, minW: Int = 0, color: Int = -1): HudExample {
        val w = Math.max(minW, textWidth(lines))
        return HudExample(w, textHeight(lines.size, lineH)) { ctx -> drawText(ctx, lines, lineH, w, center, color) }
    }

    /** Example of a HUDComponent HUD: its real RenderSupplier, drawn where the component sits so scaledX/scaledY land on the origin. */
    @JvmStatic
    @JvmOverloads
    fun component(c: HUDComponent, render: HUDComponent.RenderSupplier, w: Int = c.width, h: Int = c.height): HudExample =
        HudExample(w, h) { ctx ->
            val pose = ctx.pose()
            pose.pushMatrix()
            try {
                pose.translate(-c.scaledX.toFloat(), -c.scaledY.toFloat())
                render.render(c, ctx)
            } finally {
                pose.popMatrix()
            }
        }
}
