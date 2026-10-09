package dev.podium;

import dev.podium.command.LeaderboardCommand;
import dev.podium.command.PodiumCommand;
import dev.podium.command.StatsCommand;
import dev.podium.config.Settings;
import dev.podium.menu.Menus;
import dev.podium.world.WorldBoards;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PodiumPlugin extends JavaPlugin {

    private WorldBoards worlds;

    @Override
    public void onEnable() {
        Settings settings = new Settings(this);
        if (!settings.loaded()) {
            getLogger().severe("Podium cannot start with a broken file, so it is switching itself off. Fix the file named above, "
                    + "or delete it to get the default one, then restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Menus menus = new Menus(this, settings);
        getServer().getPluginManager().registerEvents(menus, this);

        worlds = new WorldBoards(this, settings);
        getServer().getPluginManager().registerEvents(worlds, this);
        worlds.start();

        LeaderboardCommand leaderboardCommand = new LeaderboardCommand(settings, menus, worlds);
        PluginCommand leaderboards = getCommand("leaderboards");
        leaderboards.setExecutor(leaderboardCommand);
        leaderboards.setTabCompleter(leaderboardCommand);
        getCommand("stats").setExecutor(new StatsCommand(settings, menus));

        PodiumCommand podium = new PodiumCommand(settings, worlds);
        PluginCommand podiumCommand = getCommand("podium");
        podiumCommand.setExecutor(podium);
        podiumCommand.setTabCompleter(podium);

        Banner.print(this, "Thanks for keeping score.");
    }

    @Override
    public void onDisable() {
        if (worlds != null) worlds.stop();
    }
}
