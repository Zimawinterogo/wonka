from pathlib import Path
import json,zipfile
root=Path(__file__).resolve().parents[1];r=root/'src/main/resources';a=r/'assets/whiteout'
files=list(r.rglob('*.json'))
for p in files:json.loads(p.read_text())
for p in (a/'models/block').glob('*.json'):
 d=json.loads(p.read_text())
 if 'elements' not in d:
  assert d.get('parent','').startswith('minecraft:block/'),p
  assert isinstance(d.get('textures'),dict) and d['textures'],p
  continue
 for e in d['elements']:
  assert all(-16<=x<=32 for x in e['from']+e['to']),p
  assert all(lo<hi for lo,hi in zip(e['from'],e['to'])),p
  for f in e['faces'].values():assert f['texture'][1:] in d['textures'],p
for p in (a/'blockstates').glob('*.json'):
 states=json.loads(p.read_text()); variants=states.get('variants',{})
 if 'multipart' in states:
  variants={str(i):part['apply'] for i,part in enumerate(states['multipart'])}
 else:
  expected={'emergency_lamp':2,'rotten_spruce_planks':1,'frost_window':4,'wall_thermometer':4,'barricade':36}.get(p.stem,8)
  assert len(variants)==expected,p
 for v in variants.values():
  namespace,path=v['model'].split(':',1)
  if namespace=='minecraft':
   assert path in ('block/lantern','block/lantern_hanging'),v
  else:
   assert namespace=='whiteout' and (a/('models/'+path+'.json')).exists(),v
for event, config in json.loads((a/'sounds.json').read_text()).items():
 for sound in config.get('sounds',[]):
  key=sound['name'] if isinstance(sound,dict) else sound
  if ':' in key:namespace,rel=key.split(':',1)
  else:namespace,rel='whiteout',key
  assert namespace=='whiteout' and (a/f'sounds/{rel}.ogg').is_file(),event
assert json.loads((a/'lang/ru_ru.json').read_text()).keys()==json.loads((a/'lang/en_us.json').read_text()).keys()
# Five redesigned items deliberately override placeholders from the frozen base JAR.
approved_item_overrides={f'assets/whiteout/textures/item/{name}.png'
                         for name in ('battery','goggles','headlamp','meteoresonator','radio')}
with zipfile.ZipFile(root/'base/Whiteout-3.4.2-1.21.1.jar') as z:
 assert json.loads(z.read('fabric.mod.json'))['version']=='3.4.2'
 for p in r.rglob('*'):
  if p.is_file():
   rel=p.relative_to(r).as_posix()
   if rel in approved_item_overrides and rel in z.namelist():
    assert p.read_bytes()!=z.read(rel),f'expected redesigned texture for {rel}'
   if rel in z.namelist() and rel.startswith('data/whiteout/') and rel.endswith('.json'):
    # Match the assembler's semantic equality exception; changed loot remains forbidden.
    assert json.loads(p.read_bytes())==json.loads(z.read(rel)),rel
    continue
   assert rel not in z.namelist() or rel in approved_item_overrides or rel.startswith('assets/whiteout/blockstates/') or rel.startswith('assets/whiteout/models/') or (rel.startswith('assets/whiteout/sounds/music_slot_') and rel.endswith('.ogg')) or ((rel.startswith('assets/whiteout/sounds/') or rel.startswith('data/whiteout/')) and p.read_bytes()==z.read(rel)) or rel in ('assets/whiteout/shaders/snow.frag','assets/whiteout/shaders/winter.fsh') or rel in ('fabric.mod.json','assets/whiteout/sounds.json','assets/whiteout/lang/ru_ru.json','assets/whiteout/lang/en_us.json'),rel
print('PASS',len(files),'JSON files; blockstates, models, sounds, locales and base-resource collision checks')
