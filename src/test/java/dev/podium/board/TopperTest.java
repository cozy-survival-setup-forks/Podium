package dev.podium.board;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopperTest {

    @Test
    void aHolderBecomesTheTopperPlaceholders() {
        Board.PlaceholderPair pair = Topper.pair("money", null);
        assertEquals("%topper_money;top_name;<position>%", pair.namePattern());
        assertEquals("%topper_money;top_value;<position>%", pair.valuePattern());
        assertEquals("%topper_money;top_value;<position>;#,###%", Topper.pair("money", " #,### ").valuePattern());
        assertEquals("%topper_money;top_value;<position>%", Topper.pair("money", "  ").valuePattern());
    }

    @Test
    void timedHoldersFillTheirPeriodsByName() {
        Set<String> holders = new LinkedHashSet<>(List.of("money", "money_daily", "Money_Monthly", "kills"));
        Map<String, String> periods = Topper.periodHolders("money", holders);
        assertEquals("money", periods.get("all_time"));
        assertEquals("money_daily", periods.get("daily"));
        assertEquals("Money_Monthly", periods.get("monthly"));
        assertFalse(periods.containsKey("weekly"));
        assertEquals(Map.of("all_time", "kills"), Topper.periodHolders("kills", holders));
    }

    @Test
    void aHolderThatFeedsAnotherOnesWindowIsNotABoardOfItsOwn() {
        Set<String> holders = Set.of("money", "money_daily", "daily_rewards", "kills_weekly");
        assertTrue(Topper.isWindowOf("money_daily", holders));
        assertFalse(Topper.isWindowOf("money", holders));
        assertFalse(Topper.isWindowOf("daily_rewards", holders));
        assertFalse(Topper.isWindowOf("kills_weekly", holders), "there is no 'kills' holder it could belong to");
    }

    @Test
    void namesAreTurnedIntoTitles() {
        assertEquals("Mob Kills", Topper.title("mob_kills"));
        assertEquals("Blocks Broken", Topper.title("blocks-broken"));
        assertEquals("Money", Topper.title("money"));
    }

    @Test
    void holdersAreReadFromToppersOwnConfig(@TempDir Path plugins) throws Exception {
        assertTrue(Topper.holders(plugins.toFile()).isEmpty(), "no Topper folder");

        File folder = new File(plugins.toFile(), "Topper");
        assertTrue(folder.mkdirs());
        Files.writeString(new File(folder, "config.yml").toPath(), """
                holders:
                  money:
                    value-provider:
                      type: placeholder
                  kills:
                    value-provider:
                      type: statistic
                storage-type: flat
                """);
        assertEquals(List.of("money", "kills"), List.copyOf(Topper.holders(plugins.toFile())));
    }
}
