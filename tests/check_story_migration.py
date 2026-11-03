"""Exercise the real offline converter, including all-or-nothing output behavior."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile

tool = Path(__file__).resolve().parents[1] / 'tools/migrate_story_triggers.py'
spec = importlib.util.spec_from_file_location('migrate', tool)
migrate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(migrate)
trigger = dict(id='lake_gate', enabled=True, once=True, type='ENTER_BOX', delayTicks=0,
               dimension='minecraft:overworld', x1=12, x2=10, y1=70, y2=72, z1=-2, z2=2,
               sceneId='', conditions=[dict(key='chapter', value='lake')],
               actions=[dict(key='gate_seen', value='yes')], effects=[])
data = dict(triggers=[trigger])
result = migrate.convert(data)
event = result['events'][0]
assert event['key'] == 'cutscene_trigger:lake_gate' and event['once'] is True
assert event['conditions'] == trigger['conditions'] and event['actions'] == trigger['actions']
assert event['area']['box'] == dict(x1=10, x2=13, y1=70, y2=73, z1=-2, z2=3)
assert data == dict(triggers=[trigger])
assert migrate.convert(dict(triggers=[dict(trigger, enabled=False)]))['events'] == []
for change in (dict(type='EXIT_BOX'), dict(type='SIGNAL'), dict(type='INTERACT_ENTITY'),
               dict(delayTicks=1), dict(effects=[dict(type='COMMAND')]), dict(x1=float('nan')),
               dict(x1=1000), dict(x1=True), dict(once=1), dict(enabled='true'),
               dict(sceneId='../bad'), dict(dimension='bad'), dict(actions=None)):
    try:
        migrate.convert(dict(triggers=[dict(trigger, **change)]))
        raise AssertionError(f'Accepted {change}')
    except ValueError:
        pass
try:
    migrate.convert(dict(triggers=[trigger, copy.deepcopy(trigger)]))
    raise AssertionError('Accepted duplicate')
except ValueError:
    pass
with tempfile.TemporaryDirectory() as folder:
    root = Path(folder)
    scene = dict(id='wdlg_lake', events=[dict(type='DIALOGUE', value='lake')])
    (root / 'wdlg_lake.json').write_text(json.dumps(scene), encoding='utf-8')
    assert len(migrate.convert(dict(triggers=[dict(trigger, sceneId='wdlg_lake')]), root)['events']) == 1
    source, output = root / 'triggers.json', root / 'candidate.json'
    source.write_text(json.dumps(data), encoding='utf-8')
    command = [sys.executable, '-X', 'utf8', str(tool), str(source), '--output', str(output)]
    assert subprocess.run(command, capture_output=True).returncode == 0
    original = output.read_bytes()
    assert json.loads(original) == result
    assert subprocess.run(command, capture_output=True).returncode == 2
    assert output.read_bytes() == original
    output.unlink()
    source.write_text(json.dumps(dict(triggers=[trigger, dict(trigger, id='bad', type='SIGNAL')])), encoding='utf-8')
    assert subprocess.run(command, capture_output=True).returncode == 2 and not output.exists()
    scene['events'].append(dict(type='COMMAND', value='anything'))
    (root / 'wdlg_lake.json').write_text(json.dumps(scene), encoding='utf-8')
    try:
        migrate.convert(dict(triggers=[dict(trigger, sceneId='wdlg_lake')]), root)
        raise AssertionError('Accepted mixed effects')
    except ValueError:
        pass
print('PASS legacy trigger migration, exact box bounds, dialogue verification, rejection and output preservation')
