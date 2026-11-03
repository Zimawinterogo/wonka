"""Check shipped geometry against editable Blockbench sources, including both LODs."""
from pathlib import Path
import importlib.util,json,base64,struct,math
root=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('exporter',root/'tools/export_generator_meshes.py')
exporter=importlib.util.module_from_spec(spec);spec.loader.exec_module(exporter)
def assert_mesh(actual,expected):
    # libm acos/sqrt and floating-point summation vary across OS/Python versions.
    # Geometry, UVs, flags and quad order must remain exact; only unit normals
    # may differ by at most two float32 ULPs at magnitude 1 (absolute 2e-7).
    assert actual[:8]==expected[:8] and len(actual)==len(expected)
    magic,count=struct.unpack_from('>II',actual)
    assert magic==0x57474D31 and len(actual)==8+129*count
    changed=0;largest=0.0
    for quad in range(count):
        offset=8+129*quad
        assert actual[offset]==expected[offset],('lamp',quad)
        for vertex in range(4):
            start=offset+1+vertex*32
            assert actual[start:start+20]==expected[start:start+20],('position/UV',quad,vertex)
            a=struct.unpack_from('>3f',actual,start+20);b=struct.unpack_from('>3f',expected,start+20)
            for x,y in zip(a,b):
                assert math.isfinite(x) and math.isfinite(y) and abs(x-y)<=2e-7,('normal',quad,vertex,x,y)
                if x!=y:changed+=1;largest=max(largest,abs(x-y))
    return changed,largest
for name,game,cubes in [('new_generator','generator',176),('old_generator','old_generator',219)]:
    model=json.loads((root/'model-source/generators'/f'{name}.bbmodel').read_text(encoding='utf8'))
    assert sum(e['type']=='cube' for e in model['elements'])==cubes
    assert sum(e['type']=='mesh' for e in model['elements'])==6
    for low in (False,True):
        data,count=exporter.export(model,low)
        changed,largest=assert_mesh(data,(exporter.DEST/'meshes'/f'{game}{"_far" if low else ""}.mesh').read_bytes())
        if changed:print(f'{game} low={low}: {changed} normal components differ, max absolute delta {largest:g}')
    assert base64.b64decode(model['textures'][0]['source'].split(',',1)[1])==(exporter.DEST/'textures/block'/f'{name}_atlas.png').read_bytes()
    item=json.loads((exporter.DEST/'models/item'/f'{game}.json').read_text())
    assert item['parent']=='minecraft:builtin/entity'
print('PASS exact geometry/UV/flags, bounded normal rounding, distant LOD, original atlases, dynamic item model registration')
