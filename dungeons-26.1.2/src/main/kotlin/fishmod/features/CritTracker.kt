package fishmod.features

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fishmod.utils.Constants
import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type
import java.util.regex.Pattern

/**
 * Backs the `.crit` / `!crit` command:
 *  - <b>Crit</b>: per-enemy damage of your P1 (Maxor) Explosive Shot hits — latest + running avg.
 *  - <b>Storm Kill</b>: the P2 clock time (seconds) at which Storm dies — latest + running avg.
 *    Fed from [fishmod.features.dungeon.f7.StormTickTimer]; only recorded while you're on Archer.
 *
 * Samples are appended to config/fishmod-crit.json and kept across runs and relogs (rolling
 * window of the last [MAX_SAMPLES]). The average is over every stored sample, not the current run.
 */
object CritTracker {

    // Same line ExplosiveShot.kt parses: "Your Explosive Shot hit N enemy/enemies for D damage"
    private val PATTERN: Pattern = Pattern.compile(
        "Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,]+(?:\\.\\d+)?) damage"
    )

    private const val MAX_SAMPLES = 100
    private const val FILE_PATH = "config/fishmod-crit.json"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private class Store {
        val crit: MutableList<Double> = ArrayList()
        val storm: MutableList<Double> = ArrayList()
    }

    private var data = Store()

    @JvmStatic
    fun init() {
        load()
        Events.ON_GAME_MESSAGE.register { text -> onMessage(text.string) }
    }

    private fun onMessage(s: String?): Boolean {
        if (s == null || s.indexOf("Explosive Shot") < 0) return false
        if (!Phase.inP1()) return false

        val m = PATTERN.matcher(s)
        if (!m.find()) return false

        val enemies: Int
        val total: Double
        try {
            enemies = m.group(1).toInt()
            total = m.group(2).replace(",", "").toDouble()
        } catch (e: NumberFormatException) {
            return false
        }
        if (enemies <= 0) return false

        add(data.crit, total / enemies)
        return false // keep the original chat line
    }

    /** Storm's P2 death time in seconds, pushed by StormTickTimer. Archer-only. */
    @JvmStatic
    fun onStormDeath(seconds: Double) {
        if (seconds <= 0.0 || !DungeonClass.isClass(DungeonClass.ARCHER)) return
        add(data.storm, seconds)
    }

    private fun add(list: MutableList<Double>, value: Double) {
        list.add(value)
        while (list.size > MAX_SAMPLES) list.removeAt(0)
        save()
    }

    private fun last(list: List<Double>): Double = if (list.isEmpty()) 0.0 else list[list.size - 1]
    private fun avg(list: List<Double>): Double = if (list.isEmpty()) 0.0 else list.sum() / list.size

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private fun formatDamage(v: Double): String {
        if (v <= 0.0) return "N/A"
        if (v == Math.floor(v) && !v.isInfinite()) return String.format("%,d", v.toLong())
        return String.format("%,.1f", v)
    }

    private fun formatTime(v: Double): String = if (v <= 0.0) "N/A" else Constants.DECIMAL_FORMAT.format(v) + "s"

    @JvmStatic
    fun buildMessage(): String {
        return "Crit: " + formatDamage(last(data.crit)) + " (avg " + formatDamage(avg(data.crit)) + ", " + data.crit.size + ") | " +
            "Storm Kill: " + formatTime(last(data.storm)) + " (avg " + formatTime(avg(data.storm)) + ", " + data.storm.size + ")"
    }

    private fun load() {
        val file = File(FILE_PATH)
        if (!file.exists()) return
        try {
            FileReader(file).use { reader ->
                val type: Type = object : TypeToken<Store>() {}.type
                val loaded: Store? = GSON.fromJson(reader, type)
                if (loaded != null) data = loaded
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        try {
            val file = File(FILE_PATH)
            file.parentFile?.mkdirs()
            FileWriter(file).use { writer -> GSON.toJson(data, writer) }
        } catch (_: Exception) {
        }
    }
}
