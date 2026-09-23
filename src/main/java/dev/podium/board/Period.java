package dev.podium.board;

/** One of the time windows the dashboard's filter cycles through (all_time, monthly, ...). */
public record Period(String id, String label) {
}
