package fishmod.utils.dungeon.map;

/**
 * A live player position on the map, in fractional tile coordinates (e.g. x=2.5 = halfway through
 * tile 2). {@code name} is the raw name string off the map decoration (may be null — Hypixel
 * doesn't always populate it), used to look up a skin for the head icon of non-self markers.
 */
public record PlayerMarker(float tileX, float tileZ, float yaw, boolean self, String name) {
}
