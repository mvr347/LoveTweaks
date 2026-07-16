---
name: lovetweaks-gui-style
description: Unified visual/config style guide for every LoveTweaks GUI menu (the built-in /scoreboard settings menu and any companion DeluxeMenus-style menu such as main_menu.yml, player_settings.yml, player_customization.yml). Use this whenever creating or editing a GUI menu, item config, or inventory layout for this project so every menu looks and behaves the same.
---

# LoveTweaks GUI style

One visual language, one config pattern, across every menu in this project — the
built-in Java-rendered `/scoreboard` GUI (`ScoreboardGUI.java` +
`config.yml#scoreboard.gui`) and any DeluxeMenus-style menu config
(`main_menu.yml`, `player_settings.yml`, `player_customization.yml`, etc.).
Follow these rules instead of improvising per-menu.

## Icons: base64 heads, state = texture

- Every clickable button is a player-head skull with a base64 texture
  (`material: 'basehead-<base64>'` in DeluxeMenus configs, or the identical
  `basehead-<base64>` string form used by LoveTweaks' own config parser).
  Plain vanilla items/dyes are **not** used for interactive buttons.
- Two exceptions: the filler/border pane (`GRAY_STAINED_GLASS_PANE`, one space
  as the name) and the player's own head (`head-%player_name%` /
  `head-%player%`), always at slot 0, always opening the profile menu.
- A toggle-like control (on/off, or on/off/locked) gets **one fixed texture per
  state**, reused for every button in that family. Don't hand-pick a bespoke
  icon per item — the state is what the texture communicates, not the item's
  identity. LoveTweaks' scoreboard placeholders are the reference example:
  every placeholder button uses the same `placeholder-on` / `placeholder-off`
  / `placeholder-blocked` textures from `config.yml#scoreboard.gui`,
  regardless of which placeholder it represents.
- "Locked/unavailable" always uses the *same* blocked texture no matter why
  it's locked (missing permission, missing clan, cap reached, etc.). The
  specific reason goes in the lore's reason line, never in the icon.
- Never invent a new base64 hash for a state that already has one — reuse the
  existing config key (`placeholder-on`, `placeholder-off`,
  `placeholder-blocked`, `toggle-on`, `toggle-off`, `toggle-empty`,
  `back-button`, `close-button`, `profile-button`) from
  `src/main/resources/config.yml`.

## Color & text

- Legacy `&` color codes throughout.
- Display names: plain colored text, **no emoji/symbol prefixes** — the head
  texture already conveys identity/state, a text badge is redundant clutter.
  `'&6Профиль &f%player_name%'`, not `'⭐ &6Профиль'`.
- Color meaning, kept consistent everywhere:
  - `&6`/`&e` (gold/yellow) — primary label / highlight
  - `&b` (aqua) — informational value
  - `&a` (green) — enabled / positive / confirm action
  - `&c` (red) — destructive / close / disabled-off action
  - `&7` (gray) — secondary text, inactive state
  - `&8` (dark gray) — locked / unavailable
- Lore pattern:
  - A blank `''` line separates descriptive lore from the action hints.
  - Action hints are `'&aЛКМ &7— <action>'` (or `&c` for a destructive
    action) — name the *action*, never re-explain what the item is.
  - A locked item's lore ends with a short `&c` "unavailable" line plus one
    `&7` reason line — no more.
- Scoreboard **body** lines (not menu chrome) may use the small set of
  dingbat glyphs already proven to render in vanilla font: `⚖ ⚔ ☠ ✦ ⚑ ◆ ☯ ⚡ ▸
  ✌`. Pattern: `&<accent><symbol> &f%value% &7<description>`. True
  pictographic emoji (📡, 😀, …) are never used, in menus or on the
  scoreboard — they don't render reliably in the vanilla font.

## Layout

- Standard sizes: 27 slots (3-row utility/settings menus) or 54 slots
  (6-row content menus).
- Slot 0 is always the player's own head, opening the profile menu.
- Border/filler: every slot that isn't a functional button is
  `GRAY_STAINED_GLASS_PANE` with a single-space name — including the first
  and last column of any interior row that hosts a content grid.
- Content grid rule: inside a region, remove the gaps between related
  buttons — lay them out contiguously, row by row, **grouped by category**
  (e.g. player stats, then clan, then behavior, then combat stats, then
  date/time — see `sort-group` in `config.yml`) so buttons for the same
  topic sit next to each other. Only the outer border stays empty.
- Footer row: global actions only (back, close) — never mix content buttons
  into it. Close sits in the very last slot; back sits immediately to its
  left.
- A control's GUI slot is fixed once assigned (by category, not by runtime
  state). If a click reorders something (e.g. the scoreboard's LMB/RMB
  placeholder reordering), that reordering must change a separate "position"
  value, never the slot the button occupies in the menu — a button jumping
  slots after you click it is a bug, not a feature.

## Config-driven, not hardcoded

- Every texture, display-name, lore line, and command lives in `config.yml`
  (or the menu's own yml). Code only supplies structure — which slot, which
  state maps to which texture key — never literal user-facing strings.
- Per-item unlock conditions are a PAPI requirement, not a hardcoded
  enum/if-chain: `placeholder: '%some_placeholder%'` plus optional `equals:
  'value'` (omit `equals` to mean "must resolve to a non-empty value") plus a
  `reason:` string shown in the locked item's lore. See
  `ScoreboardRequirement` / `config.yml#scoreboard.placeholders.*.requirement`
  for the canonical implementation — mirror this shape in any new menu
  config that needs conditional/locked buttons.

## When adding a new menu or button

1. Reuse an existing texture/state key if the button represents the same
   kind of state as something that already exists (on/off/locked, close,
   back, profile). Only mint a new base64 head for a genuinely new concept.
2. Pick its `sort-group`/category and let it flow into the next open grid
   slot in that category — don't hardcode a slot number by hand unless the
   menu has no category system.
3. Write display-name + lore following the color/lore conventions above.
4. If it can be locked, add a `requirement` block instead of special-casing
   it in code.
