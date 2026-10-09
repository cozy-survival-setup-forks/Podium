package dev.podium.world;

import dev.podium.board.Board;
import dev.podium.util.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;

/** Turns what the placeholders say into the lines of a board. Player names and values are plain text, never read as tags. */
final class Rows {

    static final int MAX_NAME = 20;
    static final int MAX_VALUE = 24;

    /** One line of the table. */
    record Row(Component rank, Component name, Component value) {
        static final Row BLANK = new Row(Component.empty(), Component.empty(), Component.empty());
    }

    private Rows() {
    }

    /** Takes out what could break the layout: colour codes, line breaks and other control characters, and a long tail. */
    static String clean(String text, int max) {
        StringBuilder out = new StringBuilder();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '§') {
                i++; // the code letter after it goes too
                continue;
            }
            if (c < 32 || c == 127) continue;
            out.append(c);
        }
        String result = out.toString().strip();
        return result.length() > max ? result.substring(0, max - 1) + "…" : result;
    }

    /** The text of a placeholder, or null if it did not resolve to anything. */
    static String resolve(OfflinePlayer context, String pattern, int position, int max) {
        String resolved = Placeholders.resolve(context, pattern.replace("<position>", String.valueOf(position)));
        if (resolved == null || resolved.isBlank() || resolved.contains("%")) return null;
        String cleaned = clean(resolved, max);
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** The colour of a rank: the first ones have their own, the rest share one. */
    static TextColor rankColor(int position, List<TextColor> special, TextColor other) {
        return position >= 1 && position <= special.size() ? special.get(position - 1) : other;
    }

    /** The rows 1..count for a board and a period. A missing entry shows the empty texts. */
    static List<Row> build(OfflinePlayer context, Board.PlaceholderPair pair, int count, Style style, Component suffix) {
        List<Row> rows = new ArrayList<>(count);
        for (int position = 1; position <= count; position++) {
            String name = pair == null ? null : resolve(context, pair.namePattern(), position, MAX_NAME);
            String value = pair == null || name == null ? null : resolve(context, pair.valuePattern(), position, MAX_VALUE);
            Component rank = Component.text(String.valueOf(position), rankColor(position, style.rankColors, style.rankColor));
            if (name == null) {
                rows.add(new Row(rank, style.emptyPlayer, style.emptyValue));
            } else {
                Component shown = Component.text(value == null ? "-" : value, style.valueColor).append(suffix);
                rows.add(new Row(rank, Component.text(name, style.playerColor), value == null ? style.emptyValue : shown));
            }
        }
        return rows;
    }
}
