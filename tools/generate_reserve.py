"""Writes the reserve of blank weapons that designs are written onto at runtime.

The game cannot register a new weapon after startup, so every design borrows one of these.
Each kind (ballistic shot, laser bolt, beam, missile) needs its own stock: the kind of a
weapon is fixed by its .wpn and .proj files, everything else is rewritten from the save.

Run from the repo root:  python tools/generate_reserve.py
Keep RESERVE in step with Kind.RESERVE in the Java code (the build checks it).
Every blank weapon names the mod's script classes (on-hit for projectiles and missiles, a beam
effect for beams), which run whatever scripts the design written onto it has bought.
"""
import os

RESERVE = 10
HIT_EFFECT = 'weapondesigner.combat.ScriptHitEffect'
BEAM_EFFECT = 'weapondesigner.combat.ScriptBeamEffect'
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEAPONS = os.path.join(ROOT, 'data', 'weapons')
PROJ = os.path.join(WEAPONS, 'proj')
STRINGS = os.path.join(ROOT, 'data', 'strings')

HEADER = ('name,id,tier,rarity,base value,range,damage/second,damage/shot,emp,impact,turn rate,OPs,ammo,'
          'ammo/sec,reload size,type,energy/shot,energy/second,chargeup,chargedown,burst size,burst delay,'
          'min spread,max spread,spread/shot,spread decay/sec,beam speed,proj speed,launch speed,flight time,'
          'proj hitpoints,autofireAccBonus,extraArcForAI,hints,tags,groupTag,tech/manufacturer,'
          'for weapon tooltip>>,primaryRoleStr,speedStr,trackingStr,turnRateStr,accuracyStr,customPrimary,'
          'customPrimaryHL,customAncillary,customAncillaryHL,noDPSInTooltip,number')
COLUMNS = HEADER.split(',')
TAGS = 'restricted, no_drop, no_sell, no_bp_drop, hide_in_codex'
NAME = 'Blank weapon design'

ROWS = {
    'shot': {'range': 700, 'damage/shot': 50, 'impact': 3, 'turn rate': 30, 'type': 'KINETIC',
             'energy/shot': 50, 'chargeup': 0, 'chargedown': 0.5, 'burst size': 1, 'min spread': 0,
             'max spread': 6, 'spread/shot': 2, 'spread decay/sec': 12, 'proj speed': 900},
    'laser': {'range': 500, 'damage/shot': 50, 'impact': 0, 'turn rate': 50, 'type': 'ENERGY',
              'energy/shot': 40, 'chargeup': 0, 'chargedown': 0.33, 'burst size': 1, 'min spread': 0,
              'max spread': 0, 'spread/shot': 0, 'spread decay/sec': 0, 'proj speed': 1000},
    'beam': {'range': 1000, 'damage/second': 75, 'impact': 0, 'turn rate': 20, 'type': 'ENERGY',
             'energy/second': 60, 'chargeup': 0.1, 'chargedown': 0.1, 'beam speed': 2400},
    'missile': {'range': 2400, 'damage/shot': 750, 'impact': 15, 'turn rate': 30, 'ammo': 3,
                'type': 'HIGH_EXPLOSIVE', 'energy/shot': 0, 'chargeup': 0, 'chargedown': 1, 'burst size': 1,
                'min spread': 0, 'max spread': 0, 'spread/shot': 0, 'proj speed': 300, 'launch speed': 100,
                'flight time': 8, 'proj hitpoints': 150, 'hints': 'DO_NOT_AIM'},
}

WPN = {
    'shot': '''{
	"id":"%(id)s",
	"specClass":"projectile",
	"type":"BALLISTIC",
	"size":"SMALL",
	"turretSprite":"graphics/weapons/light_autocannon_turret_base.png",
	"turretGunSprite":"graphics/weapons/light_autocannon_turret_recoil.png",
	"hardpointSprite":"graphics/weapons/light_autocannon_hardpoint_base.png",
	"hardpointGunSprite":"graphics/weapons/light_autocannon_hardpoint_recoil.png",
	"visualRecoil":7.0,
	"renderHints":["RENDER_BARREL_BELOW"],
	"turretOffsets":[10, 0],
	"turretAngleOffsets":[0],
	"hardpointOffsets":[15, 0],
	"hardpointAngleOffsets":[0],
	"barrelMode":"ALTERNATING",
	"animationType":"MUZZLE_FLASH",
	"muzzleFlashSpec":{"length":10.0,
					   "spread":28.0,
					   "particleSizeMin":12.0,
					   "particleSizeRange":14.0,
					   "particleDuration":0.2,
					   "particleCount":18,
					   "particleColor":[255,225,165,175]},
	"projectileSpecId":"%(id)s",
	"fireSoundTwo":"light_autocannon_fire",
}
''',
    'laser': '''{
	"id":"%(id)s",
	"specClass":"projectile",
	"type":"ENERGY",
	"size":"SMALL",
	"turretSprite":"graphics/weapons/light_pulse_laser_turret_base.png",
	"turretGlowSprite":"graphics/weapons/light_pulse_laser_turret_glow.png",
	"hardpointSprite":"graphics/weapons/light_pulse_laser_hardpoint_base.png",
	"hardpointGlowSprite":"graphics/weapons/light_pulse_laser_hardpoint_glow.png",
	"glowColor":[255,100,100,255],
	"turretOffsets":[10, 0],
	"turretAngleOffsets":[0],
	"hardpointOffsets":[15, 0],
	"hardpointAngleOffsets":[0],
	"barrelMode":"ALTERNATING",
	"animationType":"GLOW_AND_FLASH",
	"muzzleFlashSpec":{"length":23.0,
					   "spread":5.0,
					   "particleSizeMin":10.0,
					   "particleSizeRange":12.0,
					   "particleDuration":0.12,
					   "particleCount":12,
					   "particleColor":[255,100,100,255]},
	"autocharge":true,
	"requiresFullCharge":false,
	"projectileSpecId":"%(id)s",
	"fireSoundTwo":"ir_pulse_laser_fire",
}
''',
    'beam': '''{
	"id":"%(id)s",
	"specClass":"beam",
	"type":"ENERGY",
	"size":"SMALL",
	"turretSprite":"graphics/weapons/tactical_laser_turret.png",
	"turretGlowSprite":"graphics/weapons/tactical_laser_turret_glow.png",
	"hardpointSprite":"graphics/weapons/tactical_laser_hardpoint.png",
	"hardpointGlowSprite":"graphics/weapons/tactical_laser_hardpoint_glow.png",
	"turretOffsets":[10, 0],
	"turretAngleOffsets":[0],
	"hardpointOffsets":[15, 0],
	"hardpointAngleOffsets":[0],
	"fringeColor":[0,255,0,225],
	"coreColor":[255,255,255,255],
	"glowColor":[155,255,155,255],
	"width":13.0,
	"textureType":ROUGH,
	"textureScrollSpeed":72.0,
	"pixelsPerTexel":5.0,
	"pierceSet":[PROJECTILE_FF,PROJECTILE_NO_FF,PROJECTILE_FIGHTER,MISSILE_FF,MISSILE_NO_FF],
	"fireSoundOne":"laser_intro",
	"fireSoundTwo":"laser_loop",
	"beamEffect":"%(beam_effect)s",
}
''',
    'missile': '''{
	"id":"%(id)s",
	"specClass":"projectile",
	"type":"MISSILE",
	"size":"SMALL",
	"turretSprite":"graphics/weapons/missile_rack_open_m_3x_turret_base.png",
	"hardpointSprite":"graphics/weapons/missile_rack_open_m_3x_hardpoint_base.png",
	"hardpointOffsets":[15, -6, 15, 6, 15, 0],
	"turretOffsets":[8, -6, 8, 6, 8, 0],
	"hardpointAngleOffsets":[0, 0, 0],
	"turretAngleOffsets":[0, 0, 0],
	"barrelMode":"ALTERNATING",
	"animationType":"SMOKE",
	"interruptibleBurst":false,
	"smokeSpec":{"particleSizeMin":20.0,
				 "particleSizeRange":20.0,
				 "cloudParticleCount":3,
				 "cloudDuration":1.0,
				 "cloudRadius":10.0,
				 "blowbackParticleCount":3,
				 "blowbackDuration":2.0,
				 "blowbackLength":30.0,
				 "blowbackSpread":10.0,
				 "particleColor":[100,100,100,200]},
	"projectileSpecId":"%(id)s",
	"fireSoundTwo":"harpoon_fire",
}
''',
}

PROJ_TEMPLATES = {
    'shot': '''{
	"id":"%(id)s",
	"specClass":"projectile",
	"spawnType":"BALLISTIC",
	"collisionClass":"PROJECTILE_FF",
	"collisionClassByFighter":"PROJECTILE_FIGHTER",
	"length":20.0,
	"hitGlowRadius":24,
	"width":5.5,
	"fadeTime":0.15,
	"fringeColor":[255,225,165,235],
	"coreColor":[255,255,200,200],
	"textureScrollSpeed":64.0,
	"pixelsPerTexel":5.0,
	"bulletSprite":"graphics/missiles/shell_small_yellow.png",
	"onHitEffect":"%(hit_effect)s"
}
''',
    'laser': '''{
	"id":"%(id)s",
	"specClass":"projectile",
	"spawnType":"BALLISTIC_AS_BEAM",
	"collisionClass":"RAY",
	"collisionClassByFighter":"RAY_FIGHTER",
	"length":70.0,
	"width":11.0,
	"fadeTime":0.25,
	"fringeColor":[200,0,0,255],
	"coreColor":[255,200,200,200],
	"textureType":"SMOOTH",
	"textureScrollSpeed":-256.0,
	"pixelsPerTexel":1.0,
	"onHitEffect":"%(hit_effect)s"
}
''',
    'missile': '''{
	"id":"%(id)s",
	"specClass":"missile",
	"missileType":"MISSILE",
	"sprite":"graphics/missiles/missile_harpoon.png",
	"size":[10,22],
	"center":[5,12],
	"collisionRadius":16,
	"collisionClass":"MISSILE_NO_FF",
	"explosionColor":[255,165,100,255],
	"explosionRadius":150,
	"onHitEffect":"%(hit_effect)s",
	"engineSpec":{"turnAcc":210,
				  "turnRate":70,
				  "acc":1000,
				  "dec":500},
	"engineSlots":[{"id":"ES1",
					"loc":[-13, 0],
					"style":"CUSTOM",
					"styleSpec":{
						"mode":"QUAD_STRIP",
						"engineColor":[255,165,100,255],
						"contrailDuration":1,
						"contrailWidthMult":1,
						"contrailWidthAddedFractionAtEnd":2.5,
						"contrailMinSeg":5,
						"contrailMaxSpeedMult":0.5,
						"contrailAngularVelocityMult":0.5,
						"contrailColor":[255,165,100,75],
						"type":"SMOKE"
					},
					"width":7.0,
					"length":40.0,
					"angle":180.0}]
}
''',
}


def csv_field(value):
    text = '' if value is None else str(value)
    if any(ch in text for ch in ',"\n'):
        text = '"' + text.replace('"', '""') + '"'
    return text


def weapon_ids(kind):
    return ['wd_%s_%03d' % (kind, i) for i in range(1, RESERVE + 1)]


def write(path, text):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(text)


def main():
    os.makedirs(PROJ, exist_ok=True)
    os.makedirs(STRINGS, exist_ok=True)
    for name in os.listdir(WEAPONS):
        if name.startswith('wd_') and name.endswith('.wpn'):
            os.remove(os.path.join(WEAPONS, name))
    for name in os.listdir(PROJ):
        if name.startswith('wd_') and name.endswith('.proj'):
            os.remove(os.path.join(PROJ, name))

    rows = [HEADER]
    descriptions = ['id,type,text1,text2,text3,text4,text5,notes']
    for kind in ('shot', 'laser', 'beam', 'missile'):
        for wid in weapon_ids(kind):
            values = dict(ROWS[kind])
            values.update({'name': NAME, 'id': wid, 'tier': 0, 'rarity': 0, 'base value': 100, 'OPs': 1,
                           'tags': TAGS, 'tech/manufacturer': 'Custom', 'primaryRoleStr': 'Custom'})
            rows.append(','.join(csv_field(values.get(col)) for col in COLUMNS))
            descriptions.append('%s,WEAPON,%s,,,,,' % (wid, csv_field('Custom weapon design.')))
            write(os.path.join(WEAPONS, wid + '.wpn'), WPN[kind] % {'id': wid, 'beam_effect': BEAM_EFFECT})
            if kind in PROJ_TEMPLATES:
                write(os.path.join(PROJ, wid + '.proj'), PROJ_TEMPLATES[kind] % {'id': wid, 'hit_effect': HIT_EFFECT})
    write(os.path.join(WEAPONS, 'weapon_data.csv'), '\n'.join(rows) + '\n')
    write(os.path.join(STRINGS, 'descriptions.csv'), '\n'.join(descriptions) + '\n')
    print('wrote %d reserved weapons' % (len(rows) - 1))


if __name__ == '__main__':
    main()
