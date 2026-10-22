"""Check shipped geometry against editable Blockbench sources, including both LODs."""
from pathlib import Path
import importlib.util,json,base64
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('exporter',root/'tools/export_generator_meshes.py')
exporter=importlib.util.module_from_spec(spec);spec.loader.exec_module(exporter)
for name,game,cubes in [('new_generator','generator',176),('old_generator','old_generator',219)]:
    model=json.loads((root/'model-source/generators'/f'{name}.bbmodel').read_text(encoding='utf8'))
    assert sum(e['type']=='cube' for e in model['elements'])==cubes
    assert sum(e['type']=='mesh' for e in model['elements'])==6
    for low in (False,True):
        data,count=exporter.export(model,low)
        assert data==(exporter.DEST/'meshes'/f'{game}{"_far" if low else ""}.mesh').read_bytes()
    assert base64.b64decode(model['textures'][0]['source'].split(',',1)[1])==(exporter.DEST/'textures/block'/f'{name}_atlas.png').read_bytes()
    item=json.loads((exporter.DEST/'models/item'/f'{game}.json').read_text())
    assert item['parent']=='minecraft:builtin/entity'
print('PASS exact Blockbench-to-game export, distant LOD, original atlases, dynamic item model registration')
