package fishmod.utils.dungeon.map;

/**
 * A cell in the dungeon's logical grid. Room-quadrant cells sit at even (x, z); connector/door
 * cells sit at odd/even, even/odd, or odd/odd — mirroring the 32-block physical room spacing
 * (2 grid units = 1 room). Grid coordinates are relative to wherever the player first calibrated,
 * not an absolute dungeon origin, since the true origin isn't knowable until the whole dungeon
 * is explored.
 */
public record GridPos(int x, int z) {
    public boolean isRoomCell() {
        return (x & 1) == 0 && (z & 1) == 0;
    }

    public GridPos offset(int dx, int dz) {
        return new GridPos(x + dx, z + dz);
    }

    /**
     * Converts an absolute world position to its grid cell. Hypixel dungeons are always instanced
     * 8 blocks off a fixed 32-block room grid — a fixed fact about the game's coordinate system,
     * independent of any per-run calibration.
     */
    public static GridPos fromWorld(double x, double z) {
        int px = (int) Math.floor(x + 8.5);
        int pz = (int) Math.floor(z + 8.5);
        int nwX = px - Math.floorMod(px, 32) - 8;
        int nwZ = pz - Math.floorMod(pz, 32) - 8;
        int roomGridX = Math.floorDiv(nwX + 8, 32);
        int roomGridZ = Math.floorDiv(nwZ + 8, 32);
        return new GridPos(roomGridX * 2, roomGridZ * 2);
    }
}
