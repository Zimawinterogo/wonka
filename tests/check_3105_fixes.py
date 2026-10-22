"""Retained historical asset checks; gameplay is covered by executable Java tests."""
from pathlib import Path
import struct
from zipfile import ZipFile
import json
r=Path(__file__).resolve().parents[1]
a=r/'src/main/resources/assets/whiteout'
for mode in ('off','on'):
 p=a/f'models/block/emergency_lamp_{mode}.json'
 d=json.loads(p.read_text());assert len(d['elements'])>=3
 assert 'minecraft:block/redstone_lamp' not in p.read_text()
 assert (a/f'textures/block/emergency_lamp_{mode}.png').exists()
def dimensions(path):
 data=path.read_bytes()
 assert data[:8]==bytes.fromhex('89504e470d0a1a0a')
 return struct.unpack('>II',data[16:24])
assert dimensions(a/'textures/block/wall_thermometer.png')==(16,16)
assert dimensions(a/'textures/block/rime_glass_weather.png')==(32,32)
with ZipFile(r/'base/Whiteout-3.4.2-1.21.1.jar') as z:
 assert z.read('assets/whiteout/textures/block/rime_glass.png') != (a/'textures/block/rime_glass_weather.png').read_bytes()
for name in ('old_house_creak','old_window_rattle','old_door_slam','door_ice_crack'):
 assert (a/f'sounds/{name}.ogg').stat().st_size>1500
print('PASS retained emergency lamp, glass, thermometer and atmosphere assets')
