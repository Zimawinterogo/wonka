"""Packaged backpack resources and remapped Mixin contracts. Not a live game test."""
import json
import struct
from pathlib import Path
from zipfile import ZipFile

root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
version=json.loads((resources/'fabric.mod.json').read_text())['version']
with ZipFile(root/f'build/release/Whiteout-{version}-1.21.1.jar') as archive:
    mix=json.loads(archive.read('whiteout-visual.mixins.json'))
    refmap=json.loads(archive.read('whiteout-visual-refmap.json'))
    for name in ('BackpackScreenMixin','BackpackSlotAccess','BackpackHiddenSlotMixin','BackpackCameraMixin','BackpackPlayerPoseMixin'):
        assert name in mix['client'] and name not in mix.get('mixins',[])
        key='dev/whiteout/power/mixin/'+name
        assert key+'.class' in archive.namelist()
        assert refmap['mappings'].get(key), ('missing Mixin remapping',key)
    for name in ('BackpackClient','BackpackLayout','BackpackPreferences','BackpackScreen','BackpackOpening','BackpackOpeningRenderer','BackpackNetwork','BackpackVisualClient','BackpackFeatureRenderer','BackpackPosePolicy'):
        assert 'dev/whiteout/power/backpack/'+name+'.class' in archive.namelist()
    for name,size in (('bag.png',(1499,1049)),('pocket.png',(20,20))):
        path='assets/whiteout/textures/gui/backpack/'+name
        data=archive.read(path)
        assert data==(resources/path).read_bytes()
        assert struct.unpack('>II',data[16:24])==size
        assert data[25]==6,'RGBA required'
        assert json.loads(archive.read(path+'.mcmeta'))['texture']['blur'] is False
    for lang in ('ru_ru','en_us'):
        strings=json.loads(archive.read('assets/whiteout/lang/'+lang+'.json'))
        assert all('backpack.whiteout.'+name in strings for name in ('title','enabled','disabled','equipment','slot.45'))
    player=archive.read('dev/whiteout/power/PowerClient.class')
    assert b'dev/whiteout/power/backpack/BackpackClient' in player
print('PASS backpack: full JAR assets, languages, client-only mixins, generated refmap, startup integration')

with ZipFile(root/f'build/release/Whiteout-{version}-1.21.1.jar') as archive:
    for name in ('BackpackNetwork','BackpackPosePolicy'):
        assert b'net/minecraft/client/' not in archive.read('dev/whiteout/power/backpack/'+name+'.class'), 'server loads client code'
    assert 'BackpackFreshCameraMixin' in mix['client']
    assert archive.read('assets/whiteout/textures/entity/backpack.png')==(resources/'assets/whiteout/textures/entity/backpack.png').read_bytes()
    assert b'BACKPACK' in archive.read('dev/whiteout/power/backpack/BackpackScreen.class')
print('PASS backpack multiplayer: server-safe classes, permanent model texture, optional FFP hook, BACKPACK patch')
