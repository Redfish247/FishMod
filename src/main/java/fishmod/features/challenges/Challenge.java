package fishmod.features.challenges;

import java.util.UUID;

/** A single active or completed challenge. Serialized to JSON. */
public class Challenge {
    public String id = UUID.randomUUID().toString();
    public Tier   tier;
    public ChallengeTemplate template;

    // Template parameters
    public String paramStr  = "";
    public long   paramLong = 0;
    public int    paramInt  = 0;

    // Progress tracking
    public double baseline  = 0;   // value at acceptance
    public double target    = 0;   // value to reach (baseline + delta, or absolute)
    public double current   = 0;   // last-known value

    // Timing
    public long startedAtMs   = 0;
    public long expiresAtMs   = 0;  // startedAtMs + tier.durationMs
    public long completedAtMs = 0;  // 0 if still active
    public long activeMs      = 0;  // wall-time minus AFK pause
    public long lastTickMs    = 0;  // for incrementing activeMs

    public int awardedPoints  = 0;

    public boolean isComplete() { return completedAtMs > 0; }
    public boolean isExpired()  { return !isComplete() && System.currentTimeMillis() > expiresAtMs; }

    public double progressPct() {
        if (target <= baseline) return isComplete() ? 1.0 : 0.0;
        double p = (current - baseline) / (target - baseline);
        return Math.max(0, Math.min(1, p));
    }

    public String describe() {
        return template == null ? "—" : template.describe(paramStr, paramLong, paramInt);
    }
}
