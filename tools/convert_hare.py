"""Turns art/hare.bbmodel into the GeckoLib files for the hare.

RUN and IDLE come from the .bbmodel unchanged. The head and ears hold a cube for each
coat ("_white" / "_desert"); each of those cubes gets a bone of its own here so the
renderer can show the one that matches the hare.
Textures: texturehare.png (white, snowy biomes) and texturedeserthare.png (desert).
"""
import base64, json, os, uuid
import convert_model as cm

SRC = os.path.join(cm.ROOT, 'art', 'hare.bbmodel')
NAME = 'hare'
ASSETS = cm.ASSETS
TEXTURES = {'texturehare.png': 'hare_white.png', 'texturedeserthare.png': 'hare_desert.png'}


def split_coats(j):
    """Wrap each *_white / *_desert cube in its own group with the parent's pivot."""
    groups = {g['uuid']: g for g in j['groups']}
    elems = {e['uuid']: e for e in j['elements']}

    def walk(node):
        g = groups[node['uuid']]
        kids = []
        for c in node['children']:
            if isinstance(c, str) and elems[c]['name'].endswith(('_white', '_desert')):
                nid = str(uuid.uuid4())
                j['groups'].append({'name': elems[c]['name'], 'uuid': nid, 'origin': list(g['origin']), 'rotation': [0, 0, 0]})
                kids.append({'uuid': nid, 'children': [c]})
            else:
                if isinstance(c, dict): walk(c)
                kids.append(c)
        node['children'] = kids

    for n in j['outliner']:
        if isinstance(n, dict): walk(n)


def main():
    j = json.load(open(SRC))
    split_coats(j)
    for d in ('geo', 'animations', 'textures'):
        os.makedirs(os.path.join(ASSETS, d, 'entity'), exist_ok=True)
    geo = cm.build_geo(j, NAME, mouth=False)
    names = [b['name'] for b in geo['minecraft:geometry'][0]['bones']]
    for coat in ('head_white', 'head_desert', 'ear_left_white', 'ear_left_desert', 'ear_right_white', 'ear_right_desert'):
        assert coat in names, coat
    json.dump(geo, open(os.path.join(ASSETS, 'geo', 'entity', NAME + '.geo.json'), 'w'), indent=1)
    anims = {a['name'].lower(): cm.from_bbmodel(a, j) for a in j.get('animations', [])}
    for need in ('run', 'idle'):
        assert need in anims, f'{need} missing from the .bbmodel'
    for a in anims.values():
        cm.to_bedrock(a)
    out = {'format_version': '1.8.0', 'animations': {f'animation.{NAME}.{k}': v for k, v in anims.items()}}
    json.dump(out, open(os.path.join(ASSETS, 'animations', 'entity', NAME + '.animation.json'), 'w'), indent=1)
    for t in j['textures']:
        open(os.path.join(ASSETS, 'textures', 'entity', TEXTURES[t['name']]), 'wb').write(base64.b64decode(t['source'].split(',', 1)[1]))
    print('bones:', ', '.join(names))
    print('animations:', ', '.join(anims))


if __name__ == '__main__':
    main()
