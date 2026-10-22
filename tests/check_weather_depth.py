"""Check release integration of the vanilla late depth snapshot."""
import json
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
version=json.loads((root/'src/main/resources/fabric.mod.json').read_text())['version']
with ZipFile(root/f'build/release/Whiteout-{version}-1.21.1.jar') as z:
    config=json.loads(z.read('whiteout-visual.mixins.json'))
    assert 'WeatherDepthCaptureMixin' in config['client']
    mapping=json.loads(z.read('whiteout-visual-refmap.json'))['mappings']['dev/whiteout/power/mixin/WeatherDepthCaptureMixin']
    assert any('getTranslucent' in k for k in mapping),mapping
    depth=z.read('dev/whiteout/power/WeatherDepth.class')
    assert b'BEFORE_DEBUG_RENDER' not in depth,'premature snapshot registered'
    assert b'captureBeforeTranslucent' in depth and b'shaderPack' in depth
    assert b'dev/whiteout/power/WeatherDepth' in z.read('dev/whiteout/client/WinterEffect.class')
print('PASS weather depth: late capture client hook, remapped target, shaderpack guard, full-JAR integration')
