"""Release-level passport contracts; no game launch implied."""
import json
import re
import hashlib
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
version=json.loads((root/'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))['version']
with ZipFile(root/f'build/release/Whiteout-{version}-1.21.1.jar') as release:
    assert release.testzip() is None
    for cls in ('MapPassport','PassportIssue','PassportRules','PassportCollector','PassportNetwork','PassportClient','PassportScreen'):
        data=release.read(f'dev/whiteout/power/{cls}.class')
        assert int.from_bytes(data[6:8],'big')==65, 'Java 21 bytecode'
        if cls in ('MapPassport','PassportIssue','PassportRules','PassportCollector','PassportNetwork'):
            assert b'net/minecraft/client/' not in data and b'dev/whiteout/client/' not in data, cls
    keys=set()
    for file in (root/'src/main/java/dev/whiteout/power').glob('Passport*.java'):
        text=file.read_text(encoding='utf-8')
        keys.update(re.findall(r'"passport\.whiteout\.([a-z_]+)"',text))
        keys.update(re.findall(r'\btr\("([a-z_]+)"\)',text))
        keys.update(re.findall(r'\.issue\((?:ERROR|WARN|INFO),"([a-z_]+)"',text))
    for locale in ('en_us','ru_ru'):
        language=json.loads(release.read(f'assets/whiteout/lang/{locale}.json'))
        for key in keys:assert 'passport.whiteout.'+key in language,(locale,key)
    with ZipFile(root/'Whiteout-3.12.9-1.21.1.jar') as original:
        for i in range(1,8):
            name=f'assets/whiteout/sounds/music_slot_{i}.ogg'
            data=release.read(name)
            assert len(data)>0 and hashlib.sha256(data).digest()==hashlib.sha256(original.read(name)).digest(),name
print('PASS passport release classes, Java 21, server/client separation, RU/EN keys, seven byte-identical tracks')
