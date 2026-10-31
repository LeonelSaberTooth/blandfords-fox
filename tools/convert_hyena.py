"""Turns art/hyena.bbmodel into the GeckoLib files for the hyena.

WALK, RUN, REST and SLEEP come from the .bbmodel unchanged; IDLE is made here.
Textures: "hyena texturefull.png" (awake) and "hyena texturefullsleep new.png" (eyes closed).
"""
import base64, json, math, os
import convert_model as cm

SRC = os.path.join(cm.ROOT, 'art', 'hyena.bbmodel')
NAME = 'hyena'
ASSETS = cm.ASSETS
AWAKE, ASLEEP = 'hyena texturefull.png', 'hyena texturefullsleep new.png'


def idle():
    L = 3.2
    s = lambda p, k=1: math.sin(2 * math.pi * p * k)
    return {'loop': True, 'animation_length': L, 'bones': {
        'torso': {'scale': cm.wave(L, 24, lambda p: [1, 1 + 0.012 * (0.5 - 0.5 * math.cos(4 * math.pi * p)), 1])},
        'tail': {'rotation': cm.wave(L, 24, lambda p: [2 * s(p), 6 * s(p), 0])},
        'neck': {'rotation': cm.wave(L, 24, lambda p: [1.2 * s(p, 2), 0, 0])},
        'ear_left': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '1.4': {'vector': [0, 0, 0]},
                                  '1.5': {'vector': [-12, 0, -8]}, '1.65': {'vector': [0, 0, 0]}}},
        'ear_right': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '2.6': {'vector': [0, 0, 0]},
                                   '2.7': {'vector': [-12, 0, 8]}, '2.85': {'vector': [0, 0, 0]}}},
    }}


def main():
    j = json.load(open(SRC))
    for d in ('geo', 'animations', 'textures'):
        os.makedirs(os.path.join(ASSETS, d, 'entity'), exist_ok=True)
    json.dump(cm.build_geo(j, NAME, mouth=False), open(os.path.join(ASSETS, 'geo', 'entity', NAME + '.geo.json'), 'w'), indent=1)
    anims = {a['name'].lower(): cm.from_bbmodel(a, j) for a in j.get('animations', [])}
    for need in ('walk', 'run', 'rest', 'sleep'):
        assert need in anims, f'{need} missing from the .bbmodel'
    anims['idle'] = idle()
    for a in anims.values():
        cm.to_bedrock(a)
    out = {'format_version': '1.8.0', 'animations': {f'animation.{NAME}.{k}': v for k, v in anims.items()}}
    json.dump(out, open(os.path.join(ASSETS, 'animations', 'entity', NAME + '.animation.json'), 'w'), indent=1)
    tex = {t['name']: t for t in j['textures']}
    for name, fn in ((AWAKE, NAME + '.png'), (ASLEEP, NAME + '_sleeping.png')):
        open(os.path.join(ASSETS, 'textures', 'entity', fn), 'wb').write(base64.b64decode(tex[name]['source'].split(',', 1)[1]))
    print('animations:', ', '.join(anims))


if __name__ == '__main__':
    main()
