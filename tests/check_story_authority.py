"""Packaged server authority wiring. Does not substitute for the Fabric server probe."""
from pathlib import Path
from zipfile import ZipFile
import json

root = Path(__file__).resolve().parents[1]
version = json.loads((root / 'src/main/resources/fabric.mod.json').read_text(encoding='utf-8'))['version']
with ZipFile(root / f'build/release/Whiteout-{version}-1.21.1.jar') as jar:
    for name in ('StoryAuthority', 'StoryEventCatalog', 'StoryRequestPolicy', 'StoryCommitTransaction', 'StoryReceipts', 'StoryAuthorityNetwork', 'StoryObjectiveActions', 'StoryWorldConditions', 'StoryWorldSource', 'CrisisScenes', 'StorySceneActions'):
        data = jar.read(f'dev/whiteout/power/{name}.class')
        assert int.from_bytes(data[6:8], 'big') == 65
        assert b'net/minecraft/client/' not in data and b'dev/whiteout/client/' not in data
    mixins = json.loads(jar.read('whiteout-visual.mixins.json'))
    assert mixins['required'] and 'ZoneHistoryMixin' in mixins['mixins']
    for name in ('StoryRuntimeAuthorityMixin', 'DialogueChoiceAuthorityMixin'):
        assert name in mixins['client'] and name not in mixins['mixins']
        assert jar.read(f'dev/whiteout/power/mixin/{name}.class')
    guard = jar.read('dev/whiteout/power/mixin/ZoneHistoryMixin.class')
    assert b'dev/whiteout/power/StoryAuthority' in guard and b'cancellable' in guard and b'cancel' in guard
    assert b'dev/whiteout/power/StoryAuthority' in jar.read('dev/whiteout/power/PowerDevices.class')
    for locale in ('ru_ru', 'en_us'):
        assert 'story.whiteout.request_denied' in json.loads(jar.read(f'assets/whiteout/lang/{locale}.json'))
print('PASS packaged story authority classes, common Mixin, lifecycle entry and localization')
