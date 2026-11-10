"""Makes child_<n>.png, the children's clothes, from maiden_<n>.png: her own head, face, hair and neck, and plain
covered clothes (a long-sleeved tunic, trousers and shoes, shaded a little from top to bottom) everywhere else.
Children never wear the grown-ups' outfits."""
import json, os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'maidens', 'textures', 'entity', 'maiden')
J = json.load(open(os.path.join(ROOT, 'art', 'maiden.bbmodel')))
TUNICS = [(70, 110, 160), (150, 70, 60), (80, 130, 80), (170, 140, 70), (110, 80, 140), (60, 120, 130)]
TROUSERS = [(90, 70, 50), (60, 60, 80), (100, 85, 60), (70, 60, 55), (85, 75, 70), (75, 65, 50)]
SHOES = (50, 38, 30)

def faces(name):
    for e in J['elements']:
        if e['name'] != name:
            continue
        for side, face in e['faces'].items():
            u0, v0, u1, v1 = face['uv']
            yield side, (int(min(u0, u1)), int(min(v0, v1)), int(max(u0, u1)), int(max(v0, v1)))

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)

def paint(img, name, colour, shoes=False):
    for side, (u0, v0, u1, v1) in faces(name):
        for x in range(u0, u1):
            for y in range(v0, v1):
                rows = max(1, v1 - v0)
                k = 1.05 - 0.15 * (y - v0) / rows - (0.08 if (x + y) % 5 == 0 else 0)
                c = SHOES if shoes and side not in ('up',) and y >= v1 - 2 else colour
                img.putpixel((x, y), shade(c, k))

n = 1
while os.path.exists(os.path.join(TEX, f'maiden_{n}.png')):
    skin = Image.open(os.path.join(TEX, f'maiden_{n}.png')).convert('RGBA')
    out = Image.new('RGBA', skin.size, (0, 0, 0, 0))
    for part in ('head', 'neck', 'head_layer'):
        for side, (u0, v0, u1, v1) in faces(part):
            for x in range(u0, u1):
                for y in range(v0, v1):
                    out.putpixel((x, y), skin.getpixel((x, y)))
    tunic, trousers = TUNICS[(n - 1) % len(TUNICS)], TROUSERS[(n - 1) % len(TROUSERS)]
    for part in ('chest', 'breast', 'waist', 'right_arm', 'left_arm'):
        paint(out, part, tunic)
    for part in ('hips',):
        paint(out, part, trousers)
    for part in ('right_leg', 'left_leg'):
        paint(out, part, trousers, shoes=True)
    out.save(os.path.join(TEX, f'child_{n}.png'))
    n += 1
print('children', n - 1)
