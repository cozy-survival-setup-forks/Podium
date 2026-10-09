package dev.podium.config;

import dev.podium.board.Board;
import dev.podium.board.Period;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Loads config.yml, leaderboards.yml and stats-menu.yml. Call {@link #reload()} to re-read them. */
public final class Settings {

    /** A single named GUI item (icon/name/lore), the smallest reusable config shape here. */
    public record MenuItem(int slot, Material material, String name, List<String> lore) {
    }

    private static final int MAX_SLOT = 53;

    private final JavaPlugin plugin;

    private YamlConfiguration config;
    private YamlConfiguration leaderboards;
    private YamlConfiguration statsMenu;
    private YamlConfiguration messages;

    private final Map<String, Board> boards = new LinkedHashMap<>();
    private final List<Period> periods = new ArrayList<>();

    private boolean loaded;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        loaded = reload();
    }

    /** False when the first read failed, the plugin has nothing to run on. */
    public boolean loaded() {
        return loaded;
    }

    /**
     * Reads the four files. If one has a mistake in it, nothing is replaced: what was loaded before stays in use and
     * the console says which file and why.
     *
     * @return false if a file could not be read
     */
    public boolean reload() {
        plugin.saveDefaultConfig();
        saveDefault("leaderboards.yml");
        saveDefault("stats-menu.yml");
        saveDefault("messages.yml");

        YamlConfiguration newConfig, newBoards, newStats, newMessages;
        try {
            newConfig = strict("config.yml");
            newBoards = strict("leaderboards.yml");
            newStats = strict("stats-menu.yml");
            newMessages = strict("messages.yml");
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            plugin.getLogger().severe(e.getMessage() + " Fix the file, or delete it to get the default one.");
            return false;
        }
        // Texts added in newer versions still work with an older messages.yml
        try (var defaults = plugin.getResource("messages.yml")) {
            if (defaults != null) {
                newMessages.setDefaults(YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(defaults, java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not read the default messages.yml: " + e.getMessage());
        }

        config = newConfig;
        leaderboards = newBoards;
        statsMenu = newStats;
        messages = newMessages;

        loadPeriods();
        loadBoards();
        return true;
    }

    /** Bukkit's own loader only logs a broken file and carries on with an empty one. */
    private YamlConfiguration strict(String name) throws java.io.IOException, org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        File file = new File(plugin.getDataFolder(), name);
        try {
            yaml.load(file);
        } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new org.bukkit.configuration.InvalidConfigurationException(name + " could not be read: " + e.getMessage());
        }
        return yaml;
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
    }

    private void loadPeriods() {
        periods.clear();
        for (Map<?, ?> entry : leaderboards.getMapList("periods")) {
            if (entry.get("id") == null) {
                plugin.getLogger().warning("leaderboards.yml: a period without an id was skipped.");
                continue;
            }
            String id = String.valueOf(entry.get("id"));
            String label = entry.get("label") == null ? id : String.valueOf(entry.get("label"));
            periods.add(new Period(id, label));
        }
    }

    private void loadBoards() {
        boards.clear();
        ConfigurationSection section = leaderboards.getConfigurationSection("boards");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            ConfigurationSection board = section.getConfigurationSection(id);
            if (board == null) continue;

            Map<String, Board.PlaceholderPair> periodMap = new LinkedHashMap<>();
            ConfigurationSection periodsSection = board.getConfigurationSection("periods");
            if (periodsSection != null) {
                for (String periodId : periodsSection.getKeys(false)) {
                    String path = "periods." + periodId + ".";
                    periodMap.put(periodId, new Board.PlaceholderPair(
                            board.getString(path + "name", ""),
                            board.getString(path + "value", "")));
                }
            }

            boards.put(id, new Board(
                    id,
                    matchMaterial(board.getString("icon", "STONE")),
                    board.getString("name", id),
                    board.getString("category", ""),
                    board.getStringList("description"),
                    periodMap));
        }
    }

    public int refreshIntervalTicks() {
        return config.getInt("refresh-interval-ticks", 100);
    }

    public List<Period> periods() {
        return periods;
    }

    public Map<String, Board> boards() {
        return boards;
    }

    public YamlConfiguration leaderboards() {
        return leaderboards;
    }

    public YamlConfiguration statsMenu() {
        return statsMenu;
    }

    public String message(String key) {
        String text = messages.getString(key);
        return text == null ? "" : text;
    }

    public String prefixed(String key) {
        return message("prefix") + message(key);
    }

    public MenuItem item(YamlConfiguration source, String path) {
        return new MenuItem(
                source.getInt(path + ".slot", 0),
                matchMaterial(source.getString(path + ".material", "STONE")),
                source.getString(path + ".name", ""),
                source.getStringList(path + ".lore"));
    }

    /** {@link Material#matchMaterial} but falls back to STONE for a typo'd or missing name, instead of null. */
    public static Material matchMaterial(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null ? material : Material.STONE;
    }

    /**
     * Parses "[0-6, 9-16, 25]" style slot lists into an ordered list of slots. Anything that is not a slot of an
     * inventory (0 to 53) or not a number is left out, so a typo cannot break a menu.
     */
    public static List<Integer> parseSlots(String raw) {
        List<Integer> slots = new ArrayList<>();
        if (raw == null) return slots;
        String cleaned = raw.replaceAll("[\\[\\]]", "");
        for (String part : cleaned.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            try {
                if (token.contains("-")) {
                    String[] bounds = token.split("-", 2);
                    int from = Integer.parseInt(bounds[0].trim());
                    int to = Integer.parseInt(bounds[1].trim());
                    for (int slot = Math.max(0, from); slot <= Math.min(MAX_SLOT, to); slot++) slots.add(slot);
                } else {
                    int slot = Integer.parseInt(token);
                    if (slot >= 0 && slot <= MAX_SLOT) slots.add(slot);
                }
            } catch (NumberFormatException e) {
                // not a slot, skipped
            }
        }
        return slots;
    }
}
