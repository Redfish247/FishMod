package fishmod.utils.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MixinGuard {
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private MixinGuard() {}

    public static void run(String code, String message, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            fail(code, message, t);
        }
    }

    public static void fail(String code, String message, Throwable t) {
        FishDiag.fail(code, message, t);
        if (LOGGED.add(code)) FishDebug.LOGGER.error("[FishMod] " + message + "; suppressed (logged once)", t);
    }
}
