# MenuForge

No-code chest GUI builder for Paper 1.21.4 (Java 21). Java + Bedrock (through Geyser/Floodgate),
a built-in web editor with visual click flows and a pixel texture studio, inventory skins through a
generated resource pack, and plain YAML underneath for people who prefer files.

Non-commercial hobby project; inspired by the idea of commercial "menu builder" plugins but written
from scratch.

## Features

- **Menus from YAML** in `plugins/MenuForge/menus/*.yml`, hot-reloaded when files change.
- **Web editor** (`/mf editor`): chest canvas with drag & drop, inspector, templates, one-click **Deploy**
  straight into the running server, "open for player" test button.
- **Visual scripting**: every click type (left/right/middle/shift-L/shift-R/any), *on open*, *on close*,
  *open requirements*, *visible if* and item **states** are lists of blocks. Conditions
  (cooldown, permission, money, has-item, placeholder compare, chance, world, is-bedrock) carry a
  **DENY** branch that runs when they fail.
- **Actions**: message, broadcast, actionbar, title, sound, console/player command, open menu, close,
  back, refresh, delay, give money/item, reset cooldown, teleport, set variable.
- **Automatic shop**: `shop: {buy: 100, sell: 25}` on an item generates the whole buy/sell flow (Vault).
- **Bedrock**: the same menu is shown as a form; per-item button text/image overrides.
- **Texture studio**: draw or import a PNG skin; the plugin turns it into a font glyph, builds the
  resource pack, hosts it and sends it to players. `/mf title <texture>` gives a title string that works
  in DeluxeMenus/ExcellentCrates/any plugin with a menu title.
- **Placeholders**: built-ins (`%player_name%`, `%money%`, `%cooldown_<key>%`, `%arg_1%`, ...) plus
  PlaceholderAPI when installed. MiniMessage and `&a` legacy codes both work.
- Persistent cooldowns, per-menu commands registered at runtime, back-button history.

## Build

```bash
cd MenuForge
gradle build          # -> build/libs/MenuForge-0.1.0.jar
```

Needs network access to `repo.papermc.io` (Paper API) and Maven Central.

## Quick start

1. Drop the jar into `plugins/`, start the server. Example menus (`/menu`, `/dailygifts`, `/shop`,
   `/warps`) are created on first start.
2. Run `/mf editor`, open the link (default port 8085, change in `config.yml`), paste the token.
3. Build menus, press **Deploy**.

Optional plugins: Vault (money), PlaceholderAPI, Floodgate or Geyser-Spigot (Bedrock forms).

## YAML cheat sheet

```yaml
title: "<dark_gray>Daily Gifts"
texture: daily_gifts        # textures/daily_gifts.png -> inventory skin
rows: 3
command: dailygifts
aliases: [gifts]
refresh: 20                 # ticks between placeholder refreshes (0 = off)
open-requirements:
  - { type: permission, value: gifts.use, deny: ["[message] <red>No access"] }
items:
  claim:
    slot: 13                # or slots: [1, 2, "10-16"]
    material: CHEST
    name: "<gold>Claim"
    lore: ["<gray>Next: %cooldown_daily_gift%"]
    glow: true
    states:
      - label: waiting
        requirements:
          - { type: placeholder, input: "%cooldown_daily_gift%", operator: "!=", output: "ready" }
        material: CLOCK
        name: "<red>Come back later"
    left-click:
      - type: cooldown
        key: daily_gift
        duration: 1d
        deny: ["[message] <red>Come back in %cooldown_daily_gift%"]
      - "[console] give %player_name% diamond"
      - "[sound] entity.player.levelup"
      - "[refresh]"
    bedrock: { text: "Claim", image: "textures/blocks/chest_front" }
```

Blocks can be written as maps (`{type: ..., ...}`) or as shorthand strings:
`[message]`, `[broadcast]`, `[actionbar]`, `[title] a;b`, `[sound] key;vol;pitch`, `[console]`,
`[player]`, `[open] menu args`, `[close]`, `[back]`, `[refresh]`, `[delay] ticks`,
`[permission] node`, `[money] 100`, `[give-money] 50`, `[cooldown] key 1d`, `[give-item] DIAMOND 2`,
`[chance] 25`, `[reset-cooldown] key`.

## Layout

```
src/main/java/io/github/itsrazyhero/menuforge
  flow/      blocks, parser, runner (the "visual scripting" engine)
  menu/      model, YAML loader, manager, runtime commands
  gui/       Java inventory sessions + item building
  bedrock/   Geyser/Floodgate forms (reflection, no hard dependency)
  pack/      resource pack + font glyph title composer
  web/       embedded HTTP server, editor + deploy API
  ...
src/main/resources/web/editor.html   the no-code editor (single file, no build step)
src/main/resources/templates/        ready-made menus
```
