"""Check the complete assembled artifact, localization and unchanged protected code."""
from pathlib import Path
from zipfile import ZipFile
import json,re

root=Path(__file__).resolve().parents[1]
version=json.loads((root/'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))['version']
with ZipFile(root/f'build/release/Whiteout-{version}-1.21.1.jar') as z:
    assert z.testzip() is None
    for name in ('DialogueRoute','DialogueRouteStore','DialogueRouteConditions','DialogueRouteQueue','DialogueRouteNetwork','DialogueRouteClient',
                 'DialogueRouteScreen','DialogueRouteEditScreen','DialogueRoutePickerScreen','DialogueRouteVariantScreen','mixin/ConditionalDialogueMixin'):
        data=z.read(f'dev/whiteout/power/{name}.class')
        assert int.from_bytes(data[6:8],'big')==65
        if name in ('DialogueRoute','DialogueRouteStore','DialogueRouteConditions','DialogueRouteQueue','DialogueRouteNetwork'):
            assert b'net/minecraft/client/' not in data and b'dev/whiteout/client/' not in data,name
    mix=json.loads(z.read('whiteout-visual.mixins.json'))
    assert 'ConditionalDialogueMixin' in mix['client'] and 'ConditionalDialogueMixin' not in mix.get('mixins',[])
    keys={'type_'+x for x in ('network','generator','weather_zone','blizzard','story')}
    for path in (root/'src/main/java/dev/whiteout/power').glob('DialogueRoute*.java'):
        source=path.read_text(encoding='utf-8')
        keys.update(re.findall(r'\b(?:text|message)\("([a-z_]+)"\s*[,)]',source))
    for locale in ('ru_ru','en_us'):
        lang=json.loads(z.read(f'assets/whiteout/lang/{locale}.json'))
        for key in keys:assert 'route.whiteout.'+key in lang,(locale,key)
    # If the preceding complete release is available, protected gameplay classes must match exactly.
    prior=root/'build/release/Whiteout-3.12.19-1.21.1.jar'
    if prior.exists():
        with ZipFile(prior) as old:
            protected=('dev/whiteout/power/Lake','dev/whiteout/power/WinterCrisis','dev/whiteout/power/Crisis',
                       'dev/whiteout/power/backpack/','dev/whiteout/power/Forest','dev/whiteout/power/WorldAtmosphere',
                       'dev/whiteout/power/GridManager','dev/whiteout/power/StoryPortraits','dev/whiteout/power/StoryDialogueHud')
            count=0
            for name in old.namelist():
                if name.endswith('.class') and name.startswith(protected):assert old.read(name)==z.read(name),name;count+=1
            print(f'PASS {count} protected gameplay classes byte-identical to 3.12.19')
print('PASS conditional route release classes, client-only Mixin, server separation and RU/EN localization')
