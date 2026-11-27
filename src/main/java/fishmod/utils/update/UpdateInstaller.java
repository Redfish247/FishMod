package fishmod.utils.update;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

public final class UpdateInstaller {

    public static void main(String[] args) throws Exception {
        if (args.length != 4) return;
        long pid = Long.parseLong(args[0]);
        Path oldJar = Paths.get(args[1]);
        Path staged = Paths.get(args[2]);
        Path target = Paths.get(args[3]);
        log("old=" + oldJar + " staged=" + staged + " target=" + target);

        ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
        if (parent != null) {
            try { parent.onExit().get(10, TimeUnit.MINUTES); } catch (Exception e) { log("gave up waiting for game exit: " + e); return; }
        }
        log("game exited");
        if (!Files.isRegularFile(staged)) { log("staged jar missing, nothing to do"); return; }
        long size = Files.size(staged);

        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.copy(staged, tmp, StandardCopyOption.REPLACE_EXISTING);

        Path backup = staged.resolveSibling(oldJar.getFileName() + ".bak");
        boolean moved = !Files.exists(oldJar);
        for (int i = 0; i < 60 && !moved; i++) {
            try {
                Files.move(oldJar, backup, StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (Exception e) {
                Thread.sleep(500);
            }
        }
        if (!moved) { log("old jar still locked, aborting"); Files.deleteIfExists(tmp); return; }
        log("old jar moved to backup");

        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (!Files.isRegularFile(target) || Files.size(target) != size) throw new IllegalStateException("target missing after move");
        } catch (Exception e) {
            log("install failed, restoring old jar: " + e);
            Files.deleteIfExists(tmp);
            if (Files.exists(backup)) Files.move(backup, oldJar, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        log("installed " + target.getFileName());
    }

    private static void log(String msg) {
        System.out.println("[" + LocalTime.now().withNano(0) + "] " + msg);
    }
}
