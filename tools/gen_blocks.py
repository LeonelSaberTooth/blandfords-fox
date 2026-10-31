"""Writes the data and asset files for the limestone brick set and the birch log variation.

Limestone bricks and mossy limestone bricks: bricks on the sides, plain limestone on top and
bottom, for the stairs, slab and wall too. Mossy: limestone bricks + a vine or a moss block. Made from 2x2 limestone (4 bricks); stairs, slab and wall
crafted from bricks like stone bricks; all of them from limestone or bricks on a stonecutter.

Needs the vanilla 1.21.1 block models and blockstates in ../ref/va (from misode/mcmeta).

Birch log: the vanilla bark texture slid sideways by a random 0-15 px on each block (each
side face is two quads carrying the two halves of the texture, wrapped round), so a trunk
no longer repeats the same pattern block after block. Uses the game's own birch textures,
so resource packs still apply.
"""
import json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
A = os.path.join(RES, 'assets')
D = os.path.join(RES, 'data')
VA = os.path.join(os.path.dirname(ROOT), 'ref', 'va')
NS = 'blandfordsfox'

SIDE = f'{NS}:block/limestone_bricks'
TOP = f'{NS}:block/limestone'


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


def vanilla(rel):
    return json.load(open(os.path.join(VA, rel)))


def model(name, obj):
    put(os.path.join(A, NS, 'models', 'block', name + '.json'), obj)


def item(name, parent):
    put(os.path.join(A, NS, 'models', 'item', name + '.json'), {'parent': parent})


def blockstate(name, obj):
    put(os.path.join(A, NS, 'blockstates', name + '.json'), obj)


def renamed(obj, base):
    s = json.dumps(obj).replace('minecraft:block/stone_brick', f'{NS}:block/{base}')
    return json.loads(s)


def wall_template(src, dst):
    """A vanilla wall template with the top and bottom faces taking #top instead of #wall."""
    m = vanilla(f'models/block/{src}.json')
    for e in m.get('elements', []):
        for side, face in e['faces'].items():
            if side in ('up', 'down'): face['texture'] = '#top'
    m.setdefault('textures', {})['particle'] = '#wall'
    if 'parent' in m and not m['parent'].startswith('minecraft:'):
        m['parent'] = 'minecraft:' + m['parent']
    model(dst, m)


def brick_set(full, base, side):
    """A brick block (full, e.g. limestone_bricks) and its stairs, slab and wall (base_stairs...):
    side texture on the sides, plain limestone on top and bottom."""
    tex = {'bottom': TOP, 'top': TOP, 'side': side}
    model(full, {'parent': 'minecraft:block/cube_column', 'textures': {'end': TOP, 'side': side}})
    blockstate(full, {'variants': {'': {'model': f'{NS}:block/{full}'}}})
    item(full, f'{NS}:block/{full}')

    for suffix in ('', '_inner', '_outer'):
        model(base + '_stairs' + suffix, {'parent': {'': 'minecraft:block/stairs', '_inner': 'minecraft:block/inner_stairs',
                                                    '_outer': 'minecraft:block/outer_stairs'}[suffix], 'textures': tex})
    blockstate(base + '_stairs', renamed(vanilla('blockstates/stone_brick_stairs.json'), base))
    item(base + '_stairs', f'{NS}:block/{base}_stairs')

    model(base + '_slab', {'parent': 'minecraft:block/slab', 'textures': tex})
    model(base + '_slab_top', {'parent': 'minecraft:block/slab_top', 'textures': tex})
    bs = renamed(vanilla('blockstates/stone_brick_slab.json'), base)
    bs['variants']['type=double']['model'] = f'{NS}:block/{full}'
    blockstate(base + '_slab', bs)
    item(base + '_slab', f'{NS}:block/{base}_slab')

    for t in ('wall_post', 'wall_side', 'wall_side_tall', 'wall_inventory'):
        wall_template('template_' + t if t != 'wall_inventory' else t, 'template_limestone_' + t)
        model(base + '_' + t, {'parent': f'{NS}:block/template_limestone_' + t, 'textures': {'wall': side, 'top': TOP}})
    blockstate(base + '_wall', renamed(vanilla('blockstates/stone_brick_wall.json'), base))
    item(base + '_wall', f'{NS}:block/{base}_wall_inventory')

    # loot: each drops itself (a double slab drops two)
    def self_drop(name):
        return {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{name}', 'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}]}]}
    for n in (full, base + '_stairs', base + '_wall'):
        put(os.path.join(D, NS, 'loot_table', 'blocks', n + '.json'), self_drop(n))
    slab = json.loads(open(os.path.join(VA, 'loot', 'stone_brick_slab.json')).read()
                      .replace('minecraft:stone_brick_slab', f'{NS}:{base}_slab')
                      .replace('minecraft:blocks/stone_brick_slab', f'{NS}:blocks/{base}_slab'))
    put(os.path.join(D, NS, 'loot_table', 'blocks', base + '_slab.json'), slab)

    # stairs, slab and wall from the bricks, at the crafting table and the stonecutter
    rec('%s_slab' % base, shaped(full, ['###'], base + '_slab', 6))
    rec('%s_stairs' % base, shaped(full, ['#  ', '## ', '###'], base + '_stairs', 4))
    rec('%s_wall' % base, shaped(full, ['###', '###'], base + '_wall', 6, 'misc'))
    for src in ((full,) if full != 'limestone_bricks' else ('limestone', full)):
        rec(f'{base}_slab_from_{src}_stonecutting', cut(src, base + '_slab', 2))
        rec(f'{base}_stairs_from_{src}_stonecutting', cut(src, base + '_stairs', 1))
        rec(f'{base}_wall_from_{src}_stonecutting', cut(src, base + '_wall', 1))

    # tags
    ids = [f'{NS}:{full}'] + [f'{NS}:{base}_{k}' for k in ('stairs', 'slab', 'wall')]
    add_tag('minecraft/tags/block/mineable/pickaxe.json', ids)
    for kind, block in (('stairs', 'stairs'), ('slabs', 'slab'), ('walls', 'wall')):
        one = [f'{NS}:{base}_' + block]
        add_tag(f'minecraft/tags/block/{kind}.json', one)
        add_tag(f'minecraft/tags/item/{kind}.json', one)


def rec(n, o):
    put(os.path.join(D, NS, 'recipe', n + '.json'), o)


def shaped(key, pattern, out, count, cat='building'):
    return {'type': 'minecraft:crafting_shaped', 'category': cat, 'key': {'#': {'item': f'{NS}:{key}'}},
            'pattern': pattern, 'result': {'count': count, 'id': f'{NS}:{out}'}}


def cut(src, out, count):
    return {'type': 'minecraft:stonecutting', 'ingredient': {'item': f'{NS}:{src}'},
            'result': {'count': count, 'id': f'{NS}:{out}'}}


def add_tag(path, values):
    p = os.path.join(D, path)
    old = json.load(open(p))['values'] if os.path.exists(p) else []
    put(p, {'values': old + [v for v in values if v not in old]})


def unlock(name, have, prefix):
    """Recipe book: unlock the recipes starting with prefix once you have one of the items."""
    recipes = sorted(f'{NS}:' + f[:-5] for f in os.listdir(os.path.join(D, NS, 'recipe')) if f.startswith(prefix))
    put(os.path.join(D, NS, 'advancement', 'recipes', 'building_blocks', name + '.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {'has_item': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': have}]}}},
        'requirements': [['has_item']],
        'rewards': {'recipes': recipes}})


def bricks():
    rec('limestone_bricks', shaped('limestone', ['##', '##'], 'limestone_bricks', 4))
    rec('limestone_bricks_from_limestone_stonecutting', cut('limestone', 'limestone_bricks', 1))
    brick_set('limestone_bricks', 'limestone_brick', f'{NS}:block/limestone_bricks')
    unlock('limestone_bricks', [f'{NS}:limestone', f'{NS}:limestone_bricks'], 'limestone_brick')

    # mossy: limestone bricks with a vine or a moss block, like mossy stone bricks
    for extra in ('vine', 'moss_block'):
        rec(f'mossy_limestone_bricks_from_{extra}', {
            'type': 'minecraft:crafting_shapeless', 'category': 'building', 'group': 'mossy_limestone_bricks',
            'ingredients': [{'item': f'{NS}:limestone_bricks'}, {'item': f'minecraft:{extra}'}],
            'result': {'count': 1, 'id': f'{NS}:mossy_limestone_bricks'}})
    brick_set('mossy_limestone_bricks', 'mossy_limestone_brick', f'{NS}:block/mossy_limestone_bricks')
    unlock('mossy_limestone_bricks', [f'{NS}:mossy_limestone_bricks', 'minecraft:vine', 'minecraft:moss_block'], 'mossy_limestone_brick')


def birch():
    """A model per sideways shift, picked at random per block by the blockstate."""
    def u_auto(face, c):            # vanilla's default u for a point on a side face
        return {'north': 16 - c, 'south': c, 'west': c, 'east': 16 - c}[face]

    def plane(face, a, b, k):
        """The part of a side face from a to b (x for north/south, z for west/east), texture slid by k."""
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
            # split where the slid texture wraps round
            cut = {'north': k, 'south': 16 - k, 'west': 16 - k, 'east': k}[face]
            elems.append(plane(face, 0, cut, k))
            elems.append(plane(face, cut, 16, k))
        name = f'birch_log_slid_{k}'
        model(name, {'parent': 'minecraft:block/block',
                     'textures': {'particle': 'minecraft:block/birch_log', 'side': 'minecraft:block/birch_log',
                                  'end': 'minecraft:block/birch_log_top'},
                     'elements': elems})
        variants.append(f'{NS}:block/{name}')
    put(os.path.join(A, 'minecraft', 'blockstates', 'birch_log.json'), {'variants': {
        'axis=y': [{'model': m} for m in variants],
        'axis=z': [{'model': m, 'x': 90} for m in variants],
        'axis=x': [{'model': m, 'x': 90, 'y': 90} for m in variants]}})


def check_birch():
    """Each slid face must show texture columns k..15 then 0..k-1, left to right, with no gaps."""
    for k in range(16):
        m = json.load(open(os.path.join(A, NS, 'models', 'block', f'birch_log_slid_{k}.json')))
        for face in ('north', 'south', 'west', 'east'):
            cover = {}
            for e in m['elements']:
                if face not in e['faces'] or e['from'] == [0, 0, 0] and e['to'] == [16, 16, 16]: continue
                axis = 0 if face in ('north', 'south') else 2
                a, b = e['from'][axis], e['to'][axis]
                u1, _, u2, _ = e['faces'][face]['uv']
                assert 0 <= u1 < u2 <= 16, (k, face, u1, u2)
                for c in range(a, b):            # texel column at position c..c+1
                    t = (c - a) / (b - a)
                    # u runs u1 -> u2 from the face's left edge, which is the high end for north/east
                    left_high = face in ('north', 'east')
                    frac = 1 - t - 1 / (b - a) if left_high else t
                    cover[c] = int(round(u1 + frac * (u2 - u1)))
            assert sorted(cover) == list(range(16)), (k, face)
            for c in range(16):
                want = (int({'north': 15 - c, 'south': c, 'west': c, 'east': 15 - c}[face]) + k) % 16
                assert cover[c] == want, (k, face, c, cover[c], want)
    print('birch: 16 slid models check out')


if __name__ == '__main__':
    bricks()
    birch()
    check_birch()
    print('done')
