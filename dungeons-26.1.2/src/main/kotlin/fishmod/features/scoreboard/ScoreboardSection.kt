package fishmod.features.scoreboard

import java.util.regex.Pattern

/** Known Hypixel Skyblock scoreboard line categories. Each has a match pattern (tested against
 *  the color-code-stripped line, with any leading icon glyph trimmed off) and its own on/off
 *  toggle in [fishmod.utils.config.values.FishSettings]. OTHER is the catch-all bucket for
 *  anything that doesn't match a known pattern, so unrecognized lines don't just disappear. */
enum class ScoreboardSection(val label: String, private val pattern: Pattern?) {
    DATE("Date", Pattern.compile("^(Early |Late )?(Spring|Summer|Autumn|Winter) \\d+(st|nd|rd|th)$")),
    TIME("Time of Day", Pattern.compile("^\\d{1,2}:\\d{2}(am|pm)$", Pattern.CASE_INSENSITIVE)),
    LOCATION("Location", Pattern.compile(
        "^(Hub|Village|Dungeon Hub|The Catacombs\\b.*|Crimson Isle|Spider's Den|The End|Winter Island|" +
            "Dwarven Mines|Crystal Hollows|Gold Mine|Deep Caverns|The Barn|Mushroom Desert|Blazing Fortress|" +
            "Kuudra's Hollow|The Rift|Garden|Private Island|South Park|Jerry's Workshop)$")),
    PLAYERS("Players", Pattern.compile("^Players: \\d+/\\d+$")),
    GAME_MODE("Game Mode", Pattern.compile("^(Ironman|Stranded|Bingo|Self[- ]?Sufficient)$")),
    PURSE("Purse", Pattern.compile("^Purse:")),
    BANK("Bank", Pattern.compile("^(Bank|Personal Bank|Co-op Bank):")),
    MOTES("Motes", Pattern.compile("^Motes:")),
    BITS("Bits", Pattern.compile("^Bits( Available)?:")),
    COPPER("Copper", Pattern.compile("^Copper:")),
    SOWDUST("Sowdust", Pattern.compile("^Sowdust:")),
    GEMS("Gems", Pattern.compile("^Gems:")),
    HEAT("Heat", Pattern.compile("^Heat:")),
    COLD("Cold", Pattern.compile("^Cold:")),
    NORTH_STARS("North Stars", Pattern.compile("^North Stars:")),
    SOULFLOW("Soulflow", Pattern.compile("^Soulflow:")),
    GUILD("Guild", Pattern.compile("^Guild:")),
    COOKIE("Cookie Buff", Pattern.compile("Booster Cookie|do not have a cookie|^Cookie Buff:", Pattern.CASE_INSENSITIVE)),
    SKILL_AVERAGE("Skill Average", Pattern.compile("^Skill Average:")),
    OBJECTIVE("Objective", Pattern.compile("^Objective:$")),
    SLAYER("Slayer", Pattern.compile("Slayer", Pattern.CASE_INSENSITIVE)),
    POWDER("Powder (HotM)", Pattern.compile("^Powder$")),
    DIANA("Diana", Pattern.compile("^Diana(\\s*\\(.*\\))?$")),
    PARTY("Party", Pattern.compile("^Party \\(\\d+\\):?$")),
    EQUIPMENT("Power/Tuning", Pattern.compile("^(Power|Tuning):")),
    DUNGEON("Dungeon Stats", Pattern.compile("Secrets Found|Completed Rooms|Crypts:|Team Deaths|Puzzles:|Cleared:|^Time:|The Catacombs", Pattern.CASE_INSENSITIVE)),
    PET("Pet", Pattern.compile("^Pet:")),
    OTHER("Other Lines", null); // catch-all, checked last

    fun matches(strippedLine: String): Boolean = pattern != null && pattern.matcher(strippedLine).find()

    companion object {
        /** Leading icon glyphs (private-use font codepoints, bullets, etc.) that precede a lot of
         *  Hypixel scoreboard lines and would otherwise stop a whole-line pattern like [LOCATION]
         *  or [GAME_MODE] from matching. */
        private val LEADING_ICON: Pattern = Pattern.compile("^[^\\p{L}\\p{N}]+")

        @JvmStatic
        fun classify(strippedLine: String): ScoreboardSection {
            val bare = LEADING_ICON.matcher(strippedLine).replaceFirst("").trim()
            for (s in entries) {
                if (s == OTHER) continue
                if (s.matches(strippedLine) || s.matches(bare)) return s
            }
            return OTHER
        }
    }
}
