package fishmod.utils.events.interfaces;

import fishmod.utils.Location;

public interface LocationChangeEvent {
    boolean onLocationChange(Location newLocation);
}
