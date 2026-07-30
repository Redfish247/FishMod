package fishmod.utils

/** Case-insensitive, comma-separated player-name list (used for party-action whitelists/blacklists). */
object NameList {

    @JvmStatic
    fun contains(csv: String?, name: String?): Boolean {
        if (csv == null || csv.isBlank() || name == null) return false
        for (s in csv.split(",")) {
            if (s.trim().equals(name.trim(), ignoreCase = true)) return true
        }
        return false
    }

    @JvmStatic
    fun add(csv: String?, name: String?): String? {
        if (name == null || name.isBlank() || contains(csv, name)) return csv
        val names = toList(csv)
        names.add(name.trim())
        return names.joinToString(",")
    }

    @JvmStatic
    fun remove(csv: String?, name: String?): String? {
        if (name == null) return csv
        val names = toList(csv)
        names.removeIf { it.equals(name.trim(), ignoreCase = true) }
        return names.joinToString(",")
    }

    @JvmStatic
    fun toList(csv: String?): ArrayList<String> {
        val out = ArrayList<String>()
        if (csv == null || csv.isBlank()) return out
        for (s in csv.split(",")) {
            val t = s.trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out
    }
}
