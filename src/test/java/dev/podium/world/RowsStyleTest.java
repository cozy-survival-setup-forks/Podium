package dev.podium.world;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowsStyleTest {

    @Test
    void textFromPlaceholdersCannotBreakTheLayout() {
        assertEquals("Steve", Rows.clean("  Steve  ", 20));
        assertEquals("Steve", Rows.clean("§cSteve", 20), "colour codes are removed");
        assertEquals("AB", Rows.clean("A\nB", 20), "line breaks are removed");
        assertEquals("ab", Rows.clean("a\tb\u0000", 20));
        String cut = Rows.clean("abcdefghijklmnopqrstuvwxyz", 10);
        assertEquals(10, cut.length());
        assertTrue(cut.endsWith("…"));
        assertEquals("", Rows.clean("§c\n", 10));
    }

    @Test
    void theFirstRanksHaveTheirOwnColour() {
        List<TextColor> special = List.of(TextColor.color(0xFFD700), TextColor.color(0xC0C0C0));
        TextColor other = TextColor.color(0xFFFFFF);
        assertEquals(TextColor.color(0xFFD700), Rows.rankColor(1, special, other));
        assertEquals(TextColor.color(0xC0C0C0), Rows.rankColor(2, special, other));
        assertEquals(other, Rows.rankColor(3, special, other));
        assertEquals(other, Rows.rankColor(0, special, other));
    }

    @Test
    void coloursAreReadWithOrWithoutOpacity() {
        Color fallback = Color.fromARGB(1, 2, 3, 4);
        assertEquals(Color.fromARGB(255, 0x10, 0x20, 0x30), Style.color("#102030", fallback));
        assertEquals(Color.fromARGB(0x80, 0x10, 0x20, 0x30), Style.color("#80102030", fallback));
        assertEquals(Color.fromARGB(0, 0, 0, 0), Style.color("transparent", fallback));
        assertEquals(fallback, Style.color("nonsense", fallback));
        assertEquals(fallback, Style.color(null, fallback));
        assertEquals(TextColor.color(0x102030), Style.textColor("#102030", TextColor.color(0)));
        assertEquals(TextColor.color(0x999999), Style.textColor("bad", TextColor.color(0x999999)));
    }

    @Test
    void anEmptyFileGivesTheDefaults() {
        Style style = new Style(new YamlConfiguration());
        assertEquals(10, style.rows);
        assertEquals(5, style.refreshSeconds);
        assertEquals(30, style.idleResetSeconds);
        assertTrue(style.transition);
        assertTrue(style.panelEnabled);
        assertEquals(3, style.rankColors.size());
        assertTrue(style.tabHitWidth < style.tabSpacing, "a tab must be narrower than the space it has");
    }

    @Test
    void wildValuesAreKeptInRange() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rows", 500);
        yaml.set("refresh-seconds", 0);
        yaml.set("text-scale", -3);
        yaml.set("idle-reset-seconds", -5);
        yaml.set("row-height", 9000);
        Style style = new Style(yaml);
        assertEquals(15, style.rows);
        assertEquals(1, style.refreshSeconds);
        assertEquals(0.1, style.textScale, 1e-9);
        assertEquals(0, style.idleResetSeconds);
        assertEquals(1.0, style.rowHeight, 1e-9);
        assertFalse(style.rankColors.isEmpty());
    }
}
