package fishmod.compat.conflicts

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import fishmod.utils.debug.FishDiag
import net.fabricmc.loader.api.FabricLoader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path

/** A FishMod feature master toggle the conflicts page can read and flip. */
class FishFeatureRef(val name: String, val get: () -> Boolean, val set: (Boolean) -> Unit)

/** Position another mod stores for a HUD: raw x/y (and scale when it has one). */
class OtherPos(val x: Double, val y: Double, val scale: Double?)

/** One boolean setting found in another mod's config file. */
class OtherSetting(
    val file: String,
    val path: List<String>,
    val label: String,
    val group: String,
    val enabled: Boolean,
    val writable: Boolean,
    val pos: OtherPos? = null,
) {
    val key: String get() = file + "|" + path.joinToString("/")
}

/** [exact]: the names match fully (not just similar), so a bulk action may touch the FishMod feature. */
class ConflictEntry(val setting: OtherSetting, val fish: FishFeatureRef?, val exact: Boolean = false)
class ModGroup(val id: String, val name: String, val entries: List<ConflictEntry>, val note: String?)
class ScanResult(val mods: List<ModGroup>)

/**
 * Finds other Skyblock mods, reads the on/off settings from their config files, matches them against FishMod
 * features by name, and queues "turn this off" edits. Other mods rewrite their config when the game closes, so
 * edits are queued and applied by [ConflictPreLaunch] on the next launch, before those mods read their config.
 */
object ModConflicts {
    private const val PENDING_FILE = "fishmod/conflict-pending.json"
    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private val KNOWN_IDS = setOf(
        "skyhanni", "skyblocker", "odin", "noammaddons", "firmament", "devonian", "notenoughupdates", "betterpv",
        "sbo", "skyblockoverhaul", "skyblockaddons", "dulkirmod", "cookies-mod", "skyblocktweaks", "bazaarutils",
    )
    private val NEVER_IDS = setOf("hypixel-mod-api", "hypixelmodapi", "modmenu", "fabricloader", "minecraft", "java")
    private val GENERIC_KEYS = setOf("enabled", "enable", "value", "state", "on", "active", "toggle", "status")
    private val IDENT_FIELDS = listOf("name", "option", "id", "key")
    private val NOISE = Regex("(first ?(run|launch|time)|seen|dismiss|version|migrat|telemetry|setup complete|acknowledg)")
    private val SKIP_FILE = Regex("(cache|data|storage|waypoint|persistent|backup|\\.bak|profiles|keybind)")
    private val SKIP_DIR = setOf("repo", "cache", "data", "backups", "profiles", "logs")
    private val STOP = setOf("show", "display", "enable", "enabled", "toggle", "the", "a", "an", "of", "to", "in", "on", "for", "and", "mod", "feature", "render")
    private val SYNONYM = mapOf("esp" to "highlight", "glow" to "highlight")

    private class Raw(val path: List<String>, val label: String, val group: String, val enabled: Boolean)
    private class PosRaw(val path: List<String>, val pos: OtherPos)

    // ---------- scanning ----------

    @JvmStatic
    fun scan(fish: List<FishFeatureRef>): ScanResult {
        val loader = FabricLoader.getInstance()
        val cfg = loader.configDir
        val fishTokens = fish.map { it to tokens(it.name) }
        val groups = ArrayList<ModGroup>()
        for (mod in loader.allMods) {
            val md = mod.metadata
            val id = md.id
            if (id in NEVER_IDS || id.startsWith("fishmod") || id.startsWith("fabric-")) continue
            val text = (md.name + " " + md.description).lowercase()
            if (id !in KNOWN_IDS && !text.contains("skyblock") && !text.contains("hypixel")) continue
            try {
                val files = findConfigFiles(cfg, id, md.name)
                val entries = ArrayList<ConflictEntry>()
                for (f in files) {
                    val rel = cfg.relativize(f).toString().replace('\\', '/')
                    val writable = rel.endsWith(".json", true)
                    val read = readSettings(f)
                    for (r in read.first) {
                        if (!r.enabled) continue
                        val s = OtherSetting(rel, r.path, r.label, r.group, true, writable, posFor(r.path, read.second))
                        val m = bestMatch(tokens(r.label), fishTokens)
                        entries.add(ConflictEntry(s, m?.first, (m?.second ?: 0f) >= 0.99f))
                    }
                    if (entries.size > 1500) break
                }
                entries.sortWith(compareBy({ it.fish == null }, { it.setting.label.lowercase() }))
                val note = if (files.isEmpty()) "No readable config file found" else if (entries.isEmpty()) "No enabled toggles found" else null
                groups.add(ModGroup(id, md.name, entries, note))
            } catch (t: Throwable) {
                FishDiag.fail("ModConflicts.1", "scan failed for mod $id", t)
            }
        }
        groups.sortWith(compareBy({ -it.entries.count { e -> e.fish != null } }, { it.name.lowercase() }))
        return ScanResult(groups)
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun findConfigFiles(cfg: Path, id: String, name: String): List<Path> {
        val keys = listOf(norm(id), norm(name)).filter { it.length >= 4 }.distinct()
        val out = LinkedHashSet<Path>()
        if (!Files.isDirectory(cfg)) return emptyList()
        Files.list(cfg).use { s ->
            for (p in s) {
                val fn = p.fileName.toString()
                val dir = Files.isDirectory(p)
                val n = norm(if (dir) fn else fn.substringBeforeLast('.', fn))
                if (keys.none { n.startsWith(it) || (n.length >= 5 && it.startsWith(n)) }) continue
                if (dir) collect(p, 2, out) else if (isConfigFile(p)) out.add(p)
            }
        }
        return out.toList()
    }

    private fun collect(dir: Path, depth: Int, out: MutableSet<Path>) {
        Files.list(dir).use { s ->
            for (p in s) {
                val fn = p.fileName.toString().lowercase()
                if (Files.isDirectory(p)) { if (depth > 1 && fn !in SKIP_DIR) collect(p, depth - 1, out) } else if (isConfigFile(p)) out.add(p)
            }
        }
    }

    private fun isConfigFile(p: Path): Boolean {
        val fn = p.fileName.toString().lowercase()
        if (!(fn.endsWith(".json") || fn.endsWith(".json5") || fn.endsWith(".jsonc"))) return false
        if (SKIP_FILE.containsMatchIn(fn)) return false
        return try { Files.size(p) < 3_000_000 } catch (_: Throwable) { false }
    }

    private fun parse(text: String): JsonElement {
        val r = JsonReader(StringReader(text)); r.isLenient = true
        return JsonParser.parseReader(r)
    }

    private fun readSettings(f: Path): Pair<List<Raw>, List<PosRaw>> {
        val out = ArrayList<Raw>()
        val pos = ArrayList<PosRaw>()
        try {
            flatten(parse(Files.readString(f)), emptyList(), emptyList(), out, pos, 0)
        } catch (t: Throwable) {
            FishDiag.fail("ModConflicts.2", "could not read ${f.fileName}", t)
        }
        return out to pos
    }

    private fun num(o: JsonObject, k: String): Double? = o.get(k)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble

    // A HUD position belongs to a toggle when it sits in the same feature object (up to two levels below the toggle's parent).
    private fun posFor(path: List<String>, all: List<PosRaw>): OtherPos? {
        val parent = path.dropLast(1)
        var best: PosRaw? = null
        for (p in all) {
            if (p.path.size < parent.size || p.path.size > parent.size + 3 || p.path.subList(0, parent.size) != parent) continue
            if (best == null || p.path.size < best.path.size) best = p
        }
        return best?.pos
    }

    private fun identOf(o: JsonObject): Pair<String, String>? {
        for (f in IDENT_FIELDS) {
            val v = o.get(f)
            if (v != null && v.isJsonPrimitive && v.asJsonPrimitive.isString) return f to v.asString
        }
        return null
    }

    private fun flatten(el: JsonElement, path: List<String>, names: List<String>, out: MutableList<Raw>, pos: MutableList<PosRaw>, depth: Int) {
        if (depth > 10 || out.size > 4000) return
        if (el.isJsonObject) {
            val o = el.asJsonObject
            val ident = identOf(o)
            val px = num(o, "x"); val py = num(o, "y")
            if (px != null && py != null && pos.size < 2000) pos.add(PosRaw(path, OtherPos(px, py, num(o, "scale"))))
            for ((k, v) in o.entrySet()) {
                if (v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) {
                    val generic = k.lowercase() in GENERIC_KEYS
                    val base = if (generic && names.isNotEmpty()) names else names + pretty(k)
                    val label = base.last()
                    if (NOISE.containsMatchIn(label.lowercase()) || NOISE.containsMatchIn(k.lowercase())) continue
                    out.add(Raw(path + k, label, base.dropLast(1).takeLast(2).joinToString(" › "), v.asBoolean))
                } else if (v.isJsonObject || v.isJsonArray) {
                    flatten(v, path + k, names + pretty(k), out, pos, depth + 1)
                }
            }
            if (ident == null) return
        } else if (el.isJsonArray) {
            val a: JsonArray = el.asJsonArray
            if (a.size() > 600) return
            for (e in a) {
                if (!e.isJsonObject) continue
                val (field, value) = identOf(e.asJsonObject) ?: continue
                flatten(e, path + "@$field=$value", names + value, out, pos, depth + 1)
            }
        }
    }

    private fun pretty(k: String): String {
        val spaced = k.replace(Regex("[_\\-.$]+"), " ").replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").trim()
        return spaced.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    // ---------- matching ----------

    private fun tokens(s: String): Set<String> =
        s.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ").lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in STOP }
            .map { t -> val x = SYNONYM[t] ?: t; if (x.length > 3 && x.endsWith("s") && !x.endsWith("ss")) x.dropLast(1) else x }
            .toSet()

    private fun bestMatch(t: Set<String>, fish: List<Pair<FishFeatureRef, Set<String>>>): Pair<FishFeatureRef, Float>? {
        if (t.isEmpty()) return null
        var best: FishFeatureRef? = null
        var bestScore = 0f
        for ((f, ft) in fish) {
            if (ft.isEmpty()) continue
            val inter = t.count { it in ft }
            if (inter == 0) continue
            val jac = inter.toFloat() / (t.size + ft.size - inter)
            val ok = jac >= 0.5f || (inter >= 2 && inter == minOf(t.size, ft.size))
            if (ok && jac > bestScore) { bestScore = jac; best = f }
        }
        return best?.let { it to bestScore }
    }

    // ---------- pending edits ----------

    private class Pending(val file: String, val path: List<String>, val value: Boolean, val mod: String, val label: String)

    private var pending: MutableMap<String, Pending>? = null

    private fun pendingPath(): Path = FabricLoader.getInstance().configDir.resolve(PENDING_FILE)

    @Synchronized
    private fun loadPending(): MutableMap<String, Pending> {
        pending?.let { return it }
        val m = LinkedHashMap<String, Pending>()
        try {
            val p = pendingPath()
            if (Files.exists(p)) {
                for (e in JsonParser.parseString(Files.readString(p)).asJsonArray) {
                    val o = e.asJsonObject
                    val path = o.getAsJsonArray("path").map { it.asString }
                    val pe = Pending(o.get("file").asString, path, o.get("value").asBoolean, o.get("mod")?.asString ?: "", o.get("label")?.asString ?: "")
                    m[pe.file + "|" + path.joinToString("/")] = pe
                }
            }
        } catch (t: Throwable) {
            FishDiag.fail("ModConflicts.3", "could not read pending conflict edits", t)
        }
        pending = m
        return m
    }

    @Synchronized
    private fun savePending() {
        val m = loadPending()
        try {
            val p = pendingPath()
            if (m.isEmpty()) { Files.deleteIfExists(p); return }
            Files.createDirectories(p.parent)
            val arr = JsonArray()
            for (e in m.values) {
                val o = JsonObject()
                o.addProperty("file", e.file); o.addProperty("value", e.value); o.addProperty("mod", e.mod); o.addProperty("label", e.label)
                val pa = JsonArray(); e.path.forEach { pa.add(it) }; o.add("path", pa)
                arr.add(o)
            }
            Files.writeString(p, GSON.toJson(arr))
        } catch (t: Throwable) {
            FishDiag.fail("ModConflicts.4", "could not save pending conflict edits", t)
        }
    }

    @JvmStatic @Synchronized fun isQueued(s: OtherSetting): Boolean = loadPending().containsKey(s.key)

    @JvmStatic @Synchronized
    fun queueDisable(s: OtherSetting, modName: String) {
        if (!s.writable) return
        loadPending()[s.key] = Pending(s.file, s.path, false, modName, s.label)
        savePending()
    }

    @JvmStatic @Synchronized
    fun unqueue(s: OtherSetting) {
        loadPending().remove(s.key)
        savePending()
    }

    // ---------- ignored (user cannot or will not resolve) ----------

    private const val IGNORED_FILE = "fishmod/conflict-ignored.json"
    private var ignored: MutableSet<String>? = null

    private fun ignoredPath(): Path = FabricLoader.getInstance().configDir.resolve(IGNORED_FILE)

    @Synchronized
    private fun loadIgnored(): MutableSet<String> {
        ignored?.let { return it }
        val set = LinkedHashSet<String>()
        try {
            val p = ignoredPath()
            if (Files.exists(p)) for (e in JsonParser.parseString(Files.readString(p)).asJsonArray) set.add(e.asString)
        } catch (t: Throwable) {
            FishDiag.fail("ModConflicts.6", "could not read ignored conflicts", t)
        }
        ignored = set
        return set
    }

    @Synchronized
    private fun saveIgnored() {
        val set = loadIgnored()
        try {
            val p = ignoredPath()
            if (set.isEmpty()) { Files.deleteIfExists(p); return }
            Files.createDirectories(p.parent)
            val arr = JsonArray(); set.forEach { arr.add(it) }
            Files.writeString(p, GSON.toJson(arr))
        } catch (t: Throwable) {
            FishDiag.fail("ModConflicts.7", "could not save ignored conflicts", t)
        }
    }

    private fun modKey(modId: String) = "mod:$modId"

    @JvmStatic @Synchronized fun isIgnored(s: OtherSetting): Boolean = loadIgnored().contains(s.key)
    @JvmStatic @Synchronized fun isModIgnored(modId: String): Boolean = loadIgnored().contains(modKey(modId))

    @JvmStatic @Synchronized
    fun setIgnored(s: OtherSetting, on: Boolean) { if (if (on) loadIgnored().add(s.key) else loadIgnored().remove(s.key)) saveIgnored() }

    @JvmStatic @Synchronized
    fun setModIgnored(modId: String, on: Boolean) { if (if (on) loadIgnored().add(modKey(modId)) else loadIgnored().remove(modKey(modId))) saveIgnored() }

    /** Runs from the preLaunch entrypoint, before other mods load their config. */
    @JvmStatic
    fun applyPending() {
        val p = pendingPath()
        if (!Files.exists(p)) return
        val m = loadPending().values.toList()
        val cfg = FabricLoader.getInstance().configDir
        var applied = 0
        for ((file, edits) in m.groupBy { it.file }) {
            try {
                val target = cfg.resolve(file)
                if (!Files.exists(target)) continue
                val original = Files.readString(target)
                val root = parse(original)
                var text = original
                var n = 0
                for (e in edits) {
                    val steps = concretePath(root, e.path) ?: continue
                    val patched = TextPatch.setBoolean(text, steps, e.value) ?: continue
                    if (setAt(root, e.path, e.value)) { text = patched; n++ }
                }
                if (n == 0 || text == original) continue
                if (parse(text) != root) {
                    FishDiag.fail("ModConflicts.8", "patched $file differs from expected, left untouched", IllegalStateException("verify failed"))
                    continue
                }
                val bak = target.resolveSibling(target.fileName.toString() + ".fishmod-bak")
                Files.copy(target, bak, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                Files.writeString(target, text)
                applied += n
            } catch (t: Throwable) {
                FishDiag.fail("ModConflicts.5", "could not apply conflict edit to $file", t)
            }
        }
        synchronized(this) { loadPending().clear(); savePending() }
        if (applied > 0) System.out.println("[FishMod] Applied $applied queued setting change(s) to other mods' configs")
    }

    private fun concretePath(root: JsonElement, path: List<String>): List<Any>? {
        val steps = ArrayList<Any>()
        var cur: JsonElement = root
        for (tok in path) {
            if (tok.startsWith("@")) {
                val arr = cur as? JsonArray ?: return null
                val eq = tok.indexOf('=')
                val f = tok.substring(1, eq); val want = tok.substring(eq + 1)
                val idx = arr.indexOfFirst { e -> e.isJsonObject && e.asJsonObject.get(f)?.let { x -> x.isJsonPrimitive && x.asString == want } == true }
                if (idx < 0) return null
                steps.add(idx); cur = arr[idx]
            } else {
                steps.add(tok); cur = (cur as? JsonObject)?.get(tok) ?: return null
            }
        }
        return steps
    }

    private fun setAt(root: JsonElement, path: List<String>, v: Boolean): Boolean {
        var cur: JsonElement = root
        for ((i, tok) in path.withIndex()) {
            if (i == path.lastIndex) {
                val o = cur as? JsonObject ?: return false
                if (tok.startsWith("@") || o.get(tok)?.isJsonPrimitive != true) return false
                o.addProperty(tok, v)
                return true
            }
            cur = if (tok.startsWith("@")) {
                val arr = cur as? JsonArray ?: return false
                val eq = tok.indexOf('=')
                val f = tok.substring(1, eq); val want = tok.substring(eq + 1)
                arr.firstOrNull { e -> e.isJsonObject && e.asJsonObject.get(f)?.let { x -> x.isJsonPrimitive && x.asString == want } == true } ?: return false
            } else {
                (cur as? JsonObject)?.get(tok) ?: return false
            }
        }
        return false
    }
}
