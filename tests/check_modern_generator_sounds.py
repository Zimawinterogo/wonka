"""Modern motor event isolation, Vorbis format, decoded level and loop seam."""
from pathlib import Path
import array
import json
import math
import os
import shutil
import struct
import subprocess

root=Path(__file__).resolve().parents[1]
assets=root/'src/main/resources/assets/whiteout'
events=json.loads((assets/'sounds.json').read_text(encoding='utf-8'))
routes={'generator_engine':'loop','generator_pull':'pull','generator_start':'start',
        'generator_stop':'stop','modern_generator_fail':'fail'}
durations={'loop':6,'pull':.5,'start':1.8,'stop':1.45,'fail':.72}
assert events['generator_fail']['sounds'][0]['name']=='whiteout:generator_fail', 'electrical fault event changed'
assert events['generator_engine']['sounds'][0]['attenuation_distance']==24
for suffix in ('loop','start','stop','catch','fail'):
    assert events['old_generator_'+suffix]['sounds'][0]['name']=='whiteout:old_generator_'+suffix

ffmpeg=os.environ.get('WHITEOUT_FFMPEG') or shutil.which('ffmpeg')
for event,suffix in routes.items():
    sound=events[event]['sounds'][0]
    assert sound['name']=='whiteout:modern_generator_'+suffix and sound['stream'] is False
    path=assets/'sounds'/('modern_generator_'+suffix+'.ogg')
    data=path.read_bytes();at=0;packet=bytearray();header=None;last=0
    while at<len(data):
        assert data[at:at+4]==b'OggS'
        granule=struct.unpack_from('<Q',data,at+6)[0]
        if granule!=(1<<64)-1:last=max(last,granule)
        count=data[at+26];sizes=data[at+27:at+27+count];offset=at+27+count
        for size in sizes:
            packet.extend(data[offset:offset+size]);offset+=size
            if size<255:
                if header is None:header=bytes(packet)
                packet.clear()
        at=offset
    assert header[:7]==b'\x01vorbis' and header[11]==1
    assert struct.unpack_from('<I',header,12)[0]==44100
    assert abs(last/44100-durations[suffix])<1/44100
    if ffmpeg:
        result=subprocess.run([ffmpeg,'-v','error','-i',str(path),'-f','f32le','-ac','1','-ar','44100','-'],capture_output=True,check=True)
        assert not result.stderr, result.stderr
        values=array.array('f');values.frombytes(result.stdout)
        if __import__('sys').byteorder!='little':values.byteswap()
        assert all(math.isfinite(value) for value in values)
        assert abs(len(values)/44100-durations[suffix])<.004
        peak=max(abs(value) for value in values)
        rms=math.sqrt(sum(value*value for value in values)/len(values))
        assert .15<peak<.95 and .035<rms<.3,(suffix,peak,rms)
        if suffix=='loop':
            # Seam must be below normal high-percentile within-loop sample steps.
            steps=sorted(abs(values[i]-values[i-1]) for i in range(1,len(values)))
            assert abs(values[0]-values[-1])<max(.01,steps[int(len(steps)*.99)])
            assert abs(sum(values)/len(values))<.002
        else:
            assert max(abs(value) for value in values[:64])<.05
            assert max(abs(value) for value in values[-64:])<.05
print('PASS modern motor: 5 events, isolated failure, mono 44100 Hz, exact Vorbis durations')
print('PASS decoded PCM: no clipping, levels, one-shot fades, loop seam/DC' if ffmpeg else 'SKIP PCM validation: set WHITEOUT_FFMPEG')
