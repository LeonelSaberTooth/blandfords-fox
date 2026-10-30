"""Writes the worldgen data files for Negev-style deserts.

Needs vanilla 1.21.1 data (sloped_cheese and the overworld noise settings), e.g. from
https://github.com/misode/mcmeta tag 1.21.1-data. Usage: python3 tools/gen_worldgen.py <vanilla data/minecraft/worldgen dir>
"""
import json, os, sys

VANILLA = sys.argv[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, 'src', 'main', 'resources', 'data')

def write(path, obj):
    path = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, 'w'), indent=2)

INPUTS = {
    "temperature": {"type": "minecraft:shifted_noise", "noise": "minecraft:temperature", "xz_scale": 0.25, "y_scale": 0.0,
                    "shift_x": "minecraft:shift_x", "shift_y": 0.0, "shift_z": "minecraft:shift_z"},
    "continents": "minecraft:overworld/continents",
    "erosion": "minecraft:overworld/erosion",
    "depth": "minecraft:overworld/depth",
    "noise_big": "blandfordsfox:negev_big",
    "noise_mid": "blandfordsfox:negev_mid",
    "noise_small": "blandfordsfox:negev_small",
    "noise_fine": "blandfordsfox:negev_fine",
}

# noises (same octave setup as tools/preview/Preview.java)
write('blandfordsfox/worldgen/noise/negev_big.json', {"firstOctave": -10, "amplitudes": [1.0, 1.0]})
write('blandfordsfox/worldgen/noise/negev_mid.json', {"firstOctave": -8, "amplitudes": [1.0, 0.5, 0.25]})
write('blandfordsfox/worldgen/noise/negev_small.json', {"firstOctave": -6, "amplitudes": [1.0, 0.5]})
write('blandfordsfox/worldgen/noise/negev_fine.json', {"firstOctave": -4, "amplitudes": [1.0, 0.5]})

# smooth shape: wrap vanilla's sloped cheese
cheese = json.load(open(os.path.join(VANILLA, 'density_function', 'overworld', 'sloped_cheese.json')))
write('minecraft/worldgen/density_function/overworld/sloped_cheese.json',
      dict({"type": "blandfordsfox:negev_terrain", "input": cheese}, **INPUTS))

# exact terraces: wrap the overworld's final density
ns = json.load(open(os.path.join(VANILLA, 'noise_settings', 'overworld.json')))
ns['noise_router']['final_density'] = dict({"type": "blandfordsfox:negev_detail", "input": ns['noise_router']['final_density']}, **INPUTS)
write('minecraft/worldgen/noise_settings/overworld.json', ns)

# features
for name, step_heightmap in (('negev_paint', 'WORLD_SURFACE_WG'), ('negev_decorate', 'WORLD_SURFACE_WG')):
    write(f'blandfordsfox/worldgen/configured_feature/{name}.json', {"type": f"blandfordsfox:{name}", "config": {}})
    write(f'blandfordsfox/worldgen/placed_feature/{name}.json', {
        "feature": f"blandfordsfox:{name}",
        "placement": [{"type": "minecraft:heightmap", "heightmap": step_heightmap}]})

write('blandfordsfox/neoforge/biome_modifier/negev_paint.json', {
    "type": "neoforge:add_features", "biomes": "minecraft:desert",
    "features": "blandfordsfox:negev_paint", "step": "raw_generation"})
write('blandfordsfox/neoforge/biome_modifier/negev_decorate.json', {
    "type": "neoforge:add_features", "biomes": "minecraft:desert",
    "features": "blandfordsfox:negev_decorate", "step": "vegetal_decoration"})
write('blandfordsfox/neoforge/biome_modifier/no_desert_cactus.json', {
    "type": "neoforge:remove_features", "biomes": "minecraft:desert",
    "features": "minecraft:patch_cactus_desert", "steps": "vegetal_decoration"})
print('ok')
