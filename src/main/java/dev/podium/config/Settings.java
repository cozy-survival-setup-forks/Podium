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

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        saveDefault("leaderboards.yml");
        saveDefault("stats-menu.yml");
        saveDefault("messages.yml");

        config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        leaderboards = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "leaderboards.yml"));
        statsMenu = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "stats-menu.yml"));
        messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));

        loadPeriods();
        loadBoards();
    }

    private void saveDefault(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
    }

    private void loadPeriods() {
        periods.clear();
        for (Map<?, ?> entry : leaderboards.getMapList("periods")) {
            String id = String.valueOf(entry.get("id"));
            String label = String.valueOf(entry.get("label"));
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
        return messages.getString(key, "");
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
