"""Review-only migration of supported legacy triggers to a server-owned catalog.

No client files are trusted automatically; an operator reviews and installs the result.
Unsupported enabled triggers abort the entire conversion, never a partial cutover.
"""
import argparse
import json
import math
from pathlib import Path
import re


def read_json(path):
    if path.stat().st_size > 1048576:
        raise ValueError(f'{path.name}: input exceeds 1 MiB')
    return json.loads(path.read_text(encoding='utf-8-sig'),
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))


def convert(data, scenes=None):
    if not isinstance(data, dict) or not isinstance(data.get('triggers'), list):
        raise ValueError('Expected an object containing triggers[]')
    events, keys = [], set()
    for trigger in data['triggers']:
        if not isinstance(trigger, dict):
            raise ValueError('Invalid trigger')
        if trigger.get('enabled') is False:
            continue
        if trigger.get('enabled') is not True:
            raise ValueError('Explicit enabled flag required')
        ident = trigger.get('id')
        if not isinstance(ident, str) or not re.fullmatch(r'[a-zA-Z0-9_.:/-]{1,111}', ident):
            raise ValueError('Invalid trigger id')
        key = 'cutscene_trigger:' + ident
        if key in keys:
            raise ValueError(f'{ident}: duplicate key')
        keys.add(key)
        if trigger.get('type') != 'ENTER_BOX' or trigger.get('delayTicks', 0) != 0:
            raise ValueError(f'{ident}: only immediate ENTER_BOX is supported')
        if trigger.get('effects', []) != []:
            raise ValueError(f'{ident}: effects require a server scenario; migration refused')
        scene_id = trigger.get('sceneId', '')
        if scene_id:
            if not isinstance(scene_id, str) or not re.fullmatch(r'wdlg_[a-zA-Z0-9_-]+', scene_id) or scenes is None:
                raise ValueError(f'{ident}: a verified generated dialogue scene is required')
            scene = read_json(Path(scenes) / (scene_id + '.json'))
            content = scene.get('events') if isinstance(scene, dict) else None
            if (not isinstance(scene, dict) or scene.get('id') != scene_id or not isinstance(content, list) or len(content) != 1
                    or not isinstance(content[0], dict) or content[0].get('type') != 'DIALOGUE'
                    or not isinstance(content[0].get('value'), str) or not content[0]['value']):
                raise ValueError(f'{ident}: scene is not a generated dialogue-only scene')
        dimension = trigger.get('dimension')
        if not isinstance(dimension, str) or len(dimension) > 128 or not re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9/._-]+', dimension):
            raise ValueError(f'{ident}: invalid dimension')
        box = {}
        for axis in 'xyz':
            a, b = trigger.get(axis + '1'), trigger.get(axis + '2')
            if any(type(v) not in (int, float) or not math.isfinite(v) for v in (a, b)):
                raise ValueError(f'{ident}: invalid {axis} bounds')
            lo, hi = min(a, b), max(a, b) + 1
            limit = 2048 if axis == 'y' else 30000000
            if hi - lo > 256 or lo >= hi or abs(lo) > limit or abs(hi) > limit:
                raise ValueError(f'{ident}: {axis} bounds exceed catalog limits')
            box[axis + '1'], box[axis + '2'] = lo, hi
        if type(trigger.get('once')) is not bool:
            raise ValueError(f'{ident}: explicit once flag required')
        rules = {}
        for name in ('conditions', 'actions'):
            value = trigger.get(name, [])
            if not isinstance(value, list) or any(not isinstance(rule, dict) for rule in value):
                raise ValueError(f'{ident}: invalid {name}')
            # Preserve author data verbatim; the production StoryRules validator remains final.
            rules[name] = value
        events.append(dict(key=key, once=trigger['once'], area=dict(dimension=dimension, box=box), **rules))
    if len(events) > 128:
        raise ValueError('More than 128 enabled events')
    return dict(schema=1, events=events)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('triggers', type=Path)
    parser.add_argument('--scenes', type=Path, help='Directory containing generated wdlg_*.json scenes')
    parser.add_argument('--output', type=Path, help='Create a NEW candidate file; otherwise print a preview')
    args = parser.parse_args(argv)
    try:
        result = json.dumps(convert(read_json(args.triggers), args.scenes), ensure_ascii=False, indent=2, allow_nan=False) + '\n'
        if len(result.encode('utf-8')) > 262144:
            raise ValueError('Catalog exceeds 256 KiB')
        if args.output:
            with args.output.open('x', encoding='utf-8', newline='\n') as target:
                target.write(result)
        else:
            print(result, end='')
    except (ValueError, OSError) as failure:
        parser.exit(2, f'Migration refused: {failure}\n')


if __name__ == '__main__':
    main()
