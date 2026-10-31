"""Writes the data and asset files for the limestone brick set and the birch log variation.

Limestone bricks: bricks on the sides, plain limestone on top and bottom, like the block,
for the stairs, slab and wall too. Made from 2x2 limestone (4 bricks); stairs, slab and wall
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


def renamed(obj):
    s = json.dumps(obj).replace('minecraft:block/stone_brick', f'{NS}:block/limestone_brick')
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


def bricks():
    tex = {'bottom': TOP, 'top': TOP, 'side': SIDE}
    model('limestone_bricks', {'parent': 'minecraft:block/cube_column', 'textures': {'end': TOP, 'side': SIDE}})
    blockstate('limestone_bricks', {'variants': {'': {'model': f'{NS}:block/limestone_bricks'}}})
    item('limestone_bricks', f'{NS}:block/limestone_bricks')

    for suffix in ('', '_inner', '_outer'):
        model('limestone_brick_stairs' + suffix, {'parent': {'': 'minecraft:block/stairs', '_inner': 'minecraft:block/inner_stairs',
                                                            '_outer': 'minecraft:block/outer_stairs'}[suffix], 'textures': tex})
    blockstate('limestone_brick_stairs', renamed(vanilla('blockstates/stone_brick_stairs.json')))
    item('limestone_brick_stairs', f'{NS}:block/limestone_brick_stairs')

    model('limestone_brick_slab', {'parent': 'minecraft:block/slab', 'textures': tex})
    model('limestone_brick_slab_top', {'parent': 'minecraft:block/slab_top', 'textures': tex})
    bs = renamed(vanilla('blockstates/stone_brick_slab.json'))
    bs['variants']['type=double']['model'] = f'{NS}:block/limestone_bricks'
    blockstate('limestone_brick_slab', bs)
    item('limestone_brick_slab', f'{NS}:block/limestone_brick_slab')

    for t in ('wall_post', 'wall_side', 'wall_side_tall', 'wall_inventory'):
        wall_template('template_' + t if t != 'wall_inventory' else t, 'template_limestone_' + t)
        model('limestone_brick_' + t, {'parent': f'{NS}:block/template_limestone_' + t, 'textures': {'wall': SIDE, 'top': TOP}})
    blockstate('limestone_brick_wall', renamed(vanilla('blockstates/stone_brick_wall.json')))
    item('limestone_brick_wall', f'{NS}:block/limestone_brick_wall_inventory')

    # loot: each drops itself (a double slab drops two)
    def self_drop(name):
        return {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{name}', 'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}]}]}
    for n in ('limestone_bricks', 'limestone_brick_stairs', 'limestone_brick_wall'):
        put(os.path.join(D, NS, 'loot_table', 'blocks', n + '.json'), self_drop(n))
    slab = json.loads(open(os.path.join(VA, 'loot', 'stone_brick_slab.json')).read()
                      .replace('minecraft:stone_brick_slab', f'{NS}:limestone_brick_slab')
                      .replace('minecraft:blocks/stone_brick_slab', f'{NS}:blocks/limestone_brick_slab'))
    put(os.path.join(D, NS, 'loot_table', 'blocks', 'limestone_brick_slab.json'), slab)

    # recipes
    rec = lambda n, o: put(os.path.join(D, NS, 'recipe', n + '.json'), o)
    shaped = lambda key, pattern, out, count, cat='building': {
        'type': 'minecraft:crafting_shaped', 'category': cat, 'key': {'#': {'item': f'{NS}:{key}'}},
        'pattern': pattern, 'result': {'count': count, 'id': f'{NS}:{out}'}}
    cut = lambda src, out, count: {'type': 'minecraft:stonecutting', 'ingredient': {'item': f'{NS}:{src}'},
                                   'result': {'count': count, 'id': f'{NS}:{out}'}}
    rec('limestone_bricks', shaped('limestone', ['##', '##'], 'limestone_bricks', 4))
    rec('limestone_brick_slab', shaped('limestone_bricks', ['###'], 'limestone_brick_slab', 6))
    rec('limestone_brick_stairs', shaped('limestone_bricks', ['#  ', '## ', '###'], 'limestone_brick_stairs', 4))
    rec('limestone_brick_wall', shaped('limestone_bricks', ['###', '###'], 'limestone_brick_wall', 6, 'misc'))
    rec('limestone_bricks_from_limestone_stonecutting', cut('limestone', 'limestone_bricks', 1))
    for src in ('limestone', 'limestone_bricks'):
        rec(f'limestone_brick_slab_from_{src}_stonecutting', cut(src, 'limestone_brick_slab', 2))
        rec(f'limestone_brick_stairs_from_{src}_stonecutting', cut(src, 'limestone_brick_stairs', 1))
        rec(f'limestone_brick_wall_from_{src}_stonecutting', cut(src, 'limestone_brick_wall', 1))

    # recipe book: unlock the recipes once you have limestone or limestone bricks
    recipes = sorted(f'{NS}:' + f[:-5] for f in os.listdir(os.path.join(D, NS, 'recipe')) if f.startswith('limestone_brick'))
    put(os.path.join(D, NS, 'advancement', 'recipes', 'building_blocks', 'limestone_bricks.json'), {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_limestone': {'trigger': 'minecraft:inventory_changed',
                              'conditions': {'items': [{'items': [f'{NS}:limestone', f'{NS}:limestone_bricks']}]}}},
        'requirements': [['has_limestone']],
        'rewards': {'recipes': recipes}})

    # tags
    def add_tag(path, values):
        p = os.path.join(D, path)
        old = json.load(open(p))['values'] if os.path.exists(p) else []
        put(p, {'values': old + [v for v in values if v not in old]})
    ids = [f'{NS}:limestone_bricks', f'{NS}:limestone_brick_stairs', f'{NS}:limestone_brick_slab', f'{NS}:limestone_brick_wall']
    add_tag('minecraft/tags/block/mineable/pickaxe.json', ids)
    for kind in ('stairs', 'slabs', 'walls'):
        one = [f'{NS}:limestone_brick_' + kind.rstrip('s')]
        add_tag(f'minecraft/tags/block/{kind}.json', one)
        add_tag(f'minecraft/tags/item/{kind}.json', one)


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
