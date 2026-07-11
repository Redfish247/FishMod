package fishmod.utils.dungeon.map;

import java.util.ArrayList;
import java.util.List;

/**
 * A rotation-normalized fingerprint of a room's physical footprint plus which of its perimeter
 * sides have a confirmed door, used as the key into {@link RoomSignatureDB}. Two rooms with the
 * same shape and the same doors-relative-to-shape, just rotated differently, produce the same key.
 */
public record RoomSignature(String key, Shape shape) {

    public static RoomSignature of(RoomTile tile) {
        List<RoomTile> segments = DungeonGrid.segmentsOf(tile);
        List<int[]> cells = new ArrayList<>();
        for (RoomTile seg : segments) cells.add(new int[]{seg.pos().x(), seg.pos().z()});

        List<int[]> doorOffsets = new ArrayList<>();
        for (RoomTile seg : segments) doorOffsets.addAll(doorSidesOf(seg.pos()));

        int minX = cells.stream().mapToInt(c -> c[0]).min().orElse(0);
        int minZ = cells.stream().mapToInt(c -> c[1]).min().orElse(0);
        int maxX = cells.stream().mapToInt(c -> c[0]).max().orElse(0);
        int maxZ = cells.stream().mapToInt(c -> c[1]).max().orElse(0);
        Shape shape = Shape.fromSegmentCount(cells.size(), (maxX - minX) / 2 + 1, (maxZ - minZ) / 2 + 1);

        String canonical = canonicalize(cells, doorOffsets);
        return new RoomSignature(canonical, shape);
    }

    /**
     * Builds the canonical key for a hypothetical single-cell (1x1) room with doors on exactly the
     * given sides. Used by {@link RoomSignatureDB#predictPartial} to query candidate signatures for
     * a not-yet-fully-revealed room cell — see that method's javadoc for why prediction is scoped
     * to single cells only in this version.
     *
     * @param doorSides subset of {@code {(1,0), (-1,0), (0,1), (0,-1)}}
     */
    public static String singleCellKey(List<int[]> doorSides) {
        return canonicalize(List.of(new int[]{0, 0}), doorSides);
    }

    /** The (dx, dz) offsets of pos's 4 sides that have a confirmed door, in DungeonGrid's DoorKey scheme. */
    static List<int[]> doorSidesOf(GridPos pos) {
        List<int[]> sides = new ArrayList<>();
        if (DungeonGrid.allDoors().containsKey(new DoorKey(pos, true))) sides.add(new int[]{pos.x() + 1, pos.z()});
        if (DungeonGrid.allDoors().containsKey(new DoorKey(pos.offset(-1, 0), true))) sides.add(new int[]{pos.x() - 1, pos.z()});
        if (DungeonGrid.allDoors().containsKey(new DoorKey(pos, false))) sides.add(new int[]{pos.x(), pos.z() + 1});
        if (DungeonGrid.allDoors().containsKey(new DoorKey(pos.offset(0, -1), false))) sides.add(new int[]{pos.x(), pos.z() - 1});
        return sides;
    }

    /** Tries all 4 rotations of (cells, doors) together and returns the lexicographically smallest serialization. */
    private static String canonicalize(List<int[]> cells, List<int[]> doors) {
        String best = null;
        List<int[]> rotCells = cells;
        List<int[]> rotDoors = doors;
        for (int r = 0; r < 4; r++) {
            String serialized = serialize(rotCells, rotDoors);
            if (best == null || serialized.compareTo(best) < 0) best = serialized;
            rotCells = rotate(rotCells);
            rotDoors = rotate(rotDoors);
        }
        return best;
    }

    /** 90-degree rotation: (x, z) -> (z, -x). */
    private static List<int[]> rotate(List<int[]> points) {
        List<int[]> rotated = new ArrayList<>();
        for (int[] p : points) rotated.add(new int[]{p[1], -p[0]});
        return rotated;
    }

    private static String serialize(List<int[]> cells, List<int[]> doors) {
        int minX = cells.stream().mapToInt(c -> c[0]).min().orElse(0);
        int minZ = cells.stream().mapToInt(c -> c[1]).min().orElse(0);
        List<String> cellStrs = new ArrayList<>();
        for (int[] c : cells) cellStrs.add((c[0] - minX) + "," + (c[1] - minZ));
        cellStrs.sort(String::compareTo);
        List<String> doorStrs = new ArrayList<>();
        for (int[] d : doors) doorStrs.add((d[0] - minX) + "," + (d[1] - minZ));
        doorStrs.sort(String::compareTo);
        return "C[" + String.join(";", cellStrs) + "]D[" + String.join(";", doorStrs) + "]";
    }
}
