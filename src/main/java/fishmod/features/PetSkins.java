package fishmod.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Curated Hypixel SkyBlock pet-skin catalog, bundled as {@code assets/fishmod/petskins.json}
 * (generated from the NotEnoughUpdates item repo). Each entry is a pet-skin display name plus its
 * head texture (a base64 "textures" value), keyed by the SkyBlock {@code PET_SKIN_<TYPE>_<NAME>}
 * internal id so the customizer can offer the skins that match the pet you're editing.
 */
public final class PetSkins {
    private PetSkins() {}

    public record Skin(String id, String name, String tex) {}

    private static final Pattern TEX_HASH = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]+)");

    private static List<Skin> all; // lazily loaded

    /** All bundled pet skins (loaded once from the resource). Never null. */
    public static synchronized List<Skin> all() {
        if (all != null) return all;
        List<Skin> out = new ArrayList<>();
        try (InputStream in = PetSkins.class.getResourceAsStream("/assets/fishmod/petskins.json")) {
            if (in != null) {
                JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
                for (var el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    out.add(new Skin(o.get("id").getAsString(), o.get("name").getAsString(), o.get("tex").getAsString()));
                }
            }
        } catch (Exception ignored) {}
        all = out;
        return all;
    }

    /**
     * Skins that belong to the given pet type (e.g. {@code ENDER_DRAGON}). Pet-skin ids are prefixed
     * {@code PET_SKIN_<TYPE>_}, so a prefix match is exact. Returns all skins when {@code petType} is
     * null/empty (a generic head with no pet type).
     */
    public static List<Skin> forPet(String petType) {
        if (petType == null || petType.isEmpty()) return all();
        String prefix = "PET_SKIN_" + petType + "_";
        List<Skin> out = new ArrayList<>();
        for (Skin s : all()) if (s.id().startsWith(prefix)) out.add(s);
        return out;
    }

    /** Extracts the bare texture hash from a base64 "textures" value (for a tidy skin field), or null. */
    public static String hashFromValue(String b64) {
        if (b64 == null || b64.isEmpty()) return null;
        try {
            String json = new String(java.util.Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
            Matcher m = TEX_HASH.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {}
        return null;
    }
}
