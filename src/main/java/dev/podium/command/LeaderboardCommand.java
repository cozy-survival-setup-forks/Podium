package dev.podium.command;

import dev.podium.board.Board;
import dev.podium.config.Settings;
import dev.podium.menu.Menus;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class LeaderboardCommand implements CommandExecutor, TabCompleter {

    private final Settings settings;
    private final Menus menus;

    public LeaderboardCommand(Settings settings, Menus menus) {
        this.settings = settings;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("podium.leaderboards")) {
            player.sendMessage(settings.prefixed("no-permission"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("podium.admin")) {
                player.sendMessage(settings.prefixed("no-permission"));
                return true;
            }
            settings.reload();
            player.sendMessage(settings.prefixed("reloaded"));
            return true;
        }

        if (args.length > 0) {
            Board board = settings.boards().get(args[0].toLowerCase(java.util.Locale.ROOT));
            if (board == null) {
                player.sendMessage(settings.prefixed("board-not-found").replace("<board>", args[0]));
                return true;
            }
            menus.openBoard(player, board);
            return true;
        }

        menus.openDashboard(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return new ArrayList<>();
        List<String> options = new ArrayList<>(settings.boards().keySet());
        if (sender.hasPermission("podium.admin")) options.add("reload");
        return options;
    }
}
