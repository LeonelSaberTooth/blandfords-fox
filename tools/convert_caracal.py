"""Turns art/caracal.bbmodel into the GeckoLib files for the caracal.

WALK, RUN, SNEAK and SLEEP come from the .bbmodel unchanged; IDLE is made here.
Textures: the model's own texture, plus its closed-eye sleep texture.
"""
import base64, json, math, os
import convert_model as cm

ROOT = cm.ROOT
SRC = os.path.join(ROOT, 'art', 'caracal.bbmodel')
NAME = 'caracal'
ASSETS = cm.ASSETS


def idle():
    L = 3.0
    s = lambda p, k=1: math.sin(2 * math.pi * p * k)
    return {'loop': True, 'animation_length': L, 'bones': {
        'torso': {'scale': cm.wave(L, 24, lambda p: [1, 1 + 0.012 * (0.5 - 0.5 * math.cos(4 * math.pi * p)), 1])},
        'tail': {'rotation': cm.wave(L, 24, lambda p: [3 * s(p), 8 * s(p), 0])},
        'head': {'rotation': cm.wave(L, 24, lambda p: [1.0 * s(p, 2), 0, 0])},
        # a flick of each ear partway through
        'ear_left': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '1.2': {'vector': [0, 0, 0]},
                                  '1.3': {'vector': [-10, 0, -8]}, '1.45': {'vector': [0, 0, 0]}}},
        'ear_right': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '2.3': {'vector': [0, 0, 0]},
                                   '2.4': {'vector': [-10, 0, 8]}, '2.55': {'vector': [0, 0, 0]}}},
    }}


def main():
    j = json.load(open(SRC))
    for d in ('geo', 'animations', 'textures'):
        os.makedirs(os.path.join(ASSETS, d, 'entity'), exist_ok=True)
    json.dump(cm.build_geo(j, NAME, mouth=False), open(os.path.join(ASSETS, 'geo', 'entity', NAME + '.geo.json'), 'w'), indent=1)

    anims = {a['name'].lower(): cm.from_bbmodel(a, j) for a in j.get('animations', [])}
    for need in ('walk', 'run', 'sneak', 'sleep'):
        assert need in anims, f'{need} missing from the .bbmodel'
    anims['idle'] = idle()
    for a in anims.values():
        cm.to_bedrock(a)
    out = {'format_version': '1.8.0', 'animations': {f'animation.{NAME}.{k}': v for k, v in anims.items()}}
    json.dump(out, open(os.path.join(ASSETS, 'animations', 'entity', NAME + '.animation.json'), 'w'), indent=1)

    # the texture the cubes actually use, and its sleeping version
    used = {f.get('texture') for e in j['elements'] for f in e['faces'].values()}
    assert len(used) == 1, used
    tex = j['textures'][used.pop()]
    sleep = next(t for t in j['textures'] if t['name'] == tex['name'].replace('.png', '_sleep.png'))
    for t, fn in ((tex, NAME + '.png'), (sleep, NAME + '_sleeping.png')):
        open(os.path.join(ASSETS, 'textures', 'entity', fn), 'wb').write(base64.b64decode(t['source'].split(',', 1)[1]))
    print('animations:', ', '.join(anims), '| texture', tex['name'])


if __name__ == '__main__':
    main()
