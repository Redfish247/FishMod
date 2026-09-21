package fishmod.cosmetic.badge

/** Server-defined badge metadata, fetched from `/badge-defs` — never hardcoded client-side, so
 *  the admin dashboard can add/edit/reorder/disable badges without a mod update. */
data class BadgeDef(
    val id: String,
    val displayName: String,
    val symbol: String,
    val colorRgb: Int,
    val order: Int,
)
