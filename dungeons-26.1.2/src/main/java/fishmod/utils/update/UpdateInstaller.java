package fishmod.utils.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

// Standalone (JDK-only) helper run in its own JVM after the game exits: swaps the old mod jar for the staged one.
// Copied out of the mod jar before launch, so it must stay a single class with no Kotlin / Minecraft references.
public final class UpdateInstaller {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) return;
        long pid = Long.parseLong(args[0]);
        Path oldJar = Paths.get(args[1]);
        Path staged = Paths.get(args[2]);
        Path target = Paths.get(args[3]);

        ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
        if (parent != null) {
            try { parent.onExit().get(10, TimeUnit.MINUTES); } catch (Exception ignored) { return; }
        }
        if (!Files.isRegularFile(staged)) return;

        // Windows can hold the jar lock briefly after exit; never move the new jar in while the old one remains (duplicate mod id).
        boolean removed = false;
        for (int i = 0; i < 60 && !removed; i++) {
            try {
                Files.deleteIfExists(oldJar);
                removed = true;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        if (!removed) return;

        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(staged, tmp, StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        Files.deleteIfExists(staged);
    }
}
