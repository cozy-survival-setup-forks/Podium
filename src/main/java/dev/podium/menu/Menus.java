package dev.podium.menu;

import dev.podium.board.Board;
import dev.podium.board.Period;
import dev.podium.config.Settings;
import dev.podium.util.ItemBuilder;
import dev.podium.util.Placeholders;
import dev.podium.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Renders and handles clicks for the leaderboards dashboard, a single board's ranking list, and the stats menu. */
public final class Menus implements Listener {

    private static final class DashboardHolder implements InventoryHolder {
        Inventory inventory;
        int page;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class BoardHolder implements InventoryHolder {
        Inventory inventory;
        final Board board;
        int page;

        BoardHolder(Board board) {
            this.board = board;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class StatsHolder implements InventoryHolder {
        Inventory inventory;
        final org.bukkit.OfflinePlayer target;

        StatsHolder(org.bukkit.OfflinePlayer target) {
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;
    private final Settings settings;
    private final NamespacedKey entryPlayerKey;
    private final Map<UUID, String> selectedPeriod = new HashMap<>();

    public Menus(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.entryPlayerKey = new NamespacedKey(plugin, "entry-player");
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenMenus, 20L, Math.max(20, settings.refreshIntervalTicks()));
    }

    private String periodFor(Player player) {
        return selectedPeriod.getOrDefault(player.getUniqueId(), "all_time");
    }

    private String periodLabel(String id) {
        for (Period period : settings.periods()) {
            if (period.id().equals(id)) return period.label();
        }
        return id;
    }

    // ---------------------------------------------------------------- dashboard

    public void openDashboard(Player player) {
        openDashboard(player, 0);
    }

    private void openDashboard(Player player, int page) {
        YamlConfiguration lb = settings.leaderboards();
        int rows = lb.getInt("dashboard.rows", 4);
        String title = TextUtil.fill(lb.getString("dashboard.title", "Leaderboards - <period>"),
                Map.of("<period>", periodLabel(periodFor(player))));

        DashboardHolder holder = new DashboardHolder();
        holder.page = page;
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, TextUtil.item(title));
        holder.inventory = inventory;

        fillFiller(inventory, lb, "dashboard.filler");

        List<Integer> slots = Settings.parseSlots(lb.getString("dashboard.board-slots"));
        List<Board> boards = new ArrayList<>(settings.boards().values());
        int start = page * slots.size();

        for (int i = 0; i < slots.size() && start + i < boards.size(); i++) {
            Board board = boards.get(start + i);
            List<String> lore = new ArrayList<>();
            lore.add(board.category());
            lore.add("");
            lore.addAll(board.description());
            lore.add("");
            lore.add("Click to Browse");
            inventory.setItem(slots.get(i), new ItemBuilder(board.icon()).name(board.name()).lore(lore).build());
        }

        placeFilter(inventory, player, lb);

        if (page > 0) placeNav(inventory, lb, "dashboard.previous-page");
        if (start + slots.size() < boards.size()) placeNav(inventory, lb, "dashboard.next-page");

        player.openInventory(inventory);
    }

    private void placeFilter(Inventory inventory, Player player, YamlConfiguration lb) {
        int slot = lb.getInt("dashboard.filter.slot");
        if (slot < 0 || slot >= inventory.getSize()) return;

        String selected = periodFor(player);
        List<String> lore = new ArrayList<>();
        for (String line : lb.getStringList("dashboard.filter.lore")) {
            if (line.equals("<period-list>")) {
                for (Period period : settings.periods()) {
                    String key = period.id().equals(selected) ? "dashboard.filter.period-selected" : "dashboard.filter.period-unselected";
                    lore.add(TextUtil.fill(lb.getString(key), Map.of("<period>", period.label())));
                }
            } else {
                lore.add(line);
            }
        }

        Material material = Material.matchMaterial(lb.getString("dashboard.filter.material", "HOPPER"));
        inventory.setItem(slot, new ItemBuilder(material).name(lb.getString("dashboard.filter.name")).lore(lore).build());
    }

    private void cycleFilter(Player player) {
        List<Period> periods = settings.periods();
        if (periods.isEmpty()) return;
        String current = periodFor(player);
        int index = 0;
        for (int i = 0; i < periods.size(); i++) {
            if (periods.get(i).id().equals(current)) {
                index = (i + 1) % periods.size();
                break;
            }
        }
        selectedPeriod.put(player.getUniqueId(), periods.get(index).id());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        openDashboard(player, 0);
    }

    // ---------------------------------------------------------------- board list

    public void openBoard(Player player, Board board) {
        openBoard(player, board, 0);
    }

    private void openBoard(Player player, Board board, int page) {
        YamlConfiguration lb = settings.leaderboards();
        int rows = lb.getInt("board-template.rows", 6);
        String title = TextUtil.fill(lb.getString("board-template.title", "<board> - <period>"),
                Map.of("<board>", board.name(), "<period>", periodLabel(periodFor(player))));

        BoardHolder holder = new BoardHolder(board);
        holder.page = page;
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, TextUtil.item(title));
        holder.inventory = inventory;

        fillFiller(inventory, lb, "board-template.filler");

        Board.PlaceholderPair pair = board.placeholdersFor(periodFor(player));
        List<Integer> slots = Settings.parseSlots(lb.getString("board-template.entry-slots"));
        List<String> loreTemplate = lb.getStringList("board-template.entry-lore");
        String nameTemplate = lb.getString("board-template.entry-name");

        int startPosition = page * slots.size() + 1;
        int filled = 0;
        if (pair != null) {
            for (int i = 0; i < slots.size(); i++) {
                int position = startPosition + i;
                String namePlaceholder = pair.namePattern().replace("<position>", String.valueOf(position));
                String resolvedName = Placeholders.resolve(player, namePlaceholder);
                if (resolvedName.isBlank() || resolvedName.contains("%")) break;

                String valuePlaceholder = pair.valuePattern().replace("<position>", String.valueOf(position));
                String resolvedValue = Placeholders.resolve(player, valuePlaceholder);

                Map<String, String> tokens = Map.of("<position>", String.valueOf(position),
                        "<player>", resolvedName, "<value>", resolvedValue, "<board>", board.name());

                List<String> lore = new ArrayList<>();
                for (String line : loreTemplate) lore.add(TextUtil.fill(line, tokens));

                inventory.setItem(slots.get(i), new ItemBuilder(Material.PLAYER_HEAD)
                        .name(TextUtil.fill(nameTemplate, tokens)).lore(lore)
                        .tag(entryPlayerKey, resolvedName).build());
                filled++;
            }
        }

        placeNav(inventory, lb, "board-template.back");
        if (page > 0) placeNav(inventory, lb, "board-template.previous-page");
        if (filled == slots.size()) placeNav(inventory, lb, "board-template.next-page");

        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- stats

    public void openStats(Player viewer, org.bukkit.OfflinePlayer target) {
        YamlConfiguration stats = settings.statsMenu();
        int rows = stats.getInt("rows", 5);
        String title = TextUtil.fill(stats.getString("title", "<player>'s Stats"),
                Map.of("<player>", String.valueOf(target.getName())));

        StatsHolder holder = new StatsHolder(target);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, TextUtil.item(title));
        holder.inventory = inventory;
        renderStats(inventory, stats, target);
        viewer.openInventory(inventory);
    }

    private void renderStats(Inventory inventory, YamlConfiguration stats, org.bukkit.OfflinePlayer target) {
        fillFiller(inventory, stats, "filler");

        Settings.MenuItem profile = settings.item(stats, "profile");
        inventory.setItem(profile.slot(), new ItemBuilder(profile.material())
                .name(resolveEach(profile.name(), target))
                .lore(resolveEach(profile.lore(), target))
                .build());

        var section = stats.getConfigurationSection("stats");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Settings.MenuItem item = settings.item(stats, "stats." + key);
            inventory.setItem(item.slot(), new ItemBuilder(item.material())
                    .name(resolveEach(item.name(), target))
                    .lore(resolveEach(item.lore(), target))
                    .build());
        }
    }

    private String resolveEach(String text, org.bukkit.OfflinePlayer target) {
        String withName = text.replace("<player>", String.valueOf(target.getName()));
        return Placeholders.resolve(target, withName);
    }

    private List<String> resolveEach(List<String> lines, org.bukkit.OfflinePlayer target) {
        List<String> out = new ArrayList<>();
        for (String line : lines) out.add(resolveEach(line, target));
        return out;
    }

    // ---------------------------------------------------------------- shared rendering

    private void fillFiller(Inventory inventory, YamlConfiguration source, String path) {
        Material material = Material.matchMaterial(source.getString(path + ".material", "BLACK_STAINED_GLASS_PANE"));
        Component name = TextUtil.item(source.getString(path + ".name", " "));
        ItemStack filler = new ItemStack(material == null ? Material.BLACK_STAINED_GLASS_PANE : material);
        var meta = filler.getItemMeta();
        meta.displayName(name);
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private void placeNav(Inventory inventory, YamlConfiguration source, String path) {
        Settings.MenuItem item = settings.item(source, path);
        inventory.setItem(item.slot(), new ItemBuilder(item.material()).name(item.name()).build());
    }

    private void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof BoardHolder boardHolder) {
                openBoard(player, boardHolder.board, boardHolder.page);
            } else if (holder instanceof StatsHolder statsHolder) {
                renderStats(player.getOpenInventory().getTopInventory(), settings.statsMenu(), statsHolder.target);
            }
        }
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof DashboardHolder) && !(holder instanceof BoardHolder) && !(holder instanceof StatsHolder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        YamlConfiguration lb = settings.leaderboards();

        if (holder instanceof DashboardHolder dashboard) {
            int slot = event.getSlot();
            if (slot == lb.getInt("dashboard.filter.slot")) {
                cycleFilter(player);
            } else if (slot == lb.getInt("dashboard.previous-page.slot")) {
                openDashboard(player, dashboard.page - 1);
            } else if (slot == lb.getInt("dashboard.next-page.slot")) {
                openDashboard(player, dashboard.page + 1);
            } else {
                List<Integer> slots = Settings.parseSlots(lb.getString("dashboard.board-slots"));
                int index = slots.indexOf(slot);
                if (index < 0) return;
                List<Board> boards = new ArrayList<>(settings.boards().values());
                int boardIndex = dashboard.page * slots.size() + index;
                if (boardIndex < boards.size()) openBoard(player, boards.get(boardIndex));
            }
        } else if (holder instanceof BoardHolder board) {
            int slot = event.getSlot();
            if (slot == lb.getInt("board-template.back.slot")) {
                openDashboard(player, 0);
            } else if (slot == lb.getInt("board-template.previous-page.slot")) {
                openBoard(player, board.board, board.page - 1);
            } else if (slot == lb.getInt("board-template.next-page.slot")) {
                openBoard(player, board.board, board.page + 1);
            } else {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getItemMeta() == null) return;
                String playerName = clicked.getItemMeta().getPersistentDataContainer()
                        .get(entryPlayerKey, org.bukkit.persistence.PersistentDataType.STRING);
                if (playerName == null) return;
                for (String action : lb.getStringList("board-template.entry-actions")) {
                    runAction(player, action.replace("<player>", playerName));
                }
            }
        }
    }

    private void runAction(Player player, String action) {
        String trimmed = action.trim();
        if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("[command]")) {
            String command = trimmed.substring("[command]".length()).trim();
            player.performCommand(command);
        }
    }
}
