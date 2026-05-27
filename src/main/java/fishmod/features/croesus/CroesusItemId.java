package fishmod.features.croesus;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Optional;

/**
 * Resolves a SkyBlock item to a pricing-friendly id (Bazaar / lowest-BIN key).
 *
 * The raw SkyBlock id from CUSTOM_DATA works for most items, but a few classes
 * need re-mapping:
 *   • Enchanted books → ENCHANTMENT_<NAME>_<LEVEL>  (the actual bazaar key)
 *   • Runes           → RUNE_<TYPE>_<LEVEL>
 */
public final class CroesusItemId {

    public static String resolve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent nbt = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return nameBasedId(stack);
        NbtCompound c = nbt.copyNbt();
        String id = c.getString("id", null);
        // Some items (essences especially) may not carry a top-level id in older
        // snapshots — fall back to ExtraAttributes.id, then name derivation.
        if (id == null || id.isEmpty()) {
            NbtElement ea = c.get("ExtraAttributes");
            if (ea != null) {
                Optional<NbtCompound> eaOpt = ea.asCompound();
                if (eaOpt.isPresent()) id = eaOpt.get().getString("id", null);
            }
        }
        if (id == null || id.isEmpty()) return nameBasedId(stack);

        if (id.equals("ENCHANTED_BOOK")) {
            String[] best = firstEnchant(c);
            if (best != null) return "ENCHANTMENT_" + best[0].toUpperCase() + "_" + best[1];
            return id;
        }
        if (id.equals("RUNE") || id.equals("UNIQUE_RUNE")) {
            // Runes store runes: { type: level } in CUSTOM_DATA
            NbtElement runes = c.get("runes");
            Optional<NbtCompound> opt = runes == null ? Optional.empty() : runes.asCompound();
            if (opt.isPresent()) {
                NbtCompound rc = opt.get();
                for (String k : rc.getKeys()) {
                    int lvl = rc.getInt(k, 0);
                    if (lvl > 0) return "RUNE_" + k.toUpperCase() + "_" + lvl;
                }
            }
        }
        return id;
    }

    /** Pretty display name including the enchant level for books. */
    public static String displayName(ItemStack stack) {
        String base = stack.getName().getString().replaceAll("§.", "").trim();
        NbtComponent nbt = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (nbt == null) return base;
        NbtCompound c = nbt.copyNbt();
        String id = c.getString("id", null);
        if ("ENCHANTED_BOOK".equals(id)) {
            String[] best = firstEnchant(c);
            if (best != null) return "Enchanted Book (" + pretty(best[0]) + " " + roman(Integer.parseInt(best[1])) + ")";
        }
        return base;
    }

    /** Derives a pricing id purely from the display name for items with no CUSTOM_DATA id. */
    private static String nameBasedId(ItemStack stack) {
        String name = stack.getName().getString().replaceAll("§.", "").trim();
        // Strip trailing "x<N>" essence count before matching.
        name = name.replaceAll("(?i)\\s*x\\s*\\d+\\s*$", "").trim();
        // Common patterns: "<Type> Essence" → ESSENCE_<TYPE>
        if (name.endsWith(" Essence")) {
            String type = name.replace(" Essence", "").trim().toUpperCase().replace(" ", "_");
            return "ESSENCE_" + type;
        }
        return null;
    }

    private static String[] firstEnchant(NbtCompound c) {
        NbtElement encEl = c.get("enchantments");
        if (encEl == null) return null;
        Optional<NbtCompound> opt = encEl.asCompound();
        if (opt.isEmpty()) return null;
        NbtCompound enchants = opt.get();
        // Prefer ultimate_* / highest-level enchant — usually the named drop.
        String bestName = null;
        int bestLvl = 0;
        boolean bestUlt = false;
        for (String k : enchants.getKeys()) {
            int lvl = enchants.getInt(k, 0);
            if (lvl <= 0) continue;
            boolean ult = k.startsWith("ultimate_");
            if (ult && !bestUlt) { bestName = k; bestLvl = lvl; bestUlt = true; continue; }
            if (ult == bestUlt && lvl > bestLvl) { bestName = k; bestLvl = lvl; }
        }
        if (bestName == null) return null;
        return new String[]{ bestName, String.valueOf(bestLvl) };
    }

    private static String pretty(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
