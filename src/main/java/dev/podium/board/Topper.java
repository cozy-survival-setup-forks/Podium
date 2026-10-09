package dev.podium.board;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The link to the Topper plugin. Topper shows its leaderboards through PlaceholderAPI as
 * {@code %topper_<holder>;top_name;<position>%}, so a board only needs the name of a holder. The holders themselves
 * are read from Topper's own config.yml, to check the names and to offer them in commands.
 */
public final class Topper {

    /** The time windows Podium knows, and the ending a holder has for it (TimedTopper's way of naming them). */
    private static final Map<String, String> WINDOWS = Map.of("daily", "_daily", "weekly", "_weekly", "monthly", "_monthly");

    private Topper() {
    }

    /** The placeholders of a holder. {@code format} is a Topper number format such as "#,###", or null for Topper's own. */
    public static Board.PlaceholderPair pair(String holder, String format) {
        String tail = format == null || format.isBlank() ? "" : ";" + format.trim();
        return new Board.PlaceholderPair("%topper_" + holder + ";top_name;<position>%",
                "%topper_" + holder + ";top_value;<position>" + tail + "%");
    }

    /**
     * Which holder fills which period for a board built on {@code base}: the holder itself is "all_time", and
     * {@code base_daily}, {@code base_weekly} and {@code base_monthly} are used when Topper has them.
     */
    public static Map<String, String> periodHolders(String base, Set<String> holders) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("all_time", base);
        for (Map.Entry<String, String> window : WINDOWS.entrySet()) {
            String candidate = base + window.getValue();
            for (String holder : holders) {
                if (holder.equalsIgnoreCase(candidate)) result.put(window.getKey(), holder);
            }
        }
        return result;
    }

    /** True for a holder that only exists to feed a time window of another one, like money_daily next to money. */
    public static boolean isWindowOf(String holder, Set<String> holders) {
        String lower = holder.toLowerCase(Locale.ROOT);
        for (String ending : WINDOWS.values()) {
            if (!lower.endsWith(ending)) continue;
            String base = lower.substring(0, lower.length() - ending.length());
            for (String other : holders) if (other.equalsIgnoreCase(base)) return true;
        }
        return false;
    }

    /** The holders Topper has, in its order. Empty when Topper is not installed or has none. */
    public static Set<String> holders(File pluginsFolder) {
        File file = new File(new File(pluginsFolder, "Topper"), "config.yml");
        Set<String> names = new LinkedHashSet<>();
        if (!file.isFile()) return names;
        var section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("holders");
        if (section != null) names.addAll(section.getKeys(false));
        return names;
    }

    /** "mob_kills" becomes "Mob Kills". */
    public static String title(String holder) {
        StringBuilder out = new StringBuilder();
        for (String word : holder.split("[_\\-\\s]+")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
