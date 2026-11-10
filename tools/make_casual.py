"""Makes casual_<n>.png (her clothes at home) for every maiden from casual_1.png (made for maiden 1).

casual_1 is maiden 1's skin in casual clothes. For maiden n: her own head, face, hair and neck (the head, neck and
head_layer parts of art/maiden.bbmodel), and maiden 1's skin tones in the casual texture shifted to maiden n's skin
tone (taken from her face), keeping the shading; the clothes stay as drawn.
"""
import json, os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'maidens', 'textures', 'entity', 'maiden')
J = json.load(open(os.path.join(ROOT, 'art', 'maiden.bbmodel')))
OWN = ('head', 'neck', 'head_layer')   # parts taken whole from each maiden's own skin

def regions(names):
    out = []
    for e in J['elements']:
        if e['name'] not in names:
            continue
        for face in e['faces'].values():
            u0, v0, u1, v1 = face['uv']
            out.append((int(min(u0, u1)), int(min(v0, v1)), int(max(u0, u1)), int(max(v0, v1))))
    return out

def is_skin(c):
    r, g, b, a = c
    if a < 200: return False
    mx, mn = max(r, g, b), min(r, g, b)
    return r >= g >= b and mx > 120 and (mx - mn) > 20 and (r - b) < 140 and (r - g) < 70

def skin_tone(img, face_regions):
    """The commonest skin-like colour on her face."""
    counts = {}
    for (u0, v0, u1, v1) in face_regions:
        for x in range(u0, u1):
            for y in range(v0, v1):
                c = img.getpixel((x, y))
                if is_skin(c):
                    counts[c] = counts.get(c, 0) + 1
    return max(counts, key=counts.get)

base = Image.open(os.path.join(TEX, 'casual_1.png')).convert('RGBA')
first = Image.open(os.path.join(TEX, 'maiden_1.png')).convert('RGBA')
own = regions(OWN)
# her skin tone: from her neck and the lower half of her face (not her hair)
face = regions(('neck',))
for e in J['elements']:
    if e['name'] == 'head':
        u0, v0, u1, v1 = e['faces']['north']['uv']
        u0, u1 = sorted((int(u0), int(u1)))
        v0, v1 = sorted((int(v0), int(v1)))
        face.append((u0 + 1, (v0 + v1) // 2, u1 - 1, v1))
tone1 = skin_tone(first, face)
n = 2
while os.path.exists(os.path.join(TEX, f'maiden_{n}.png')):
    skin = Image.open(os.path.join(TEX, f'maiden_{n}.png')).convert('RGBA')
    tone = skin_tone(skin, face)
    out = base.copy()
    # skin tones shifted to hers (the same shading, relative to maiden 1's own tone)
    for x in range(64):
        for y in range(64):
            c = out.getpixel((x, y))
            if is_skin(c):
                out.putpixel((x, y), tuple(max(0, min(255, c[i] + tone[i] - tone1[i])) for i in range(3)) + (c[3],))
    # her own head, face, hair and neck
    for (u0, v0, u1, v1) in own:
        for x in range(u0, u1):
            for y in range(v0, v1):
                out.putpixel((x, y), skin.getpixel((x, y)))
    out.save(os.path.join(TEX, f'casual_{n}.png'))
    print('casual', n, 'tone', tone[:3], 'from', tone1[:3])
    n += 1
