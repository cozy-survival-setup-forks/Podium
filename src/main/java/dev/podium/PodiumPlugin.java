package dev.podium;

import dev.podium.command.LeaderboardCommand;
import dev.podium.command.PodiumCommand;
import dev.podium.command.StatsCommand;
import dev.podium.config.Settings;
import dev.podium.menu.Menus;
import dev.podium.safe.Doctor;
import dev.podium.safe.FileBackups;
import dev.podium.safe.Health;
import dev.podium.safe.Prep;
import dev.podium.safe.SafeIo;
import dev.podium.safe.ServerId;
import dev.podium.util.Placeholders;
import dev.podium.world.WorldBoards;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PodiumPlugin extends JavaPlugin {

    private static final List<String> CONTENT_FILES = List.of("leaderboards.yml", "stats-menu.yml");

    private WorldBoards worlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Health.storage("YAML files in the plugin folder (config.yml, display.yml, messages.yml, leaderboards.yml, stats-menu.yml, displays.yml, data.yml)");
        Prep.startup(this, Settings.FILES);
        for (String name : CONTENT_FILES) {
            // boards and menu items are the owner's own work: only kept readable, never added to
            Path file = getDataFolder().toPath().resolve(name);
            if (Files.exists(file)) {
                SafeIo.loadYaml(file, SafeIo.Policy.SETTINGS, getLogger());
                SafeIo.refreshBackup(file);
            }
        }
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

        PodiumCommand podium = new PodiumCommand(this, settings, worlds);
        PluginCommand podiumCommand = getCommand("podium");
        podiumCommand.setExecutor(podium);
        podiumCommand.setTabCompleter(podium);

        boolean beacon = getConfig().getBoolean("metrics.enabled", true);
        Metrics.start(this, ServerId.resolve(getDataFolder().toPath(),
                ServerId.inYaml(new File(getDataFolder(), "data.yml").toPath(), getLogger()), beacon, getLogger()));
        int holders = settings.topperHolders().size();
        getLogger().info("Ready: " + settings.boards().size() + " board(s), " + worlds.ids().size() + " world display(s), "
                + (holders > 0 ? "Topper with " + holders + " holder(s)" : "no Topper holders found")
                + ", PlaceholderAPI " + (Placeholders.present() ? "found" : "MISSING") + ".");
        if (!Placeholders.present()) {
            getLogger().severe("PlaceholderAPI is not installed. Podium reads every name and number through it, so every board will be empty.");
        }
        Banner.print(this, "Thanks for keeping score.");
    }

    @Override
    public void onDisable() {
        if (worlds != null) worlds.stop();
    }

    /** The text of /podium doctor. */
    public List<String> doctor() {
        List<String> extra = new ArrayList<>(Prep.versionLines(this, Settings.FILES));
        extra.add("World displays: " + (worlds == null ? "not loaded" : worlds.broken()
                ? "displays.yml could not be read, nothing is saved over it"
                : worlds.ids().size() + " loaded"));
        extra.add("Pending writes: 0 (displays.yml is written as soon as a display changes)");
        return Doctor.report(getName(), getPluginMeta().getVersion(), extra);
    }

    /** /podium backup now: a verified copy of the settings and display files. */
    public boolean backupNow() {
        List<String> names = new ArrayList<>(Prep.fileNames(Settings.FILES));
        names.addAll(CONTENT_FILES);
        names.add("displays.yml");
        names.add("data.yml");
        return FileBackups.snapshot(getDataFolder().toPath(), names, 5, getLogger());
    }
}
