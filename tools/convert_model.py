"""Turns art/blandfords_fox.bbmodel into the GeckoLib files the mod loads.

Writes, under src/main/resources/assets/blandfordsfox/:
  geo/entity/blandfords_fox.geo.json
  animations/entity/blandfords_fox.animation.json
  textures/entity/blandfords_fox.png

WALK, RUN and SLEEP come from the .bbmodel unchanged. IDLE, SIT, CROUCH and
POUNCE are posed here, for the vanilla fox states the model had no animation for.
Values follow Blockbench's own convention (what you'd type in the keyframe panel).
"""
import base64, json, math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'art', 'blandfords_fox.bbmodel')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'blandfordsfox')
NAME = 'blandfords_fox'

# Where the fox holds items in its mouth (Blockbench coordinates, inside `head`).
MOUTH = [0, 6.2, -6.8]


def num(v):
    return float(v) if not isinstance(v, str) else float(v.strip() or 0)


def r(x):
    x = round(x, 4)
    return 0 if x == 0 else x


# ---------------------------------------------------------------- geometry
def build_geo(j):
    groups = {g['uuid']: g for g in j.get('groups', [])}
    elems = {e['uuid']: e for e in j['elements']}
    bones = []

    def bone(node, parent):
        g = groups.get(node['uuid'], node)
        o = g.get('origin', [0, 0, 0]); rot = g.get('rotation', [0, 0, 0])
        b = {'name': g['name'], 'pivot': [r(-o[0]), r(o[1]), r(o[2])]}
        if parent: b['parent'] = parent
        if any(rot): b['rotation'] = [r(-rot[0]), r(-rot[1]), r(rot[2])]
        cubes = []
        for c in node.get('children', []):
            if isinstance(c, str):
                e = elems[c]
                if e.get('export') is False: continue
                f, t = e['from'], e['to']
                cube = {'origin': [r(-t[0]), r(f[1]), r(f[2])],
                        'size': [r(t[0] - f[0]), r(t[1] - f[1]), r(t[2] - f[2])],
                        'uv': e.get('uv_offset') or [0, 0]}
                if e.get('inflate'): cube['inflate'] = e['inflate']
                if e.get('mirror_uv'): cube['mirror'] = True
                er = e.get('rotation') or [0, 0, 0]
                if any(er):
                    eo = e['origin']
                    cube['pivot'] = [r(-eo[0]), r(eo[1]), r(eo[2])]
                    cube['rotation'] = [r(-er[0]), r(-er[1]), r(er[2])]
                cubes.append(cube)
        if cubes: b['cubes'] = cubes
        bones.append(b)
        for c in node.get('children', []):
            if isinstance(c, dict): bone(c, g['name'])
        if g['name'] == 'head':
            bones.append({'name': 'mouth_item', 'parent': 'head',
                          'pivot': [r(-MOUTH[0]), MOUTH[1], MOUTH[2]]})

    for n in j['outliner']:
        if isinstance(n, dict): bone(n, None)
    names = [b['name'] for b in bones]
    assert 'head' in names and 'mouth_item' in names, names
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.' + NAME,
                        'texture_width': j['resolution']['width'], 'texture_height': j['resolution']['height'],
                        'visible_bounds_width': 3, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, 0.5, 0]},
        'bones': bones}]}


# -------------------------------------------------------------- animations
def from_bbmodel(a, j):
    names = {g['uuid']: g['name'] for g in j.get('groups', [])}
    bones = {}
    for uid, an in a['animators'].items():
        bn = an.get('name') or names[uid]
        for k in an['keyframes']:
            if k['channel'] not in ('rotation', 'position', 'scale'): continue
            d = k['data_points'][0]
            v = [r(num(d[c])) for c in 'xyz']
            entry = {'vector': v}
            if k.get('interpolation') == 'catmullrom': entry['lerp_mode'] = 'catmullrom'
            bones.setdefault(bn, {}).setdefault(k['channel'], {})[f"{r(k['time'])}"] = entry
    for ch in bones.values():                       # keyframes in time order
        for c in ch: ch[c] = dict(sorted(ch[c].items(), key=lambda kv: float(kv[0])))
    out = {'animation_length': r(num(a['length'])), 'bones': bones}
    out['loop'] = {'loop': True, 'once': False, 'hold': 'hold_on_last_frame'}.get(a.get('loop'), True)
    return out


def static(pose, length=1.0, loop=True):
    """A held pose: {bone: {channel: [x,y,z]}}."""
    return {'loop': loop, 'animation_length': length,
            'bones': {b: {c: {'0.0': {'vector': v}} for c, v in ch.items()} for b, ch in pose.items()}}


def wave(length, n, fn):
    """Sampled loop keyframes for a channel: fn(phase 0..1) -> [x,y,z]."""
    return {f"{r(length * i / n)}": {'vector': [r(x) for x in fn(i / n)], 'lerp_mode': 'catmullrom'} for i in range(n + 1)}


def made_here():
    s = lambda p, k=1: math.sin(2 * math.pi * p * k)
    L = 3.0
    breathe = wave(L, 24, lambda p: [1, 1 + 0.012 * (0.5 - 0.5 * math.cos(2 * math.pi * p * 2)), 1])

    idle = {'loop': True, 'animation_length': L, 'bones': {
        'torso': {'scale': breathe},
        'tail': {'rotation': wave(L, 24, lambda p: [1.5 * s(p), 5 * s(p), 0])},
        'head': {'rotation': wave(L, 24, lambda p: [1.2 * s(p, 2), 0, 0])},
        # a quick flick of the left ear partway through
        'ear_left': {'rotation': {'0.0': {'vector': [0, 0, 0]}, '1.6': {'vector': [0, 0, 0]},
                                  '1.7': {'vector': [12, 0, -10]}, '1.85': {'vector': [0, 0, 0]}}},
    }}

    # SIT: body tips up 30 deg about its pivot and drops 1.9 so the front paws
    # stay on the ground; the haunches rest on the floor, back legs fold forward.
    sit = static({
        'body': {'position': [0, -1.9, 0], 'rotation': [30, 0, 0]},
        'left_arm': {'rotation': [-30, 0, 0]}, 'right_arm': {'rotation': [-30, 0, 0]},
        'left_leg': {'rotation': [68, 0, 0]}, 'right_leg': {'rotation': [68, 0, 0]},
        'head': {'rotation': [-22, 0, 0]},
        'tail': {'position': [0, 1.75, 0], 'rotation': [-30, 18, 0]},
    })
    sit['bones']['torso'] = {'scale': breathe}; sit['animation_length'] = L

    # CROUCH (stalking prey): body 1 lower, legs angled out so paws stay planted.
    a = 48
    crouch = static({
        'body': {'position': [0, -1.0, 0]},
        'left_arm': {'rotation': [a, 0, 0]}, 'right_arm': {'rotation': [a, 0, 0]},
        'left_leg': {'rotation': [-a, 0, 0]}, 'right_leg': {'rotation': [-a, 0, 0]},
        'head': {'position': [0, -0.3, -0.5], 'rotation': [-12, 0, 0]},
        'ear_left': {'rotation': [-15, 0, 0]}, 'ear_right': {'rotation': [-15, 0, 0]},
        'tail': {'rotation': [5, 0, 0]},
    })

    # POUNCE: stretched out in the air; the renderer tilts the whole fox along its jump.
    pounce = static({
        'left_arm': {'rotation': [65, 0, 0]}, 'right_arm': {'rotation': [65, 0, 0]},
        'left_leg': {'rotation': [-65, 0, 0]}, 'right_leg': {'rotation': [-65, 0, 0]},
        'head': {'rotation': [8, 0, 0]},
        'ear_left': {'rotation': [25, 0, 0]}, 'ear_right': {'rotation': [25, 0, 0]},
        'tail': {'rotation': [-15, 0, 0]},
    }, length=0.2, loop='hold_on_last_frame')
    return {'idle': idle, 'sit': sit, 'crouch': crouch, 'pounce': pounce}


def main():
    j = json.load(open(SRC))
    os.makedirs(os.path.join(ASSETS, 'geo', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'animations', 'entity'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity'), exist_ok=True)

    json.dump(build_geo(j), open(os.path.join(ASSETS, 'geo', 'entity', NAME + '.geo.json'), 'w'), indent=1)

    anims = {}
    for a in j.get('animations', []):
        anims[a['name'].lower()] = from_bbmodel(a, j)
    for need in ('walk', 'run', 'sleep'):
        assert need in anims, f'{need} missing from the .bbmodel'
    anims.update(made_here())
    out = {'format_version': '1.8.0',
           'animations': {f'animation.{NAME}.{k}': v for k, v in anims.items()}}
    json.dump(out, open(os.path.join(ASSETS, 'animations', 'entity', NAME + '.animation.json'), 'w'), indent=1)

    src = j['textures'][0]['source']
    open(os.path.join(ASSETS, 'textures', 'entity', NAME + '.png'), 'wb').write(base64.b64decode(src.split(',', 1)[1]))
    print('animations:', ', '.join(anims))


if __name__ == '__main__':
    main()
