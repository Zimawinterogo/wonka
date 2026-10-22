"""Read-only resource audit of the base + source overlay; never builds a JAR.

Run: python -X utf8 tools/audit_resources.py --output audit-current/resources.json
Unreferenced candidates are conservative static findings, not deletion advice.
"""
import argparse
import json
import re
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def strict_json(data):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('duplicate key: ' + key)
            result[key] = value
        return result
    def invalid(value):
        raise ValueError('non-JSON number: ' + value)
    return json.loads(data, object_pairs_hook=pairs, parse_constant=invalid)


def png(data):
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('PNG signature')
    at, compressed, ended = 8, bytearray(), False
    while at < len(data):
        size = struct.unpack_from('>I', data, at)[0]
        kind = data[at+4:at+8]
        body = data[at+8:at+8+size]
        crc = struct.unpack_from('>I', data, at+8+size)[0]
        if zlib.crc32(kind + body) & 0xffffffff != crc:
            raise ValueError('PNG CRC')
        if kind == b'IHDR' and (size != 13 or not all(struct.unpack('>II', body[:8]))):
            raise ValueError('PNG dimensions')
        if kind == b'IDAT':
            compressed.extend(body)
        at += size + 12
        if kind == b'IEND':
            ended = True
            break
    if not ended or not compressed:
        raise ValueError('PNG missing image/end')
    zlib.decompress(compressed)


def ogg(data):
    at = 0
    while at < len(data):
        if data[at:at+4] != b'OggS' or len(data)-at < 27 or data[at+4] != 0:
            raise ValueError('OGG page header')
        count = data[at+26]
        table = data[at+27:at+27+count]
        if len(table) != count:
            raise ValueError('OGG segment table')
        at += 27 + count + sum(table)
        if at > len(data):
            raise ValueError('OGG truncated page')
    if not at:
        raise ValueError('Empty OGG')


def audit(root=ROOT):
    errors, warnings = [], []
    with zipfile.ZipFile(root/'base/Whiteout-3.4.2-1.21.1.jar') as archive:
        files = {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}
    # Match the existing packaging rules without executing release tasks.
    files = {k: v for k, v in files.items() if not (k.startswith('assets/whiteout/sounds/music_slot_') and k.endswith('.ogg'))}
    base_sounds = strict_json(files['assets/whiteout/sounds.json'])
    files['assets/whiteout/sounds.json'] = json.dumps({k: v for k, v in base_sounds.items() if not k.startswith('music_slot_')}).encode()
    for path in sorted((root/'src/main/resources').rglob('*')):
        if not path.is_file():
            continue
        name, data = path.relative_to(root/'src/main/resources').as_posix(), path.read_bytes()
        try:
            if name.endswith('.json'):
                strict_json(data)
            if name in files and (name == 'assets/whiteout/sounds.json' or name.startswith('assets/whiteout/lang/')):
                merged = strict_json(files[name]); merged.update(strict_json(data))
                data = json.dumps(merged).encode()
        except Exception as ex:
            errors.append(f'{name}: {ex}')
        files[name] = data
    parsed = {}
    for name, data in files.items():
        if not (name.startswith(('assets/', 'data/')) or name.endswith('.json')):
            continue
        try:
            if name.endswith('.json'):
                parsed[name] = strict_json(data)
            elif name.endswith('.png'):
                png(data)
            elif name.endswith('.ogg'):
                ogg(data)
        except Exception as ex:
            errors.append(f'{name}: {ex}')
    references = set()
    external = set()
    def ref(value, category, suffix, owner, default='minecraft'):
        namespace, path = value.split(':', 1) if ':' in value else (default, value)
        if not re.fullmatch(r'[a-z0-9_.-]+', namespace) or not re.fullmatch(r'[a-z0-9_./-]+', path):
            errors.append(f'{owner}: invalid identifier {value}')
            return
        target = f'assets/{namespace}/{category}/{path}{suffix}'
        if namespace != 'whiteout':
            external.add(target)
        elif target not in files:
            errors.append(f'{owner}: missing {target}')
        references.add(target)
    def models(node, owner):
        if isinstance(node, dict):
            for key, value in node.items():
                if key == 'model' and isinstance(value, str):
                    ref(value, 'models', '.json', owner)
                else:
                    models(value, owner)
        elif isinstance(node, list):
            for value in node:
                models(value, owner)
    def inherited(name, seen=None):
        seen = set() if seen is None else seen
        if name in seen:
            errors.append(f'{name}: model parent cycle')
            return {}, False
        seen.add(name)
        model = parsed.get(name, {})
        parent = model.get('parent', '')
        textures, external_parent = {}, bool(parent and not parent.startswith('whiteout:'))
        if parent.startswith('whiteout:'):
            textures, external_parent = inherited('assets/whiteout/models/'+parent.split(':', 1)[1]+'.json', seen)
        return {**textures, **model.get('textures', {})}, external_parent
    for name, value in parsed.items():
        if '/models/' in name:
            parent = value.get('parent')
            if parent and not parent.startswith('builtin/'):
                ref(parent, 'models', '.json', name)
            textures = value.get('textures', {})
            for texture in textures.values():
                if not texture.startswith('#'):
                    ref(texture, 'textures', '.png', name)
            # Follow aliases available in this model. Inherited variables need parent resolution.
            for key in textures:
                seen, current = set(), key
                while current in textures and textures[current].startswith('#'):
                    if current in seen:
                        errors.append(f'{name}: texture alias cycle {key}'); break
                    seen.add(current); current = textures[current][1:]
                if current not in textures and not parent:
                    errors.append(f'{name}: missing texture variable {current}')
            models(value.get('overrides', []), name)
            available, external_parent = inherited(name)
            for element in value.get('elements', []):
                for face in element.get('faces', {}).values():
                    texture, seen = face.get('texture', ''), set()
                    while texture.startswith('#'):
                        key = texture[1:]
                        if key in seen:
                            errors.append(f'{name}: face texture cycle {key}'); break
                        seen.add(key)
                        if key not in available:
                            if not external_parent:
                                errors.append(f'{name}: missing face texture {key}')
                            break
                        texture = available[key]
                    if texture and not texture.startswith('#'):
                        ref(texture, 'textures', '.png', name)
        elif '/blockstates/' in name:
            models(value, name)
    soundmap = parsed.get('assets/whiteout/sounds.json', {})
    for event, config in soundmap.items():
        for sound in config.get('sounds', []):
            target = sound if isinstance(sound, str) else sound['name']
            if isinstance(sound, dict) and sound.get('type') == 'event':
                namespace, key = target.split(':', 1) if ':' in target else ('whiteout', target)
                if namespace == 'whiteout' and key not in soundmap:
                    errors.append(f'sound {event}: missing event {target}')
            else:
                ref(target, 'sounds', '.ogg', 'sound '+event, 'whiteout')
    ru, en = (parsed.get(f'assets/whiteout/lang/{lang}.json', {}) for lang in ('ru_ru', 'en_us'))
    if ru.keys() != en.keys():
        warnings.append({'localization_only_ru': sorted(ru.keys()-en.keys()), 'localization_only_en': sorted(en.keys()-ru.keys())})
    code = '\n'.join(p.read_text(encoding='utf-8') for p in (root/'src/main/java').rglob('*.java'))
    binary = b'\n'.join(data for name, data in files.items() if name.endswith('.class'))
    candidates = []
    for name in sorted(files):
        if not name.startswith('assets/whiteout/') or not name.endswith(('.png', '.ogg', '.json', '.mesh')):
            continue
        relative = name.removeprefix('assets/whiteout/')
        if name in references or '/lang/' in name or '/blockstates/' in name or '/models/item/' in name or name.endswith('sounds.json'):
            continue
        if relative in code or relative.encode() in binary or Path(relative).stem in code:
            continue
        candidates.append(name)
    return {'errors': errors, 'warnings': warnings, 'json_count': len(parsed),
            'png_count': sum(n.endswith('.png') for n in files), 'ogg_count': sum(n.endswith('.ogg') for n in files),
            'unreferenced_candidates': candidates, 'external_references_not_verified': sorted(external),
            'limitations': ['Static candidates may be loaded by generated identifiers or base code.',
                           'OGG framing checked; audio decoding not performed.',
                           'Minecraft/external namespace existence and runtime shader compatibility not checked.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--output', type=Path)
    args = parser.parse_args(); result = audit()
    output = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.write_text(output+'\n', encoding='utf-8')
    print(f"JSON={result['json_count']} PNG={result['png_count']} OGG={result['ogg_count']} errors={len(result['errors'])} candidates={len(result['unreferenced_candidates'])}")
    for error in result['errors']:
        print(error)
    raise SystemExit(bool(result['errors']))
