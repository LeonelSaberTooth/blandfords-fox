"""Splits the maiden skins into layers, so every maiden can be put together from parts (see LayeredSkin):

  layers/tone_<n>.png         her skin, all over (from maiden n's skin colour, with her face's own shading)
  layers/casual_tone_<n>.png  the same on the casual body (thinner legs: different texture spots)
  layers/clothes_<n>.png      maiden n's clothes (everything on her body that isn't skin)
  layers/casual_clothes.png   her clothes at home (from art/skincomp.png, the casual texture: everything on it that
                              isn't skin), on the casual body, over her own skin tone
  layers/job_map.png          where each spot of the usual body takes its colour from a vanilla villager's texture
                              (red = x, green = y, opaque where mapped): her work clothes are the villager clothes of
                              her trade, put on her body by LayeredSkin
  layers/child_clothes_<n>.png  children's clothes (covered, plain), in outfit n's main colour
  layers/eyes_<n>.png         maiden n's eyes (and lids)
  layers/hair_<n>.png         maiden n's hair (her head except the face, and the long hair layer)

The head is laid out the same on both bodies, so eyes and hair fit either. The layers are numbered from 1 in each
bank; some skins give only some of their parts (HAIR_ONLY: just her hair, without her headband).
"""
import json, os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'maidens', 'textures', 'entity', 'maiden')
OUT = os.path.join(TEX, 'layers')
os.makedirs(OUT, exist_ok=True)
USUAL = json.load(open(os.path.join(ROOT, 'art', 'maiden.bbmodel')))
CASUAL = json.load(open(os.path.join(ROOT, 'art', 'maiden_casual.bbmodel')))
SHADE = {'north': 1.0, 'up': 1.04, 'east': 0.93, 'west': 0.93, 'south': 0.88, 'down': 0.8}
EYES = [(x, y) for y in (11, 12) for x in (7, 8, 9, 12, 13, 14)]
FACE = (7, 7, 15, 15)

def faces(model, names):
    for e in model['elements']:
        if e['name'] not in names:
            continue
        for side, f in e['faces'].items():
            u0, v0, u1, v1 = f['uv']
            yield side, (int(min(u0, u1)), int(min(v0, v1)), int(max(u0, u1)), int(max(v0, v1)))

BODY = ('neck', 'chest', 'breast', 'waist', 'hips', 'right_leg', 'left_leg', 'right_arm', 'left_arm')
HEAD = ('head',)

def dist(a, b):
    return sum((a[i] - b[i]) ** 2 for i in range(3)) ** 0.5

def tone_of(img):
    """Her skin colour: the commonest light, warm colour on her neck and the lower half of her face."""
    counts = {}
    spots = [(x, y) for x in range(8, 14) for y in range(13, 15)] + [(x, y) for _, (u0, v0, u1, v1) in faces(USUAL, ('neck',)) for x in range(u0, u1) for y in range(v0, v1)]
    for p in spots:
        c = img.getpixel(p)
        if c[3] > 200 and c[0] >= c[2] and c[0] > 110:
            counts[c[:3]] = counts.get(c[:3], 0) + 1
    return max(counts, key=counts.get)

def is_skin(c, tone, tol=48):
    return c[3] > 200 and dist(c, tone) < tol

def shade(c, k):
    return tuple(max(0, min(255, int(c[i] * k))) for i in range(3)) + (255,)

def tone_layer(model, img, tone):
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    names = [e['name'] for e in model['elements'] if e['name'] != 'head_layer']
    for side, (u0, v0, u1, v1) in faces(model, names):
        for x in range(u0, u1):
            for y in range(v0, v1):
                k = SHADE[side] - 0.06 * (y - v0) / max(1, v1 - v0) - (0.03 if (x * 7 + y * 3) % 11 == 0 else 0)
                out.putpixel((x, y), shade(tone, k))
    # her face's own shading (nose, mouth, cheeks), with her eyes and hair taken out
    for x in range(FACE[0], FACE[2]):
        for y in range(FACE[1], FACE[3]):
            c = img.getpixel((x, y))
            if (x, y) not in EYES and is_skin(c, tone, 40):
                out.putpixel((x, y), c)
    return out

def clothes_layer(model, img, tone):
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for side, (u0, v0, u1, v1) in faces(model, BODY):
        for x in range(u0, u1):
            for y in range(v0, v1):
                c = img.getpixel((x, y))
                if c[3] > 0 and not is_skin(c, tone):
                    out.putpixel((x, y), c)
    return out

def eyes_layer(img):
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for p in EYES:
        x, _ = p
        c = img.getpixel(p)
        # the outer columns are only part of the eyes where they're dark (lashes), not hair at her temples
        if x in (7, 14) and sum(c[:3]) > 200:
            continue
        out.putpixel(p, c)
    return out

def hair_layer(img, tone):
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for side, (u0, v0, u1, v1) in faces(USUAL, HEAD):
        for x in range(u0, u1):
            for y in range(v0, v1):
                c = img.getpixel((x, y))
                if (x, y) in EYES or c[3] == 0:
                    continue
                # on her face only what isn't skin; elsewhere on her head, all of it
                if side == 'north' and is_skin(c, tone, 40):
                    continue
                if side == 'north' and FACE[1] + 4 <= y and FACE[0] < x < FACE[2] - 1:
                    continue           # below the eyes, between her temples: face, not hair
                out.putpixel((x, y), c)
    for side, (u0, v0, u1, v1) in faces(USUAL, ('head_layer',)):
        for x in range(u0, u1):
            for y in range(v0, v1):
                c = img.getpixel((x, y))
                if c[3] > 0:
                    out.putpixel((x, y), c)
    return out

def main_colour(layer):
    counts = {}
    for x in range(64):
        for y in range(15, 64):
            c = layer.getpixel((x, y))
            if c[3] > 200:
                counts[c[:3]] = counts.get(c[:3], 0) + 1
    return max(counts, key=counts.get) if counts else (90, 90, 120)

def casual_clothes(colour):
    """A plain shift for home: covers her from the neck to the knees, short sleeves, in a soft colour."""
    soft = colour
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    for side, (u0, v0, u1, v1) in faces(CASUAL, BODY):
        for x in range(u0, u1):
            for y in range(v0, v1):
                part = next(e['name'] for e in CASUAL['elements'] for f in e['faces'].values()
                            if e['name'] in BODY and min(f['uv'][0], f['uv'][2]) <= x < max(f['uv'][0], f['uv'][2])
                            and min(f['uv'][1], f['uv'][3]) <= y < max(f['uv'][1], f['uv'][3]))
                rows = max(1, v1 - v0)
                if part in ('right_arm', 'left_arm') and (y - v0) > rows * 0.35 and side not in ('up',):
                    continue                    # short sleeves
                if part in ('right_leg', 'left_leg') and (y - v0) > rows * 0.4 and side not in ('up',):
                    continue                    # to the knee
                if part == 'neck':
                    continue
                k = SHADE[side] - 0.08 * (y - v0) / rows - (0.04 if (x * 3 + y) % 7 == 0 else 0)
                out.putpixel((x, y), shade(soft, k))
    return out

def child_clothes(colour):
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    trousers = tuple(max(0, int(v * 0.6)) for v in colour)
    for side, (u0, v0, u1, v1) in faces(USUAL, BODY):
        for x in range(u0, u1):
            for y in range(v0, v1):
                k = SHADE[side] - 0.1 * (y - v0) / max(1, v1 - v0) - (0.06 if (x + y) % 5 == 0 else 0)
                c = trousers if y >= 32 and x < 34 else colour
                if y >= 56 and x < 34:
                    c = (50, 38, 30)
                out.putpixel((x, y), shade(c, k))
    return out

def casual_layer():
    """The casual texture's clothes (everything on it that isn't skin) on the casual body."""
    img = Image.open(os.path.join(ROOT, 'art', 'skincomp.png')).convert('RGBA')
    return clothes_layer(CASUAL, img, tone_of(img))

# ------------------------------------------------------------------ work clothes: a villager's, on her body

# vanilla VillagerModel boxes: (texture u, v), size (w, h, d)
ROBE = ((0, 38), (8, 20, 6))
VLEG = ((0, 22), (4, 12, 4))
VARM = ((44, 22), (4, 8, 4))

def box_face(box, side):
    """Where a face of a vanilla box is in its texture: (u0, v0, w, h)."""
    (u, v), (w, h, d) = box
    return {'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h),
            'south': (u + d + w + d, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}[side]

def job_map():
    """For each spot on her body (torso and legs, upper arms): the villager texture spot it takes. Heights are
    matched as a share of her height from neck to feet (a villager is 24 tall: the robe covers the top 20)."""
    out = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    top = 27.5
    for e in USUAL['elements']:
        name = e['name']
        if name not in ('chest', 'breast', 'waist', 'hips', 'right_leg', 'left_leg', 'right_arm', 'left_arm'):
            continue
        (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
        for side, f in e['faces'].items():
            u0, v0, u1, v1 = f['uv']
            fu0, fv0, fu1, fv1 = int(min(u0, u1)), int(min(v0, v1)), int(max(u0, u1)), int(max(v0, v1))
            W, H = fu1 - fu0, fv1 - fv0
            for i in range(W):
                for j in range(H):
                    fx, fy = (i + 0.5) / W, (j + 0.5) / H
                    if name in ('right_arm', 'left_arm'):
                        if side in ('north', 'south', 'east', 'west') and fy > 0.62:
                            continue                       # her forearms and hands stay bare
                        su, sv, sw, sh = box_face(VARM, side)
                        sy = fy / 0.62 if side in ('north', 'south', 'east', 'west') else fy
                        src = (su + int(fx * sw), sv + min(sh - 1, int(sy * sh)))
                    else:
                        # the height of this spot (faces up/down are flat: their own height)
                        if side in ('up', 'down'):
                            y = y1 if side == 'up' else y0
                        else:
                            y = y1 - fy * (y1 - y0)
                        t = (top - y) / top * 24                 # rows down from a villager's shoulders
                        s2 = 'north' if name == 'breast' and side not in ('south',) else side
                        if name == 'breast' and s2 == 'north':
                            fx = (fx * (x1 - x0) + x0 + 4) / 8 if side == 'north' else (0.05 if side == 'east' else 0.95)
                        elif side in ('north', 'south', 'up', 'down'):
                            fx = (x0 + 4 + fx * (x1 - x0)) / 8   # across the robe, by where she is
                        if t < 20 or name in ('chest', 'breast', 'waist', 'hips'):
                            su, sv, sw, sh = box_face(ROBE, s2)
                            if s2 in ('up', 'down'):
                                src = (su + min(sw - 1, int(fx * sw)), sv + min(sh - 1, int(fy * sh)))
                            else:
                                src = (su + min(sw - 1, int(fx * sw)), sv + min(sh - 1, int(min(19.99, t))))
                        else:
                            su, sv, sw, sh = box_face(VLEG, side)
                            lx = fx if side in ('east', 'west', 'up', 'down') else (fx * 8 - (0 if fx < 0.5 else 4)) / 4
                            lx = max(0.0, min(0.999, lx))
                            src = (su + int(lx * sw), sv + min(sh - 1, int((t - 12) / 12 * sh)) if side not in ('up', 'down') else sv + int(fy * sh))
                    out.putpixel((fu0 + i, fv0 + j), (src[0], src[1], 0, 255))
    return out

SHIFTS = [(176, 196, 222), (236, 222, 196), (178, 200, 170), (226, 180, 186), (196, 184, 220)]   # soft blue, cream, sage, rose, lavender
HAIR_ONLY = {2: 9}       # skin 2: only her hair, and her headband (this row of her head) painted over

def without_band(hair, row):
    out = hair.copy()
    for x in range(0, 31):
        c = hair.getpixel((x, row - 1))
        if c[3] > 0:
            out.putpixel((x, row), c)
    return out

for f in os.listdir(OUT):
    os.remove(os.path.join(OUT, f))
skins = []
n = 1
while os.path.exists(os.path.join(TEX, f'maiden_{n}.png')):
    skins.append(Image.open(os.path.join(TEX, f'maiden_{n}.png')).convert('RGBA'))
    n += 1
whole = hair = 0
for n, img in enumerate(skins, start=1):
    tone = tone_of(img)
    h = hair_layer(img, tone)
    if n in HAIR_ONLY:
        h = without_band(h, HAIR_ONLY[n])
    else:
        whole += 1
        tone_layer(USUAL, img, tone).save(os.path.join(OUT, f'tone_{whole}.png'))
        tone_layer(CASUAL, img, tone).save(os.path.join(OUT, f'casual_tone_{whole}.png'))
        clothes = clothes_layer(USUAL, img, tone)
        clothes.save(os.path.join(OUT, f'clothes_{whole}.png'))
        child_clothes(main_colour(clothes)).save(os.path.join(OUT, f'child_clothes_{whole}.png'))
        eyes_layer(img).save(os.path.join(OUT, f'eyes_{whole}.png'))
    hair += 1
    h.save(os.path.join(OUT, f'hair_{hair}.png'))
    print('skin', n, 'tone', tone, 'hair only' if n in HAIR_ONLY else '')
casual_layer().save(os.path.join(OUT, 'casual_clothes.png'))
job_map().save(os.path.join(OUT, 'job_map.png'))
print(whole, 'skin tones, clothes and eyes;', hair, 'hairstyles')
