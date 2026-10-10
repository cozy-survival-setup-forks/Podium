## Podium

Leaderboards for Paper 1.21.11, three ways: a menu, boards that stand in the world, and a stats menu. All the numbers come from PlaceholderAPI, and it is made to go with [Topper](https://github.com/Topper-MC/Topper): a board is one line, the name of a Topper holder. Anything else that gives PlaceholderAPI placeholders works too.

Needs PlaceholderAPI. Topper is optional but is what the default boards use.

### Boards in the world

Look at a wall and run `/podium display create <id> <board> [more boards]`. A board appears there: a title, a table of the top 10 (rank, player, value), and tabs under it. Right or left click a tab to switch.

- The bottom row of tabs switches between the boards you named (Balance, Kills, ...).
- The row above it switches the time window: Daily, Monthly, All Time. A board only gets the windows it has numbers for (see below), and the tabs are rebuilt when you change board.
- Clicking blanks the table for a moment and fills it in again from the top, with a click sound. Everyone sees the same board. After 30 seconds without a click it goes back to the first board and period.
- A board is only built while a player is near it, so empty places cost nothing. It refreshes every 5 seconds.
- Nothing is saved in the world: the displays are rebuilt from `displays.yml` when someone comes near, so a restart or an unloaded chunk leaves nothing behind.

| Command | What it does |
| --- | --- |
| `/podium display create <id> <board...>` | Put one up on the wall you are looking at (or in front of you) |
| `/podium display place <id> <world> <x> <y> <z> <yaw> <board...>` | The same with exact coordinates, also from the console |
| `/podium display move <id>` | Move it to where you are looking |
| `/podium display scale <id> <number>` | Make it bigger or smaller (1 is normal) |
| `/podium display rotate <id> [degrees]` | Turn it, 180 by default. Use it if the text reads backwards |
| `/podium display tp <id>` | Teleport in front of it |
| `/podium display select <id> <board or period>` | Switch it from a command |
| `/podium display refresh <id>`, `list`, `remove <id>` | |
| `/podium reload` | Reload every file and rebuild the boards |
| `/podium topper` | The Topper holders Podium found |

`display.yml` is the look: rows, text and letter size, row spacing, the dark panel behind it, column positions, rank colours, tab text and colours, click sound, how long before it resets, and the blank flash. `always-built: true` keeps boards built with nobody near. `displays.yml` holds where each board is (the commands write it, an unreadable file is never overwritten).

Text is drawn with the plain text display entity, so a wooden frame like the one in your screenshots is your own build: turn the panel off in `display.yml` (`panel.enabled: false`) and scale the board to fit it. If the rows do not line up with your frame, `row-height` and `text-scale` are the two numbers to change.

### The Topper hook

In `leaderboards.yml` a board is:

```yaml
boards:
  money:
    icon: EMERALD
    name: "&a$ Top Money"
    category: "Money"
    description:
      - "Ranks players by the total"
      - "amount of money they have earned."
    topper: money
    display:
      value-header: "Balance"
      tab: "Balance"
```

`topper: money` is the name of the holder. Podium reads Topper's own `config.yml` for the holders, tells you at startup if a name is not there, and uses Topper's placeholders (`%topper_money;top_name;<position>%` and `top_value`) for the names and values. If Topper also has holders named `money_daily`, `money_weekly` or `money_monthly` (what TimedTopper makes), the Daily, Weekly and Monthly tabs and filters use them by themselves. To name them yourself:

```yaml
    topper:
      all_time: money
      daily: money_daily
      monthly: money_monthly
```

`value-format: "#,###"` is a Topper number format for the values. Without a daily or monthly holder a board only has All Time, and its tab row is not shown: Podium does not show all-time numbers under a Daily label.

Also in the hook: `/podium display create wall topper:kills` makes a board from a Topper holder that is not in `leaderboards.yml`, and `topper.auto-boards: true` in `config.yml` adds every holder no board uses yet to the dashboard.

For anything that is not Topper, write the placeholders out, one pair for each period the board has:

```yaml
    periods:
      all_time:
        name: "%some_placeholder_<position>_name%"
        value: "%some_placeholder_<position>_value%"
```

### Menus

`/leaderboards` opens a dashboard: every board is an icon, with a filter to switch between All Time, Monthly, Weekly and Daily. Click a board for its ranking list, click a name for that player's stats. `/leaderboards <board>` jumps to one, `/leaderboards reload` reloads. The grid, filler and buttons are defined once under `board-template:` in `leaderboards.yml`.

`/stats` (or `/stats <player>` with `podium.stats.others`) opens one menu, edited in `stats-menu.yml`: a slot, an icon, and any PlaceholderAPI placeholder in the lore.

### Permissions

- `podium.leaderboards` (everyone): the menus, and clicking the tabs of a board in the world
- `podium.stats` (everyone), `podium.stats.others` (op)
- `podium.admin` (op): `/podium`

### Keeping your files safe

- `config.yml`, `display.yml` and `messages.yml` start with a `config-version` / `lang-version` number. After an update, new settings are added to your files with their comments, and nothing you changed is touched. The old file is kept next to it as `<name>.<date>.bak` (the newest 5). A setting is only removed when the changelog says so.
- A value with a mistake (a negative time, an item that does not exist, text where a number belongs) is named in the console by file and key. On a reload, the settings in use stay as they were.
- Files are written to a temporary file and moved into place, with the previous version kept as `.bak`. A file that cannot be read is restored from its `.bak`, and the unreadable one is kept as `.broken-<time>`.
- `leaderboards.yml` and `stats-menu.yml` are your own lists of boards and menu items, so nothing is added to them; they are only kept readable (restored from their `.bak` if damaged). `displays.yml`, where your world boards are, is restored from its `.bak` if it is damaged, and with no usable backup it is left exactly as it is and nothing is written over it.
- A file or database that was made by a newer version of the plugin is left alone and a warning is logged.
- `/podium doctor` shows the health of the files, versions, last backup and recent save failures (no player data). `/podium backup now` makes a checked backup right away. Both need the admin permission.

## Telemetry

On startup Podium sends a small anonymous beacon (plugin name/version, server software/version, online/max player counts, and a random ID with no player data) so we know which versions are in use. Turn it off with `metrics.enabled: false` in `config.yml`. The random ID is kept as `server-id` in `data.yml` (older versions kept it in a `.server-id` file, which is moved over unchanged). The address and the interval are fixed in the plugin and are not settings.

### License

See `LICENSE`: free to run on your own servers, not for redistribution or resale.
