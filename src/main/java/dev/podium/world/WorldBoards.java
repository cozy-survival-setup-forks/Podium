package dev.podium.world;

import dev.podium.config.Settings;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keeps the boards that stand in the world: where they are (displays.yml), builds them while a player is near,
 * refreshes them, and turns a click on a tab into a change of board or period.
 */
public final class WorldBoards implements Listener {

    public static final List<String> DEFAULT_PERIODS = List.of("daily", "monthly", "all_time");
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final long CLICK_GAP_MILLIS = 300;

    private final JavaPlugin plugin;
    private final Settings settings;
    private final File file;
    private final Map<String, WorldBoard> boards = new LinkedHashMap<>();
    private final Map<UUID, Long> clicks = new HashMap<>();
    private Style style;
    private BukkitTask task;
    /** Set when displays.yml could not be read: nothing is written over it then. */
    private boolean broken;

    public WorldBoards(JavaPlugin plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.file = new File(plugin.getDataFolder(), "displays.yml");
        this.style = new Style(settings.display());
    }

    // ---- life ----

    public void start() {
        stop();
        style = new Style(settings.display());
        load();
        for (WorldBoard board : boards.values()) board.setStyle(style);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (WorldBoard board : boards.values()) board.despawn();
        boards.clear();
    }

    /** Re-reads display.yml and displays.yml. */
    public void reload() {
        start();
    }

    private void load() {
        broken = false;
        if (!file.isFile()) return;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            broken = true;
            plugin.getLogger().severe("displays.yml could not be read, so no world board is shown and nothing will be saved over it: "
                    + e.getMessage() + " Fix the file, or delete it, then /podium reload.");
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("displays");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection each = section.getConfigurationSection(id);
            if (each == null || !ID.matcher(id).matches()) continue;
            String world = each.getString("world", "");
            List<String> boardIds = each.getStringList("boards");
            if (world.isEmpty() || boardIds.isEmpty()) {
                plugin.getLogger().warning("displays.yml: '" + id + "' has no world or no boards, skipped.");
                continue;
            }
            List<String> periods = each.isList("periods") ? each.getStringList("periods") : DEFAULT_PERIODS;
            Placement placement = new Placement(world, each.getDouble("x"), each.getDouble("y"), each.getDouble("z"),
                    each.getDouble("yaw"), Math.max(0.1, Math.min(10, each.getDouble("scale", 1.0))));
            for (String boardId : boardIds) {
                if (settings.board(boardId) == null) {
                    plugin.getLogger().warning("displays.yml: '" + id + "' uses the board '" + boardId + "', which does not exist (yet).");
                }
            }
            boards.put(id, new WorldBoard(plugin, settings, style, id, placement, boardIds, periods));
        }
    }

    /** Writes displays.yml through a temporary file, so a crash cannot leave half a file. */
    private boolean save() {
        if (broken) return false;
        YamlConfiguration yaml = new YamlConfiguration();
        for (WorldBoard board : boards.values()) {
            String path = "displays." + board.id + ".";
            Placement p = board.placement();
            yaml.set(path + "world", p.world());
            yaml.set(path + "x", p.x());
            yaml.set(path + "y", p.y());
            yaml.set(path + "z", p.z());
            yaml.set(path + "yaw", p.yaw());
            yaml.set(path + "scale", p.scale());
            yaml.set(path + "boards", board.boardIds());
            yaml.set(path + "periods", board.periodIds());
        }
        File temp = new File(file.getParentFile(), "displays.yml.tmp");
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(temp);
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save displays.yml: " + e.getMessage());
            return false;
        }
    }

    // ---- what the commands use ----

    public boolean broken() {
        return broken;
    }

    /** The yaw a board needs to face out of a wall whose side looks along (nx, nz). */
    public static double yawFacing(int nx, int nz) {
        return Layout.yawFacing(nx, nz);
    }

    public static boolean validId(String id) {
        return ID.matcher(id).matches();
    }

    public List<String> ids() {
        return new ArrayList<>(boards.keySet());
    }

    public boolean has(String id) {
        return boards.containsKey(id);
    }

    /** @return null when it worked, else the key of the message to show */
    public String create(String id, Location at, double yaw, double scale, List<String> boardIds, List<String> periods) {
        if (broken) return "display-storage-broken";
        if (!ID.matcher(id).matches()) return "display-bad-id";
        if (boards.containsKey(id)) return "display-exists";
        Placement placement = new Placement(at.getWorld().getName(), at.getX(), at.getY(), at.getZ(), Layout.wrap(yaw), scale);
        boards.put(id, new WorldBoard(plugin, settings, style, id, placement, boardIds, periods.isEmpty() ? DEFAULT_PERIODS : periods));
        save();
        return null;
    }

    public String remove(String id) {
        if (broken) return "display-storage-broken";
        WorldBoard board = boards.remove(id);
        if (board == null) return "display-unknown";
        board.despawn();
        save();
        return null;
    }

    /** Changes where, how big or which way a board is, and puts it up again. */
    public String place(String id, Placement placement) {
        if (broken) return "display-storage-broken";
        WorldBoard board = boards.get(id);
        if (board == null) return "display-unknown";
        board.despawn();
        board.setPlacement(new Placement(placement.world(), placement.x(), placement.y(), placement.z(), Layout.wrap(placement.yaw()),
                Math.max(0.1, Math.min(10, placement.scale()))));
        save();
        return null;
    }

    public Placement placement(String id) {
        WorldBoard board = boards.get(id);
        return board == null ? null : board.placement();
    }

    public List<String> boardIdsOf(String id) {
        WorldBoard board = boards.get(id);
        return board == null ? List.of() : board.boardIds();
    }

    public String stateOf(String id) {
        WorldBoard board = boards.get(id);
        return board == null ? "" : board.currentBoard() + "/" + board.currentPeriod();
    }

    public Location locationOf(String id) {
        WorldBoard board = boards.get(id);
        return board == null ? null : board.where();
    }

    public boolean shown(String id) {
        WorldBoard board = boards.get(id);
        return board != null && board.spawned();
    }

    /** Selects a board or a period by id, for commands and scripts. @return null when it worked */
    public String select(String id, String what, Player by) {
        WorldBoard board = boards.get(id);
        if (board == null) return "display-unknown";
        if (!board.spawned()) return "display-not-shown";
        boolean done;
        if (board.boardIds().contains(what)) done = board.select(WorldBoard.Kind.BOARD, what, by);
        else done = board.select(WorldBoard.Kind.PERIOD, what, by);
        return done ? null : "display-nothing-to-select";
    }

    /** Reads the placeholders of a board again right now. */
    public void refresh(String id) {
        WorldBoard board = boards.get(id);
        if (board != null && board.spawned()) board.render(board.nearest(), true);
    }

    // ---- the clock ----

    private boolean near(Location at, double range) {
        World world = at.getWorld();
        if (world == null) return false;
        double square = range * range;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(at) < square) return true;
        }
        return false;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (WorldBoard board : boards.values()) {
            try {
                tick(board, now);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("The display '" + board.id + "' failed and will be rebuilt: " + e);
                board.despawn();
            }
        }
    }

    private void tick(WorldBoard board, long now) {
        Location at = board.where();
        if (at == null) return;

        if (board.spawned() && !board.intact()) board.despawn();

        // Built when a player comes near, taken down when they are all far away, so empty places cost nothing
        if (board.spawned()) {
            if (!near(at, style.viewRange + 64)) {
                board.despawn();
                return;
            }
        } else if (near(at, style.viewRange + 16) && board.chunksLoaded(at.getWorld())) {
            Player nearest = nearestTo(at);
            board.spawn(nearest);
        }
        if (!board.spawned()) return;

        board.resetIfIdle(now, board.nearest());
        if (board.due(now)) {
            Player nearest = board.nearest();
            if (nearest != null) board.render(nearest, false);
        }
    }

    private Player nearestTo(Location at) {
        Player best = null;
        double distance = Double.MAX_VALUE;
        for (Player player : at.getWorld().getPlayers()) {
            double d = player.getLocation().distanceSquared(at);
            if (d < distance) {
                best = player;
                distance = d;
            }
        }
        return best;
    }

    // ---- clicks ----

    private boolean handleClick(Player player, Entity entity) {
        if (!(entity instanceof Interaction)) return false;
        WorldBoard owner = null;
        for (WorldBoard board : boards.values()) {
            if (board.owns(entity.getUniqueId())) {
                owner = board;
                break;
            }
        }
        if (owner == null) return false;

        if (!player.hasPermission("podium.leaderboards")) {
            player.sendMessage(settings.prefixed("no-permission"));
            return true;
        }
        long now = System.currentTimeMillis();
        Long last = clicks.get(player.getUniqueId());
        if (last != null && now - last < CLICK_GAP_MILLIS) return true; // one click arrives as several events
        clicks.put(player.getUniqueId(), now);

        WorldBoard.Tab tab = owner.tabOf(entity.getUniqueId());
        if (tab != null && owner.select(tab, player) && !"NONE".equalsIgnoreCase(style.clickSound)) {
            player.playSound(player.getLocation(), style.clickSound.toLowerCase(Locale.ROOT), 0.6f, 1.2f);
        }
        return true;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) return;
        if (event.getHand() != EquipmentSlot.HAND) {
            if (isTab(event.getRightClicked())) event.setCancelled(true);
            return;
        }
        if (handleClick(event.getPlayer(), event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (handleClick(event.getPlayer(), event.getAttacked())) event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && handleClick(player, event.getEntity())) event.setCancelled(true);
    }

    private boolean isTab(Entity entity) {
        for (WorldBoard board : boards.values()) if (board.owns(entity.getUniqueId())) return true;
        return false;
    }

    // ---- chunks and worlds ----

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (boards.isEmpty()) return;
        long key = event.getChunk().getChunkKey();
        for (WorldBoard board : boards.values()) {
            if (board.spawned() && board.placement().world().equals(event.getWorld().getName()) && board.chunkKeys().contains(key)) {
                board.despawn();
            }
        }
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (WorldBoard board : boards.values()) {
            if (board.spawned() && board.placement().world().equals(event.getWorld().getName())) board.despawn();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clicks.remove(event.getPlayer().getUniqueId());
    }
}
