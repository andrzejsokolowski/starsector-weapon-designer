# Weapon Designer

These instructions apply to this repository, including when it is opened without the parent mod workspace.
The workspace `AGENTS.md` one folder up still governs releases, packaging, Git identity and communication.

## Notion

Tasks, history and test lists: the **Weapon Designer board** in Andrzej's Notion, under
**Starsector mods** (`collection://37541cbb-acfe-497b-b0c9-23af1129f403`). Working rules and game
API notes: **Claude Information** (`collection://7bf9b1d6-b9e5-4b62-87f1-757bbaa51919`), starting
with "Start here: working on Starsector mods through Notion", and "Game API: changing weapons
while the game runs" for how this mod works. Ticket ids start with WD. Read the board with
`notion-query-data-sources` mode `view` on `view://40420ca7-5d20-81b9-a8e7-000c9c37bb56`. Update the
board during the work, not only at release time.

## Build

`./gradlew releaseZip` compiles against the game and LunaLib (found in the game's mods folder),
runs the checks (`modelChecks`, forbidden API scan, metadata) and writes `WeaponDesigner.zip` in
this folder.

`python tools/generate_reserve.py` rewrites the reserved weapons in `data/weapons` and
`data/strings/descriptions.csv`. Keep its `RESERVE` equal to `Kind.RESERVE`.

## How it works

The game cannot add a weapon after startup, so the mod ships 10 blank weapons per projectile
type (`wd_shot_###`, `wd_laser_###`, `wd_beam_###`, `wd_missile_###`). A design borrows one and
is written onto the game's shared weapon definition at runtime. Andrzej asked to keep this list
short (it shows in the console's weapon list), so do not raise `RESERVE` without asking.

- `Design`, `Stat`, `Script`, `Kind` - the model. Designs are saved as a JSON string in the
  sector's persistent data (`DesignStore`), so saves hold none of this mod's classes. Stat
  ladders start with a table and keep going (fixed step or ratio). Only spread stops, at perfect.
  The game fires a weapon at most once per frame (no firing loop in its weapon code, checked in
  0.98a-RC8), so `SpecWriter` writes a refire of at most 30 shots a second and
  `combat.RapidFire` (named as onFireEffect in every blank .proj) tops each real shot up with
  extra projectiles, by the time actually passed, until the designed rate is met. Past 6 per
  shot (2 for missiles) the shots get heavier instead. `ScriptBook.rateFor` says which designs
  need it. Andrzej asked for this instead of a fire rate cap. Ladders also go below their tables
  (negative rungs) to the absolute minimums Andrzej asked for, so saved designs keep their values.
- `Borrowed`, `Borrowing` - scripts borrowed from other loaded weapons (on-hit, on-fire,
  every-frame, beam), same family only (guns, missiles, beams). Priced at the donor's OP x the
  LunaLib percent x 10 DP. `combat.ScriptHitEffect`, `RapidFire`, `ScriptEveryFrame` (named in every
  blank .wpn) and `ScriptBeamEffect` run them behind `combat.Guard`: a script that throws is
  switched off for that design for the battle and logged. Andrzej expected scripts to be reused
  from existing weapons; the 8 made-up ones stay by his choice.
- `Costing` - design points (DP): 10 x the stats' worth (fitted to the base game's guns:
  Light Autocannon, Tactical Laser and Harpoon copies are worth 40 DP) x the scripts' worth
  multiplier, plus 1 DP per step away from each stat's starting rung and per script level. That
  flat part is what makes every step change the total by at least 1. OP = DP x the LunaLib
  conversion rate (10% default), at most 999: the game's refit screen calls any weapon of 1000 OP
  or more "not installable" (`FighterPickerDialog.UNINSTALLABLE_OP`). DP can go below 1. Price
  follows DP, not OP.
- `Settings` - the LunaLib settings, read on load and when the designer opens, cached so costing
  never calls LunaLib (the checks run without it).
- `SpecWriter` - writes a design onto its weapon, including the description text, the PD hint
  for the point defense script, the script levels (`combat.ScriptBook`) and the tooltip numbers
  the game caches at startup (`WeaponSpreadsheetLoader.o`).
- `combat` - the scripts in battle. Every blank .proj names `ScriptHitEffect` and every blank beam
  .wpn names `ScriptBeamEffect`; they look the weapon up in `ScriptBook` and run `Effects`.
  Beams count a hit every 0.5 s of contact. `Slowdowns` is the engine jammer's timer plugin.
  At most 40 arcs and blasts are drawn per frame; past that `Effects` applies their damage directly.
- `Text` - number formatting (always with a decimal point) and `fontSafe`, which swaps letters
  the game's fonts lack (most Polish ones, curly quotes) for plain ones. A missing glyph would only
  draw as "?", it cannot crash.
- `Looks` - borrows sprites, barrel offsets and sounds from another weapon of the same kind and
  size: the one the player picked (`Design.look`, any loaded weapon) or, by default, a base game
  weapon of the same mount.
- `WeaponDesignerModPlugin.rebuildAll` - every load (and every time the designer opens) writes
  the save's designs and blanks the rest, because weapon definitions are shared by every save
  loaded in one session. Designs on ids past the reserve (0.2.x had 100) move to a free one.
- `Refits` - after an edit, copies that no longer fit their slot go to cargo.
- `ui/DesignerPanel` - the screen. Draws and hit-tests its own controls (see the Gambling Den
  note on tooltip buttons in custom panels). The middle area shows stats, scripts, the look grid
  or the description editor.
- `rules.csv` + `WD_CanDesign` / `WD_OpenDesigner` - the colony option. `Workshop` accepts Orbital
  Works and any building whose downgrade chain leads to it (Vaults of Knowledge's `triheavy` and
  `hegeheavy`), holding a Pristine Nanoforge or Vaults of Knowledge's Tenebrian Nanoforge
  (`aotd_shrouded_nanoforge`).
