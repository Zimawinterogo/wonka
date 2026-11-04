"""Review-only migration of supported legacy triggers to a server-owned catalog.

No client files are trusted automatically; an operator reviews and installs the result.
Unsupported enabled triggers abort the entire conversion, never a partial cutover.
"""
import argparse
import json
import math
from pathlib import Path
import re
from uuid import UUID


def read_json(path):
    if path.stat().st_size > 1048576:
        raise ValueError(f'{path.name}: input exceeds 1 MiB')
    return json.loads(path.read_text(encoding='utf-8-sig'),
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))


def convert(data, scenes=None, dialogues=None):
    if not isinstance(data, dict) or not isinstance(data.get('triggers'), list):
        raise ValueError('Expected triggers[]')
    events = {}

    def add(key, source, binding):
        if len(events) >= 128 or not re.fullmatch(r'[a-zA-Z0-9_.:/-]{1,128}', key):
            raise ValueError(f'Invalid or excessive event: {key}')
        once = source.get('once', False)
        if type(once) is not bool:
            raise ValueError(f'{key}: invalid once')
        event = dict(key=key, once=once, trigger=binding)
        for field in ('conditions', 'actions'):
            rules = source.get(field, [])
            if not isinstance(rules, list) or any(not isinstance(r, dict) for r in rules):
                raise ValueError(f'{key}: invalid {field}')
            event[field] = rules
        events[key] = event
        return event

    def scene_dialogue(scene):
        if not isinstance(scene, str) or not re.fullmatch(r'[a-zA-Z0-9_-]+', scene) or scenes is None:
            raise ValueError('Scene directory and safe scene id required')
        doc = read_json(Path(scenes) / (scene + '.json'))
        lines = doc.get('events', [])
        if doc.get('id') != scene or len(lines) != 1 or lines[0].get('type') != 'DIALOGUE':
            raise ValueError(f'{scene}: review cinematic events manually')
        return lines[0]['value']

    def effects(event, source):
        script = None
        for effect in source.get('effects', []):
            kind = effect['type']
            if kind in ('RADIO', 'SOUND'):
                continue
            if kind in ('SCENE', 'DIALOGUE') and script is None:
                script = scene_dialogue(effect['target']) if kind == 'SCENE' else effect['target']
            elif kind in ('OBJECTIVE_START', 'OBJECTIVE_UPDATE', 'OBJECTIVE_FAIL', 'OBJECTIVE_COMPLETE'):
                event.setdefault('objectives', []).append(dict(id=effect['target'], event=kind[10:], text=effect.get('text', '')))
            else:
                raise ValueError(f'Unsupported effect combination: {kind}')
        return script

    def branch(script, index=0):
        if not isinstance(dialogues, dict) or script not in dialogues.get('dialogues', {}):
            raise ValueError(f'--dialogues must contain {script}')
        lines = dialogues['dialogues'][script]['lines']
        while 0 <= index < len(lines):
            line = lines[index]
            if line.get('auto_reply') or line.get('prewritten_reply'):
                raise ValueError(f'{script}:{index}: review automatic reply manually')
            choices = line.get('choices', [])
            if not choices:
                index += 1
                continue
            keys = [f'dialogue_choice:{script}:{index}:{i}' for i in range(len(choices))]
            if keys[0] in events:
                return keys
            for key, choice in zip(keys, choices):
                add(key, choice, dict(type='CHOICE'))
            for key, choice in zip(keys, choices):
                binding = events[key]['trigger']
                opened = effects(events[key], choice)
                command = choice.get('command', '')
                if command:
                    if opened or not command.lower().startswith('director_signal:') or not command[16:].strip():
                        raise ValueError(f'{key}: unsupported command')
                    binding['emit'] = command[16:].strip()
                elif opened or choice.get('dialogue'):
                    binding['nextChoices'] = branch(opened or choice['dialogue'])
                else:
                    dest = choice.get('next', -1)
                    binding['nextChoices'] = branch(script, index + 1 if dest < 0 else dest)
            return keys
        return []

    for trigger in data['triggers']:
        if trigger.get('enabled') is False:
            continue
        if trigger.get('enabled') is not True or type(trigger.get('once')) is not bool:
            raise ValueError('Explicit enabled and once flags required')
        key = 'cutscene_trigger:' + trigger['id']
        if key in events or not trigger['id']:
            raise ValueError(f'Duplicate or empty trigger: {key}')
        kind, delay = trigger.get('type'), trigger.get('delayTicks', 0)
        if type(delay) is not int or not 0 <= delay <= 72000:
            raise ValueError(f'{key}: invalid delay')
        binding = dict(type=kind, delayTicks=delay)
        event = add(key, trigger, binding)
        if kind in ('ENTER_BOX', 'EXIT_BOX'):
            dimension = trigger.get('dimension', '')
            if len(dimension) > 128 or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', dimension):
                raise ValueError(f'{key}: invalid dimension')
            box = {}
            for axis in 'xyz':
                a, b = trigger.get(axis + '1'), trigger.get(axis + '2')
                if any(type(v) not in (int, float) or not math.isfinite(v) for v in (a, b)):
                    raise ValueError(f'{key}: invalid bounds')
                lo, hi = sorted((math.floor(a), math.floor(b)))
                hi += 3 if axis == 'y' and lo == hi else 1
                limit = 2048 if axis == 'y' else 30000000
                if hi - lo > 256 or max(abs(lo), abs(hi)) > limit:
                    raise ValueError(f'{key}: bounds exceed catalog limits')
                box[axis + '1'], box[axis + '2'] = lo, hi
            event['area'] = dict(dimension=dimension, box=box)
        elif kind in ('SIGNAL', 'INTERACT_ENTITY'):
            target = trigger.get('target', '')
            if not target.strip() or len(target) > 128:
                raise ValueError(f'{key}: named target required')
            if kind == 'INTERACT_ENTITY' and str(UUID(target)) != target.lower():
                raise ValueError(f'{key}: expected NPC UUID')
            binding['target'] = target
        else:
            raise ValueError(f'{key}: unknown trigger type')
        script = effects(event, trigger)
        if trigger.get('sceneId'):
            if script:
                raise ValueError(f'{key}: overlapping presentations')
            script = scene_dialogue(trigger['sceneId'])
        if script:
            binding['nextChoices'] = branch(script)
    return dict(schema=2, events=list(events.values()))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('triggers', type=Path)
    parser.add_argument('--scenes', type=Path, help='Directory containing generated wdlg_*.json scenes')
    parser.add_argument('--output', type=Path, help='Create a NEW candidate file; otherwise print a preview')
    parser.add_argument('--dialogues', type=Path, help='Legacy dialogue definitions referenced by these triggers')
    args = parser.parse_args(argv)
    try:
        dialogues = read_json(args.dialogues) if args.dialogues else None
        result = json.dumps(convert(read_json(args.triggers), args.scenes, dialogues), ensure_ascii=False, indent=2, allow_nan=False) + '\n'
        if len(result.encode('utf-8')) > 262144:
            raise ValueError('Catalog exceeds 256 KiB')
        if args.output:
            with args.output.open('x', encoding='utf-8', newline='\n') as target:
                target.write(result)
        else:
            print(result, end='')
    except (ValueError, OSError, TypeError, AttributeError, KeyError, IndexError, OverflowError, RecursionError) as failure:
        parser.exit(2, f'Migration refused: {failure}\n')


if __name__ == '__main__':
    main()
