package dev.podium.world;

import dev.podium.board.Board;
import dev.podium.board.Period;
import dev.podium.config.Settings;
import dev.podium.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One board standing in the world. It is made of text displays (a panel, a title, and three of them for every row:
 * the rank, the player and the value) and, under the table, a row of tabs: a text display to show it and an
 * interaction entity to click. Everything is spawned without being saved, so a restart or a chunk unload cannot leave
 * anything behind, and it is put back when the chunk loads.
 */
final class WorldBoard {

    enum Kind { BOARD, PERIOD }

    /** One tab: what it switches to and what it shows. */
    record Tab(Kind kind, String id, String label) {
    }

    private static final class TabView {
        final Tab tab;
        TextDisplay text;
        Interaction hit;

        TabView(Tab tab) {
            this.tab = tab;
        }
    }

    private static final double FACE = 0.012; // how far the texts sit in front of the panel, so they do not flicker with it

    private final JavaPlugin plugin;
    private final Settings settings;
    private final NamespacedKey tag;

    final String id;
    private Placement placement;
    private final List<String> boardIds;
    private final List<String> periodIds;
    private Style style;

    private String board;
    private String period;
    private long lastClick;
    private long nextRefresh;
    private int generation;

    private TextDisplay panel;
    private TextDisplay title;
    private final List<TextDisplay[]> lines = new ArrayList<>(); // 0 is the header, then one per row; rank, player, value
    private final List<TabView> tabs = new ArrayList<>();
    private Component lastTitle = Component.empty();
    private final List<Rows.Row> lastRows = new ArrayList<>();
    private Layout.Plan plan;

    WorldBoard(JavaPlugin plugin, Settings settings, Style style, String id, Placement placement, List<String> boardIds, List<String> periodIds) {
        this.plugin = plugin;
        this.settings = settings;
        this.style = style;
        this.tag = new NamespacedKey(plugin, "board");
        this.id = id;
        this.placement = placement;
        this.boardIds = List.copyOf(boardIds);
        this.periodIds = List.copyOf(periodIds);
        selectDefault();
    }

    // ---- what it holds ----

    Placement placement() {
        return placement;
    }

    List<String> boardIds() {
        return boardIds;
    }

    List<String> periodIds() {
        return periodIds;
    }

    String currentBoard() {
        return board;
    }

    String currentPeriod() {
        return period;
    }

    void setPlacement(Placement placement) {
        this.placement = placement;
    }

    void setStyle(Style style) {
        this.style = style;
    }

    /** The periods a board has data for, in the order this board lists them. */
    List<String> periodsOf(String boardId) {
        Board found = settings.board(boardId);
        List<String> result = new ArrayList<>();
        if (found == null) return result;
        for (String wanted : periodIds) if (found.periods().containsKey(wanted)) result.add(wanted);
        if (result.isEmpty() && !found.periods().isEmpty()) result.add(found.periods().keySet().iterator().next());
        return result;
    }

    private String defaultPeriodOf(String boardId) {
        List<String> available = periodsOf(boardId);
        if (available.isEmpty()) return "all_time";
        return available.contains("all_time") ? "all_time" : available.get(0);
    }

    private void selectDefault() {
        board = boardIds.isEmpty() ? "" : boardIds.get(0);
        period = defaultPeriodOf(board);
        lastClick = 0;
    }

    private boolean atDefault() {
        return !boardIds.isEmpty() && board.equals(boardIds.get(0)) && period.equals(defaultPeriodOf(board));
    }

    /** The label of a period, "Daily", from leaderboards.yml. */
    private String periodLabel(String periodId) {
        for (Period each : settings.periods()) if (each.id().equals(periodId)) return each.label();
        return periodId;
    }

    // ---- geometry ----

    private boolean wantsPeriodRow() {
        for (String each : boardIds) if (periodsOf(each).size() > 1) return true;
        return false;
    }

    private boolean wantsBoardRow() {
        return boardIds.size() > 1;
    }

    private Layout.Plan computePlan() {
        double scale = placement.scale();
        double halfColumns = Math.max(Math.abs(style.rankX), Math.max(Math.abs(style.playerX), Math.abs(style.valueX))) * scale;
        double textWidth = 2.4 * style.textScale * scale;
        return Layout.plan(style.rows, style.rowHeight * scale, wantsPeriodRow(), wantsBoardRow(), halfColumns, textWidth, style.panelPadding * scale);
    }

    private Layout.Point origin() {
        return new Layout.Point(placement.x(), placement.y(), placement.z());
    }

    private Layout.Point spot(double right, double up, double forward) {
        return Layout.at(origin(), placement.yaw(), right, up, forward);
    }

    private Location location(World world, Layout.Point point) {
        return new Location(world, point.x(), point.y(), point.z(), (float) placement.yaw(), 0f);
    }

    /** The chunks the board touches, as keys, so it is only built when all of them are loaded. */
    Set<Long> chunkKeys() {
        Layout.Plan p = computePlan();
        double half = p.panelWidth() / 2;
        double top = p.panelY() + p.panelHeight() / 2;
        Set<Long> keys = new HashSet<>();
        for (double right : new double[]{-half, 0, half}) {
            Layout.Point point = spot(right, top, 0);
            keys.add(org.bukkit.Chunk.getChunkKey((int) Math.floor(point.x()) >> 4, (int) Math.floor(point.z()) >> 4));
        }
        return keys;
    }

    boolean chunksLoaded(World world) {
        for (long key : chunkKeys()) {
            if (!world.isChunkLoaded((int) key, (int) (key >> 32))) return false;
        }
        return true;
    }

    // ---- spawning ----

    boolean spawned() {
        return panel != null;
    }

    /** False when something is missing, a chunk unloaded under it or another plugin removed an entity. */
    boolean intact() {
        if (!spawned() || !panel.isValid() || !title.isValid()) return false;
        for (TextDisplay[] line : lines) for (TextDisplay each : line) if (!each.isValid()) return false;
        for (TabView view : tabs) if (!view.text.isValid() || !view.hit.isValid()) return false;
        return true;
    }

    /** Builds everything. Does nothing if the world is not loaded. */
    void spawn(OfflinePlayer context) {
        World world = Bukkit.getWorld(placement.world());
        if (world == null || spawned()) return;
        plan = computePlan();
        double scale = placement.scale();
        double ts = style.textScale * scale;

        if (style.panelEnabled) {
            // A space has a background of 0.15 by 0.25 blocks at scale 1, so stretching it makes the panel
            panel = text(world, spot(0, plan.panelY(), 0), Component.text(" "), plan.panelWidth() / 0.15, plan.panelHeight() / 0.25, style.panelColor);
        } else {
            panel = text(world, spot(0, plan.panelY(), 0), Component.empty(), 0.01, 0.01, Color.fromARGB(0, 0, 0, 0));
        }
        title = text(world, spot(0, plan.titleY(), FACE), Component.empty(), ts * 1.35, ts * 1.35, Color.fromARGB(0, 0, 0, 0));

        double[] xs = {style.rankX * scale, style.playerX * scale, style.valueX * scale};
        for (int line = 0; line <= style.rows; line++) {
            TextDisplay[] cells = new TextDisplay[3];
            for (int column = 0; column < 3; column++) {
                cells[column] = text(world, spot(xs[column], plan.rowY(line), FACE), Component.empty(), ts, ts, Color.fromARGB(0, 0, 0, 0));
            }
            lines.add(cells);
        }
        buildTabs(world);
        lastRows.clear();
        lastTitle = Component.empty();
        render(context, true);
    }

    void despawn() {
        generation++;
        removeTabs();
        for (TextDisplay[] line : lines) for (TextDisplay each : line) each.remove();
        lines.clear();
        lastRows.clear();
        if (title != null) title.remove();
        if (panel != null) panel.remove();
        title = null;
        panel = null;
    }

    private TextDisplay text(World world, Layout.Point at, Component content, double scaleX, double scaleY, Color background) {
        return world.spawn(location(world, at), TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.FIXED);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(400);
            d.setDefaultBackground(false);
            d.setBackgroundColor(background);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(Math.max(0.1f, style.viewRange / 64f));
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f((float) scaleX, (float) scaleY, 1f), new AxisAngle4f()));
            d.setPersistent(false);
            d.setInvulnerable(true);
            d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
            d.text(content);
        });
    }

    // ---- tabs ----

    private List<Tab> wantedTabs(Kind kind) {
        List<Tab> result = new ArrayList<>();
        if (kind == Kind.BOARD) {
            if (!wantsBoardRow()) return result;
            for (String each : boardIds) {
                Board found = settings.board(each);
                result.add(new Tab(Kind.BOARD, each, found == null ? each : found.look().tab()));
            }
        } else {
            List<String> available = periodsOf(board);
            if (available.size() < 2) return result;
            for (String each : available) result.add(new Tab(Kind.PERIOD, each, periodLabel(each)));
        }
        return result;
    }

    private void buildTabs(World world) {
        removeTabs();
        placeRow(world, wantedTabs(Kind.PERIOD), plan.periodY());
        placeRow(world, wantedTabs(Kind.BOARD), plan.boardY());
    }

    private void placeRow(World world, List<Tab> row, double up) {
        double scale = placement.scale();
        double ts = style.textScale * scale;
        for (int i = 0; i < row.size(); i++) {
            double right = (i - (row.size() - 1) / 2.0) * style.tabSpacing * scale;
            TabView view = new TabView(row.get(i));
            view.text = text(world, spot(right, up, FACE), Component.empty(), ts, ts, Color.fromARGB(0, 0, 0, 0));
            Layout.Point foot = spot(right, up - plan.tabHeight() / 2, FACE);
            view.hit = world.spawn(location(world, foot), Interaction.class, h -> {
                h.setInteractionWidth((float) (style.tabHitWidth * scale));
                h.setInteractionHeight((float) plan.tabHeight());
                h.setResponsive(true);
                h.setPersistent(false);
                h.getPersistentDataContainer().set(tag, PersistentDataType.STRING, id);
            });
            tabs.add(view);
        }
        styleTabs();
    }

    private void removeTabs() {
        for (TabView view : tabs) {
            view.text.remove();
            view.hit.remove();
        }
        tabs.clear();
    }

    private void styleTabs() {
        for (TabView view : tabs) {
            boolean selected = view.tab.kind() == Kind.BOARD ? view.tab.id().equals(board) : view.tab.id().equals(period);
            String format = selected ? style.tabSelected : style.tabUnselected;
            view.text.text(TextUtil.component(TextUtil.fill(format, Map.of("<label>", view.tab.label()))));
            view.text.setBackgroundColor(selected ? style.tabSelectedBackground : style.tabUnselectedBackground);
        }
    }

    /** The tab that owns an interaction entity, or null. */
    Tab tabOf(UUID entity) {
        for (TabView view : tabs) if (view.hit.getUniqueId().equals(entity)) return view.tab;
        return null;
    }

    boolean owns(UUID entity) {
        return tabOf(entity) != null;
    }

    // ---- choosing ----

    /** Switches to a tab. @return false if there was nothing to change */
    boolean select(Tab tab, OfflinePlayer context) {
        return select(tab.kind(), tab.id(), context);
    }

    boolean select(Kind kind, String target, OfflinePlayer context) {
        lastClick = System.currentTimeMillis();
        if (kind == Kind.BOARD) {
            if (target.equals(board) || !boardIds.contains(target)) return false;
            board = target;
            period = periodsOf(board).contains(period) ? period : defaultPeriodOf(board);
        } else {
            if (target.equals(period) || !periodsOf(board).contains(target)) return false;
            period = target;
        }
        changed(context, kind == Kind.BOARD);
        return true;
    }

    /** Back to the first board and period, when nobody has clicked for a while. */
    void resetIfIdle(long now, OfflinePlayer context) {
        if (style.idleResetSeconds <= 0 || lastClick == 0 || atDefault()) return;
        if (now - lastClick < style.idleResetSeconds * 1000L) return;
        selectDefault();
        changed(context, true);
    }

    private void changed(OfflinePlayer context, boolean rebuildTabs) {
        if (!spawned()) return;
        int mine = ++generation;
        World world = panel.getWorld();
        if (rebuildTabs) buildTabs(world);
        else styleTabs();

        if (!style.transition) {
            render(context, true);
            return;
        }
        // Blank for a moment, then the rows fill in from the top
        clearTexts();
        List<Rows.Row> rows = fetchRows(context);
        Component head = titleText();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (generation != mine || !intact()) return;
            applyTitle(head);
            applyRow(0, header());
            fill(rows, 1, mine);
        }, 3L);
    }

    private void fill(List<Rows.Row> rows, int from, int mine) {
        int stop = Math.min(rows.size() + 1, from + 3);
        for (int i = from; i < stop; i++) applyRow(i, rows.get(i - 1));
        if (stop <= rows.size()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (generation == mine && intact()) fill(rows, stop, mine);
            }, 1L);
        }
    }

    private void clearTexts() {
        title.text(Component.empty());
        lastTitle = Component.empty();
        lastRows.clear();
        for (TextDisplay[] line : lines) for (TextDisplay each : line) each.text(Component.empty());
    }

    // ---- drawing ----

    private Board currentBoardObject() {
        return settings.board(board);
    }

    private Component titleText() {
        Board found = currentBoardObject();
        String name = found == null ? board : found.look().title();
        return TextUtil.component(TextUtil.fill(style.title, Map.of("<board>", name, "<period>", periodLabel(period))));
    }

    private Rows.Row header() {
        Board found = currentBoardObject();
        String valueHeader = found == null ? "Value" : found.look().valueHeader();
        return new Rows.Row(TextUtil.component(style.headerRank), TextUtil.component(style.headerPlayer),
                TextUtil.component(TextUtil.fill(style.headerValue, Map.of("<value-header>", valueHeader))));
    }

    private List<Rows.Row> fetchRows(OfflinePlayer context) {
        Board found = currentBoardObject();
        if (found == null) return Rows.build(context, null, style.rows, style, Component.empty());
        return Rows.build(context, found.placeholdersFor(period), style.rows, style, style.suffix(found.look().valueSuffix()));
    }

    /** Reads the placeholders and redraws what changed. */
    void render(OfflinePlayer context, boolean force) {
        if (!spawned()) return;
        applyTitle(titleText());
        applyRow(0, header());
        List<Rows.Row> rows = fetchRows(context);
        for (int i = 0; i < rows.size(); i++) applyRow(i + 1, rows.get(i));
    }

    private void applyTitle(Component text) {
        if (text.equals(lastTitle)) return;
        title.text(text);
        lastTitle = text;
    }

    private void applyRow(int index, Rows.Row row) {
        while (lastRows.size() <= index) lastRows.add(null);
        Rows.Row before = lastRows.get(index);
        if (row.equals(before)) return;
        TextDisplay[] cells = lines.get(index);
        if (before == null || !before.rank().equals(row.rank())) cells[0].text(row.rank());
        if (before == null || !before.name().equals(row.name())) cells[1].text(row.name());
        if (before == null || !before.value().equals(row.value())) cells[2].text(row.value());
        lastRows.set(index, row);
    }

    // ---- timing ----

    boolean due(long now) {
        if (now < nextRefresh) return false;
        nextRefresh = now + style.refreshSeconds * 1000L;
        return true;
    }

    /** The nearest player within view range, or null. */
    Player nearest() {
        if (!spawned()) return null;
        Location here = panel.getLocation();
        Player best = null;
        double bestDistance = (double) style.viewRange * style.viewRange;
        for (Player player : here.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(here);
            if (distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        return best;
    }

    Location where() {
        World world = Bukkit.getWorld(placement.world());
        return world == null ? null : new Location(world, placement.x(), placement.y(), placement.z(), (float) placement.yaw(), 0f);
    }
}
