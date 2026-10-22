#!/usr/bin/env python3
"""Source + asset regression checks for the feathered ground-snow particle."""
from pathlib import Path
import json
import struct
import zlib

root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources/assets/whiteout'
model=json.loads((resources/'particles/ground_snow_wisp.json').read_text(encoding='utf8'))
assert len(model['textures'])==3
for name in model['textures']:
    assert name.startswith('whiteout:ground_snow_wisp_')
    png=resources/'textures/particle'/ (name.split(':')[1]+'.png')
    assert png.exists(),png
    # Validate the RGBA PNG and reconstruct alpha without optional dependencies.
    data=png.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n")
    offset=8;compressed=b'';wh=None
    while offset<len(data):
        length=struct.unpack_from('>I',data,offset)[0]
        kind=data[offset+4:offset+8]
        body=data[offset+8:offset+8+length]
        offset+=12+length
        if kind==b'IHDR':
            width,height,depth,color,_,_,interlace=struct.unpack('>IIBBBBB',body)
            assert (width,height,depth,color,interlace)==(64,64,8,6,0)
            wh=(width,height)
        if kind==b'IDAT':compressed+=body
    assert wh==(64,64)
    raw=zlib.decompress(compressed)
    stride=64*4;rows=[];previous=bytearray(stride);pos=0
    for y in range(64):
        filter_type=raw[pos];pos+=1
        current=bytearray(raw[pos:pos+stride]);pos+=stride
        for i in range(stride):
            a=current[i-4] if i>=4 else 0
            b=previous[i]
            c=previous[i-4] if i>=4 else 0
            if filter_type==1:v=a
            elif filter_type==2:v=b
            elif filter_type==3:v=(a+b)//2
            elif filter_type==4:
                p=a+b-c;da=abs(p-a);db=abs(p-b);dc=abs(p-c)
                v=a if da<=db and da<=dc else (b if db<=dc else c)
            elif filter_type==0:v=0
            else:raise AssertionError('Unsupported PNG filter')
            current[i]=(current[i]+v)&255
        rows.append(current);previous=current
    alphas=[r[i] for r in rows for i in range(3,stride,4)]
    assert all(rows[y][x*4+3]==0 for x,y in [(0,0),(0,63),(63,0),(63,63)])
    assert max(alphas)>65, 'streak invisible'
    assert sum(a>=15 for a in alphas)<800, 'texture could look like solid card'
    assert sum(a==0 for a in alphas)>2000, 'texture lacks transparent background'

power=(root/'src/main/java/dev/whiteout/power/PowerDevices.java').read_text()
snow=(root/'src/main/java/dev/whiteout/power/GroundSnow.java').read_text()
assert 'GROUND_SNOW_PARTICLE=Registry.register(Registries.PARTICLE_TYPE' in power
assert 'ParticleFactoryRegistry.getInstance().register(PowerDevices.GROUND_SNOW_PARTICLE' in snow
assert 'PARTICLE_SHEET_TRANSLUCENT' in snow
assert 'setSprite(sprites)' in snow
assert 'getModelParticleSprite' not in snow
assert 'TERRAIN_SHEET' not in snow
assert 'stats' in snow and 'rejectedSurface' in snow
assert 'getTopY(Heightmap.Type.WORLD_SURFACE' in snow
print('PASS: 3 feathered particle textures, JSON atlas registration, drift rendering and diagnostics')
