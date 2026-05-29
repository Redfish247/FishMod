package fishmod.features.challenges;

import java.util.HashMap;
import java.util.Map;

/** Lightweight snapshot of a Hypixel SkyBlock profile for gap-detection & completion checks. */
public class ProfileSnapshot {
    /** skill name (lowercase, e.g. "combat","mining","farming") -> XP */
    public Map<String, Long> skillXp = new HashMap<>();
    /** pet identifier (e.g. "ENDER_DRAGON_LEGENDARY") -> level (1..200) */
    public Map<String, Integer> petLevels = new HashMap<>();
    /** slayer name (zombie, spider, wolf, enderman, blaze, vampire) -> XP */
    public Map<String, Long> slayerXp = new HashMap<>();
    /** "F1".."F7","M1".."M7","E" -> completions */
    public Map<String, Long> floorCompletions = new HashMap<>();
    /** catacombs XP */
    public long cataXp = 0;
    /** SkyBlock XP (leveling.experience) — divide by 100 for "SkyBlock level". */
    public long skyblockXp = 0;
    /** coin_purse + banking.balance */
    public long purseAndBank = 0;
    /** how many pets are currently at level 100. */
    public int  petsAt100 = 0;
    /** collection name -> amount collected (we map "amount" to tier later) */
    public Map<String, Long> collections = new HashMap<>();
    /** Heart of the Mountain level. */
    public int  hotmLevel = 0;
    /** Magical Power (accessory_bag_storage.highest_magical_power). */
    public int  magicalPower = 0;
    /** Total fairy souls collected. */
    public int  fairySouls = 0;
    /** Dungeon class levels — keys: healer/mage/berserk/archer/tank. */
    public Map<String, Integer> classLevels = new HashMap<>();
    /** SkyBlock level (leveling.experience / 100). */
    public int  sbLevel = 0;

    public long fetchedAtMs = 0;
}
