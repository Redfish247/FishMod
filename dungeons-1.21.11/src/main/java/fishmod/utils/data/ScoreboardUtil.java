package fishmod.utils.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class ScoreboardUtil {

    public static String getCurrentClass() {
        ClassInfo info = getClassInfo();
        return info != null ? info.className : null;
    }

    public static int getCurrentClassLevel() {
        ClassInfo info = getClassInfo();
        return info != null ? info.level : 0;
    }

    private static ClassInfo getClassInfo() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null)
            return null;

        String myName = mc.player.getName().getString();

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            String profileName = entry.getProfile().name();
            if (!profileName.equals(myName)) continue;

            String display = entry.getDisplayName() != null
                    ? entry.getDisplayName().getString()
                    : profileName;

            // Look for "(Mage XLIX)"
            int start = display.indexOf("(");
            int end = display.indexOf(")");
            if (start == -1 || end == -1) return null;

            String inside = display.substring(start + 1, end).trim();
            // inside = "Mage XLIX"

            String[] parts = inside.split(" ");
            if (parts.length != 2) return null;

            String className = parts[0];
            String roman = parts[1];

            int level = romanToInt(roman);

            return new ClassInfo(className, level);
        }

        return null;
    }

    private static int romanToInt(String roman) {
        int sum = 0;
        int prev = 0;

        for (int i = roman.length() - 1; i >= 0; i--) {
            int val = romanValue(roman.charAt(i));
            if (val < prev) sum -= val;
            else sum += val;
            prev = val;
        }

        return sum;
    }

    private static int romanValue(char c) {
        return switch (c) {
            case 'I' -> 1;
            case 'V' -> 5;
            case 'X' -> 10;
            case 'L' -> 50;
            case 'C' -> 100;
            case 'D' -> 500;
            case 'M' -> 1000;
            default -> 0;
        };
    }

    private record ClassInfo(String className, int level) {}
}
