package fishmod.features.dungeon.map
import fishmod.compat.hudHidden

import fishmod.utils.debug.FishDiag
import fishmod.utils.config.values.DungeonMapSettings
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

object MapInfoHud {

    private var cachedL1: String = ""
    private var cachedL2: String = ""
    private var cachedBlockW: Int = 0

    @JvmStatic
    fun enabled(): Boolean = DungeonMapSettings.mapInfoEnabled == true

    @JvmStatic
    fun register() {
        fishmod.utils.rendering.FishHudLayer.addLast(Identifier.fromNamespaceAndPath("fishmod", "dungeon_map_info_hud")) { g, _ ->
            if (enabled() && !fishmod.features.FishHudEditor.isOpen()) {
                val mc = Minecraft.getInstance()
                if (!mc.hudHidden && DungeonState.isInDungeon()) {
                    val allowed = if (DungeonMapSettings.mapInfoMapTied) {
                        DungeonMapSettings.mapEnabled && !DungeonState.isInBoss()
                    } else {
                        !(DungeonState.isInBoss() && DungeonMapSettings.mapScoreStandaloneHideInBoss)
                    }
                    if (allowed) {
                        try { render(g, mc, false) } catch (e: Exception) { FishDiag.fail("MapInfoHud.1", "map info HUD render failed score=${DungeonScore.score} secrets=${DungeonScore.secretsFound}", e) }
                    }
                }
            }
        }
    }

    private fun render(g: GuiGraphicsExtractor, mc: Minecraft, edit: Boolean) {
        val data = info(edit && Scan.rooms.isEmpty())
        val l1 = line1(data)
        val l2 = line2(data)
        if (l1.isEmpty() && l2.isEmpty()) return

        val s = DungeonMapSettings.mapInfoScale.coerceIn(0.5f, 20.0f)
        val lh = mc.font.lineHeight
        val pose = g.pose()

        if (DungeonMapSettings.mapInfoMapTied) {
            val a = MapHud.tiedAnchor()
            pose.pushMatrix()
            pose.translate(a[0], a[1])
            pose.scale(s, s)
            drawLines(g, mc, l1, l2, 0, lh)
            pose.popMatrix()
        } else {
            if (l1 != cachedL1 || l2 != cachedL2) {
                cachedL1 = l1
                cachedL2 = l2
                cachedBlockW = maxOf(mc.font.width(l1), mc.font.width(l2))
            }
            pose.pushMatrix()
            pose.translate(DungeonMapSettings.mapInfoX, DungeonMapSettings.mapInfoY)
            pose.scale(s, s)
            drawLines(g, mc, l1, l2, cachedBlockW / 2, lh)
            pose.popMatrix()
        }
    }

    private fun drawLines(g: GuiGraphicsExtractor, mc: Minecraft, l1: String, l2: String, cx: Int, lh: Int) {
        if (l1.isNotEmpty() && l2.isNotEmpty()) {
            g.centeredText(mc.font, l1, cx, 0, -1)
            g.centeredText(mc.font, l2, cx, lh + 1, -1)
        } else {
            g.centeredText(mc.font, l1.ifEmpty { l2 }, cx, 0, -1)
        }
    }

    // Example: the real pieces with example values, laid out like the non-tied block (origin = left edge)
    fun registerPreview() {
        fishmod.features.HudPreview.register("Dungeon Map Info") {
            val font = Minecraft.getInstance().font
            val l1 = line1(EXAMPLE)
            val l2 = line2(EXAMPLE)
            if (l1.isEmpty() && l2.isEmpty()) return@register null
            val w = maxOf(font.width(l1), font.width(l2))
            val h = if (l1.isNotEmpty() && l2.isNotEmpty()) font.lineHeight * 2 + 1 else font.lineHeight
            fishmod.features.HudExample(w, h) { drawLines(it, Minecraft.getInstance(), l1, l2, w / 2, font.lineHeight) }
        }
    }

    // Example block width at scale 1 (same measure as the example)
    private fun exampleW(): Int {
        val font = Minecraft.getInstance().font
        return maxOf(font.width(line1(EXAMPLE)), font.width(line2(EXAMPLE)))
    }

    private fun scale() = DungeonMapSettings.mapInfoScale.coerceIn(0.5f, 20.0f)

    @JvmStatic
    fun editorX(): Int =
        if (DungeonMapSettings.mapInfoMapTied) Math.round(MapHud.tiedAnchor()[0] - exampleW() * scale() / 2f)
        else DungeonMapSettings.mapInfoX.toInt()

    @JvmStatic
    fun editorY(): Int =
        if (DungeonMapSettings.mapInfoMapTied) Math.round(MapHud.tiedAnchor()[1]) else DungeonMapSettings.mapInfoY.toInt()

    private fun untie() {
        if (!DungeonMapSettings.mapInfoMapTied) return
        val x = editorX(); val y = editorY()
        DungeonMapSettings.mapInfoMapTied = false
        DungeonMapSettings.mapInfoX = x.toFloat()
        DungeonMapSettings.mapInfoY = y.toFloat()
    }

    @JvmStatic
    fun setEditorX(v: Int) {
        if (v == editorX()) return
        untie()
        DungeonMapSettings.mapInfoX = v.toFloat()
    }

    @JvmStatic
    fun setEditorY(v: Int) {
        if (v == editorY()) return
        untie()
        DungeonMapSettings.mapInfoY = v.toFloat()
    }

    private fun noWords(): Boolean = DungeonMapSettings.mapInfoNoWords

    private fun join(vararg parts: String): String {
        val sb = StringBuilder()
        for (p in parts) {
            if (p.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("   ")
                sb.append(p)
            }
        }
        return sb.toString()
    }

    /** The numbers the info lines show; live from DungeonScore or example data for the editor. */
    private class Info(
        val secrets: Int, val needed: Int, val tail: Int, val score: Int,
        val deaths: Int, val mimic: Boolean, val prince: Boolean, val crypts: Int,
    )

    private val EXAMPLE = Info(34, 41, 52, 287, 0, false, false, 3)

    private fun info(example: Boolean): Info = if (example) EXAMPLE else Info(
        DungeonScore.secretsFound,
        DungeonScore.calculateMinimumSecrets(false, false),
        if (DungeonMapSettings.mapInfoShowLeft) Math.max(0, 300 - DungeonScore.projectedFullClearScore()) else DungeonScore.calculateTotalSecrets(),
        DungeonScore.score, DungeonScore.deaths, DungeonScore.mimicKilled, DungeonScore.princeKilled, DungeonScore.crypts,
    )

    private fun line1(i: Info): String = join(secretsPiece(i), scorePiece(i))

    private fun line2(i: Info): String = join(deathsPiece(i), mimicPiece(i), princePiece(i), cryptsPiece(i))

    private fun secretsPiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowSecrets) return ""
        val s = "§b${i.secrets}§7-§e${i.needed}§7-§c${i.tail}"
        return if (noWords()) s else "§fSecrets: $s"
    }

    private fun scorePiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowScore) return ""
        val color = if (i.score < 270) "§c" else if (i.score < 300) "§e" else "§a"
        val prefix = if (noWords()) "" else "§fScore: "
        return "$prefix$color${i.score}"
    }

    private fun deathsPiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowDeaths) return ""
        val d = (if (i.deaths > 0) "§c" else "§a") + i.deaths
        val prefix = if (noWords()) "§7D: " else "§fDeaths: "
        return prefix + d
    }

    private fun mimicPiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowMimic) return ""
        if (DungeonMapSettings.mapInfoHideCompleted && i.mimic) return ""
        val prefix = if (noWords()) "§7M: " else "§fM: "
        return prefix + if (i.mimic) "§a✔" else "§c✖"
    }

    private fun princePiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowPrince) return ""
        if (DungeonMapSettings.mapInfoHideCompleted && i.prince) return ""
        val prefix = if (noWords()) "§7P: " else "§fP: "
        return prefix + if (i.prince) "§a✔" else "§c✖"
    }

    private fun cryptsPiece(i: Info): String {
        if (!DungeonMapSettings.mapInfoShowCrypts) return ""
        val cr = minOf(i.crypts, 5)
        if (DungeonMapSettings.mapInfoHideCompleted && cr >= 5) return ""
        val cc = if (cr >= 5) "§a" else if (cr >= 3) "§e" else "§c"
        return if (noWords()) "$cc$cr§7/§a5" else "§fCrypts: $cc$cr"
    }

}
