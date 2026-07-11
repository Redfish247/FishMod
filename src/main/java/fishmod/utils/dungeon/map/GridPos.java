package fishmod.utils.dungeon.map;

/**
 * A cell in the dungeon's fixed 6x6 room grid, in half-unit coordinates (0..10): room-quadrant
 * cells sit at even (x, z); door/connector cells sit at odd/even or even/odd. Unlike earlier
 * versions of this class, grid coordinates are no longer derived from the player's world position
 * at all — the whole grid is indexed purely off the map's own pixel data (see MapReader), matching
 * how Odin (github.com/odtheking/Odin) does it. That sidesteps needing any correspondence between
 * world coordinates and map pixel space, which turned out to be the source of miscalibration.
 */
public record GridPos(int x, int z) {
    public boolean isRoomCell() {
        return (x & 1) == 0 && (z & 1) == 0;
    }

    public GridPos offset(int dx, int dz) {
        return new GridPos(x + dx, z + dz);
    }
}
