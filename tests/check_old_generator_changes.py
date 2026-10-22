"""Actual Ogg/Vorbis resource metadata; standard library only."""
from pathlib import Path
import json,struct,os,shutil,subprocess,array
r=Path(__file__).resolve().parents[1]
a=r/'src/main/resources/assets/whiteout'
def vorbis(path):
 data=path.read_bytes();at=0;packet=bytearray();header=None;last=0
 while at<len(data):
  assert data[at:at+4]==b'OggS',path
  granule=struct.unpack_from('<Q',data,at+6)[0]
  if granule!=(1<<64)-1:last=max(last,granule)
  n=data[at+26];sizes=data[at+27:at+27+n];offset=at+27+n
  for size in sizes:
   packet.extend(data[offset:offset+size]);offset+=size
   if size<255:
    if header is None:header=bytes(packet)
    packet.clear()
  at=offset
 assert header[:7]==b'\x01vorbis',path
 channels=header[11];rate=struct.unpack_from('<I',header,12)[0]
 return channels,rate,last/rate
sounds=json.loads((a/'sounds.json').read_text(encoding='utf-8'))
for name in ('old_generator_loop','old_generator_start','old_generator_stop','old_generator_catch','old_generator_fail'):
 assert sounds[name]['sounds'][0]['name']=='whiteout:'+name
 channels,rate,duration=vorbis(a/f'sounds/{name}.ogg')
 assert (channels,rate)==(1,44100),(name,channels,rate)
 assert duration>0,name
 if name.endswith('_loop'):assert abs(duration-6)<.001,duration
 if name.endswith('_start'):assert abs(duration-4.2)<.001,duration
for lang in ('en_us','ru_ru'):
 assert '5' in json.loads((a/f'lang/{lang}.json').read_text(encoding='utf-8'))['screen.whiteout.worn_instruction']
print('PASS actual mono 44100 Hz Vorbis files, 4.2-second ignition, 6-second loop, five-stage labels')
ffmpeg=os.environ.get('WHITEOUT_FFMPEG') or shutil.which('ffmpeg')
if ffmpeg:
 raw=subprocess.run([ffmpeg,'-v','error','-i',str(a/'sounds/old_generator_loop.ogg'),'-f','f32le','-ac','1','-ar','44100','-'],capture_output=True,check=True).stdout
 samples=array.array('f');samples.frombytes(raw)
 import sys
 if sys.byteorder!='little':samples.byteswap()
 assert len(samples)==264600
 differences=sorted(abs(b-a) for a,b in zip(samples,samples[1:]))
 assert max(map(abs,samples))<.95,'clipping'
 assert abs(samples[0]-samples[-1])<differences[int((len(differences)-1)*.995)]*1.5,'loop seam jump'
 print('PASS decoded six-second waveform: peak below 0.95 and loop seam below retained threshold')
else:
 print('SKIP decoded waveform check: set WHITEOUT_FFMPEG or put ffmpeg on PATH')
