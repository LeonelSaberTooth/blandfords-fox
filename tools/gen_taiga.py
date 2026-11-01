"""Writes the worldgen data for the taiga changes and the birch trunk base.

- Every vanilla birch tree feature gets the birch_base decorator, so the bottom log of a
  natural birch is the weathered trunk base (saplings grow these trees too).
- Taiga (minecraft:taiga): trees are 50% spruce, 25% pine (both spruce trees, as vanilla's
  2:1) and 25% birch, every trunk 2 blocks taller than vanilla with the same crown; short
  grass and sugar cane are gone and the grass patches are all ferns; the ground is podzol
  (taiga_podzol, run last so berry bushes and trees still find grass to grow on first).

Needs the vanilla 1.21.1 worldgen JSON in ../ref/va/wg (from misode/mcmeta).
"""
import copy, json, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'src', 'main', 'resources', 'data')
WG = os.path.join(os.path.dirname(ROOT), 'ref', 'va', 'wg')
NS = 'blandfordsfox'
TAIGA = 'minecraft:taiga'
TALLER = 2
BASE = {'type': f'{NS}:birch_base'}
BIRCH_TREES = ['birch', 'birch_bees_0002', 'birch_bees_002', 'birch_bees_005', 'super_birch_bees', 'super_birch_bees_0002']


def put(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


def vanilla(kind, name):
    return json.load(open(os.path.join(WG, kind, name + '.json')))


def ours(kind, name, obj):
    put(os.path.join(D, NS, 'worldgen', kind, name + '.json'), obj)


def checked(feature, sapling):
    return {'feature': f'{NS}:{feature}', 'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {
        'type': 'minecraft:would_survive', 'state': {'Name': f'minecraft:{sapling}', 'Properties': {'stage': '0'}}}}]}


def taller(tree):
    t = copy.deepcopy(tree)
    t['config']['trunk_placer']['base_height'] += TALLER
    fp = t['config']['foliage_placer']
    if fp['type'] == 'minecraft:spruce_foliage_placer':
        # the spruce crown grows with the trunk unless its bare part grows too
        fp['trunk_height'] = {k: (v + TALLER if k != 'type' else v) for k, v in fp['trunk_height'].items()}
    return t


def main():
    for name in BIRCH_TREES:
        t = vanilla('configured_feature', name)
        assert t['config']['trunk_provider']['state']['Name'] == 'minecraft:birch_log', name
        t['config']['decorators'] = [d for d in t['config']['decorators'] if d.get('type') != BASE['type']] + [BASE]
        put(os.path.join(D, 'minecraft', 'worldgen', 'configured_feature', name + '.json'), t)

    # taiga trees
    spruce, pine, birch = taller(vanilla('configured_feature', 'spruce')), taller(vanilla('configured_feature', 'pine')), \
        taller(vanilla('configured_feature', 'birch'))
    birch['config']['decorators'] = birch['config']['decorators'] + [BASE]
    for name, tree, sapling in (('taiga_spruce', spruce, 'spruce_sapling'), ('taiga_pine', pine, 'spruce_sapling'),
                                ('taiga_birch', birch, 'birch_sapling')):
        ours('configured_feature', name, tree)
        ours('placed_feature', name + '_checked', checked(name, sapling))
    # random_selector tries in order: birch 1/4, then pine 1/3 of the rest (1/4), else spruce (1/2)
    ours('configured_feature', 'taiga_trees', {'type': 'minecraft:random_selector', 'config': {
        'default': f'{NS}:taiga_spruce_checked',
        'features': [{'chance': 0.25, 'feature': f'{NS}:taiga_birch_checked'},
                     {'chance': 0.33333334, 'feature': f'{NS}:taiga_pine_checked'}]}})
    placed = vanilla('placed_feature', 'trees_taiga')
    placed['feature'] = f'{NS}:taiga_trees'
    ours('placed_feature', 'taiga_trees', placed)

    # ferns instead of the short grass / fern mix
    ferns = vanilla('configured_feature', 'patch_taiga_grass')
    ferns['config']['feature']['feature']['config']['to_place'] = {'type': 'minecraft:simple_state_provider',
                                                                    'state': {'Name': 'minecraft:fern'}}
    ours('configured_feature', 'taiga_ferns', ferns)
    placed = vanilla('placed_feature', 'patch_grass_taiga_2')
    placed['feature'] = f'{NS}:taiga_ferns'
    ours('placed_feature', 'taiga_ferns', placed)

    # podzol ground
    ours('configured_feature', 'taiga_podzol', {'type': f'{NS}:taiga_podzol', 'config': {}})
    ours('placed_feature', 'taiga_podzol', {'feature': f'{NS}:taiga_podzol', 'placement': []})

    bm = lambda name, obj: put(os.path.join(D, NS, 'neoforge', 'biome_modifier', name + '.json'), obj)
    bm('taiga_remove', {'type': 'neoforge:remove_features', 'biomes': TAIGA,
                        'features': ['minecraft:trees_taiga', 'minecraft:patch_grass_taiga_2', 'minecraft:patch_sugar_cane'],
                        'steps': 'vegetal_decoration'})
    bm('taiga_add', {'type': 'neoforge:add_features', 'biomes': TAIGA,
                     'features': [f'{NS}:taiga_trees', f'{NS}:taiga_ferns'], 'step': 'vegetal_decoration'})
    bm('taiga_podzol', {'type': 'neoforge:add_features', 'biomes': TAIGA,
                        'features': f'{NS}:taiga_podzol', 'step': 'top_layer_modification'})
    print('heights: spruce', spruce['config']['trunk_placer'], spruce['config']['foliage_placer']['trunk_height'],
          '| pine', pine['config']['trunk_placer'], '| birch', birch['config']['trunk_placer'])


if __name__ == '__main__':
    main()
