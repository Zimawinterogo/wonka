#!/usr/bin/env python3
"""Synthesises the old-generator sound set (all original, generated from scratch -
no recordings, no third-party material, no licence restrictions).

Outputs mono 44.1 kHz Ogg Vorbis into src/main/resources/assets/whiteout/sounds/:
  old_generator_crank.ogg  - rope pull / starter turning the engine over (unused by Java: kept inside catch/fail)
  old_generator_catch.ogg  - successful pull: crank + uneven sputter that does not yet sustain
  old_generator_fail.ogg   - missed pull: crank, weak cough, stall against compression
  old_generator_start.ogg  - 5th stage: crank, sputter, backfire, roar with rev overshoot, settles to steady idle
  old_generator_loop.ogg   - seamless 6.0 s heavy running loop (built circularly: no seam)
  old_generator_stop.ogg   - revs fall, stumbling last firings, flywheel spin-down and clunk

Requires numpy, scipy and ffmpeg (libvorbis).  Usage: python3 tools/make_old_generator_sounds.py
"""
import subprocess, tempfile, wave
from pathlib import Path
import numpy as np
from scipy import signal

SR = 44100
OUT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/whiteout/sounds'
rng = np.random.default_rng(20261013)


# ---------------------------------------------------------------- filters
def _sos(kind, f, order=2):
    return signal.butter(order, f, btype=kind, fs=SR, output='sos')

def lp(x, f, order=2): return signal.sosfilt(_sos('low', f, order), x)
def hp(x, f, order=2): return signal.sosfilt(_sos('high', f, order), x)
def bp(x, lo, hi, order=2): return signal.sosfilt(_sos('band', [lo, hi], order), x)

def circ(fn, x):
    """Apply a causal filter to a periodic signal without a seam: filter 3 copies, keep the middle one."""
    n = len(x)
    return fn(np.tile(x, 3))[n:2 * n]

def noise(n): return rng.standard_normal(n)

def db(x): return 20 * np.log10(max(x, 1e-9))


# ---------------------------------------------------------------- building blocks
def bang_ir(kind='main', length=0.55):
    """Impulse response of one combustion event: pitch-dropping body thump, resonant block modes,
    exhaust puff and a short metallic crack.  Several variants avoid machine-gun repetition."""
    n = int(length * SR); t = np.arange(n) / SR
    thump_f = 90 * np.exp(-t / 0.035) + 52                     # 142 Hz -> 52 Hz drop (audible on small speakers)
    thump = np.sin(2 * np.pi * np.cumsum(thump_f) / SR) * np.exp(-t / 0.075)
    modes = np.zeros(n)
    for f, a, d in ((64, .7, .09), (96, .7, .06), (142, .6, .045), (210, .5, .035), (310, .4, .025), (470, .25, .02)):
        modes += a * np.sin(2 * np.pi * f * (1 + rng.normal(0, .015)) * t + rng.uniform(0, 6.28)) * np.exp(-t / d)
    puff = lp(noise(n), 700) * np.exp(-t / 0.055)
    crack = bp(noise(n), 1400, 5200) * np.exp(-t / 0.006)
    if kind == 'pop':      # backfire / sputter: sharper, less body
        ir = 0.55 * thump + 0.5 * modes + 1.0 * puff + 1.5 * crack
    elif kind == 'weak':
        ir = 0.5 * thump + 0.35 * modes + 0.5 * puff + 0.15 * crack
    else:
        ir = 1.0 * thump + 0.8 * modes + 0.9 * puff + 0.45 * crack
    ir[:40] *= np.linspace(0.3, 1, 40)                          # avoid a click at t=0
    return ir / np.max(np.abs(ir))

IR = {k: [bang_ir(k) for _ in range(4)] for k in ('main', 'pop', 'weak')}

def add_event(out, t, amp, kind='main', circular=False):
    ir = IR[kind][rng.integers(0, 4)] * amp
    i = int(round(t * SR)); n = len(out)
    if circular:
        idx = (i + np.arange(len(ir))) % n
        np.add.at(out, idx, ir)
    else:
        if i >= n: return
        m = min(len(ir), n - i)
        if m > 0: out[i:i + m] += ir[:m]

def tick_train(n, times, amps, circular=False, lo=1800, hi=6000, decay=0.003):
    """Valve-train / mechanical ticks."""
    L = int(0.03 * SR); t = np.arange(L) / SR
    out = np.zeros(n)
    for ti, a in zip(times, amps):
        burst = bp(noise(L), lo, hi) * np.exp(-t / decay)
        i = int(round(ti * SR))
        if circular: np.add.at(out, (i + np.arange(L)) % n, burst * a)
        elif i < n:
            m = min(L, n - i)
            out[i:i + m] += burst[:m] * a
    return out

def envelope_from_events(n, times, amps, circular):
    imp = np.zeros(n)
    for t, a in zip(times, amps):
        i = int(round(t * SR))
        if circular: imp[i % n] += a
        elif i < n: imp[i] += a
    f = (lambda x: circ(lambda y: lp(y, 45, 2), x)) if circular else (lambda x: lp(x, 45, 2))
    e = f(imp)
    e = np.maximum(e, 0)
    return e / (e.max() + 1e-9)

def soft(x, drive):
    return np.tanh(drive * x) / np.tanh(drive)

def room(x, circular=False, wet=0.16, tail=0.18):
    """A small hard shed: short decaying-noise reverb."""
    n = int(tail * SR); t = np.arange(n) / SR
    ir = noise(n) * np.exp(-t / 0.05); ir = lp(ir, 3500); ir[0] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    if circular:
        return (1 - wet) * x + wet * np.real(np.fft.ifft(np.fft.fft(x) * np.fft.fft(ir, len(x))))
    return (1 - wet) * x + wet * signal.fftconvolve(x, ir)[:len(x)]


def engine_mix(n, ev_t, ev_a, ev_kind, rot_phase, rot_amp, rattle_amp, circular):
    """Common engine voice. ev_*: combustion events; rot_phase: radians per sample (array, flywheel/alternator);
    rot_amp: per-sample mechanical hum amplitude; rattle_amp: per-sample amplitude of block rattle."""
    bangs = np.zeros(n)
    for t, a, k in zip(ev_t, ev_a, ev_kind): add_event(bangs, t, a, k, circular)
    env = envelope_from_events(n, ev_t, ev_a, circular)
    F = (lambda fn, x: circ(fn, x)) if circular else (lambda fn, x: fn(x))
    # block vibration: noisy band that swells with every firing
    rat = F(lambda y: bp(y, 140, 850, 2), noise(n)) * rattle_amp * (0.35 + 0.9 * env)
    # deep rumble below the bangs
    rum = F(lambda y: lp(y, 85, 2), noise(n)) * (0.25 + 0.75 * env) * rattle_amp * 0.9
    # valve clatter + injector-ish ticks, half a cycle after each firing (loose old tappets)
    ticks_t = [t + 0.012 + abs(rng.normal(0, .004)) for t in ev_t] + [t + 0.037 for t in ev_t]
    ticks_a = [0.5 * a * rng.uniform(.5, 1) for a in ev_a] * 2
    clat = tick_train(n, ticks_t, ticks_a, circular) * 0.9
    # flywheel / alternator hum: harmonics of rotation, with slight roughness
    hum = (np.sin(rot_phase) * .5 + np.sin(2 * rot_phase) * .35 + np.sin(3 * rot_phase + .6) * .18 + np.sin(4 * rot_phase + 1.1) * .12) * rot_amp
    low = bangs * 0.9 + rum + hum
    x = soft(low, 1.7) * 0.8 + rat * 1.5 + clat * 1.3 + bangs * 0.45
    return room(x, circular)


def finish(x, peak_db=-1.5, rms_db=None, fade_in=0.0, fade_out=0.0):
    x = x - np.mean(x)
    if rms_db is not None:
        x = x * 10 ** (rms_db / 20) / (np.sqrt(np.mean(x ** 2)) + 1e-12)
    x = np.tanh(x * 0.9) / 0.9 if np.max(np.abs(x)) > 0.85 else x       # soft limiter only when needed
    x = x * (10 ** (peak_db / 20) / np.max(np.abs(x)) if rms_db is None else 1.0)
    if np.max(np.abs(x)) > 10 ** (peak_db / 20): x *= 10 ** (peak_db / 20) / np.max(np.abs(x))
    if fade_in: x[:int(fade_in * SR)] *= np.linspace(0, 1, int(fade_in * SR)) ** 2
    if fade_out: x[-int(fade_out * SR):] *= np.linspace(1, 0, int(fade_out * SR)) ** 1.5
    return x


# ---------------------------------------------------------------- one-shot helpers
def cumulative_firing_times(rate_fn, dur, jitter=0.0, skip=lambda t: False):
    """Integrate a time-varying firing rate (Hz); returns firing times, with timing jitter in fractions of a cycle."""
    t = np.arange(int(dur * SR)) / SR
    cum = np.cumsum(rate_fn(t)) / SR
    times = []
    k = 1
    while k <= cum[-1]:
        i = int(np.searchsorted(cum, k)); tt = t[min(i, len(t) - 1)]
        rate = max(rate_fn(np.array([tt]))[0], 1)
        tt += rng.normal(0, jitter) / rate
        if not skip(tt): times.append(max(tt, 0.0))
        k += 1
    return np.array(times)

def crank(n, t0=0.0, pull=0.36, peak_rate=7.5, decay=0.30, level=1.0):
    """Recoil-starter pull: ratchet clicks, rope zip, heavy compression strokes turning the flywheel."""
    out = np.zeros(n); t = np.arange(n) / SR
    rel = t - t0
    # turning rate (strokes/s): quick rise during the pull, then flywheel coast-down
    rate = np.where(rel < 0, 0, np.where(rel < pull, peak_rate * (rel / pull) ** 0.7, peak_rate * np.exp(-(rel - pull) / decay)))
    # rope / pawl: accelerating ratchet clicks + zipping noise
    m = (rel >= 0) & (rel < pull)
    zip_env = np.where(m, np.sin(np.pi * np.clip(rel / pull, 0, 1)) ** 0.8, 0)
    out += bp(noise(n), 700, 3200) * zip_env * 0.20 * level
    ct = []
    tt = t0
    while tt < t0 + pull:
        ct.append(tt); tt += 1 / (22 + 38 * (tt - t0) / pull)
    out += tick_train(n, ct, [0.9 * level] * len(ct), lo=2200, hi=6500, decay=0.0025) * 0.7
    # compression strokes
    cum = np.cumsum(rate) / SR
    k = 1
    while k <= cum[-1] and rate.max() > 0:
        i = int(np.searchsorted(cum, k))
        if i >= n: break
        a = level * (0.35 + 0.65 * min(rate[i] / peak_rate, 1))
        ti = i / SR
        L = int(0.22 * SR); tl = np.arange(L) / SR
        thump = np.sin(2 * np.pi * np.cumsum(70 * np.exp(-tl / .03) + 38) / SR) * np.exp(-tl / .07)
        chuff = lp(noise(L), 650) * np.exp(-tl / .05)
        seg = (thump * .9 + chuff * .8) * a
        m_ = min(L, n - i); out[i:i + m_] += seg[:m_]
        k += 1
    return out, cum

def sequence(dur, build):
    n = int(dur * SR)
    return build(n)


# ---------------------------------------------------------------- the sounds
STEADY = 15.0          # Hz firing rate of the running engine (about 1800 rpm single cylinder, 4-stroke)

def make_loop(T=6.0):
    n = int(T * SR); f_fire = STEADY; cyc = int(round(f_fire * T))          # 90 firings, integer count -> periodic
    wow = lambda t: 0.012 * np.sin(2 * np.pi * t / T) + 0.007 * np.sin(2 * np.pi * 3 * t / T + 1.3)   # governor hunting
    ev_t, ev_a, ev_k = [], [], []
    next_weak = rng.integers(8, 14)
    for k in range(cyc):
        base = k / f_fire + rng.normal(0, 0.035) / f_fire
        t = (base + wow(base)) % T
        a = 1.0 + 0.14 * (1 if k % 2 else -1) * 0.5 + rng.normal(0, .08)       # slightly uneven cylinders / ignition
        kind = 'main'
        if k == next_weak: a *= 0.5; kind = 'weak'; next_weak += rng.integers(9, 17)
        ev_t.append(t); ev_a.append(a); ev_k.append(kind)
    tt = np.arange(n) / SR
    fr = 2 * f_fire                                                          # crank rotation Hz (4-stroke)
    phase = 2 * np.pi * fr * (tt + wow(tt))
    rot_amp = 0.11 * (1 + 0.15 * np.sin(2 * np.pi * 2 * tt / T))
    ratt = 0.10 * (1 + 0.25 * np.sin(2 * np.pi * 1 * tt / T + 0.7))
    x = engine_mix(n, ev_t, ev_a, ev_k, phase, rot_amp, ratt, circular=True)
    return finish(x, peak_db=-2.0, rms_db=-14.5)

def make_stop():
    dur = 3.4; n = int(dur * SR); tt = np.arange(n) / SR
    rate = lambda t: STEADY * np.clip(1 - t / 2.35, 0, 1) ** 1.6 + 0.0
    ev = cumulative_firing_times(rate, 2.45, jitter=0.0)
    ev_t, ev_a, ev_k = [], [], []
    for i, t in enumerate(ev):
        prog = t / 2.35
        # as revs fall the firings get weak and ragged, some misses
        if prog > 0.55 and rng.random() < 0.35 * prog: continue
        a = (1.0 - 0.45 * prog) * rng.uniform(.8, 1.1)
        ev_t.append(t + rng.normal(0, 0.012) * prog); ev_a.append(a); ev_k.append('weak' if prog > .7 else 'main')
    fr = 2 * rate(tt) + 0 * tt
    phase = 2 * np.pi * np.cumsum(fr) / SR
    spin = np.exp(-tt / 1.9)
    x = engine_mix(n, ev_t, ev_a, ev_k, phase, 0.11 * np.clip(1 - tt / 2.6, 0, 1) ** .5 + 0 * tt, 0.10 * np.clip(1 - tt / 2.3, 0, 1) + 0.0 * tt, circular=False)
    # final compression clunk as the flywheel rocks back, and a long metal ring-down
    clunk_t = 2.55
    L = int(0.5 * SR); tl = np.arange(L) / SR
    clunk = (np.sin(2 * np.pi * 58 * tl) * np.exp(-tl / .09) * .55 + bp(noise(L), 300, 1800) * np.exp(-tl / .03) * .35 + np.sin(2 * np.pi * 420 * tl) * np.exp(-tl / .12) * .05)
    i = int(clunk_t * SR); x[i:i + L] += clunk[:max(0, min(L, n - i))] * 0.55
    x *= 1.0
    return finish(x, peak_db=-2.0, rms_db=-17.0, fade_out=0.35)

def make_catch():
    dur = 1.9; n = int(dur * SR)
    cr, _ = crank(n, 0.0, pull=0.34, peak_rate=7.5, decay=0.45, level=1.0)
    ev_t = [0.36, 0.60, 0.80, 1.08, 1.43]
    ev_a = [0.55, 0.85, 0.62, 0.78, 0.35]
    ev_k = ['weak', 'main', 'weak', 'main', 'weak']
    x = np.zeros(n)
    for t, a, k in zip(ev_t, ev_a, ev_k): add_event(x, t + rng.normal(0, .005), a, k)
    ticks = tick_train(n, [t + 0.012 for t in ev_t], [0.35] * len(ev_t))
    x = soft(x, 1.5) * 0.9 + 0.9 * lp(cr, 6000) + ticks * 0.3
    x = room(x)
    return finish(x, peak_db=-2.0, rms_db=-17.0, fade_out=0.25)

def make_fail():
    dur = 2.0; n = int(dur * SR)
    cr, _ = crank(n, 0.0, pull=0.34, peak_rate=7.0, decay=0.38, level=1.0)
    x = np.zeros(n)
    add_event(x, 0.40, 0.45, 'weak'); add_event(x, 0.72, 0.30, 'weak')
    # air wheeze through the carburettor as the engine gives up, then a stall against compression
    t = np.arange(n) / SR
    wheeze = bp(noise(n), 300, 1500) * np.exp(-((t - 0.95) / 0.22) ** 2) * 0.35
    L = int(0.6 * SR); tl = np.arange(L) / SR
    stall = (np.sin(2 * np.pi * 48 * tl) * np.exp(-tl / .11) * .8 + lp(noise(L), 500) * np.exp(-tl / .05) * .5
             + bp(noise(L), 1500, 4500) * np.exp(-tl / .004) * .3)
    i = int(1.15 * SR); x[i:i + L] += stall[:min(L, n - i)] * 0.9
    rock = np.sin(2 * np.pi * 36 * tl) * np.exp(-tl / .14) * 0.35
    i2 = int(1.42 * SR); x[i2:i2 + L] += rock[:min(L, n - i2)]
    x = soft(x, 1.4) * 0.9 + 0.9 * lp(cr, 6000) + wheeze
    x = room(x)
    return finish(x, peak_db=-2.0, rms_db=-17.5, fade_out=0.3)

def make_start():
    dur = 4.2; n = int(dur * SR); tt = np.arange(n) / SR
    cr, _ = crank(n, 0.0, pull=0.34, peak_rate=7.5, decay=0.30, level=1.0)
    # firing schedule: first uneven sputters, then it catches and revs up past idle, then settles
    def rate(t):
        r = np.zeros_like(t)
        r = np.where(t < 0.9, 4.0 + 6.0 * (t / 0.9), r)
        r = np.where((t >= 0.9) & (t < 1.55), 10 + 22 * np.clip((t - 0.9) / 0.65, 0, 1) ** 0.8, r)             # revs up to ~32 Hz
        r = np.where((t >= 1.55) & (t < 3.0), STEADY + (32 - STEADY) * np.exp(-(t - 1.55) / 0.55), r)  # overshoot decays
        r = np.where(t >= 3.0, STEADY, r)
        return r
    ev = cumulative_firing_times(rate, dur, jitter=0.05)
    ev_t, ev_a, ev_k = [], [], []
    for t in ev:
        if t < 0.36: continue
        if t < 0.9:                                            # sputtering: weak, missing
            if rng.random() < 0.3: continue
            a = rng.uniform(.4, .85); k = 'pop' if rng.random() < .4 else 'weak'
        elif t < 1.55:
            a = 0.8 + 0.7 * (t - 0.9) / 0.65 + rng.normal(0, .08); k = 'main'
        elif t < 3.0:
            a = 1.45 - 0.35 * min(1, (t - 1.55) / 1.45) + rng.normal(0, .08); k = 'main'
        else:
            a = 1.0 + rng.normal(0, .08); k = 'main'
        ev_t.append(t); ev_a.append(a); ev_k.append(k)
    # big backfire as it catches
    ev_t.append(0.93); ev_a.append(1.9); ev_k.append('pop')
    fr = 2 * rate(tt); phase = 2 * np.pi * np.cumsum(fr) / SR
    swell = np.clip((tt - 0.5) / 1.0, 0, 1)
    x = engine_mix(n, ev_t, ev_a, ev_k, phase, 0.13 * swell, 0.12 * swell, circular=False)
    x = x + 0.9 * lp(cr, 6000)
    x = finish(x, peak_db=-1.5, rms_db=-15.5)
    # the roar hands over to the separately-looped idle: fade the tail so the loop's fade-in carries on seamlessly
    tail = int(1.6 * SR)
    x[-tail:] *= np.linspace(1, 0.0, tail) ** 1.2
    x[:int(0.005 * SR)] *= np.linspace(0, 1, int(0.005 * SR))
    return x


# ---------------------------------------------------------------- IO
def write_ogg(name, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype('<i2')
    with tempfile.TemporaryDirectory() as d:
        w = Path(d) / 'a.wav'
        with wave.open(str(w), 'wb') as f:
            f.setnchannels(1); f.setsampwidth(2); f.setframerate(SR); f.writeframes(pcm.tobytes())
        OUT.mkdir(parents=True, exist_ok=True)
        subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', str(w), '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '5', str(OUT / name)], check=True)
    print(f'{name}: {len(x) / SR:.2f}s peak {db(np.max(np.abs(x))):.1f} dBFS rms {db(np.sqrt(np.mean(x ** 2))):.1f} dBFS')


if __name__ == '__main__':
    write_ogg('old_generator_loop.ogg', make_loop())
    write_ogg('old_generator_start.ogg', make_start())
    write_ogg('old_generator_stop.ogg', make_stop())
    write_ogg('old_generator_catch.ogg', make_catch())
    write_ogg('old_generator_fail.ogg', make_fail())
