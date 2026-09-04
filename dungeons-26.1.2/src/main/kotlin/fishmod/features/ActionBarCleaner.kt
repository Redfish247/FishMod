package fishmod.features

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component

/**
 * Action Bar cleaner: drops unwanted segments out of Hypixel's SkyBlock action bar before
 * [net.minecraft.client.gui.Gui.setOverlayMessage] stores it (hooked in [fishmod.mixin.GuiMixin]).
 *
 * Hypixel's 26.1 stat bar is glyph-free and its colours shift with state (overflow health recolours
 * the health number, etc.), so we classify by POSITION + SHAPE, not colour:
 *   §c7,268/7,268   §a1,270   §b1,448/1,448 §3780   §4145/145
 *      Health        Defence     Mana      Overflow  Vitality
 * Segments split on every colour code (§l/§m/§n/§o/§k fold in). Keyword / distinctly-shaped bits
 * (Skill XP "+X Skill (…)", Mana-use "-N Mana", Bits, Secrets, Ragnarok, "T3!") are matched first
 * and don't take a positional slot; then the plain numbers fall into order:
 *   X/Y  #1 Health  #2 Mana  #3 Vitality
 *   bare #1/#2 before Mana = Defence / True Defence ; bare after Mana = Overflow Mana
 * Legacy glyphs (❤ ❈ ❂ ✎ ʬ) still match too. Anything unrecognised is always kept.
 *
 * The vanilla HUD overlays in the same feature (XP bar, armor row, absorption hearts) are not
 * action-bar text and are handled by [fishmod.mixin.GuiMixin] / [fishmod.mixin.ExperienceBarRendererMixin].
 */
object ActionBarCleaner {

    private val COLOR = fishmod.utils.Constants.STRIP_COLOR_REGEX
    private val NONNUM  = Regex("[^0-9,/]")
    private val PAIR    = Regex("[\\d,]+/[\\d,]+")
    private val BARE    = Regex("[\\d,]+")

    private val MANA_USE = Regex("^-[\\d,]+ Mana\\b")                    // -40 Mana (Ability)
    private val SKILL_XP = Regex("^\\+[\\d,.]+ [A-Za-z ]+ \\(.+\\)$")    // +144.5 Combat (1,089,440,920/0)
    private val ARROW    = Regex("^[\\d,]+x .+? Arrows?\\b", RegexOption.IGNORE_CASE) // 31x Armorshred Arrow
    private val BITS     = Regex("\\+?[\\d,]+ Bits\\b", RegexOption.IGNORE_CASE)
    private val SECRETS  = Regex("Secrets?\\b", RegexOption.IGNORE_CASE)
    private val RAG_AXE  = Regex("ragnar", RegexOption.IGNORE_CASE)      // "RAGNAROK 4s" — best-effort
    private val LASER    = Regex("^T\\d+!?$|laser", RegexOption.IGNORE_CASE) // "T3!" F7 terminal / laser
    private val VITALITY = Regex("Vitality", RegexOption.IGNORE_CASE)

    private fun isColorCode(c: Char): Boolean {
        val l = c.lowercaseChar()
        return l in '0'..'9' || l in 'a'..'f' || l == 'r'
    }

    /** Split on colour codes; §l/§m/§n/§o/§k fold into the current segment (keep the active colour). */
    private fun segments(raw: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '§' && i + 1 < raw.length) {
                if (isColorCode(raw[i + 1]) && sb.isNotEmpty()) {
                    out.add(sb.toString()); sb.setLength(0)
                }
                sb.append(c).append(raw[i + 1]); i += 2
            } else {
                sb.append(c); i++
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    @JvmStatic
    fun filter(message: Component): Component {
        if (!FishSettings.actionBarEnabled || !Location.inSkyblock()) return message
        val raw = message.string
        // Only touch the SkyBlock stat bar — leaves jukebox / other overlay messages alone.
        if ('/' !in raw && "Mana" !in raw && '❤' !in raw) return message

        val segs = segments(raw)
        if (segs.size < 2) return message

        var pairSeen = 0   // running X/Y count  (1 Health, 2 Mana, 3 Vitality)
        var defSeen = 0    // bare numbers before Mana  (1 Defence, 2 True Defence)
        var changed = false
        val kept = StringBuilder()

        for (seg in segs) {
            val text = COLOR.replace(seg, "").trim()
            val core = NONNUM.replace(text, "")
            val isPair = '/' in core && PAIR.matches(core)
            val isBare = '/' !in core && core.isNotEmpty() && BARE.matches(core)

            val drop = when {
                text.isEmpty() -> false

                '❤' in seg -> FishSettings.abHideHealth
                '✎' in seg -> FishSettings.abHideMana
                'ʬ' in seg -> FishSettings.abHideOverflowMana
                '❂' in seg -> FishSettings.abHideTrueDefense
                '❈' in seg -> FishSettings.abHideDefense

                MANA_USE.containsMatchIn(text) -> FishSettings.abHideManaUse
                SKILL_XP.containsMatchIn(text) -> FishSettings.abHideSkillXp
                ARROW.containsMatchIn(text)    -> FishSettings.abHideArmorStacks
                BITS.containsMatchIn(text)     -> FishSettings.abHideBits
                SECRETS.containsMatchIn(text)  -> FishSettings.abHideSecrets
                RAG_AXE.containsMatchIn(text)  -> FishSettings.abHideRagAxeTimer
                LASER.containsMatchIn(text)    -> FishSettings.abHideTermLaser
                VITALITY.containsMatchIn(text) -> FishSettings.abHideVitality

                isPair -> {
                    pairSeen++
                    when (pairSeen) {
                        1 -> FishSettings.abHideHealth
                        2 -> FishSettings.abHideMana
                        else -> FishSettings.abHideVitality
                    }
                }
                isBare -> {
                    if (pairSeen < 2) {
                        defSeen++
                        if (defSeen == 1) FishSettings.abHideDefense else FishSettings.abHideTrueDefense
                    } else {
                        FishSettings.abHideOverflowMana
                    }
                }
                else -> false
            }
            if (drop) changed = true else kept.append(seg)
        }
        if (!changed) return message
        return Component.literal(kept.toString().trim().ifEmpty { " " })
    }
}
