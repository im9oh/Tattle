# Tattle

Passive monitoring and reporting for Minecraft servers.

Tattle observes chat, commands, console output and player actions, scores what
it sees, and reports notable activity to Discord for human review.

**Tattle never punishes anyone.** No auto-bans, no auto-kicks, no auto-mutes,
no cancelled events. There is no punishment code in this plugin at all — the
only thing Tattle ever does is tell staff what it saw. Staff decide.

## Requirements

- Paper or Spigot **1.20.5+** running **Java 21** (compiled against Paper API
  1.21.4, but only stable Bukkit APIs are used)
- A Discord webhook (optional — without one, reports go to the console only)

## Installation

1. Drop `Tattle-<version>.jar` into your server's `plugins/` folder.
2. Start the server once to generate `plugins/Tattle/config.yml`.
3. In Discord: **Server Settings → Integrations → Webhooks → New Webhook**,
   pick your staff review channel, and copy the webhook URL.
4. Paste it into `discord.webhook-url` in `config.yml`.
5. Run `/tattle reload`, then `/tattle test` to confirm delivery.

## What it watches

| Monitor | What it observes |
|---|---|
| **Chat** | Configurable regex rules (advertising, harassment, profanity, cheat talk), spam bursts, excessive caps |
| **Commands** | A watchlist of sensitive commands (`/op`, `/ban`, `/give`, …) run by players *or* the console, regex rules over full command lines, command spam |
| **Console** | Log lines matching configurable patterns (errors, watchdog stalls, lag, suspicious-movement kicks) |
| **Actions** | Sustained rapid block breaking, placing/using grief-adjacent items (TNT, lava, flint and steel, end crystals), gamemode changes, first-time joins, kicks, deaths |

## How scoring works

Every observation gets a **score** and a **severity** (LOW / MEDIUM / HIGH /
CRITICAL). There are two ways an observation becomes a report:

1. **Immediate** — its score is at or above `reporting.report-threshold`
   (default 5.0). Example: someone runs `/op`.
2. **Attention** — each player has a decaying "attention" score that
   accumulates from smaller observations (half-life
   `reporting.attention-half-life-seconds`, default 5 minutes). When it crosses
   `reporting.attention-threshold` (default 12.0), a report is sent and the
   score resets. This surfaces sustained low-level misbehavior — a player who
   keeps skirting the line — without any single event being reportable.

Reports are rate-limited per player and category
(`reporting.cooldown-seconds`); repeats during the cooldown are counted and
summarized in the next report. Discord delivery is batched
(`discord.batch-interval-seconds`) and filtered by `discord.min-severity`.
Every report also goes to the server console and to an in-memory buffer
(`/tattle recent`).

## Commands

All require the `tattle.admin` permission (default: op).

| Command | Description |
|---|---|
| `/tattle status` | Monitors, webhook state, counters, top attention scores |
| `/tattle reload` | Reload `config.yml` |
| `/tattle test` | Send a test report to Discord |
| `/tattle score [player]` | Show a player's attention score, or the top 10 |
| `/tattle recent [count]` | Show the most recent reports (up to 25) |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `tattle.admin` | op | Access to `/tattle` |
| `tattle.exempt` | false | Players with this permission are not monitored (checked at join) |

## Configuration

Everything is tunable in `config.yml`: thresholds, cooldowns, spam windows,
the command watchlist, watched items, and the regex rule lists for chat,
commands and console output. Each regex rule is a `name`, a Java `pattern`
(matched with `find()`), a `score`, and a `severity`. The shipped rules are
starting points — extend them to fit your community's standards.

## Building

```
mvn package
```

The jar lands in `target/Tattle-<version>.jar`. No dependencies are shaded;
everything Tattle uses at runtime (Gson, Log4j) ships with the server.

## Design notes

- **No enforcement, by design.** Tattle contains no code paths that kick,
  ban, mute, or cancel events. Adding enforcement would be a philosophical
  fork, not a feature request.
- All heavy lifting (regex matching, scoring, webhook I/O) happens off the
  main server thread or is O(1) per event; webhook failures are retried a few
  times and then dropped so Discord outages can never affect the server.
- The console monitor attaches a Log4j appender to the root logger and filters
  out Tattle's own log output to prevent feedback loops.
