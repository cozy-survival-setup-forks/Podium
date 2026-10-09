package dev.podium.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutTest {

    private static final double E = 1e-9;

    @Test
    void aBoardOnASouthFacingSideLooksSouthAndItsRightIsEast() {
        // a wall whose open side looks along +Z: the reader stands to the south, looking north, and east is on their right
        double yaw = Layout.yawFacing(0, 1);
        assertEquals(0, yaw, E);
        assertEquals(0, Layout.front(yaw)[0], E);
        assertEquals(1, Layout.front(yaw)[1], E);
        assertEquals(1, Layout.right(yaw)[0], E);
        assertEquals(0, Layout.right(yaw)[1], E);
    }

    @Test
    void theFrontAlwaysLooksTheWayThatWasAskedFor() {
        int[][] normals = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
        for (int[] n : normals) {
            double yaw = Layout.yawFacing(n[0], n[1]);
            assertEquals(n[0], Layout.front(yaw)[0], E);
            assertEquals(n[1], Layout.front(yaw)[1], E);
            // right is a quarter turn from the front, and the two are at right angles
            double dot = Layout.front(yaw)[0] * Layout.right(yaw)[0] + Layout.front(yaw)[1] * Layout.right(yaw)[1];
            assertEquals(0, dot, E);
        }
    }

    @Test
    void facingWestIsNinetyDegreesLikeMinecraftCountsIt() {
        assertEquals(90, Layout.yawFacing(-1, 0), E);
        assertEquals(-90, Layout.yawFacing(1, 0), E);
    }

    @Test
    void aSpotIsMeasuredFromTheMiddleOfTheBoard() {
        Layout.Point origin = new Layout.Point(10, 64, 20);
        Layout.Point point = Layout.at(origin, 0, 2, 1.5, 0.5);
        assertEquals(12, point.x(), E);
        assertEquals(65.5, point.y(), E);
        assertEquals(20.5, point.z(), E);

        // turned to face west, "right" points north and "forward" points west
        Layout.Point west = Layout.at(origin, 90, 2, 0, 0.5);
        assertEquals(9.5, west.x(), E);
        assertEquals(22, west.z(), E);
    }

    @Test
    void yawIsKeptBetweenMinus180And180() {
        assertEquals(-170, Layout.wrap(190), E);
        assertEquals(170, Layout.wrap(-190), E);
        assertEquals(0, Layout.wrap(360), E);
        assertEquals(-180, Layout.wrap(180), E);
    }

    @Test
    void theRowsAreEvenlySpacedAndTheTitleIsAboveTheTabs() {
        Layout.Plan plan = Layout.plan(10, 0.2, true, true, 1.0, 1.4, 0.25);
        assertEquals(11 * 0.2, plan.tableHeight(), E);
        assertEquals(0.2, plan.rowY(0) - plan.rowY(1), E);
        assertEquals(0.2, plan.rowY(4) - plan.rowY(5), E);
        // the table is centred: the header is as far above the middle as the last row is below it
        assertEquals(plan.rowY(0), -plan.rowY(10), E);
        assertTrue(plan.titleY() > plan.rowY(0));
        assertTrue(plan.periodY() < plan.rowY(10));
        assertTrue(plan.boardY() < plan.periodY());
    }

    @Test
    void thePanelCoversTheWholeBoard() {
        Layout.Plan plan = Layout.plan(10, 0.2, true, true, 1.0, 1.4, 0.25);
        double top = plan.panelY() + plan.panelHeight() / 2;
        double bottom = plan.panelY() - plan.panelHeight() / 2;
        assertTrue(top > plan.titleY() + plan.titleHeight() / 2);
        assertTrue(bottom < plan.boardY() - plan.tabHeight() / 2);
        assertTrue(plan.panelWidth() > 2.0 + 1.4);
    }

    @Test
    void withoutTabsTheTableIsTheBottom() {
        Layout.Plan plan = Layout.plan(5, 0.2, false, false, 1.0, 1.4, 0.25);
        double bottom = plan.panelY() - plan.panelHeight() / 2;
        assertEquals(-plan.tableHeight() / 2 - 0.25, bottom, E);
    }
}
