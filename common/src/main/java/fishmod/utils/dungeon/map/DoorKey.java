package fishmod.utils.dungeon.map;

/** Uniquely keys a door as "the connector on {@code tile}'s east side" (horizontal) or south side (vertical). */
public record DoorKey(GridPos tile, boolean horizontal) {
}
