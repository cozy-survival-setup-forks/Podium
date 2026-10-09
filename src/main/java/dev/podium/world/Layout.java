package dev.podium.world;

/**
 * The geometry of a world board, with no server involved so it can be tested. A board hangs in a vertical plane. Its
 * front (the side the text can be read from) points the way {@code yaw} looks, the way Minecraft counts it: yaw 0 looks
 * south (+Z), 90 looks west (-X). "Right" is what a reader of the front has on their right hand.
 */
final class Layout {

    /** A point in the world. */
    record Point(double x, double y, double z) {
    }

    /** Where the parts of a board go, as heights from the middle of the table. */
    record Plan(double rowHeight, double tableHeight, double titleY, double titleHeight, double tabHeight,
                double periodY, double boardY, double panelY, double panelWidth, double panelHeight) {

        /** The height of row {@code index} (0 is the header line), from the middle of the table. */
        double rowY(int index) {
            return tableHeight / 2 - (index + 0.5) * rowHeight;
        }
    }

    private Layout() {
    }

    static double radians(double degrees) {
        return Math.toRadians(degrees);
    }

    /** The unit vector the front looks along. */
    static double[] front(double yaw) {
        return new double[]{-Math.sin(radians(yaw)), Math.cos(radians(yaw))};
    }

    /** The unit vector that points to the right for a reader of the front. */
    static double[] right(double yaw) {
        return new double[]{Math.cos(radians(yaw)), Math.sin(radians(yaw))};
    }

    /** The yaw whose front looks along the horizontal direction (nx, nz). */
    static double yawFacing(double nx, double nz) {
        return Math.toDegrees(Math.atan2(-nx, nz));
    }

    /** Turns any yaw into the range -180 to 180. */
    static double wrap(double yaw) {
        double result = yaw % 360;
        if (result >= 180) result -= 360;
        if (result < -180) result += 360;
        return result;
    }

    /** The world position of a spot on the board: {@code right} along the board, {@code up}, and {@code forward} off its face. */
    static Point at(Point origin, double yaw, double right, double up, double forward) {
        double[] r = right(yaw);
        double[] f = front(yaw);
        return new Point(origin.x() + r[0] * right + f[0] * forward, origin.y() + up, origin.z() + r[1] * right + f[1] * forward);
    }

    /**
     * @param rows        ranking rows, not counting the header line
     * @param rowHeight   distance between two rows, already multiplied by the scale of the board
     * @param periodRow   whether there is a row of tabs for the time windows
     * @param boardRow    whether there is a row of tabs for the boards
     * @param halfColumns the largest distance from the middle to a column, already scaled
     * @param textWidth   the room a column of text needs on top of that, already scaled
     * @param padding     space between the content and the edge of the panel, already scaled
     */
    static Plan plan(int rows, double rowHeight, boolean periodRow, boolean boardRow, double halfColumns, double textWidth, double padding) {
        double table = (rows + 1) * rowHeight;
        double gap = rowHeight * 0.7;
        double titleHeight = rowHeight * 1.6;
        double tabHeight = rowHeight * 1.5;

        double titleY = table / 2 + gap + titleHeight / 2;
        double periodY = -(table / 2 + gap + tabHeight / 2);
        double boardY = periodRow ? periodY - tabHeight - gap * 0.4 : periodY;
        double bottom = boardRow ? boardY - tabHeight / 2 : periodRow ? periodY - tabHeight / 2 : -table / 2;
        double top = titleY + titleHeight / 2;

        double panelHeight = top - bottom + 2 * padding;
        double panelWidth = 2 * halfColumns + textWidth + 2 * padding;
        return new Plan(rowHeight, table, titleY, titleHeight, tabHeight, periodY, boardY, (top + bottom) / 2, panelWidth, panelHeight);
    }
}
