"""Builds the Middle East resource pack (resourcepack/MiddleEast) and its zip.

1. Birch log variation (vanilla, no mods needed): the bark slid sideways by a random 0-15 px
   on each block. Each side face is two quads carrying the two halves of the texture,
   wrapped round, using the game's own birch textures (so texture packs still apply).

2. Hares (needs Entity Model Features + Entity Texture Features): rabbits that spawn in
   desert / badlands / savanna biomes become desert hares, in snowy taiga / snowy plains /
   ice spikes white hares, from art/hare.bbmodel; everywhere else they stay vanilla.
   - optifine/cem/rabbit.properties picks the model by spawn biome (rabbit2 / rabbit3.jem).
   - The models are built in the game's own model space (x mirrored, y down from 24, as
     Blockbench's Java export does), hung under the vanilla rabbit's body part, whose pose
     is zeroed by the animation, and scaled 1/0.6 to undo the vanilla rabbit's 0.6 shrink.
   - Animations come straight from the model's keyframes: RUN is driven by the distance
     walked (one 0.365 s stride covers 2.0 blocks at the animation's own speed, so the
     paws stay planted at any speed), IDLE by time (8 s loop); they blend by walking speed.
"""
import json, math, os, shutil, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'art', 'hare.bbmodel')
TEX = os.path.join(ROOT, 'art', 'hare_textures')
OUT = os.path.join(ROOT, 'resourcepack', 'MiddleEast')
A = os.path.join(OUT, 'assets', 'minecraft')
CEM = os.path.join(A, 'optifine', 'cem')

DESERT_BIOMES = ['desert', 'badlands', 'eroded_badlands', 'wooded_badlands', 'savanna', 'savanna_plateau', 'windswept_savanna']
SNOW_BIOMES = ['snowy_taiga', 'snowy_plains', 'ice_spikes']
VANILLA_PARTS = ['body', 'left_foot', 'right_foot', 'left_thigh', 'right_thigh', 'left_arm', 'right_arm',
                 'head', 'right_ear', 'left_ear', 'tail', 'nose']
RUN_BLOCKS_PER_STRIDE = 5.5 * 0.365     # the run's ground speed (measured) x its length
LIMB_SWING_PER_BLOCK = 4.0              # vanilla: the limb counter goes up 4 per block walked
VANILLA_SHRINK = 0.6                    # vanilla draws adult rabbits at 0.6 size
RUN_FRAMES, IDLE_FRAMES = 30, 160


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1 if path.endswith('.jem') else 2)


def r(x, n=4):
    v = round(x, n)
    return 0.0 if v == 0 else v


# ------------------------------------------------------------------ birch

def birch():
    def u_auto(face, c):            # vanilla's default u for a point on a side face
        return {'north': 16 - c, 'south': c, 'west': c, 'east': 16 - c}[face]

    def plane(face, a, b, k):
        u1, u2 = sorted((u_auto(face, a), u_auto(face, b)))
        if u1 + k >= 16: u1, u2 = u1 - 16, u2 - 16
        fr = {'north': [a, 0, 0], 'south': [a, 0, 16], 'west': [0, 0, a], 'east': [16, 0, a]}[face]
        to = {'north': [b, 16, 0], 'south': [b, 16, 16], 'west': [0, 16, b], 'east': [16, 16, b]}[face]
        return {'from': fr, 'to': to, 'faces': {face: {'uv': [u1 + k, 0, u2 + k, 16], 'texture': '#side', 'cullface': face}}}

    variants = []
    for k in range(16):
        elems = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            'down': {'texture': '#end', 'cullface': 'down'}, 'up': {'texture': '#end', 'cullface': 'up'}}}]
        for face in ('north', 'south', 'west', 'east'):
            if k == 0:
                elems.append(plane(face, 0, 16, 0))
                continue
            cut = {'north': k, 'south': 16 - k, 'west': 16 - k, 'east': k}[face]
            elems.append(plane(face, 0, cut, k))
            elems.append(plane(face, cut, 16, k))
        name = f'birch_log_slid_{k}'
        put(os.path.join(A, 'models', 'block', name + '.json'), {
            'parent': 'minecraft:block/block',
            'textures': {'particle': 'minecraft:block/birch_log', 'side': 'minecraft:block/birch_log', 'end': 'minecraft:block/birch_log_top'},
            'elements': elems})
        variants.append(f'minecraft:block/{name}')
    put(os.path.join(A, 'blockstates', 'birch_log.json'), {'variants': {
        'axis=y': [{'model': m} for m in variants],
        'axis=z': [{'model': m, 'x': 90} for m in variants],
        'axis=x': [{'model': m, 'x': 90, 'y': 90} for m in variants]}})


# ------------------------------------------------------------------ hare model

def java_point(p):
    """Blockbench (Java entity format) -> game model space: x mirrored, y down from 24."""
    return [-p[0], 24 - p[1], p[2]]


def java_rot(rot):
    return [-rot[0], -rot[1], rot[2]]


def build_bones(j, coat):
    """The bone tree in game model space, keeping only this coat's head and ear cubes."""
    els = {e['uuid']: e for e in j['elements']}
    groups = {g['uuid']: g for g in j['groups']}
    other = 'white' if coat == 'desert' else 'desert'

    def box(e, pivot):
        lo = java_point([e['to'][0], e['to'][1], e['from'][2]])        # the min corner in game space
        size = [e['to'][i] - e['from'][i] for i in range(3)]
        return {'coordinates': [r(lo[0] - pivot[0]), r(lo[1] - pivot[1]), r(lo[2] - pivot[2]), r(size[0]), r(size[1]), r(size[2])],
                'textureOffset': e.get('uv_offset') or [0, 0]}

    def bone(node, parent_pivot):
        g = groups[node['uuid']]
        pivot = java_point(g['origin'])
        b = {'id': 'h_' + g['name'], 'translate': [r(pivot[i] - parent_pivot[i]) for i in range(3)]}
        rot = java_rot(g.get('rotation', [0, 0, 0]))
        if any(rot): b['rotate'] = [r(x) for x in rot]
        boxes, subs = [], []
        for c in node['children']:
            if isinstance(c, dict):
                subs.append(bone(c, pivot))
                continue
            e = els[c]
            if e['name'].endswith('_' + other): continue
            if e.get('rotation') and any(e['rotation']):
                # a cube turned on its own: give it a bone of its own at its rotation point
                cp = java_point(e['origin'])
                subs.append({'id': 'h_' + e['name'] + '_turn', 'translate': [r(cp[i] - pivot[i]) for i in range(3)],
                             'rotate': [r(x) for x in java_rot(e['rotation'])], 'boxes': [box(e, cp)]})
            else:
                boxes.append(box(e, pivot))
        if boxes: b['boxes'] = boxes
        if subs: b['submodels'] = subs
        return b

    return bone(j['outliner'][0], [0, 24, 0])


def rest_pose(bones):
    """id -> (translate, rotate in radians) for every bone."""
    out = {}

    def walk(b):
        out[b['id']] = (b['translate'], [math.radians(x) for x in b.get('rotate', [0, 0, 0])])
        for s in b.get('submodels', []): walk(s)
    walk(bones)
    return out


def sample(anim, name_by_uuid, frames):
    """bone name -> channel -> list of frames of [x, y, z] (Blockbench values), looped over the animation."""
    L = anim['length']
    out = {}
    for u, an in anim['animators'].items():
        name = an.get('name') or name_by_uuid.get(u)
        for ch in ('rotation', 'position', 'scale'):
            ks = sorted([k for k in an['keyframes'] if k['channel'] == ch], key=lambda k: k['time'])
            if not ks: continue
            pts = [(k['time'], [float(k['data_points'][0][a]) for a in 'xyz']) for k in ks]

            def at(t):
                if t <= pts[0][0]: return pts[0][1]
                for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
                    if t0 <= t <= t1:
                        f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                        return [v0[i] + (v1[i] - v0[i]) * f for i in range(3)]
                return pts[-1][1]
            out.setdefault(name, {})[ch] = [at(L * i / frames) for i in range(frames)]
    return out


def animations(j, bones):
    names = {g['uuid']: g['name'] for g in j['groups']}
    anims = {a['name'].upper(): a for a in j['animations']}
    run = sample(anims['RUN'], names, RUN_FRAMES)
    idle = sample(anims['IDLE'], names, IDLE_FRAMES)
    rest = rest_pose(bones)
    per_stride = RUN_BLOCKS_PER_STRIDE * LIMB_SWING_PER_BLOCK
    a = {
        # the vanilla body part carries the hare: put it at the model origin, unturned
        'body.tx': '0', 'body.ty': '0', 'body.tz': '0', 'body.rx': '0', 'body.ry': '0', 'body.rz': '0',
        # blend: 0 standing (idle), 1 moving (run)
        'var.w': 'clamp(limb_speed * 6, 0, 1)',
        'var.ti': f'age * {IDLE_FRAMES} / 160',                              # idle: 8 s (160 ticks) loop
        'var.tr': f'limb_swing * {RUN_FRAMES} / {r(per_stride, 5)}',          # run: one stride per 2 blocks walked
    }

    def frames_expr(vals, var):
        if max(vals) - min(vals) < 1e-5: return str(r(vals[0], 5))
        return f'keyframeloop({var}, ' + ', '.join(str(r(v, 5)) for v in vals) + ')'

    for name in sorted(set(run) | set(idle)):
        bid = 'h_' + name
        if bid not in rest: continue
        t0, r0 = rest[bid]
        for ch, comps in (('rotation', ('rx', 'ry', 'rz')), ('position', ('tx', 'ty', 'tz')), ('scale', ('sx', 'sy', 'sz'))):
            iv, rv = idle.get(name, {}).get(ch), run.get(name, {}).get(ch)
            if iv is None and rv is None: continue
            base = [1, 1, 1] if ch == 'scale' else [0, 0, 0]
            iv = iv or [base] ; rv = rv or [base]
            for axis, comp in enumerate(comps):
                if ch == 'rotation':     # Blockbench degrees -> game radians (x and y turn the other way)
                    sign = -1 if axis < 2 else 1
                    conv = lambda v: sign * math.radians(v)
                    restv = r0[axis]
                elif ch == 'position':   # pixels, x and y the other way
                    sign = -1 if axis < 2 else 1
                    conv = lambda v: sign * v
                    restv = t0[axis]
                else:
                    conv = lambda v: v
                    restv = 0
                ivals = [conv(v[axis]) for v in iv]
                rvals = [conv(v[axis]) for v in rv]
                if max(ivals + rvals) - min(ivals + rvals) < 1e-5 and abs(ivals[0] - (1 if ch == 'scale' else 0)) < 1e-5:
                    continue
                expr = f'lerp(var.w, {frames_expr(ivals, "var.ti")}, {frames_expr(rvals, "var.tr")})'
                a[f'{bid}.{comp}'] = expr if ch == 'scale' else f'{r(restv, 5)} + {expr}'
    # the head follows where the rabbit looks
    hx, hy = a.get('h_head.rx', str(r(rest['h_head'][1][0], 5))), a.get('h_head.ry', str(r(rest['h_head'][1][1], 5)))
    a['h_head.rx'] = f'{hx} + torad(head_pitch)'
    a['h_head.ry'] = f'{hy} + torad(head_yaw)'
    return a


def hare(j):
    os.makedirs(CEM, exist_ok=True)
    for coat, index in (('desert', 2), ('white', 3)):
        shutil.copy(os.path.join(TEX, f'hare_{coat}.png'), os.path.join(CEM, f'hare_{coat}.png'))
        bones = build_bones(j, coat)
        models = [{'part': p, 'id': f'vanilla_{p}', 'attach': False} for p in VANILLA_PARTS if p != 'body']
        models.append({'part': 'body', 'id': 'hare', 'attach': False, 'translate': [0, 24, 0],
                       'scale': r(1 / VANILLA_SHRINK, 5), 'submodels': [bones],
                       'animations': [animations(j, bones)]})
        put(os.path.join(CEM, f'rabbit{index}.jem'), {
            'credit': 'Middle East pack',
            'texture': f'optifine/cem/hare_{coat}.png', 'textureSize': [64, 64], 'shadowSize': 0.45,
            'models': models})
    with open(os.path.join(CEM, 'rabbit.properties'), 'w') as f:
        f.write('# Hares: the model (and its texture) follows the biome the rabbit spawned in.\n'
                '# Anywhere else (rule 1 / no rule) the rabbit stays vanilla.\n'
                f'models.1=2\nbiomes.1={" ".join(DESERT_BIOMES)}\n\n'
                f'models.2=3\nbiomes.2={" ".join(SNOW_BIOMES)}\n')


def main():
    if os.path.exists(OUT): shutil.rmtree(OUT)
    os.makedirs(OUT)
    put(os.path.join(OUT, 'pack.mcmeta'), {'pack': {'pack_format': 34, 'description':
        'Middle East: birch log variation; hares (with Entity Model Features + Entity Texture Features)'}})
    birch()
    hare(json.load(open(SRC)))
    zpath = os.path.join(ROOT, 'resourcepack', 'MiddleEast.zip')
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
        for d, _, fs in os.walk(OUT):
            for f in sorted(fs):
                p = os.path.join(d, f)
                z.write(p, os.path.relpath(p, OUT))
    print('wrote', OUT, 'and', zpath)


if __name__ == '__main__':
    main()
