"""Check generated physics table against the shipped, independent JSON assets."""
import importlib.util
import json
import re
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'src/main/java/dev/whiteout/power/barricade/ConnectedBarricadeLayouts.java').read_text()
body=source.split('BOXES=',1)[1].split(';',1)[0]
table=json.loads(body.replace('{','[').replace('}',']'))
checks=0
for phase in range(2):
 for mask in range(16):
  model=json.loads((root/f'src/main/resources/assets/whiteout/models/block/barricade_connected_{phase}_{mask}.json').read_text())
  wood=[e['from']+e['to'] for e in model['elements'] if e['faces']['south']['texture']=='#wood']
  assert table[phase][mask]==wood,(phase,mask)
  assert len(wood)<=4
  checks+=1
print(f'PASS {checks} shipped-model / physical-layout comparisons; <=4 cuboids per shape')
