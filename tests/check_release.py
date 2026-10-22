"""Packaged resources, Mixin targets and binary ABI. Not a live Mixin/game test."""
from pathlib import Path
from zipfile import ZipFile
import json, struct
r=Path(__file__).resolve().parents[1]
res=r/'src/main/resources'
a=res/'assets/whiteout'
version=json.loads((res/'fabric.mod.json').read_text(encoding='utf-8'))['version']

def fields(data):
    # Decode the class-file field table, not source substrings.
    assert data[:4]==bytes.fromhex('cafebabe')
    pos=10; pool={}; index=1; count=struct.unpack_from('>H',data,8)[0]
    while index<count:
        tag=data[pos];pos+=1
        if tag==1:
            length=struct.unpack_from('>H',data,pos)[0];pos+=2
            pool[index]=data[pos:pos+length].decode('utf-8',errors='replace');pos+=length
        elif tag in (3,4,9,10,11,12,17,18):pos+=4
        elif tag in (5,6):pos+=8;index+=1
        elif tag in (7,8,16,19,20):pos+=2
        elif tag==15:pos+=3
        else:raise AssertionError(tag)
        index+=1
    pos+=6
    interfaces=struct.unpack_from('>H',data,pos)[0];pos+=2+2*interfaces
    result={};count=struct.unpack_from('>H',data,pos)[0];pos+=2
    for _ in range(count):
        access,name,descriptor,attributes=struct.unpack_from('>HHHH',data,pos);pos+=8
        result[pool[name]]=pool[descriptor]
        for _ in range(attributes):
            length=struct.unpack_from('>I',data,pos+2)[0];pos+=6+length
    return result

with ZipFile(r/f'build/release/Whiteout-{version}-1.21.1.jar') as z:
    assert z.testzip() is None
    metadata=json.loads(z.read('fabric.mod.json'))
    assert metadata['id']=='whiteout' and metadata['version']==version
    for side in ('main','client'):
        entries=metadata['entrypoints'][side]
        assert len(entries)==len(set(entries)),('duplicate initializer',side)
        for cls in entries:assert cls.replace('.','/')+'.class' in z.namelist(),cls
    for config in metadata['mixins']:
        if isinstance(config,dict):config=config['config']
        mix=json.loads(z.read(config))
        for cls in mix.get('mixins',[])+mix.get('client',[])+mix.get('server',[]):
            assert (mix['package']+'.'+cls).replace('.','/')+'.class' in z.namelist(),cls
    mix=json.loads(z.read('whiteout-visual.mixins.json'))
    source_mix=json.loads((res/'whiteout-visual.mixins.json').read_text(encoding='utf-8'))
    assert all(mix[k]==v for k,v in source_mix.items()),'Mixin configuration changed'
    assert mix['refmap']=='whiteout-visual-refmap.json'
    assert 'ZoneScreenAccess' in mix['client']
    target=fields(z.read('dev/whiteout/client/ZoneScreen.class'))
    assert target['draft']=='Ldev/whiteout/Zone;' and target['listPage']=='I',target
    assert 'whiteout-visual-refmap.json' in z.namelist()
    for cls in ('GeneratorMotion','DeviceRenderer','IndicatorVertices','GeneratorParticles','TransientInstances',
                'PowerDiagnostics','PowerDiagnosticsClient','PowerDiagnosticsScreen','FrozenDoorEffects',
                'GlassFrostOverlay','WeatherDepth','BuildingSoundPolicy','StoryDialogueHud','StoryPortraits'):
        assert f'dev/whiteout/power/{cls}.class' in z.namelist(),cls
    # Every local resource must survive assembly (locale/sound maps are merged).
    merged={'fabric.mod.json','whiteout-visual.mixins.json','assets/whiteout/sounds.json','assets/whiteout/lang/ru_ru.json','assets/whiteout/lang/en_us.json'}
    for p in res.rglob('*'):
        if p.is_file():
            name=p.relative_to(res).as_posix()
            if name not in merged:assert z.read(name)==p.read_bytes(),name
    for lang in ('ru_ru','en_us'):
        local=json.loads((a/f'lang/{lang}.json').read_text(encoding='utf-8'))
        bundled=json.loads(z.read(f'assets/whiteout/lang/{lang}.json'))
        assert all(bundled[k]==v for k,v in local.items())
        for key in ('all','networks','generators','panels','zones','refresh','close','not_supported','scope'):
            assert 'diag.whiteout.'+key in bundled
    for event,config in json.loads(z.read('assets/whiteout/sounds.json')).items():
        for sound in config.get('sounds',[]):
            if isinstance(sound,dict) and sound.get('type')=='event':continue
            name=sound['name'] if isinstance(sound,dict) else sound
            namespace,path=name.split(':',1) if ':' in name else ('whiteout',name)
            if namespace=='whiteout':assert f'assets/whiteout/sounds/{path}.ogg' in z.namelist(),event

# Retained useful resource assertions from historical static checks, without PIL.
for name in ('frost_window','wall_thermometer'):
    for rel in (f'assets/whiteout/blockstates/{name}.json',f'assets/whiteout/models/item/{name}.json',f'data/whiteout/loot_table/blocks/{name}.json'):
        assert (res/rel).is_file(),rel
for name in ('frost_window_0','frost_window_1','frost_window_2','frost_window_3','wall_thermometer'):
    png=(a/f'textures/block/{name}.png').read_bytes()
    assert struct.unpack('>II',png[16:24])==(16,16),name
png=(a/'textures/item/unstable_fuse.png').read_bytes()
assert struct.unpack('>II',png[16:24])==(16,16)
geometry=json.loads((r/'tests/spruce_stamp_geometry_test_fixture.json').read_text())
assert geometry['HEIGHT_DELTAS']==[0,6]
for key,count in [('LEAVES',583),('SNOW_1',47),('SNOW_2',48),('LOG_Y',25)]:assert len(geometry[key])==count
for key in ('LEAVES','SNOW_1','SNOW_2','LOG_Y','LOG_X','LOG_Z'):
    assert len({tuple(p) for p in geometry[key]})==len(geometry[key]),key
print('PASS merged release, entrypoints, Mixin field ABI, complete assets, locales, sounds, retained legacy resources')
