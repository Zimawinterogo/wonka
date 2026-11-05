"""Resolve packaged board assets against the actual Minecraft client resources.

Also check quadrant topology after all four blockstate rotations. No image-name
allowlists: a reference must resolve to a real PNG inside the release or client.
"""
import json
import os
from pathlib import Path
from zipfile import ZipFile

root = Path(__file__).resolve().parents[1]
gradle = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle'))
clients = list((gradle/'caches/fabric-loom').glob('*/minecraft-client.jar'))
assert clients, 'Minecraft client resources required (run Gradle first)'
release = root/'build/release/Whiteout-3.13.0-1.21.1.jar'
with ZipFile(release) as mod, ZipFile(clients[0]) as vanilla:
    def load(name):
        return json.loads(mod.read(name))

    def resolve(model):
        textures = model['textures']
        for element in model['elements']:
            for face in element['faces'].values():
                ref = face['texture']
                seen = set()
                while ref.startswith('#'):
                    assert ref not in seen, f'cyclic texture {ref}'
                    seen.add(ref)
                    ref = textures[ref[1:]]
                namespace, path = ref.split(':')
                archive = vanilla if namespace == 'minecraft' else mod
                data = archive.read(f'assets/{namespace}/textures/{path}.png')
                assert data.startswith(b'\x89PNG\r\n\x1a\n'), ref

    variants = load('assets/whiteout/blockstates/story_board.json')['variants']
    faces = ['north', 'east', 'south', 'west']
    for rotation, face in enumerate(faces):
        rectangles = []
        for part in range(4):
            state = variants[f'facing={face},part={part}']
            assert state.get('y', 0) == rotation*90
            assert not state.get('uvlock', False), 'UV must rotate with board'
            namespace, name = state['model'].split(':')
            model = load(f'assets/{namespace}/models/{name}.json')
            resolve(model)
            surface = model['elements'][0]
            assert surface['from'] == [0, 0, 14] and surface['to'] == [16, 16, 16]
            uv = surface['faces']['north']['uv']
            left, top = part % 2 * 8, (1 - part // 2) * 8
            assert uv == [left, top, left+8, top+8], (face, part, uv)
            rectangles.append(uv)
        assert len({tuple(uv) for uv in rectangles}) == 4
        for row in [0, 2]:
            assert rectangles[row][2] == rectangles[row+1][0], 'horizontal seam'
        for col in [0, 1]:
            assert rectangles[col+2][3] == rectangles[col][1], 'vertical seam'
    item = load('assets/whiteout/models/item/story_board.json')
    resolve(item)
    fronts = [e['faces']['north']['uv'] for e in item['elements']
              if e['faces']['north']['texture'] == '#board']
    assert len(fronts) == 4 and len({tuple(uv) for uv in fronts}) == 4
print('PASS packaged board: all texture references, 16 orientations/quadrants, UV seams and complete inventory model')
