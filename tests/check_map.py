from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
config=json.loads((root/'map-config/whiteout-grid.json').read_text())
observed=json.loads((root/'map-config/observed-from-region.json').read_text())
assert len(config['lamps'])==len(observed['lamps'])==9
assert {(v['dimension'],v['x'],v['y'],v['z'],v['original']) for v in config['lamps']}=={(v['dimension'],v['x'],v['y'],v['z'],v['original']) for v in observed['lamps']}
assert (config['panelX'],config['panelY'],config['panelZ'])==(observed['panel']['x'],observed['panel']['y'],observed['panel']['z'])
assert (config['generatorX'],config['generatorY'],config['generatorZ'])==(observed['generator']['x'],observed['generator']['y'],observed['generator']['z'])
assert sum(v['original']=='minecraft:lantern' for v in config['lamps'])==4
assert sum(v['original']=='furniture:lamp_wall_green' for v in config['lamps'])==2
assert sum(v['original'].startswith('furniture:lamp_') and 'white' in v['original'] for v in config['lamps'])==3
assert not config['triggerSet']
print('PASS: map config equals 9 observed fixtures and two devices; trigger unset')
