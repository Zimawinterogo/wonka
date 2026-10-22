"""Original procedural modern motor sounds; Python standard library + FFmpeg.

Set WHITEOUT_FFMPEG or install FFmpeg in PATH. An optional local imageio-ffmpeg
installation under build/audio-tools is also supported. No old assets overwritten.
Run from any directory: python tools/make_modern_generator_sounds.py
"""
import array
import math
import os
from pathlib import Path
import random
import shutil
import subprocess
import sys
import wave

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT/'src/main/resources/assets/whiteout/sounds'
PREVIEW = ROOT/'build/modern-audio'
SR = 44100
TAU = math.tau


def encoder():
    executable = os.environ.get('WHITEOUT_FFMPEG') or shutil.which('ffmpeg')
    if executable:
        return executable
    sys.path.insert(0, str(ROOT/'build/audio-tools'))
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        raise SystemExit('Set WHITEOUT_FFMPEG to an FFmpeg executable.')


def lowpass(values, frequency, circular=False):
    coefficient = 1-math.exp(-TAU*frequency/SR)
    state = 0.0
    if circular:
        for value in values[-SR//2:]:
            state += coefficient*(value-state)
    out = array.array('d')
    for value in values:
        state += coefficient*(value-state)
        out.append(state)
    return out


def engine(duration, speed, seed, circular=False):
    """Regular four-stroke combustion, enclosed exhaust, alternator and cooling fan.

    The running loop completes 150 firings / 300 revolutions in six seconds.
    All phase modulation is periodic; filtered noise is warmed across the seam.
    """
    count = round(duration*SR)
    rng = random.Random(seed)
    noise = array.array('d', (rng.uniform(-1, 1) for _ in range(count)))
    exhaust = lowpass(noise, 850, circular)
    fan = lowpass(noise, 2800, circular)
    phase = 0.0
    body, mechanisms = array.array('d'), array.array('d')
    for i in range(count):
        time = i/SR
        rate = speed(time)
        phase += rate/SR
        wobble = .008*math.sin(TAU*time/duration) if circular else 0
        fraction = (phase+wobble) % 1
        angle = TAU*(phase+wobble)
        pulse = math.exp(-13*fraction)
        combustion = pulse*math.sin(TAU*3.6*fraction)
        hum = .14*math.sin(2*angle)+.07*math.sin(4*angle+.3)+.035*math.sin(8*angle)
        body.append(1.3*combustion+hum+.44*exhaust[i]*(.35+2*pulse))
        mechanisms.append(.035*math.sin(12*angle)+.055*(fan[i]-exhaust[i])*(.7+.3*math.sin(2*angle)))
    body = lowpass(body, 1800, circular)
    return array.array('d', (math.tanh(1.5*a)+b for a, b in zip(body, mechanisms)))


def finish(values, rms_db, fade_in=0.005, fade_out=0.04):
    mean = sum(values)/len(values)
    centered = [x-mean for x in values]
    rms = math.sqrt(sum(x*x for x in centered)/len(centered))
    scale = min(10**(rms_db/20)/max(rms, 1e-12), .80/max(abs(x) for x in centered))
    out = array.array('d', (x*scale for x in centered))
    head, tail = round(fade_in*SR), round(fade_out*SR)
    for i in range(head):
        out[i] *= math.sin(math.pi*.5*i/max(1,head-1))**2
    for i in range(tail):
        out[len(out)-tail+i] *= math.cos(math.pi*.5*i/max(1,tail-1))**2
    return out


def pull():
    count = round(.50*SR); rng = random.Random(61)
    noise = lowpass([rng.uniform(-1,1) for _ in range(count)], 3400)
    out = array.array('d'); phase = 0
    for i in range(count):
        time = i/SR; envelope = math.sin(math.pi*min(time/.50,1))**1.2
        phase += (36+100*math.sin(math.pi*time/.50))/SR
        ratchet = max(0, math.sin(TAU*phase))**12
        compression = math.sin(TAU*(70*time+35*time*time))*math.exp(-time*6)
        out.append(envelope*(.5*noise[i]+.23*ratchet+.25*compression))
    return finish(out,-17,fade_in=.008,fade_out=.065)


def start():
    out = engine(1.8,lambda t: 10+24*min(t/.65,1)-9*min(max(t-.65,0)/.5,1),62)
    for i in range(len(out)):
        time=i/SR
        out[i] *= min(time/.18,1)
    # Fades from 1.2 to 1.8 seconds as the separate running loop fades in.
    return finish(out,-14.8,fade_in=.008,fade_out=.6)


def stop():
    out = engine(1.45,lambda t: max(.6,25*(1-t/1.45)**1.35),63)
    for i in range(len(out)):
        time=i/SR
        out[i] *= max(0,1-time/1.45)**.7
    return finish(out,-17,fade_in=.010,fade_out=.25)


def fail():
    out = engine(.72,lambda t: max(.5,9*(1-t/.72)**2),64)
    for i in range(len(out)):
        time=i/SR
        out[i] *= math.exp(-time*6)
    return finish(out,-19,fade_in=.012,fade_out=.16)


def write_wave(path, values):
    samples=array.array('h',(round(max(-1,min(1,x))*32767) for x in values))
    if sys.byteorder != 'little':
        samples.byteswap()
    with wave.open(str(path),'wb') as file:
        file.setnchannels(1);file.setsampwidth(2);file.setframerate(SR);file.writeframes(samples.tobytes())


def main():
    ffmpeg=encoder();PREVIEW.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True)
    running=engine(6,lambda t:25,60,True)
    # Put the codec boundary between combustion pulses. A 3 ms endpoint taper
    # avoids Vorbis encoder edge ringing without a perceptible silence gap.
    offset=round(.03*SR);running=running[offset:]+running[:offset]
    sounds={'loop':finish(running,-13.7,.003,.003),
            'pull':pull(),'start':start(),'stop':stop(),'fail':fail()}
    for name,values in sounds.items():
        wav=PREVIEW/f'modern_generator_{name}.wav';write_wave(wav,values)
        subprocess.run([ffmpeg,'-y','-v','error','-i',str(wav),'-c:a','libvorbis','-q:a','6',str(OUT/f'modern_generator_{name}.ogg')],check=True)
        print(f'{name}: {len(values)/SR:.2f}s, peak {max(abs(x) for x in values):.3f}')
    # Audition: successful pull, failed pull, ignition crossfade, running, shutdown.
    preview=array.array('d',[0.0])*round(12*SR)
    def add(values,at,gain=1):
        offset=round(at*SR)
        for i,value in enumerate(values):
            if offset+i < len(preview):preview[offset+i]+=value*gain
    add(sounds['pull'],.25,.8);add(sounds['pull'],1.3,.8);add(sounds['fail'],1.3,.5)
    add(sounds['pull'],2.6,.8);add(sounds['start'],2.6,.8)
    for i in range(round(6.2*SR)):
        fade=min(1,i/(.6*SR))
        preview[round(3.8*SR)+i]+=sounds['loop'][i%len(sounds['loop'])]*1.15*fade
    add(sounds['stop'],10,.75)
    write_wave(PREVIEW/'modern-generator-preview.wav',preview)
    subprocess.run([ffmpeg,'-y','-v','error','-i',str(PREVIEW/'modern-generator-preview.wav'),'-c:a','libmp3lame','-q:a','2',str(PREVIEW/'modern-generator-preview.mp3')],check=True)


if __name__=='__main__':main()
