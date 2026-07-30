package fishmod.features.item

/** SkyBlock item rarities and their background tint colors. Ported from blade-addons. */
enum class ItemRarity(val color: Int) {
    NONE(0x0),
    COMMON(0xffdddddd.toInt()),
    UNCOMMON(0xff42ab42.toInt()),
    RARE(0xff4c4cd0.toInt()),
    EPIC(0xff671067.toInt()),
    LEGENDARY(0xffcf8d0a.toInt()),
    MYTHIC(0xffff55ff.toInt()),
    DIVINE(0xff4fe4e4.toInt()),
    SPECIAL(0xffff5555.toInt()),
    VERY_SPECIAL(0xffc44747.toInt()),
    ULTIMATE(0xffa10202.toInt()),
    ADMIN(0xffaa0000.toInt())
}
