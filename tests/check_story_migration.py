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
fixtures = Path(__file__).resolve().parent / 'fixtures'
data = migrate.read_json(fixtures / 'story-migration-legacy.json')
trigger = data['triggers'][0]
result = migrate.convert(data)
assert result == migrate.read_json(fixtures / 'story-migration-catalog.json')
event = result['events'][0]
assert event['key'] == 'cutscene_trigger:lake_gate' and event['once'] is True
assert event['conditions'] == trigger['conditions'] and event['actions'] == trigger['actions']
assert event['area']['box'] == dict(x1=10, x2=13, y1=70, y2=73, z1=-2, z2=3)
assert data == dict(triggers=[trigger])
assert migrate.convert(dict(triggers=[dict(trigger, enabled=False)]))['events'] == []
exit_event = migrate.convert(dict(triggers=[dict(trigger, type='EXIT_BOX', delayTicks=7, y1=70.1, y2=70.9)]))['events'][0]
assert exit_event['trigger'] == dict(type='EXIT_BOX', delayTicks=7)
assert exit_event['area']['box']['y2'] == 73
for kind, target in [('SIGNAL', 'ready'), ('INTERACT_ENTITY', 'ab3b9880-55fb-4351-9a19-25f348ab6057')]:
    event = migrate.convert(dict(triggers=[dict(trigger, type=kind, target=target)]))['events'][0]
    assert event['trigger']['target'] == target and 'area' not in event
for change in (dict(type='SIGNAL'), dict(type='INTERACT_ENTITY'),
               dict(delayTicks=-1), dict(effects=[dict(type='COMMAND')]), dict(x1=float('nan')),
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
    dialogue = dict(dialogues=dict(lake=dict(lines=[dict(choices=[
        dict(text='again', next=0), dict(text='done', once=True, command='director_signal:ready',
            effects=[dict(type='OBJECTIVE_COMPLETE', target='find'), dict(type='SOUND', target='chime')])])])))
    events = migrate.convert(dict(triggers=[dict(trigger, sceneId='wdlg_lake')]), root, dialogue)['events']
    assert events[0]['trigger']['nextChoices'] == [e['key'] for e in events[1:]]
    assert events[1]['trigger']['nextChoices'] == events[0]['trigger']['nextChoices']
    assert events[2]['once'] and events[2]['trigger']['emit'] == 'ready'
    assert events[2]['objectives'][0]['event'] == 'COMPLETE'
    source, output = root / 'triggers.json', root / 'candidate.json'
    source.write_text(json.dumps(data), encoding='utf-8')
    command = [sys.executable, '-X', 'utf8', str(tool), str(source), '--output', str(output)]
    assert subprocess.run(command, capture_output=True).returncode == 0
    original = output.read_bytes()
    assert json.loads(original) == result
    audit = subprocess.run(command[:5] + ['--audit'], capture_output=True)
    assert audit.returncode == 0 and json.loads(audit.stdout)['bindings'][0]['key'] == 'cutscene_trigger:lake_gate'
    assert subprocess.run(command, capture_output=True).returncode == 2
    assert output.read_bytes() == original
    output.unlink()
    source.write_text(json.dumps(dict(triggers=[trigger, dict(trigger, id='bad', type='SIGNAL')])), encoding='utf-8')
    assert subprocess.run(command, capture_output=True).returncode == 2 and not output.exists()
    audit = subprocess.run(command[:5] + ['--audit'], capture_output=True)
    assert audit.returncode == 2 and json.loads(audit.stdout)['rejected'][0]['index'] == 1
    scene['events'].append(dict(type='COMMAND', value='anything'))
    (root / 'wdlg_lake.json').write_text(json.dumps(scene), encoding='utf-8')
    try:
        migrate.convert(dict(triggers=[dict(trigger, sceneId='wdlg_lake')]), root)
        raise AssertionError('Accepted mixed effects')
    except ValueError:
        pass
print('PASS legacy trigger migration, exact box bounds, dialogue verification, rejection and output preservation')
