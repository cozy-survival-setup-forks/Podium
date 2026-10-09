package dev.podium.command;

import dev.podium.config.Settings;
import dev.podium.menu.Menus;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StatsCommand implements CommandExecutor {

    private final Settings settings;
    private final Menus menus;

    public StatsCommand(Settings settings, Menus menus) {
        this.settings = settings;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("podium.stats")) {
            player.sendMessage(settings.prefixed("no-permission"));
            return true;
        }

        OfflinePlayer target = player;
        if (args.length > 0) {
            if (!player.hasPermission("podium.stats.others")) {
                player.sendMessage(settings.prefixed("no-permission"));
                return true;
            }
            // Not getOfflinePlayer(name): for a name the server has never seen that asks Mojang, and waits for the answer
            target = Bukkit.getOfflinePlayerIfCached(args[0]);
            if (target == null) {
                player.sendMessage(settings.prefixed("player-not-found").replace("<player>", args[0]));
                return true;
            }
        }

        menus.openStats(player, target);
        return true;
    }
}
