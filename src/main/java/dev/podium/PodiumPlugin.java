package dev.podium;

import dev.podium.command.LeaderboardCommand;
import dev.podium.command.StatsCommand;
import dev.podium.config.Settings;
import dev.podium.menu.Menus;
import org.bukkit.plugin.java.JavaPlugin;

public final class PodiumPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Settings settings = new Settings(this);
        Menus menus = new Menus(this, settings);
        getServer().getPluginManager().registerEvents(menus, this);

        LeaderboardCommand leaderboardCommand = new LeaderboardCommand(settings, menus);
        getCommand("leaderboards").setExecutor(leaderboardCommand);
        getCommand("leaderboards").setTabCompleter(leaderboardCommand);
        getCommand("stats").setExecutor(new StatsCommand(settings, menus));
    }
}
