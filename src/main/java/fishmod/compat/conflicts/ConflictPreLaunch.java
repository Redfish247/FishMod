package fishmod.compat.conflicts;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Applies setting changes queued from the Conflicts page to other mods' configs before they load. */
public class ConflictPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        try {
            ModConflicts.applyPending();
        } catch (Throwable t) {
            System.err.println("[FishMod] applying queued conflict edits failed: " + t);
        }
    }
}
