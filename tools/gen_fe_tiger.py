"""Far East compatibility: the tiger model and animations made for it, ready for the game.

Reads tools/fe_tiger/page_tiger.json (the model and animations from the "Tiger Animations" page)
and writes, under assets/blandfordsfox/fe_tiger/:
  model.json  the parts (Java model space: x mirrored, y down from 24, like Blockbench's Java export)
  anims.json  the animations, keyframes converted the way Blockbench's vanilla Java export does:
              rotation (-x, -y, z) degrees, position (-x, y, z), scale as is; a segment is
              catmull-rom when either of its keys is (Blockbench's rule), else linear.
The model's "root" group is named "hips" in game, as in Far East's own tiger, so Far East's
animations (idle, sneak, rest, sleep, pounce, swipe, lick) play on it unchanged.
"""
import json, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'tools', 'fe_tiger', 'page_tiger.json')
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'blandfordsfox', 'fe_tiger')
RENAME = {'root': 'hips'}
ANIMS = ['GALLOP', 'WALK_NEW', 'STRETCH', 'CALL', 'REST_GROOM', 'MARK', 'CHEEK_RUB', 'SPRAY']
LOOPING = {'GALLOP', 'WALK_NEW'}


def num(v):
    v = str(v).strip()
    return float(eval(v)) if v else 0.0


def bone(name):
    return RENAME.get(name, name)


def model(M):
    G = {g['uuid']: g for g in M['groups']}
    E = {e['uuid']: e for e in M['elements']}
    parts = []

    def cube(e, piv):
        f, t = e['from'], e['to']
        size = [t[i] - f[i] for i in range(3)]
        # box UV: north face starts at (u + depth, v + depth); mirrored when its x runs backwards
        n = e['faces']['north']['uv']
        d = size[2]
        tex = [round(s) for s in size]
        u, v = min(n[0], n[2]) - tex[2], n[1] - tex[2]
        mirror = n[0] > n[2]
        infl = e.get('inflate') or 0
        deform = [(size[i] - tex[i]) / 2 + infl for i in range(3)]
        # Java space relative to the pivot: x mirrored, y flipped
        box = [piv[0] - t[0] + deform[0], piv[1] - t[1] + deform[1], f[2] - piv[2] + deform[2]]
        box = [box[0] , box[1], box[2]]
        return {'tex': [u, v], 'mirror': mirror, 'from': [round(x, 4) for x in box], 'size': tex,
                'deform': [round(x, 4) for x in deform]}

    def walk(node, parent, ppiv, depth):
        g = G[node['uuid']]
        piv = g['origin']
        if parent is None:
            off = [0.0, 24.0, 0.0]
        else:
            off = [-(piv[0] - ppiv[0]), -(piv[1] - ppiv[1]), piv[2] - ppiv[2]]
        rot = g.get('rotation') or [0, 0, 0]
        part = {'name': bone(g['name']), 'parent': parent, 'offset': [round(x, 4) for x in off],
                'rotation': [round(-math.radians(rot[0]), 6), round(-math.radians(rot[1]), 6), round(math.radians(rot[2]), 6)],
                'cubes': []}
        parts.append(part)
        k = 0
        for c in node['children']:
            if isinstance(c, str):
                e = E[c]
                r = e.get('rotation') or [0, 0, 0]
                if any(abs(x) > 1e-6 for x in r):
                    # a rotated cube: its own little part at the cube's origin (like Blockbench's "_r1" parts)
                    k += 1
                    o = e['origin']
                    sub = {'name': f"{part['name']}_r{k}", 'parent': part['name'],
                           'offset': [round(-(o[0] - piv[0]), 4), round(-(o[1] - piv[1]), 4), round(o[2] - piv[2], 4)],
                           'rotation': [round(-math.radians(r[0]), 6), round(-math.radians(r[1]), 6), round(math.radians(r[2]), 6)],
                           'cubes': [cube(e, o)]}
                    parts.append(sub)
                else:
                    part['cubes'].append(cube(e, piv))
            else:
                walk(c, part['name'], piv, depth + 1)

    for n in M['outliner']:
        walk(n, None, None, 0)
    return {'texture': M['res'], 'parts': parts}


def anim(a):
    chans = []
    for g in a['animators'].values():
        by = {}
        for k in g.get('keyframes', []):
            if k['channel'] not in ('rotation', 'position', 'scale'):
                continue
            p = k['data_points'][0]
            x, y, z = (num(p.get(c, 0)) for c in 'xyz')
            if k['channel'] == 'rotation':
                v = [-x, -y, z]
            elif k['channel'] == 'position':
                v = [-x, y, z]
            else:
                v = [x, y, z]
            by.setdefault(k['channel'], []).append((float(k['time']), v, k.get('interpolation') or 'linear'))
        for ch, ks in by.items():
            ks.sort(key=lambda t: t[0])
            out = []
            for i, (t, v, ip) in enumerate(ks):
                prev = ks[i - 1][2] if i else ip
                smooth = 'catmullrom' in (ip, prev)
                out.append([round(t, 5)] + [round(c, 4) for c in v] + [1 if smooth else 0])
            chans.append({'bone': bone(g['name']), 'target': ch, 'keys': out})
    return {'length': a['length'], 'loop': a['name'] in LOOPING, 'channels': chans}


def main():
    M = json.load(open(SRC))
    os.makedirs(OUT, exist_ok=True)
    json.dump(model(M), open(os.path.join(OUT, 'model.json'), 'w'), separators=(',', ':'))
    by = {a['name']: a for a in M['anims']}
    data = {'anims': {n: anim(by[n]) for n in ANIMS}, 'faces': {n: M['faces'].get(n, {}) for n in ANIMS},
            'trees': M['logs']['sets']}
    json.dump(data, open(os.path.join(OUT, 'anims.json'), 'w'), separators=(',', ':'))
    print('parts', len(model(M)['parts']), 'anims', {n: (len(a['channels']), a['length']) for n, a in data['anims'].items()})


if __name__ == '__main__':
    main()
