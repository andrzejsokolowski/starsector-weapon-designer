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

`./gradlew releaseZip` compiles against the game, runs the checks (`modelChecks`, forbidden API
scan, metadata) and writes `WeaponDesigner.zip` in this folder.

`python tools/generate_reserve.py` rewrites the reserved weapons in `data/weapons` and
`data/strings/descriptions.csv`. Keep its `RESERVE` equal to `Kind.RESERVE`.

## How it works

The game cannot add a weapon after startup, so the mod ships 100 blank weapons per projectile
type (`wd_shot_###`, `wd_laser_###`, `wd_beam_###`, `wd_missile_###`). A design borrows one and
is written onto the game's shared weapon definition at runtime.

- `Design`, `Stat`, `Kind` - the model. Designs are saved as a JSON string in the sector's
  persistent data (`DesignStore`), so saves hold none of this mod's classes.
- `Costing` - weapon OP (the mount cost) and the production price. Fitted to the base game's
  guns: Light Autocannon, Tactical Laser and Harpoon copies cost 4.
- `SpecWriter` - writes a design onto its weapon, including the tooltip numbers the game
  caches at startup (`WeaponSpreadsheetLoader.o`).
- `Looks` - borrows sprites, barrel offsets and sounds from another weapon of the same kind and
  size: the one the player picked (`Design.look`, any loaded weapon) or, by default, a base game
  weapon of the same mount.
- `WeaponDesignerModPlugin.rebuildAll` - every load writes the save's designs and blanks the
  rest, because weapon definitions are shared by every save loaded in one session.
- `Refits` - after an edit, copies that no longer fit their slot go to cargo.
- `ui/DesignerPanel` - the screen. Draws and hit-tests its own controls (see the Gambling Den
  note on tooltip buttons in custom panels).
- `rules.csv` + `WD_CanDesign` / `WD_OpenDesigner` - the colony option. `Workshop` accepts Orbital
  Works and any building whose downgrade chain leads to it (Vaults of Knowledge's `triheavy` and
  `hegeheavy`), holding a Pristine Nanoforge.
