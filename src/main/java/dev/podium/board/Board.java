package dev.podium.board;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * One leaderboard category shown on the dashboard, e.g. "Top Money". {@code periods} maps a
 * period id (see {@link Period}) to the placeholder pair that fills it; a period missing here
 * falls back to "all_time".
 */
public record Board(String id, Material icon, String name, String category, List<String> description,
                     Map<String, PlaceholderPair> periods, Look look) {

    public record PlaceholderPair(String namePattern, String valuePattern) {
    }

    /** How the board looks as a board in the world: its title, the head of the value column, the tab text. */
    public record Look(String title, String valueHeader, String valueSuffix, String tab) {
    }

    public PlaceholderPair placeholdersFor(String periodId) {
        PlaceholderPair exact = periods.get(periodId);
        if (exact != null) return exact;
        return periods.get("all_time");
    }
}
