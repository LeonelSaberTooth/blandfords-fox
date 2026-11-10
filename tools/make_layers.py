"""Splits the maiden skins into layers, so every maiden can be put together from parts (see LayeredSkin):

  layers/tone_<n>.png         her skin, all over (from maiden n's skin colour, with her face's own shading)
  layers/casual_tone_<n>.png  the same on the casual body (thinner legs: different texture spots)
  layers/clothes_<n>.png      maiden n's clothes (everything on her body that isn't skin)
  layers/casual_clothes.png   the casual clothes (from casual_1), on the casual body
  layers/child_clothes_<n>.png  children's clothes (covered, plain), in outfit n's main colour
  layers/eyes_<n>.png         maiden n's eyes (and lids)
  layers/hair_<n>.png         maiden n's hair (her head except the face, and the long hair layer)

The head is laid out the same on both bodies, so eyes and hair fit either.
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

skins = []
n = 1
while os.path.exists(os.path.join(TEX, f'maiden_{n}.png')):
    skins.append(Image.open(os.path.join(TEX, f'maiden_{n}.png')).convert('RGBA'))
    n += 1
casual = Image.open(os.path.join(TEX, 'casual_1.png')).convert('RGBA')
tones = [tone_of(s) for s in skins]
for i, (img, tone) in enumerate(zip(skins, tones), start=1):
    tone_layer(USUAL, img, tone).save(os.path.join(OUT, f'tone_{i}.png'))
    tone_layer(CASUAL, img, tone).save(os.path.join(OUT, f'casual_tone_{i}.png'))
    clothes = clothes_layer(USUAL, img, tone)
    clothes.save(os.path.join(OUT, f'clothes_{i}.png'))
    child_clothes(main_colour(clothes)).save(os.path.join(OUT, f'child_clothes_{i}.png'))
    eyes_layer(img).save(os.path.join(OUT, f'eyes_{i}.png'))
    hair_layer(img, tone).save(os.path.join(OUT, f'hair_{i}.png'))
    print('layers', i, 'tone', tone)
clothes_layer(CASUAL, casual, tone_of(casual)).save(os.path.join(OUT, 'casual_clothes.png'))
print('casual clothes, tone', tone_of(casual))
