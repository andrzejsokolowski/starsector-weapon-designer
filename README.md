# Weapon Designer

A Starsector 0.98a mod. Design your own weapons and build them through custom production.
Needs LunaLib.

## Where

Any colony of yours whose Orbital Works has a Pristine Nanoforge installed gets a new option on
its menu: **Weapon designer**. Buildings upgraded from Orbital Works count too, such as the
Orbital Skunkworks Facility and Orbital Fleetwork Facility from AoTD - Vaults of Knowledge, and
so does Vaults of Knowledge's Tenebrian Nanoforge in place of the Pristine one.

## Designing a weapon

Pick:

- **Size**: small, medium or large
- **Mount**: ballistic, energy or missile
- **Damage**: kinetic, high explosive, energy or fragmentation
- **Projectile**: ballistic shots, laser bolts, a beam, or missiles

Then buy the stats with **design points**:

- Ballistic and laser: damage per shot, shots per second, range, flux per damage, shot speed,
  spread, turn rate, EMP
- Beam: damage per second, range, flux per damage, turn rate, EMP
- Missile: damage per missile, missiles per salvo, seconds between salvos, missiles carried,
  missiles regained per minute, range, missile speed, missile turn rate, launcher turn rate, EMP

Stats have no upper limit. Only spread stops, at perfect accuracy. Each step's button shows how
many design points it adds or gives back. Shift-click takes 10 steps.

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

## Weapon OP and price

A share of a design's design points becomes its ordnance point cost: 10% by default, so 100
design points make a 10 OP weapon. A design that copies a base game gun costs about what that
gun costs. The production price follows the design points.

LunaLib settings:

- **Design points to OP conversion rate**: the share above, 1% to 100%
- **Production cost**: price of a copy, in percent of normal
- **Weapon OP limit**: most OP a design may cost, 0 for no limit

## Look and description

**Change look** picks the design's sprite from every loaded weapon, base game or modded, of the
same projectile type and size. The design takes that weapon's barrels and fire sounds too.
**Automatic** picks a base game weapon of the same size and mount.

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
