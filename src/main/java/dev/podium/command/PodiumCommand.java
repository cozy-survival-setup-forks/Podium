package dev.podium.command;

import dev.podium.board.Board;
import dev.podium.config.Settings;
import dev.podium.world.Placement;
import dev.podium.world.WorldBoards;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /podium: reload, and the boards that stand in the world (/podium display ...). */
public final class PodiumCommand implements TabExecutor {

    private static final List<String> DISPLAY_SUBS = List.of("create", "place", "remove", "list", "tp", "move", "scale", "rotate", "periods", "select", "refresh");

    private final Settings settings;
    private final WorldBoards worlds;

    public PodiumCommand(Settings settings, WorldBoards worlds) {
        this.settings = settings;
        this.worlds = worlds;
    }

    private void say(CommandSender to, String key, String... pairs) {
        String text = settings.prefixed(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) text = text.replace("<" + pairs[i] + ">", pairs[i + 1]);
        to.sendMessage(dev.podium.util.TextUtil.component(text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("podium.admin")) {
            say(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                boolean ok = settings.reload();
                if (ok) worlds.reload();
                say(sender, ok ? "reloaded" : "reload-failed");
            }
            case "display" -> display(sender, Arrays.copyOfRange(args, 1, args.length));
            case "topper" -> {
                if (settings.topperHolders().isEmpty()) say(sender, "topper-none");
                else say(sender, "topper-holders", "holders", String.join(", ", settings.topperHolders()));
            }
            default -> say(sender, "usage-podium");
        }
        return true;
    }

    // ---- /podium display ----

    private void display(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(sender, args);
            case "place" -> place(sender, args);
            case "list" -> list(sender);
            case "remove", "tp", "move", "scale", "rotate", "periods", "select", "refresh" -> {
                if (args.length < 2) {
                    say(sender, "usage-display");
                    return;
                }
                if (!worlds.has(args[1])) {
                    say(sender, "display-unknown", "id", args[1]);
                    return;
                }
                edit(sender, sub, args);
            }
            default -> say(sender, "usage-display");
        }
    }

    private String boardNames() {
        List<String> names = new ArrayList<>(settings.boards().keySet());
        for (String holder : settings.topperHolders()) names.add("topper:" + holder);
        return String.join(", ", names);
    }

    /** Checks the board names and says which one is wrong. */
    private List<String> boardsFrom(CommandSender sender, String[] args, int from) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            Board found = settings.board(args[i]);
            if (found == null) {
                say(sender, "display-unknown-board", "board", args[i], "boards", boardNames());
                return null;
            }
            ids.add(args[i].regionMatches(true, 0, "topper:", 0, 7) ? args[i] : found.id());
        }
        if (ids.isEmpty()) {
            say(sender, "display-no-board", "boards", boardNames());
            return null;
        }
        return ids;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            say(sender, "display-need-player");
            return;
        }
        if (args.length < 2) {
            say(sender, "usage-display");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        List<String> boards = boardsFrom(sender, args, 2);
        if (boards == null) return;

        Placement spot = target(player, 1.0);
        Location at = new Location(player.getWorld(), spot.x(), spot.y(), spot.z());
        String problem = worlds.create(id, at, spot.yaw(), 1.0, boards, WorldBoards.DEFAULT_PERIODS);
        if (problem != null) say(sender, problem, "id", id);
        else say(sender, "display-created", "id", id, "boards", String.join(", ", boards));
    }

    /** /podium display place <id> <world> <x> <y> <z> <yaw> <board...>, for the console or exact coordinates. */
    private void place(CommandSender sender, String[] args) {
        if (args.length < 8) {
            say(sender, "usage-display");
            return;
        }
        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            say(sender, "display-unknown-world", "world", args[2]);
            return;
        }
        double x, y, z, yaw;
        try {
            x = Double.parseDouble(args[3]);
            y = Double.parseDouble(args[4]);
            z = Double.parseDouble(args[5]);
            yaw = Double.parseDouble(args[6]);
        } catch (NumberFormatException e) {
            say(sender, "display-bad-number");
            return;
        }
        List<String> boards = boardsFrom(sender, args, 7);
        if (boards == null) return;
        String id = args[1].toLowerCase(Locale.ROOT);
        String problem = worlds.create(id, new Location(world, x, y, z), yaw, 1.0, boards, WorldBoards.DEFAULT_PERIODS);
        if (problem != null) say(sender, problem, "id", id);
        else say(sender, "display-created", "id", id, "boards", String.join(", ", boards));
    }

    private void list(CommandSender sender) {
        List<String> ids = worlds.ids();
        if (ids.isEmpty()) {
            say(sender, "display-none");
            return;
        }
        say(sender, "display-list-header", "count", String.valueOf(ids.size()));
        for (String id : ids) {
            Placement p = worlds.placement(id);
            say(sender, "display-list-line", "id", id, "world", p.world(),
                    "x", String.valueOf(Math.round(p.x())), "y", String.valueOf(Math.round(p.y())), "z", String.valueOf(Math.round(p.z())),
                    "boards", String.join(", ", worlds.boardIdsOf(id)),
                    "shown", worlds.shown(id) ? "shown" : "not built");
        }
    }

    private void edit(CommandSender sender, String sub, String[] args) {
        String id = args[1];
        Placement now = worlds.placement(id);
        String problem = null;
        switch (sub) {
            case "remove" -> problem = worlds.remove(id);
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    say(sender, "display-need-player");
                    return;
                }
                Location at = worlds.locationOf(id);
                if (at == null) {
                    say(sender, "display-unknown-world", "world", now.world());
                    return;
                }
                // Stand in front of it, looking at it
                double[] front = {-Math.sin(Math.toRadians(now.yaw())), Math.cos(Math.toRadians(now.yaw()))};
                Location stand = at.clone().add(front[0] * 3, -0.5, front[1] * 3);
                stand.setYaw((float) (now.yaw() + 180));
                stand.setPitch(0);
                player.teleport(stand);
                return;
            }
            case "move" -> {
                if (!(sender instanceof Player player)) {
                    say(sender, "display-need-player");
                    return;
                }
                Placement spot = target(player, now.scale());
                problem = worlds.place(id, new Placement(player.getWorld().getName(), spot.x(), spot.y(), spot.z(), spot.yaw(), now.scale()));
                if (problem == null) say(sender, "display-moved", "id", id);
            }
            case "scale" -> {
                if (args.length < 3) {
                    say(sender, "usage-display");
                    return;
                }
                double scale;
                try {
                    scale = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    say(sender, "display-bad-number");
                    return;
                }
                problem = worlds.place(id, new Placement(now.world(), now.x(), now.y(), now.z(), now.yaw(), scale));
                if (problem == null) say(sender, "display-scaled", "id", id, "scale", String.valueOf(worlds.placement(id).scale()));
            }
            case "rotate" -> {
                double degrees = 180;
                if (args.length > 2) {
                    try {
                        degrees = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        say(sender, "display-bad-number");
                        return;
                    }
                }
                problem = worlds.place(id, new Placement(now.world(), now.x(), now.y(), now.z(), now.yaw() + degrees, now.scale()));
                if (problem == null) say(sender, "display-rotated", "id", id);
            }
            case "periods" -> {
                say(sender, "display-periods-hint");
                return;
            }
            case "select" -> {
                if (args.length < 3) {
                    say(sender, "usage-display");
                    return;
                }
                problem = worlds.select(id, args[2], sender instanceof Player player ? player : null);
                if (problem == null) say(sender, "display-selected", "id", id, "state", worlds.stateOf(id));
            }
            case "refresh" -> {
                worlds.refresh(id);
                say(sender, "display-refreshed", "id", id);
                return;
            }
            default -> { }
        }
        if (problem != null) say(sender, problem, "id", id);
        else if (sub.equals("remove")) say(sender, "display-removed", "id", id);
    }

    /** Where a board goes when a player makes or moves one: on the wall they look at, else in front of them. */
    static Placement target(Player player, double scale) {
        RayTraceResult hit = player.rayTraceBlocks(8);
        if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
            BlockFace face = hit.getHitBlockFace();
            if (face.getModX() != 0 || face.getModZ() != 0) {
                double x = hit.getHitBlock().getX() + 0.5 + face.getModX() * 0.52;
                double z = hit.getHitBlock().getZ() + 0.5 + face.getModZ() * 0.52;
                double y = Math.round(hit.getHitPosition().getY() * 4) / 4.0;
                return new Placement(player.getWorld().getName(), x, y, z, WorldBoards.yawFacing(face.getModX(), face.getModZ()), scale);
            }
        }
        Vector ahead = player.getEyeLocation().getDirection().setY(0);
        if (ahead.lengthSquared() > 0) ahead.normalize();
        Location in = player.getEyeLocation().add(ahead.multiply(2.5));
        double yaw = Math.round((player.getLocation().getYaw() + 180) / 90.0) * 90.0;
        return new Placement(player.getWorld().getName(), in.getX(), in.getY(), in.getZ(), yaw, scale);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (!sender.hasPermission("podium.admin")) return options;

        if (args.length == 1) {
            options.addAll(List.of("reload", "display", "topper"));
        } else if (args[0].equalsIgnoreCase("display")) {
            String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
            if (args.length == 2) {
                options.addAll(DISPLAY_SUBS);
            } else if (args.length == 3 && List.of("remove", "tp", "move", "scale", "rotate", "periods", "select", "refresh").contains(sub)) {
                options.addAll(worlds.ids());
            } else if (sub.equals("create") && args.length >= 4 || sub.equals("place") && args.length >= 9) {
                options.addAll(settings.boards().keySet());
                for (String holder : settings.topperHolders()) options.add("topper:" + holder);
            } else if (sub.equals("select") && args.length == 4) {
                options.addAll(worlds.boardIdsOf(args[2]));
                options.addAll(List.of("daily", "weekly", "monthly", "all_time"));
            } else if (sub.equals("place") && args.length == 4) {
                for (World world : Bukkit.getWorlds()) options.add(world.getName());
            }
        }

        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(option -> !option.toLowerCase(Locale.ROOT).startsWith(typed));
        return options;
    }
}
