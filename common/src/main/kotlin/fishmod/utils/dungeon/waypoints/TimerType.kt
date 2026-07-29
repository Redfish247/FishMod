package fishmod.utils.dungeon.waypoints

/** Speedrun-route tag stored on a waypoint as metadata only — no timer logic is driven by this in this pass. */
enum class TimerType {
    NONE, START, CHECKPOINT, END
}
