"""The grown women's looks for the mod, from the model viewer's data (maiden-viewer.html, made by build.py):

  textures/entity/maiden/woman/
    tone_<1-5>.png        her skin (palest, paler, as drawn, light tan, tan), see-through spots on her body filled
    legs_tone_<1-5>.png   the same skin on the straight outdoor legs (worn under a dress)
    hair_<1-21>.png       hair
    brows_<1-8>.png, eyes_<1-8>.png   brows (under the hair) and eyes with lashes (over it)
    smile.png, blush.png, freckles.png
    swim.png              swimwear (at home)
    dress_<1-21>.png      a dress on her body; legs_dress_<1-21>.png the same dress on the outdoor legs
    job_map_body.png, job_map_legs.png   where each spot takes its colour from a villager profession texture
                          (red = x, green = y)
  and src/main/java/net/maidens/client/WomanGeometry.java: her parts (the base model) and the outdoor legs.
Everything on her body is on the base model's UV (the hybrid model), the outdoor legs on the outdoor model's.
"""
import json, base64, io, os, math
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = '/home/claude/maidens'
OUT = os.path.join(MOD, 'src/main/resources/assets/maidens/textures/entity/maiden/woman')
os.makedirs(OUT, exist_ok=True)
html = open(os.path.join(HERE, 'maiden-viewer.html')).read()
i = html.index('const DATA = ') + len('const DATA = ')
DATA, _ = json.JSONDecoder().raw_decode(html[i:])
TEX = DATA['tex']; MODELS = DATA['models']
HYB = [e for e in MODELS['hybrid']['elements'] if e['name'] != 'breast2']

def img(key):
    return Image.open(io.BytesIO(base64.b64decode(TEX[key].split(',', 1)[1]))).convert('RGBA')

def faces_of(model):
    return {e['name']: e['faces'] for e in MODELS[model]['elements']}

def remap(src, frm, to):
    """the viewer's remap: each face's pixels by share across it, flips kept; the head (rows above 16) as is"""
    if frm == to: return src.copy()
    S, D = faces_of(frm), faces_of(to)
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    sp, op = src.load(), out.load()
    for name, faces in D.items():
        sf = S.get(name)
        if not sf: continue
        for side, uv in faces.items():
            su = sf.get(side)
            if not su or not uv: continue
            x0, x1, y0, y1 = min(uv[0], uv[2]), max(uv[0], uv[2]), min(uv[1], uv[3]), max(uv[1], uv[3])
            W, H = x1 - x0, y1 - y0
            j = 0
            while j < H:
                i = 0
                while i < W:
                    fx = ((i + .5) if uv[0] <= uv[2] else (W - i - .5)) / W
                    fy = ((j + .5) if uv[1] <= uv[3] else (H - j - .5)) / H
                    sx = math.floor(su[0] + fx * (su[2] - su[0]) - (1e-6 if su[2] < su[0] else 0))
                    sy = math.floor(su[1] + fy * (su[3] - su[1]) - (1e-6 if su[3] < su[1] else 0))
                    if 0 <= sx < 64 and 0 <= sy < 64:
                        px, py = int(x0 + i), int(y0 + j)
                        if 0 <= px < 64 and 0 <= py < 64: op[px, py] = sp[sx, sy]
                    i += 1
                j += 1
    for y in range(16):
        for x in range(64):
            if op[x, y][3] == 0 and sp[x, y][3] != 0: op[x, y] = sp[x, y]
    return out

def filled(src):
    """see-through pixels on her body's faces filled from their neighbours on the same face (the viewer's skinFilled)"""
    im = src.copy(); p = im.load()
    for e in HYB:
        if e['name'] == 'head_layer': continue
        for side, u in e['faces'].items():
            if not u or (e['name'] == 'breast' and side in ('south', 'up')): continue
            x0, x1 = math.floor(min(u[0], u[2])), math.ceil(max(u[0], u[2]))
            y0, y1 = math.floor(min(u[1], u[3])), math.ceil(max(u[1], u[3]))
            for _ in range(8):
                fill = []
                for y in range(y0, y1):
                    for x in range(x0, x1):
                        if p[x, y][3]: continue
                        near = [(a, b) for a, b in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)) if x0 <= a < x1 and y0 <= b < y1 and p[a, b][3]]
                        if near: fill.append((x, y, near))
                if not fill: break
                for x, y, near in fill:
                    p[x, y] = tuple(round(sum(p[a, b][k] for a, b in near) / len(near)) for k in range(3)) + (255,)
    return im

def save(im, name):
    im.save(os.path.join(OUT, name))

TONES = ['layer_skin_palest', 'layer_skin_pale', 'layer_skin', 'layer_skin_lighttan', 'layer_skin_tan']
for n, k in enumerate(TONES, 1):
    t = filled(img(k))
    save(t, f'tone_{n}.png')
    save(remap(t, 'hybrid', 'outdoor'), f'legs_tone_{n}.png')
hairs = sorted(int(k[9:]) for k in TEX if k.startswith('opt_hair_'))
for n in hairs: save(img(f'opt_hair_{n}'), f'hair_{n}.png')
for n, e in enumerate((1, 3, 4, 5, 6, 7, 8, 9), 1):
    save(img(f'opt_eyes_{e}'), f'eyes_{n}.png'); save(img(f'opt_brows_{e}'), f'brows_{n}.png')
save(img('opt_face_smile'), 'smile.png'); save(img('opt_face_blush'), 'blush.png'); save(img('opt_face_freckles'), 'freckles.png')
save(remap(img('swim'), 'latest', 'hybrid'), 'swim.png')
outfits = sorted(int(k[8:]) for k in TEX if k.startswith('clothes_'))
for n in outfits:
    c = img(f'clothes_{n}')
    save(remap(c, 'outdoor', 'hybrid'), f'dress_{n}.png'); save(c, f'legs_dress_{n}.png')
jm = img('job_map')
save(remap(jm, 'outdoor', 'hybrid'), 'job_map_body.png'); save(jm, 'job_map_legs.png')
print('textures:', len(TONES), 'tones,', len(hairs), 'hairs, 8 eyes,', len(outfits), 'dresses')

# ---------------------------------------------------------------- geometry
def f(v): return f'{v}F' if not isinstance(v, float) or v == int(v) and abs(v) < 1e9 else f'{v}F'
def arr(a): return '{' + ', '.join(f'{float(x)!r}F' for x in a) + '}'
SIDES = ('north', 'east', 'south', 'west', 'up', 'down')
def narr(a): return 'new float[]' + arr(a)
def element(e, inflate=None):
    fc = ', '.join(arr(e['faces'][s]) if e['faces'].get(s) else 'null' for s in SIDES)
    return (f'        new E("{e["name"]}", {narr(e["from"])}, {narr(e["to"])}, {narr(e["origin"])}, {narr(e.get("rotation") or [0, 0, 0])}, '
            f'{float(e.get("inflate") or 0) if inflate is None else inflate!r}F,\n              new float[][]{{{fc}}})')
legs = [e for e in MODELS['outdoor']['elements'] if e['name'] in ('right_leg', 'left_leg')]
g = MODELS['hybrid']['groups']
java = f'''package net.maidens.client;

/**
 * Generated by the model viewer's export_mod.py from the base model (Blockbench units: y up from her feet, her
 * right at +x, her front at -z). Don't edit by hand.
 */
final class WomanGeometry {{
    private WomanGeometry() {{}}

    /** One cube: from, to, its pivot, its rotation (degrees, applied Z then Y then X), inflate, and the UV of its
     *  faces in the order north, east, south, west, up, down ([u0, v0, u1, v1], null: no face). */
    record E(String name, float[] from, float[] to, float[] origin, float[] rotation, float inflate, float[][] uv) {{}}

    /** Her parts. */
    static final E[] BODY = {{
{(',' + chr(10)).join(element(e) for e in HYB)}
    }};

    /** The straight outdoor legs, worn under a dress (their own texture; a little out past her legs). */
    static final E[] DRESS_LEGS = {{
{(',' + chr(10)).join(element(e, 0.06) for e in legs)}
    }};

    /** The parts' pivots. */
    static final float[] HEAD = {arr(g['head'])}, RIGHT_ARM = {arr(g['right_arm'])}, LEFT_ARM = {arr(g['left_arm'])},
            RIGHT_LEG = {arr(g['right_leg'])}, LEFT_LEG = {arr(g['left_leg'])};
}}
'''
open(os.path.join(MOD, 'src/main/java/net/maidens/client/WomanGeometry.java'), 'w').write(java)
print('geometry:', len(HYB), 'parts')
