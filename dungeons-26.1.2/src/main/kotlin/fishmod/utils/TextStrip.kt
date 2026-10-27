package fishmod.utils

import java.util.regex.Pattern

object TextStrip {
    private val SECTION: Pattern = Pattern.compile("§.")
    private val SECTION_AMP: Pattern = Pattern.compile("(?i)[&§][0-9a-fk-or]")

    @JvmStatic
    fun plain(s: String): String = if (s.indexOf('§') < 0) s else SECTION.matcher(s).replaceAll("")

    // also strips '&' codes, valid codes only
    @JvmStatic
    fun plainAmp(s: String): String = SECTION_AMP.matcher(s).replaceAll("")
}
