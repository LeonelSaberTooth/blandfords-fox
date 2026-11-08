"""Builds the placeholder house as a structure template:
src/main/resources/data/maidens/structure/maiden_house.nbt

A 9x9 cobblestone-floored oak cabin with a gabled roof, a door on the north side, a bed, chest,
crafting table, lantern and a potted flower, a dirt path to the door, and a wheat plot with a water
source beside it (her farm). The maiden stands inside; the game spawns her when the house is placed.
"""
import os
from nbtlib import File, Compound, List, Int, Double, String, Byte

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'src', 'main', 'resources', 'data', 'maidens', 'structure', 'maiden_house.nbt')

blocks = {}            # (x, y, z) -> (name, {props})
def put(x, y, z, name, **props): blocks[(x, y, z)] = (name, props)

HX, HZ = 0, 2          # the house occupies x 0..8, z 2..10; the path is at z 0..1 in front of the door
W = 9
for x in range(W):
    for z in range(W):
        X, Z = HX + x, HZ + z
        put(X, 0, Z, 'minecraft:cobblestone')
        edge = x in (0, W - 1) or z in (0, W - 1)
        corner = x in (0, W - 1) and z in (0, W - 1)
        for y in range(1, 4):
            if corner: put(X, y, Z, 'minecraft:oak_log', axis='y')
            elif edge: put(X, y, Z, 'minecraft:oak_planks')
            else: put(X, y, Z, 'minecraft:air')
# windows
for (x, z) in [(2, 0), (6, 0), (2, 8), (6, 8), (0, 3), (0, 5), (8, 3), (8, 5)]:
    put(HX + x, 2, HZ + z, 'minecraft:glass')
# door in the middle of the north wall, opening inwards
put(HX + 4, 1, HZ, 'minecraft:oak_door', facing='south', half='lower', hinge='left', open='false', powered='false')
put(HX + 4, 2, HZ, 'minecraft:oak_door', facing='south', half='upper', hinge='left', open='false', powered='false')
# gabled roof running north-south: stairs rising from both sides to a plank ridge
for k in range(5):
    y = 4 + k
    for z in range(-1, W + 1):
        Z = HZ + z
        if k < 4:
            put(HX + k, y, Z, 'minecraft:oak_stairs', facing='east', half='bottom', shape='straight', waterlogged='false')
            put(HX + W - 1 - k, y, Z, 'minecraft:oak_stairs', facing='west', half='bottom', shape='straight', waterlogged='false')
        else:
            put(HX + 4, y, Z, 'minecraft:oak_planks')
    # gable ends filled in, the attic inside cleared
    for x in range(k + 1, W - 1 - k):
        for z in (0, W - 1): put(HX + x, y, HZ + z, 'minecraft:oak_planks')
        for z in range(1, W - 1): put(HX + x, y, HZ + z, 'minecraft:air')
# inside
put(HX + 2, 1, HZ + 6, 'minecraft:red_bed', facing='south', part='foot', occupied='false')
put(HX + 2, 1, HZ + 7, 'minecraft:red_bed', facing='south', part='head', occupied='false')
put(HX + 6, 1, HZ + 7, 'minecraft:chest', facing='north', type='single', waterlogged='false')
put(HX + 7, 1, HZ + 7, 'minecraft:crafting_table')
put(HX + 7, 1, HZ + 1, 'minecraft:potted_poppy')
put(HX + 4, 3, HZ + 4, 'minecraft:lantern', hanging='true', waterlogged='false')
# path to the door
for z in range(0, HZ): put(HX + 4, 0, z, 'minecraft:dirt_path')
# her wheat plot east of the house: farmland around one water source, wheat at different stages
FX, FZ = 11, 3
for x in range(5):
    for z in range(7):
        X, Z = FX + x, FZ + z
        if (x, z) == (2, 3):
            put(X, 0, Z, 'minecraft:water', level='0')
            put(X, 1, Z, 'minecraft:air')
            continue
        put(X, 0, Z, 'minecraft:farmland', moisture='7')
        put(X, 1, Z, 'minecraft:wheat', age=str(7 if (x + z) % 3 else 4))

xs = [p[0] for p in blocks]; ys = [p[1] for p in blocks]; zs = [p[2] for p in blocks]
mn = (min(xs), min(ys), min(zs))
size = (max(xs) - mn[0] + 1, max(ys) - mn[1] + 1, max(zs) - mn[2] + 1)
palette, pidx, blist = [], {}, []
for pos, (name, props) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    key = (name, tuple(sorted(props.items())))
    if key not in pidx:
        pidx[key] = len(palette)
        c = Compound({'Name': String(name)})
        if props: c['Properties'] = Compound({k: String(v) for k, v in props.items()})
        palette.append(c)
    blist.append(Compound({'pos': List[Int]([Int(pos[i] - mn[i]) for i in range(3)]), 'state': Int(pidx[key])}))
mx, my, mz = HX + 4 - mn[0], 1 - mn[1], HZ + 4 - mn[2]
ent = Compound({'blockPos': List[Int]([Int(mx), Int(my), Int(mz)]),
                'pos': List[Double]([Double(mx + 0.5), Double(my), Double(mz + 0.5)]),
                'nbt': Compound({'id': String('maidens:maiden'), 'PersistenceRequired': Byte(1)})})
root = Compound({'DataVersion': Int(3955), 'size': List[Int]([Int(v) for v in size]), 'palette': List[Compound](palette),
                 'blocks': List[Compound](blist), 'entities': List[Compound]([ent])})
os.makedirs(os.path.dirname(OUT), exist_ok=True)
File(root).save(OUT, gzipped=True)
print('size', size, 'blocks', len(blist), 'palette', len(palette), 'maiden at', (mx, my, mz))
