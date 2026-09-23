package dev.podium.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/** Resolves PlaceholderAPI placeholders. Returns the text unchanged when PAPI isn't installed. */
public final class Placeholders {

    private static final boolean PRESENT = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    private Placeholders() {
    }

    public static boolean present() {
        return PRESENT;
    }

    public static String resolve(OfflinePlayer player, String text) {
        if (!PRESENT) return text;
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
    }
}
