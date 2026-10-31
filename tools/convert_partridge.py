"""Turns art/partridge.bbmodel into the GeckoLib files for the partridge.

The .bbmodel is loose cubes with no bones or animations, so this script rigs it (body,
head, wings, tail, legs) and makes the animations:
  idle  - breathing, a look around, a tail flick
  fly   - a burst of fast wingbeats, then a short glide, legs tucked
  glide - wings held out for the landing, legs coming down
Walking is not keyframed: the legs are driven in code by the distance walked, so the
feet stay planted at any speed (see PartridgeModel).

The model's feet stand 3 px above its origin; everything is lowered 3 px so they sit on
the ground.
"""
import base64, json, math, os
import convert_model as cm

SRC = os.path.join(cm.ROOT, 'art', 'partridge.bbmodel')
NAME = 'partridge'
ASSETS = cm.ASSETS
DROP = 3

# cube uuid prefix -> bone
CUBES = {'f8ed19a8': 'body', '71772ed3': 'head', '568e157d': 'head', 'e2ec7fbd': 'tail',
         '778a0462': 'wing_right', '9b06202e': 'wing_left',
         '0fc89296': 'leg_right', 'c9d90d47': 'leg_right', 'f67d32ea': 'leg_left', '04e2e3f8': 'leg_left'}

# bone: (parent, pivot in Blockbench coords before lowering)
BONES = {
    'partridge': (None, [0, 0, 0]),
    'body': ('partridge', [0, 5, 3]),
    'head': ('body', [0, 10, 0.5]),
    'wing_right': ('body', [-2.5, 10, 1]),
    'wing_left': ('body', [2.5, 10, 1]),
    'tail': ('body', [0, 5, 5]),
    'leg_right': ('partridge', [-1, 5, 3.5]),
    'leg_left': ('partridge', [1, 5, 3.5]),
}


def rig(j):
    for e in j['elements']:
        e['from'][1] -= DROP; e['to'][1] -= DROP; e['origin'][1] -= DROP
    by_bone = {}
    for e in j['elements']:
        by_bone.setdefault(CUBES[e['uuid'][:8]], []).append(e['uuid'])
    assert sum(map(len, by_bone.values())) == len(j['elements'])
    j['groups'] = [{'name': n, 'uuid': n, 'origin': [p[0], p[1] - DROP, p[2]], 'rotation': [0, 0, 0]}
                   for n, (_, p) in BONES.items()]

    def node(n):
        kids = list(by_bone.get(n, [])) + [node(c) for c, (par, _) in BONES.items() if par == n]
        return {'uuid': n, 'children': kids}
    j['outliner'] = [node('partridge')]


S = lambda p, k=1: math.sin(2 * math.pi * p * k)
TUCK = -55          # legs folded back under the body in flight


def idle():
    L = 4.0
    return {'loop': True, 'animation_length': L, 'bones': {
        'body': {'scale': cm.wave(L, 32, lambda p: [1, 1 + 0.015 * (0.5 - 0.5 * math.cos(6 * math.pi * p)), 1])},
        'head': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '1.0': {'vector': [0, 0, 0]},
                              '1.15': {'vector': [0, 28, 0]}, '1.9': {'vector': [0, 28, 0]},
                              '2.05': {'vector': [0, -18, 0]}, '2.6': {'vector': [0, -18, 0]},
                              '2.75': {'vector': [0, 0, 0]}, '3.3': {'vector': [0, 0, 0]},
                              '3.4': {'vector': [16, 0, 0]}, '3.5': {'vector': [0, 0, 0]}, '4.0': {'vector': [0, 0, 0]}}},
        'tail': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '2.3': {'vector': [0, 0, 0]},
                              '2.38': {'vector': [-25, 0, 0]}, '2.5': {'vector': [0, 0, 0]}, '4.0': {'vector': [0, 0, 0]}}},
    }}


def wing_flap(p, side):
    """Fast whirring wingbeats for 60% of the cycle, then wings held out to glide."""
    if p < 0.6:
        beat = 0.5 - 0.5 * math.cos(2 * math.pi * (p / 0.6) * 6)   # 6 beats
        up = 20 + 100 * beat
    else:
        up = 80 + 4 * S((p - 0.6) / 0.4, 2)
    return [0, 0, -side * up]


def fly():
    L = 0.8
    n = 96
    return {'loop': True, 'animation_length': L, 'bones': {
        'wing_right': {'rotation': cm.wave(L, n, lambda p: wing_flap(p, -1))},
        'wing_left': {'rotation': cm.wave(L, n, lambda p: wing_flap(p, 1))},
        'body': {'position': cm.wave(L, 24, lambda p: [0, 0.3 * S(p, 6) if p < 0.6 else 0, 0])},
        'head': {'rotation': {'0.0': {'vector': [-8, 0, 0]}}},
        'tail': {'rotation': {'0.0': {'vector': [-10, 0, 0]}}},
        'leg_right': {'rotation': {'0.0': {'vector': [TUCK, 0, 0]}}, 'position': {'0.0': {'vector': [0, 1, 0]}}},
        'leg_left': {'rotation': {'0.0': {'vector': [TUCK, 0, 0]}}, 'position': {'0.0': {'vector': [0, 1, 0]}}},
    }}


def glide():
    L = 0.6
    return {'loop': True, 'animation_length': L, 'bones': {
        'wing_right': {'rotation': cm.wave(L, 12, lambda p: [0, 0, 78 + 6 * S(p)])},
        'wing_left': {'rotation': cm.wave(L, 12, lambda p: [0, 0, -(78 + 6 * S(p))])},
        'head': {'rotation': {'0.0': {'vector': [-4, 0, 0]}}},
        'tail': {'rotation': {'0.0': {'vector': [-20, 0, 0]}}},
        'leg_right': {'rotation': {'0.0': {'vector': [-15, 0, 0]}}},
        'leg_left': {'rotation': {'0.0': {'vector': [-15, 0, 0]}}},
    }}


def main():
    j = json.load(open(SRC))
    rig(j)
    for d in ('geo', 'animations', 'textures'):
        os.makedirs(os.path.join(ASSETS, d, 'entity'), exist_ok=True)
    geo = cm.build_geo(j, NAME, mouth=False)
    geo['minecraft:geometry'][0]['description'].update(visible_bounds_width=2, visible_bounds_height=1.5)
    json.dump(geo, open(os.path.join(ASSETS, 'geo', 'entity', NAME + '.geo.json'), 'w'), indent=1)
    anims = {'idle': idle(), 'fly': fly(), 'glide': glide()}
    for a in anims.values():
        cm.to_bedrock(a)
    out = {'format_version': '1.8.0', 'animations': {f'animation.{NAME}.{k}': v for k, v in anims.items()}}
    json.dump(out, open(os.path.join(ASSETS, 'animations', 'entity', NAME + '.animation.json'), 'w'), indent=1)
    t = j['textures'][0]
    open(os.path.join(ASSETS, 'textures', 'entity', NAME + '.png'), 'wb').write(base64.b64decode(t['source'].split(',', 1)[1]))
    print('bones:', ', '.join(b['name'] for b in geo['minecraft:geometry'][0]['bones']))


if __name__ == '__main__':
    main()
