package fishmod.utils.config

import java.lang.reflect.Modifier

// Int field values captured before the config file is applied, so colour pickers can offer "Default".
object ColorDefaults {
    private val defaults = HashMap<String, Int>()

    fun snapshot(classes: List<Class<*>>) {
        for (c in classes) {
            for (f in c.declaredFields) {
                if (f.type != Int::class.javaPrimitiveType || !Modifier.isStatic(f.modifiers)) continue
                try {
                    f.isAccessible = true
                    defaults.putIfAbsent(c.name + "." + f.name, f.getInt(null))
                } catch (_: Exception) { }
            }
        }
    }

    fun of(owner: Class<*>, field: String): Int? = defaults[owner.name + "." + field]

    fun of(fieldName: String): Int? = defaults.entries.firstOrNull { it.key.endsWith(".$fieldName") }?.value
}
