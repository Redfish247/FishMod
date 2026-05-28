package fishmod.features.challenges;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** JSON-backed store for active + historical challenges and the running point total. */
public class ChallengeProgress {

    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("fishmod_challenges.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Active challenge per tier (one of each). */
    public Map<Tier, Challenge> active = new EnumMap<>(Tier.class);
    public List<Challenge> history = new ArrayList<>();
    public long totalPoints = 0;

    /** Rerolls used in a given calendar month (YYYY-MM). 1 reroll per month, across all tiers. */
    public String rerollMonthKey = "";
    public int    rerollsUsedThisMonth = 0;

    private static ChallengeProgress INSTANCE;

    public static synchronized ChallengeProgress get() {
        if (INSTANCE == null) INSTANCE = load();
        return INSTANCE;
    }

    private static ChallengeProgress load() {
        try {
            if (!Files.exists(FILE)) return new ChallengeProgress();
            ChallengeProgress p = GSON.fromJson(Files.readString(FILE), ChallengeProgress.class);
            if (p == null) return new ChallengeProgress();
            if (p.active == null) p.active = new EnumMap<>(Tier.class);
            if (p.history == null) p.history = new ArrayList<>();
            if (p.rerollMonthKey == null) p.rerollMonthKey = "";
            // Drop any pre-refactor or otherwise corrupt active challenges so the HUD never
            // shows stale targets (e.g. an old multi-pet challenge with target=123).
            p.active.entrySet().removeIf(en -> {
                Challenge c = en.getValue();
                if (c == null || c.template == null || c.tier == null) return true;
                if (c.target <= c.baseline) return true;
                return false;
            });
            return p;
        } catch (IOException | RuntimeException e) {
            return new ChallengeProgress();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (IOException ignored) {}
    }

    public synchronized boolean canReroll() {
        String cur = currentMonthKey();
        if (!cur.equals(rerollMonthKey)) return true;
        return rerollsUsedThisMonth < 1;
    }

    public synchronized void noteReroll() {
        String cur = currentMonthKey();
        if (!cur.equals(rerollMonthKey)) { rerollMonthKey = cur; rerollsUsedThisMonth = 0; }
        rerollsUsedThisMonth++;
        save();
    }

    public static String currentMonthKey() {
        java.time.LocalDate d = java.time.LocalDate.now();
        return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
    }
}
