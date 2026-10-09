package dev.podium.world;

/** Where a board stands: the middle of it, which way its front looks, and how big it is. */
public record Placement(String world, double x, double y, double z, double yaw, double scale) {
}
