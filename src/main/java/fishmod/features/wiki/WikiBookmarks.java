package fishmod.features.wiki;

import fishmod.utils.config.FolderUtility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WikiBookmarks {

    private static final Path FILE = Paths.get(FolderUtility.CONFIG_PATH + "wiki_bookmarks.txt");
    private static final List<String[]> bookmarks = new ArrayList<>(); // {url, title}
    private static boolean loaded = false;

    public static List<String[]> all() {
        ensureLoaded();
        return Collections.unmodifiableList(bookmarks);
    }

    public static void add(String url, String title) {
        ensureLoaded();
        // Don't add duplicates
        for (String[] b : bookmarks) if (b[0].equals(url)) return;
        bookmarks.add(new String[]{url, title});
        save();
    }

    public static void remove(int index) {
        ensureLoaded();
        if (index >= 0 && index < bookmarks.size()) {
            bookmarks.remove(index);
            save();
        }
    }

    private static void ensureLoaded() {
        if (!loaded) { load(); loaded = true; }
    }

    private static void load() {
        bookmarks.clear();
        if (!Files.exists(FILE)) return;
        try {
            for (String line : Files.readAllLines(FILE)) {
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].isBlank())
                    bookmarks.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        } catch (IOException ignored) {}
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            StringBuilder sb = new StringBuilder();
            for (String[] b : bookmarks)
                sb.append(b[0]).append('\t').append(b[1]).append('\n');
            Files.writeString(FILE, sb.toString());
        } catch (IOException ignored) {}
    }
}
