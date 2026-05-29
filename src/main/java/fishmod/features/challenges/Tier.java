package fishmod.features.challenges;

public enum Tier {
    DAILY  ("Daily",   24L * 60 * 60 * 1000,        10,  "§a"),
    WEEKLY ("Weekly",  7L  * 24 * 60 * 60 * 1000,   50,  "§b"),
    MONTHLY("Monthly", 30L * 24 * 60 * 60 * 1000,   250, "§d");

    public final String label;
    public final long   durationMs;
    public final int    basePoints;
    public final String color;

    Tier(String label, long durationMs, int basePoints, String color) {
        this.label = label;
        this.durationMs = durationMs;
        this.basePoints = basePoints;
        this.color = color;
    }
}
