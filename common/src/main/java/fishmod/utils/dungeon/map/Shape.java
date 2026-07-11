package fishmod.utils.dungeon.map;

/** Human-readable room shape label, derived from segment count/bounding box for display only. */
public enum Shape {
    ONE_BY_ONE, ONE_BY_TWO, ONE_BY_THREE, ONE_BY_FOUR, TWO_BY_TWO, L_SHAPE, OTHER;

    public static Shape fromSegmentCount(int count, int bboxW, int bboxH) {
        return switch (count) {
            case 1 -> ONE_BY_ONE;
            case 2 -> ONE_BY_TWO;
            case 3 -> bboxW == 2 && bboxH == 2 ? L_SHAPE : ONE_BY_THREE;
            case 4 -> bboxW == 2 && bboxH == 2 ? TWO_BY_TWO : ONE_BY_FOUR;
            default -> OTHER;
        };
    }
}
