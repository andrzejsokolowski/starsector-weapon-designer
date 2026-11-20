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
  LunaLib percent x 10 DP. Since 1.2.1 `ScriptBook` keeps only donor ids; on-fire scripts are
  made per weapon and on-hit scripts per design, fresh each battle (engine custom data), and
  `init(weapon)` / `reset()` are passed on to borrowed every-frame and beam scripts. `combat.ScriptHitEffect`, `RapidFire`, `ScriptEveryFrame` (named in every
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
- `MissileCopy` (1.1.0) - a missile design with a *picked* look fires a copy of that launcher's
  missile (type such as MIRV or mine, behavior settings, sprite, engine slots). It builds a new
  missile spec the way `WeaponSpecLoader` does (the hull's own `clone()` NPEs on missiles: they
  have no shield or reactor spec) with the blank's id as hull id (`Missile.getProjectileSpecId()`
  reads it, so `ScriptBook` lookups keep working), its own engine, damage, blast and behavior
  params, and hands it over with `setProjectileSpec` through a method handle (the projectile
  weapon class is named `while` in 0.98.5a). Automatic looks and blanks keep the blank's own
  missile, restored from `ORIGINALS`. `SpecWriter.writeCopiedDamage` writes the design's damage
  into the copy's blast and, for MIRV, splits it over `numShots`. Render hints can only be added,
  not removed, so only RENDER_LOADED_MISSILES is copied (found as the Harpoon's only hint).
  1.2.1 (WD-17): a MIRV copy splits into the design's own warhead, `<blank id>__<warhead id>`,
  registered once per session in the projectile registry `com.fs.starfarer.loading.Q`
  (`o00000(String, Object)` registers and throws if the id exists; `Õ00000(String)` looks
  up, a Latin-1 letter Java accepts). It is the donor warhead's `OoOo.clone()` with
  ScriptHitEffect as on-hit and no on-fire (drops DEM payloads like the Hydra's).
  `ScriptHitEffect.designOf` finds the design of a warhead by the weapon that fired it. A
  launcher whose warhead cannot be copied is refused (plain missile), and a failed copy puts the
  plain missile back. Uses scrambled class names (`h`, `g`, `OOO0`, `Y`, `D`):
  callers catch everything, so a game update only turns copying off. A throwaway check that
  runs it on the real classes needs the game's JRE with `-noverify` (the classes have illegal
  field names).
- `Hint` (1.1.0) - the Hints tab: AI hints the player switches on, free. `Hint.effective` is the
  whole AI hint set `SpecWriter` writes (it clears the set first): PD from the Point defense
  script, DO_NOT_AIM by itself for turning missiles, then the chosen ones that apply (missile-only
  hints for missiles, ammo hints for missiles and guns with limited ammo, "ships first" with PD).
- Guns (1.1.0) reuse `Stat.SALVO` as shots per burst and `Stat.AMMO`/`Stat.REGEN` as ammo and
  ammo per second. Gun ammo's top rung is unlimited (infinity) and is the default; REGEN is not
  steppable or charged while ammo is unlimited (`Design.inUse`). A burst keeps the average rate
  (`Costing.burstSize`/`burstDelay`, 0.1 s apart) and is off above 30 shots a second. Limited ammo
  scales a gun's worth by `ammoUptime^0.7` (ammo plus a minute of regain against a minute of fire).
- `WeaponDesignerModPlugin.rebuildAll` - every load (and every time the designer opens) writes
  the save's designs and blanks the rest, because weapon definitions are shared by every save
  loaded in one session. Designs on ids past the reserve (0.2.x had 100) move to a free one.
- `Refits` - after an edit, copies that no longer fit their slot go to cargo.
- `ui/DesignerPanel` - the screen. Draws and hit-tests its own controls (see the Gambling Den
  note on tooltip buttons in custom panels). The middle area shows stats, scripts, the look grid
  or the description editor.
- `ui/DesignerPage` (1.2.0, WD-16) - the designer is the "Weapon designer" tab on OddiszCommons'
  Manage (Y) screen (`Manage.addPage` in `onApplicationLoad`), no longer a colony menu option
  (rules.csv and the rule commands are gone). The page builds a fresh `DesignerPanel` centered in
  the tab. Since 1.3.0 nothing unlocks it: Andrzej asked for it "available immediately under
  manage (Y)" after a player without colonies (Nomadic Flotilla) was shut out, so the old colony
  check (`Workshop`: Orbital Works or an upgrade with a Pristine or Tenebrian Nanoforge) is gone.
  Do not add an unlock or a compatibility layer back without asking. The tab has no Leave button: the Manage screen's Close and Escape close it (Escape is only
  consumed to close a grid or the description editor). 1.2.1: `DesignerPage` keeps the draft,
  selection, tab and scroll (`DesignerPanel.View`) between openings, for the same campaign only
  (weak reference to the sector), and keeps the designer on screen when the tab is smaller than
  1100x680 (intel fallback dialog, small screens). Input event x/y are already in interface
  units (divide only raw `Mouse` positions by the screen scale); held keys come as repeat events. OddiszCommons is a dependency by id and
  name only; `OddiszCommonsVersion` stops the game at start below 1.0.2 (intel fallback). The
  build compiles against `../OddiszCommons/jars/OddiszCommons.jar`.
