package dev.podium.config;

import dev.podium.board.Board;
import dev.podium.board.Period;
import dev.podium.board.Topper;
import dev.podium.safe.ConfigMigrator;
import dev.podium.safe.Guard;
import dev.podium.safe.Prep;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads config.yml, leaderboards.yml and stats-menu.yml. Call {@link #reload()} to re-read them. */
public final class Settings {

    /** A single named GUI item (icon/name/lore), the smallest reusable config shape here. */
    public record MenuItem(int slot, Material material, String name, List<String> lore) {
    }

    private static final int MAX_SLOT = 53;

    public static final int CONFIG_VERSION = 1;
    public static final int LANG_VERSION = 1;

    /**
     * The files that get new settings added when the plugin is updated. leaderboards.yml and stats-menu.yml are
     * lists of boards and menu items that the owner builds, so nothing is added to them.
     */
    public static final List<Prep.Spec> FILES = List.of(
            new Prep.Spec("config.yml", "config-version", CONFIG_VERSION, Prep.configMigrator(CONFIG_VERSION), rules ->
                    rules.range("refresh-interval-ticks", 1, 72000)),
            new Prep.Spec("display.yml", "config-version", CONFIG_VERSION, new ConfigMigrator("config-version", CONFIG_VERSION), rules -> {
                rules.range("refresh-seconds", 1, 600);
                rules.range("view-range", 4, 128);
                rules.range("rows", 1, 15);
                rules.range("idle-reset-seconds", 0, 86400);
            }),
            new Prep.Spec("messages.yml", "lang-version", LANG_VERSION, new ConfigMigrator("lang-version", LANG_VERSION), null));

    private final JavaPlugin plugin;

    private YamlConfiguration config;
    private YamlConfiguration leaderboards;
    private YamlConfiguration statsMenu;
    private YamlConfiguration messages;
    private YamlConfiguration display;
    private Set<String> topperHolders = Set.of();

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
        if (loaded) {
            // a real reload (not the first start): a value with a mistake cancels it and what is in use stays
            List<Guard.Problem> problems = Prep.validate(plugin, FILES);
            if (!problems.isEmpty()) {
                Prep.logRejected(plugin, problems);
                return false;
            }
        }
        plugin.saveDefaultConfig();
        saveDefault("leaderboards.yml");
        saveDefault("stats-menu.yml");
        saveDefault("messages.yml");
        saveDefault("display.yml");

        YamlConfiguration newConfig, newBoards, newStats, newMessages, newDisplay;
        try {
            newConfig = strict("config.yml");
            newBoards = strict("leaderboards.yml");
            newStats = strict("stats-menu.yml");
            newMessages = strict("messages.yml");
            newDisplay = strict("display.yml");
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
        display = newDisplay;

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
        topperHolders = Topper.holders(plugin.getDataFolder().getParentFile());
        ConfigurationSection section = leaderboards.getConfigurationSection("boards");
        Set<String> used = new HashSet<>();

        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection board = section.getConfigurationSection(id);
                if (board == null) continue;

                Map<String, Board.PlaceholderPair> periodMap = new LinkedHashMap<>();
                String format = board.getString("value-format");

                // topper: money, or topper: {all_time: money, daily: money_daily}
                Object topper = board.get("topper");
                if (topper instanceof String holder && !holder.isBlank()) {
                    for (Map.Entry<String, String> e : Topper.periodHolders(holder.trim(), topperHolders).entrySet()) {
                        periodMap.put(e.getKey(), Topper.pair(e.getValue(), format));
                        used.add(e.getValue().toLowerCase(Locale.ROOT));
                    }
                    checkHolder(id, holder.trim());
                } else if (topper instanceof ConfigurationSection map) {
                    for (String period : map.getKeys(false)) {
                        String holder = map.getString(period, "");
                        if (holder.isBlank()) continue;
                        periodMap.put(period, Topper.pair(holder.trim(), format));
                        used.add(holder.trim().toLowerCase(Locale.ROOT));
                        checkHolder(id, holder.trim());
                    }
                }

                // placeholders written out by hand, for anything that is not Topper
                ConfigurationSection periodsSection = board.getConfigurationSection("periods");
                if (periodsSection != null) {
                    for (String periodId : periodsSection.getKeys(false)) {
                        String path = "periods." + periodId + ".";
                        periodMap.put(periodId, new Board.PlaceholderPair(
                                board.getString(path + "name", ""),
                                board.getString(path + "value", "")));
                    }
                }
                if (periodMap.isEmpty()) {
                    plugin.getLogger().warning("leaderboards.yml: board '" + id + "' has no 'topper' and no 'periods', so it shows nothing.");
                }

                String name = board.getString("name", id);
                String category = board.getString("category", "");
                boards.put(id, new Board(
                        id,
                        matchMaterial(board.getString("icon", "STONE")),
                        name,
                        category,
                        board.getStringList("description"),
                        periodMap,
                        new Board.Look(
                                board.getString("display.title", name),
                                board.getString("display.value-header", "Value"),
                                board.getString("display.value-suffix", ""),
                                board.getString("display.tab", category.isEmpty() ? id : category))));
            }
        }

        // Every holder Topper has that no board uses yet, when asked for
        if (config.getBoolean("topper.auto-boards", false)) {
            for (String holder : topperHolders) {
                String key = holder.toLowerCase(Locale.ROOT);
                if (used.contains(key) || Topper.isWindowOf(holder, topperHolders) || boards.containsKey(key)) continue;
                boards.put(key, topperBoard(holder));
            }
        }
    }

    private void checkHolder(String board, String holder) {
        if (topperHolders.isEmpty()) return;
        for (String known : topperHolders) if (known.equalsIgnoreCase(holder)) return;
        plugin.getLogger().warning("leaderboards.yml: board '" + board + "' uses the Topper holder '" + holder
                + "', which is not in Topper's config.yml (it has: " + String.join(", ", topperHolders) + ").");
    }

    /** A board for a Topper holder that has no entry in leaderboards.yml. */
    private Board topperBoard(String holder) {
        Map<String, Board.PlaceholderPair> periodMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : Topper.periodHolders(holder, topperHolders).entrySet()) {
            periodMap.put(e.getKey(), Topper.pair(e.getValue(), null));
        }
        String title = Topper.title(holder);
        return new Board(holder.toLowerCase(Locale.ROOT), Material.PAPER, "&f" + title, "Topper",
                List.of("Ranks players by " + title.toLowerCase(Locale.ROOT) + "."), periodMap,
                new Board.Look(title, "Value", "", title));
    }

    /**
     * A board by its id, or a Topper holder written as {@code topper:<holder>} even if leaderboards.yml does not have
     * it, so a board in the world needs no configuration.
     */
    public Board board(String id) {
        Board board = boards.get(id.toLowerCase(Locale.ROOT));
        if (board == null) board = boards.get(id);
        if (board != null) return board;
        if (id.regionMatches(true, 0, "topper:", 0, 7) && id.length() > 7) {
            String holder = id.substring(7);
            for (String known : topperHolders) if (known.equalsIgnoreCase(holder)) return topperBoard(known);
            if (topperHolders.isEmpty() && holder.matches("[A-Za-z0-9_\\-]+")) return topperBoard(holder);
        }
        return null;
    }

    public Set<String> topperHolders() {
        return topperHolders;
    }

    public YamlConfiguration display() {
        return display;
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
