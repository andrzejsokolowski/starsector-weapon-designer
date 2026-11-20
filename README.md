# Weapon Designer

A Starsector 0.98.5a mod. Design your own weapons and build them through custom production.
Needs LunaLib and OddiszCommons.

## Where

The **Weapon designer** tab on the Manage (Y) screen (from OddiszCommons) opens the designer
anywhere in the campaign, as long as one of your colonies has Orbital Works with a Pristine
Nanoforge installed. Until then the tab says what is missing. Buildings upgraded from Orbital
Works count too, such as the Orbital Skunkworks Facility and Orbital Fleetwork Facility from
AoTD - Vaults of Knowledge, and so does Vaults of Knowledge's Tenebrian Nanoforge in place of
the Pristine one.

## Designing a weapon

Pick:

- **Size**: small, medium or large
- **Mount**: ballistic, energy or missile
- **Damage**: kinetic, high explosive, energy or fragmentation
- **Projectile**: ballistic shots, laser bolts, a beam, or missiles

Then buy the stats with **design points**:

- Ballistic and laser: damage per shot, shots per second, shots per burst, range, flux per
  damage, shot speed, spread, turn rate, EMP, ammo, ammo regained per second
- Beam: damage per second, range, flux per damage, turn rate, EMP
- Missile: damage per missile, missiles per salvo, seconds between salvos, missiles carried,
  missiles regained per minute, range, missile speed, missile turn rate, launcher turn rate, EMP

Stats have no upper limit. Only spread stops, at perfect accuracy. They go down a long way too,
to the absolute minimums: 1 damage, 10 range, 10 shot speed, 1 degree a second of turn rate, 360
degrees of spread. Each step's button shows how many design points it adds or gives back; a
design worse than the starting point everywhere can drop below 1 design point. Shift-click takes
10 steps.

Guns fire single shots and have unlimited ammo until you change that. **Shots per burst** fires
the shots in quick bursts, 0.1 seconds apart, with a pause after each; shots per second stays the
average, so the damage per second does not change. Lowering **Ammo** from unlimited makes the gun
cheaper; **Ammo regained per second** brings spent ammo back and costs design points.

The game fires a weapon at most once per frame, so a design faster than 30 shots a second fires
30 real shots and each one brings extra projectiles along its path to make up the rest. Past 6
projectiles per real shot (2 for missiles), the rest of the rate makes every projectile hit
harder instead, so the battle never fills with more bullets than the game can handle. Extra shots
cost flux, and ammo for missiles, like real ones.

## Scripts

The Scripts page adds effects to the weapon, bought in levels, most with no upper limit:

- **Arc discharge**: hull hits throw lightning arcs at the target
- **Shield breach**: shield hits throw arcs that pass through the shield
- **Detonation**: every hit explodes and damages everything nearby except allies
- **Armor breaker**: hull hits deal extra high explosive damage
- **Shield overload**: shield hits push extra hard flux into the target
- **Leech**: hull damage dealt repairs the firing ship
- **Engine jammer**: hull hits slow the target for 3 seconds, up to 99% slower at level 28
- **Point defense**: the AI fires the weapon at missiles and fighters first

Beams count one hit for every half second they stay on a target. At most 40 arcs and blasts are
drawn per frame in a battle; past that their damage still lands, without the visuals.

## Borrowed scripts

The Borrowed tab takes scripts from any loaded weapon, base game or modded, that fires the same
way (guns, missiles or beams): its on-hit, on-fire, every-frame or beam effect. Pick a weapon in
the grid, then the script. Up to 9 per design. A borrowed script costs half the OP of the weapon it
comes from, in design points (LunaLib setting). Borrowed scripts run exactly as on their own
weapon; one that does not work on a design (some expect their own missile, animation or sprite)
switches itself off for that battle and is noted in starsector.log instead of crashing the game.

## Hints

The Hints tab tells ship AI how to use the weapon, like the hints column of the game's own
weapons. Hints are free and change no stats. Point at one to read what it does.

- All weapons: point defense only, ships first (needs the Point defense script), anti-fighter,
  strike, frigates too, dangerous, fire when wasteful, hold vs shields, aim first, autofire only
- Missiles: fire off target (on by itself for missiles that turn), fire 10 degrees off, engine
  seeker, no weaving, spread hits
- Missiles and guns with limited ammo: fire freely, save last shots (1 to 5), save for armor

## Weapon OP and price

A share of a design's design points becomes its ordnance point cost: 10% by default, so 100
design points make a 10 OP weapon. A design that copies a base game gun costs about what that
gun costs. The production price follows the design points.

The game refuses to mount any weapon of 1,000 OP or more, so a design worth more costs 999 OP.
Lower the conversion rate to make big designs cheaper to mount.

LunaLib settings:

- **Design points to OP conversion rate**: the share above, 1% to 100%
- **Production cost**: price of a copy, in percent of normal
- **Weapon OP limit**: most OP a design may cost, 0 for no limit
- **Borrowed script cost**: price of a borrowed script, in percent of its weapon's OP

## Look and description

**Change look** picks the design's sprite from every loaded weapon, base game or modded, of the
same projectile type and size. The design takes that weapon's barrels and fire sounds too.
**Automatic** picks a base game weapon of the same size and mount.

A missile design whose look you picked also fires that launcher's own missile: its sprite, trail
and the way it flies, such as the Pilum's second stage, the Hurricane's warheads or a mine. The
picker names what the missile does. The design's own numbers still apply: warheads share the
missile's damage, and blasts deal up to it. A rocket look on a design with a missile turn rate
still turns. Warheads a missile splits into carry the design's scripts; the launcher's own
warhead extras, like the Hydra's drone payload, do not come along. Launchers that show their
loaded missiles show them on the design too.

**Edit description** sets the text in the weapon's tooltip.

## Building and editing

A saved design joins your custom production at its listed price.

Designs can be edited later. Every copy already built changes with it. The projectile type
is fixed once a design is saved. Copies that no longer fit their slot after an edit go to
the cargo of the fleet or storage they were in.

## Limits

Up to 10 designs of each projectile type (40 in all) per campaign. The game cannot add weapons
after it starts, so the mod ships that many blank weapons for designs to use.

## Saves

Designs are stored in the save. Removing the mod from a campaign that has designed weapons
breaks that save, like removing any weapon mod.
