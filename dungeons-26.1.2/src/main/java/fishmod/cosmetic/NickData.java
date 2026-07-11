package fishmod.cosmetic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/** Persists the raw cosmetic nick to <gameDir>/CosmeticNameChanger/nick.txt across sessions. */
public final class NickData {
    private NickData() {}

    private static Path file() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("CosmeticNameChanger");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        return dir.resolve("nick.txt");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                String raw = Files.readString(f).trim();
                if (!raw.isEmpty()) NickState.applyFromDisk(raw);
            }
        } catch (IOException ignored) {}
    }

    public static void save(String raw) {
        try {
            Path f = file();
            if (raw != null && !raw.isEmpty()) {
                Files.writeString(f, raw);
            } else {
                Files.deleteIfExists(f);
            }
        } catch (IOException ignored) {}
    }
}
