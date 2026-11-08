package fishmod.features.fishing

import fishmod.utils.SkyblockLocation
import fishmod.utils.SkyblockLocation.*

// Sea creature table adapted from Feesh (Sleepy-Panda, Apache-2.0); see NOTICE. ALL STRINGS UNVERIFIED IN GAME.
enum class SeaRarity(val code: Char) { COMMON('f'), UNCOMMON('a'), RARE('9'), EPIC('5'), LEGENDARY('6'), MYTHIC('d') }

class SeaCreature(
    val name: String,
    val rarity: SeaRarity,
    val regex: Regex,
    val rare: Boolean,
    val noDouble: Boolean,
    val immunity: Boolean,
    val boss: Boolean,
    val lava: Boolean,
    val magmaFields: Boolean,
    // empty = any fishing world
    val areas: Set<SkyblockLocation>,
    // literal that every spawn line contains; cheap test before the regex
    val needle: String,
) {
    // Spawn immunity after the mob appears (Feesh: 5 s)
    val immunityMs: Long get() = if (immunity) 5000L else 0L

    fun inArea(loc: SkyblockLocation): Boolean = areas.isEmpty() || loc in areas
}

object SeaCreatures {
    // flags: space-separated subset of rare nodbl imm boss lava magma
    private fun sc(
        name: String, r: SeaRarity, flags: String, area: SkyblockLocation?, rx: String, needle: String = name,
    ): SeaCreature {
        val f = flags.split(' ').toSet()
        return SeaCreature(name, r, Regex("^$rx$"), "rare" in f, "nodbl" in f, "imm" in f, "boss" in f, "lava" in f, "magma" in f,
            if (area == null) emptySet() else setOf(area), needle)
    }

    private val C = SeaRarity.COMMON
    private val U = SeaRarity.UNCOMMON
    private val R = SeaRarity.RARE
    private val E = SeaRarity.EPIC
    private val L = SeaRarity.LEGENDARY
    private val M = SeaRarity.MYTHIC

    @JvmField val ALL: List<SeaCreature> = listOf(
        sc("Water Hydra", L, "rare imm", null, """The Water Hydra has come to test your strength\."""),
        sc("Carrot King", R, "rare", null, """Is this even a fish\? It's the Carrot King!"""),
        sc("Squid", C, "", null, """A Squid appeared\."""),
        sc("Sea Walker", C, "", null, """You caught a Sea Walker\."""),
        sc("Sea Witch", U, "", null, """It looks like you've disrupted the Sea Witch's brewing session\. Watch out, she's furious!"""),
        sc("Sea Archer", U, "", null, """You reeled in a Sea Archer\."""),
        sc("Rider of the Deep", U, "", null, """The Rider of the Deep has emerged\."""),
        sc("Catfish", R, "", null, """Huh\? A Catfish!"""),
        sc("Sea Leech", R, "", null, """Gross! A Sea Leech!"""),
        sc("Guardian Defender", E, "", null, """You've discovered a Guardian Defender of the sea\."""),
        sc("Deep Sea Protector", E, "", null, """You have awoken the Deep Sea Protector, prepare for a battle!"""),
        sc("Agarimoo", R, "", null, """Your Chumcap Bucket trembles, it's an Agarimoo\."""),
        sc("Inkling", U, "", null, """You get an inkling that you've caught\.\.\. an Inkling!"""),
        sc("Manta Ray", E, "", null, """A majestic creature rises from the water\. It's a Manta Ray\."""),
        sc("Frog Man", C, "", null, """Is it a frog\? Is it a man\? Well, yes, sorta, IT'S FROG MAN!!!!!!""", "FROG MAN"),
        sc("Snapping Turtle", R, "", null, """A Snapping Turtle is coming your way, and it's ANGRY!"""),
        sc("Blue Ringed Octopus", L, "rare imm", null, """A garish set of tentacles arise\. It's a Blue Ringed Octopus!"""),
        sc("Wiki Tiki", M, "rare imm", null, """The water bubbles and froths\. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price\."""),
        sc("Great White Shark", L, "rare imm", null, """Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!"""),
        sc("Nurse Shark", U, "", null, """A tiny fin emerges from the water, you've caught a Nurse Shark\."""),
        sc("Blue Shark", R, "", null, """You spot a fin as blue as the water it came from, it's a Blue Shark\."""),
        sc("Tiger Shark", E, "", null, """A striped beast bounds from the depths, the wild Tiger Shark!"""),
        sc("Reindrake", M, "rare boss", JERRYS_WORKSHOP, """A Reindrake forms from the depths\."""),
        sc("Yeti", L, "rare imm", JERRYS_WORKSHOP, """What is this creature!\?""", "What is this creature"),
        sc("Nutcracker", E, "rare", JERRYS_WORKSHOP, """You found a forgotten Nutcracker laying beneath the ice\."""),
        sc("Frozen Steve", C, "", JERRYS_WORKSHOP, """Frozen Steve fell into the pond long ago, never to resurface\.\.\.until now!"""),
        sc("Frosty", U, "", JERRYS_WORKSHOP, """It's a snowman! He looks harmless\.""", "snowman"),
        sc("Grinch", R, "", JERRYS_WORKSHOP, """The Grinch stole Jerry's Gifts\.\.\.get them back!"""),
        sc("Phantom Fisher", L, "rare imm", null, """The spirit of a long lost Phantom Fisher has come to haunt you\."""),
        sc("Grim Reaper", M, "rare imm", null, """This can't be! The manifestation of death himself!""", "manifestation of death"),
        sc("Scarecrow", U, "", null, """Phew! It's only a Scarecrow\."""),
        sc("Nightmare", R, "", null, """You hear trotting from beneath the waves, you caught a Nightmare\."""),
        sc("Werewolf", E, "", null, """It must be a full moon, a Werewolf appears\."""),
        sc("Jumpin' Jack", C, "", null, """Watch out! It's Jumpin' Jack\."""),
        sc("Fried Chicken", C, "lava", CRIMSON_ISLE, """Smells of burning\. Must be a Fried Chicken\."""),
        sc("Fireproof Witch", R, "lava", CRIMSON_ISLE, """Trouble's brewing, it's a Fireproof Witch!"""),
        sc("Magma Slug", U, "lava", CRIMSON_ISLE, """From beneath the lava appears a Magma Slug\."""),
        sc("Moogma", U, "lava", CRIMSON_ISLE, """You hear a faint Moo from the lava\.\.\. A Moogma appears\."""),
        sc("Lava Leech", R, "lava", CRIMSON_ISLE, """A small but fearsome Lava Leech emerges\."""),
        sc("Pyroclastic Worm", R, "lava", CRIMSON_ISLE, """You feel the heat radiating as a Pyroclastic Worm surfaces\."""),
        sc("Lava Flame", R, "lava", CRIMSON_ISLE, """A Lava Flame flies out from beneath the lava\."""),
        sc("Fire Eel", R, "lava", CRIMSON_ISLE, """A Fire Eel slithers out from the depths\."""),
        sc("Taurus", E, "lava", CRIMSON_ISLE, """Taurus and his steed emerge\."""),
        sc("Volcanic Snail", U, "lava", CRIMSON_ISLE, """You feel a burning sensation as you reel in a Volcanic Snail!"""),
        sc("Magma Pillar", E, "rare lava", CRIMSON_ISLE, """A Magma Pillar rises from the lava\."""),
        sc("Fiery Scuttler", L, "rare imm lava", CRIMSON_ISLE, """A Fiery Scuttler inconspicuously waddles up to you, friends in tow\."""),
        sc("Thunder", L, "rare imm lava boss", CRIMSON_ISLE, """You hear a massive rumble as Thunder emerges\."""),
        sc("Lord Jawbus", M, "rare lava boss", CRIMSON_ISLE, """You have angered a legendary creature\.\.\. Lord Jawbus has arrived\."""),
        sc("Plhlegblast", M, "rare lava boss", CRIMSON_ISLE, """WOAH! A Plhlegblast appeared\."""),
        sc("Ragnarok", M, "rare lava boss", CRIMSON_ISLE, """The sky darkens and the air thickens\. The end times are upon us: Ragnarok is here\."""),
        sc("Vanquisher", E, "rare nodbl imm lava", CRIMSON_ISLE, """A Vanquisher is spawning nearby!"""),
        sc("Oasis Rabbit", U, "", THE_FARMING_ISLANDS, """An Oasis Rabbit appears from the water\."""),
        sc("Oasis Sheep", U, "", THE_FARMING_ISLANDS, """An Oasis Sheep appears from the water\."""),
        sc("Abyssal Miner", L, "rare imm", CRYSTAL_HOLLOWS, """An Abyssal Miner breaks out of the water!"""),
        sc("Water Worm", R, "", CRYSTAL_HOLLOWS, """A Water Worm surfaces!"""),
        sc("Poisoned Water Worm", R, "", CRYSTAL_HOLLOWS, """A Poisoned Water Worm surfaces!"""),
        sc("Flaming Worm", R, "", CRYSTAL_HOLLOWS, """A Flaming Worm surfaces from the depths!"""),
        sc("Lava Blaze", E, "magma", CRYSTAL_HOLLOWS, """A Lava Blaze has surfaced from the depths!"""),
        sc("Lava Pigman", E, "magma", CRYSTAL_HOLLOWS, """A Lava Pigman arose from the depths!"""),
        sc("Small Mithril Grubber", U, "", DWARVEN_MINES, """A leech of the mines surfaces\.\.\. you've caught a Mithril Grubber\.""", "Mithril Grubber"),
        sc("Medium Mithril Grubber", U, "", DWARVEN_MINES, """A leech of the mines surfaces\.\.\. you've caught a Medium Mithril Grubber\.""", "Mithril Grubber"),
        sc("Large Mithril Grubber", U, "", DWARVEN_MINES, """A leech of the mines surfaces\.\.\. you've caught a Large Mithril Grubber\.""", "Mithril Grubber"),
        sc("Bloated Mithril Grubber", U, "", DWARVEN_MINES, """A leech of the mines surfaces\.\.\. you've caught a Bloated Mithril Grubber\.""", "Mithril Grubber"),
        sc("Trash Gobbler", C, "", null, """The Trash Gobbler is hungry for you!"""),
        sc("Dumpster Diver", U, "", null, """A Dumpster Diver has emerged from the swamp!"""),
        sc("Banshee", R, "", BACKWATER_BAYOU, """The desolate wail of a Banshee breaks the silence\."""),
        sc("Bayou Sludge", E, "", BACKWATER_BAYOU, """A swampy mass of slime emerges, the Bayou Sludge!"""),
        sc("Alligator", L, "rare imm", BACKWATER_BAYOU, """A long snout breaks the surface of the water\. It's an Alligator!"""),
        sc("Titanoboa", M, "rare imm", BACKWATER_BAYOU, """A massive Titanoboa surfaces\. Its body stretches as far as the eye can see\."""),
        sc("Nessie", M, "rare imm", MOONGLADE_MARSH, """You've caused a disturbance in the loch\. Could it be\.\.\. Nessie\?"""),
        sc("The Loch Emperor", L, "rare imm", MOONGLADE_MARSH, """The Loch Emperor arises from the depths\."""),
        sc("Bogged", C, "", MOONGLADE_MARSH, """You've hooked a Bogged!"""),
        sc("Tadgang", U, "", MOONGLADE_MARSH, """A gang of Liltads!""", "Liltads"),
        sc("Ent", U, "", MOONGLADE_MARSH, """You've hooked an Ent, as ancient as the forest itself\."""),
        sc("Wetwing", R, "", MOONGLADE_MARSH, """Look! A Wetwing emerges!"""),
        sc("Stridersurfer", R, "", MOONGLADE_MARSH, """You caught a Stridersurfer\."""),
        sc("Atoll Croaker", C, "", LOTUS_ATOLL, """An inquisitive Atoll Croaker takes the bait!"""),
        sc("Lotus Guardian", U, "", LOTUS_ATOLL, """A Lotus Guardian emerges, ready to protect the Atoll\."""),
        sc("gorF", R, "", LOTUS_ATOLL, """What even is that\?! A\.\.\. gorF\?"""),
        sc("Drowned Captain", E, "", LOTUS_ATOLL, """A Drowned Captain takes hold of your bobber!"""),
        sc("Puddle Jumper", L, "rare nodbl", LOTUS_ATOLL, """A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!"""),
        sc("Frog Prince", M, "rare imm", LOTUS_ATOLL, """Bow down before the Frog Prince\.\.\. or pay the hefty price!"""),
        sc("Haggard", C, "", TORRHUS_CANYON, """A Haggard stumbles to the shore, ready for a fight!"""),
        sc("Brineling", U, "", TORRHUS_CANYON, """A Brineling interrupts you with a stream of bubbles!"""),
        sc("Sprawl", R, "", TORRHUS_CANYON, """A Sprawl emerges from the blue, and it's looking for you!"""),
        sc("Torrid", E, "", TORRHUS_CANYON, """The laughter of a Torrid echoes through the air\."""),
        sc("Silkbreeze", L, "rare imm", TORRHUS_CANYON, """Something zips through the air - it's a Silkbreeze!"""),
        sc("Giant Isopod", M, "rare imm", TORRHUS_CANYON, """A Giant Isopod was dredged up from the depths!"""),
    )

    private val BY_NAME: Map<String, SeaCreature> = ALL.associateBy { it.name.lowercase() }

    @JvmStatic fun byName(name: String): SeaCreature? = BY_NAME[name.lowercase()]

    // First creature in the given location whose line matches; plain = colour-stripped chat.
    @JvmStatic fun match(plain: String, loc: SkyblockLocation, anyArea: Boolean): SeaCreature? {
        if (plain.length > 160) return null
        for (sc in ALL) {
            if (!anyArea && !sc.inArea(loc)) continue
            if (!plain.contains(sc.needle)) continue
            if (sc.regex.matches(plain)) return sc
        }
        return null
    }
}
