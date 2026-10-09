package dev.podium.world;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.podium.util.TextUtil;

/** How world boards look, read from display.yml. Every value has a default, so an old or short file still works. */
final class Style {

    final int refreshSeconds;
    final int viewRange;
    final int rows;
    final int idleResetSeconds;
    final boolean transition;
    final boolean alwaysBuilt;
    final String clickSound;
    final double rowHeight;
    final double textScale;

    final boolean panelEnabled;
    final Color panelColor;
    final double panelPadding;

    final String title;
    final String headerRank, headerPlayer, headerValue;
    final double rankX, playerX, valueX;
    final Component emptyPlayer, emptyValue;
    final List<TextColor> rankColors;
    final TextColor rankColor, playerColor, valueColor;

    final String tabSelected, tabUnselected;
    final Color tabSelectedBackground, tabUnselectedBackground;
    final double tabSpacing, tabHitWidth;

    Style(YamlConfiguration c) {
        refreshSeconds = clamp(c.getInt("refresh-seconds", 5), 1, 600);
        viewRange = clamp(c.getInt("view-range", 32), 4, 128);
        rows = clamp(c.getInt("rows", 10), 1, 15);
        idleResetSeconds = Math.max(0, c.getInt("idle-reset-seconds", 30));
        transition = c.getBoolean("transition", true);
        alwaysBuilt = c.getBoolean("always-built", false);
        clickSound = c.getString("click-sound", "ui.button.click");
        rowHeight = clamp(c.getDouble("row-height", 0.17), 0.05, 1.0);
        textScale = clamp(c.getDouble("text-scale", 0.6), 0.1, 4.0);

        panelEnabled = c.getBoolean("panel.enabled", true);
        panelColor = color(c.getString("panel.color", "#99000000"), Color.fromARGB(0x99, 0, 0, 0));
        panelPadding = clamp(c.getDouble("panel.padding", 0.25), 0, 2);

        title = c.getString("title", "<bold><board>");
        headerRank = c.getString("header.rank", "<gray><bold>№");
        headerPlayer = c.getString("header.player", "<gray><bold>Player");
        headerValue = c.getString("header.value", "<gray><bold><value-header>");
        rankX = clamp(c.getDouble("columns.rank", -1.0), -8, 8);
        playerX = clamp(c.getDouble("columns.player", 0.0), -8, 8);
        valueX = clamp(c.getDouble("columns.value", 1.0), -8, 8);
        emptyPlayer = TextUtil.component(c.getString("empty-player", "<dark_gray>---"));
        emptyValue = TextUtil.component(c.getString("empty-value", "<dark_gray>---"));

        List<TextColor> special = new ArrayList<>();
        for (String hex : c.getStringList("rank-colors")) special.add(textColor(hex, TextColor.color(0xFFFFFF)));
        if (!c.isSet("rank-colors")) {
            special.add(TextColor.color(0xFFD700));
            special.add(TextColor.color(0xC0C0C0));
            special.add(TextColor.color(0xCD7F32));
        }
        rankColors = List.copyOf(special);
        rankColor = textColor(c.getString("rank-color", "#FFFFFF"), TextColor.color(0xFFFFFF));
        playerColor = textColor(c.getString("player-color", "#FFFFFF"), TextColor.color(0xFFFFFF));
        valueColor = textColor(c.getString("value-color", "#FFD27F"), TextColor.color(0xFFD27F));

        tabSelected = c.getString("tabs.selected", "<white><bold> <label> ");
        tabUnselected = c.getString("tabs.unselected", "<gray> <label> ");
        tabSelectedBackground = color(c.getString("tabs.selected-background", "#B0303A48"), Color.fromARGB(0xB0, 0x30, 0x3A, 0x48));
        tabUnselectedBackground = color(c.getString("tabs.unselected-background", "#00000000"), Color.fromARGB(0, 0, 0, 0));
        tabSpacing = clamp(c.getDouble("tabs.spacing", 1.1), 0.2, 6);
        tabHitWidth = clamp(c.getDouble("tabs.hit-width", 0.7), 0.1, 3);
    }

    /** The same style with the suffix a board shows after its values. */
    Component suffix(String text) {
        return text == null || text.isEmpty() ? Component.empty() : TextUtil.component(text);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
    }

    /** #RRGGBB (opaque) or #AARRGGBB, with or without the #. Anything else gives the fallback. */
    static Color color(String text, Color fallback) {
        if (text == null) return fallback;
        String hex = text.trim().toLowerCase(Locale.ROOT);
        if (hex.equals("transparent") || hex.equals("none")) return Color.fromARGB(0, 0, 0, 0);
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            if (hex.length() == 6) {
                int rgb = Integer.parseInt(hex, 16);
                return Color.fromARGB(255, rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF);
            }
            if (hex.length() == 8) {
                long argb = Long.parseLong(hex, 16);
                return Color.fromARGB((int) (argb >> 24) & 0xFF, (int) (argb >> 16) & 0xFF, (int) (argb >> 8) & 0xFF, (int) argb & 0xFF);
            }
        } catch (NumberFormatException ignored) {
            // falls through to the fallback
        }
        return fallback;
    }

    static TextColor textColor(String text, TextColor fallback) {
        Color color = color(text, null);
        return color == null ? fallback : TextColor.color(color.getRed(), color.getGreen(), color.getBlue());
    }
}
