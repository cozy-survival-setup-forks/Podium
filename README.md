## Podium

A leaderboard dashboard and a stats menu, in one plugin. Both are pure GUI - all the actual
numbers come from PlaceholderAPI, so it works with [Topper](https://github.com/Topper-MC/Topper)
or any other placeholder provider you already have.

### Leaderboards

`/leaderboards` opens a dashboard: every configured board shows up as an icon, with a filter
item to switch between All Time / Monthly / Weekly / Daily. Clicking a board opens its own
paginated ranking list.

Adding a new leaderboard is one entry in `leaderboards.yml`, under `boards:` - icon, name,
description, and a placeholder pair per period it supports:

```yaml
boards:
  money:
    icon: EMERALD
    name: "&a$ Top Money"
    category: "Money"
    description:
      - "Ranks players by total money earned."
    periods:
      all_time:
        name: "%topper_money;top_name;<position>%"
        value: "%topper_money;top_value;<position>%"
```

It appears on the dashboard automatically - no slot to wire up. The grid, filler, navigation
and per-entry item format for every board's ranking list are all defined once, under
`board-template:`, so a new board doesn't have to redeclare any of that.

`/leaderboards <board>` jumps straight to one board. `/leaderboards reload` reloads config.

### Stats

`/stats` (or `/stats <player>` with `podium.stats.others`) opens one menu, edited in one
place: `stats-menu.yml`. Each entry is a slot, an icon, and any PlaceholderAPI placeholder
for its lore - add or remove as many as you want.

### Permissions

- `podium.leaderboards` (default: true)
- `podium.stats` (default: true)
- `podium.stats.others` (default: op)
- `podium.admin` (default: op)
