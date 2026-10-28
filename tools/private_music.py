"""Validate and extract the seven private recordings, without transcoding.

Checks ZIP CRC, every Ogg page CRC, sequence, stream markers and Vorbis headers.
The output directory is ignored by Git. Never stages music or changes ordering.
"""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile


def validate(data):
    table = []
    for i in range(256):
        v = i << 24
        for _ in range(8):
            v = ((v << 1) ^ (0x04C11DB7 if v & 0x80000000 else 0)) & 0xffffffff
        table.append(v)
    offset = 0
    sequence = 0
    serial = None
    packets = []
    pending = bytearray()
    eos = False
    granule = 0
    while offset < len(data):
        assert not eos, 'bytes after EOS'
        header = data[offset:offset + 27]
        assert len(header) == 27 and header[:4] == b'OggS' and header[4] == 0
        flags = header[5]
        assert bool(flags & 1) == bool(pending), 'bad packet continuation'
        assert bool(flags & 2) == (sequence == 0), 'bad BOS'
        page_serial = int.from_bytes(header[14:18], 'little')
        if serial is None:
            serial = page_serial
        assert page_serial == serial
        assert int.from_bytes(header[18:22], 'little') == sequence
        count = header[26]
        lacing = data[offset + 27:offset + 27 + count]
        assert len(lacing) == count
        length = 27 + count + sum(lacing)
        page = bytearray(data[offset:offset + length])
        assert len(page) == length
        expected = int.from_bytes(page[22:26], 'little')
        page[22:26] = b'\0' * 4
        crc = 0
        for byte in page:
            crc = ((crc << 8) ^ table[((crc >> 24) ^ byte) & 255]) & 0xffffffff
        assert crc == expected, f'Ogg CRC page {sequence}'
        cursor = 27 + count
        for size in lacing:
            pending.extend(page[cursor:cursor + size])
            cursor += size
            if size < 255:
                if len(packets) < 3:
                    packets.append(bytes(pending))
                pending.clear()
        granule = int.from_bytes(header[6:14], 'little')
        eos = bool(flags & 4)
        sequence += 1
        offset += length
    assert eos and not pending and granule > 0
    assert len(packets) == 3
    for packet, kind in zip(packets, (1, 3, 5)):
        assert packet[:7] == bytes([kind]) + b'vorbis'
    rate = int.from_bytes(packets[0][12:16], 'little')
    assert rate > 0 and packets[0][11] > 0
    return {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
            'pages': sequence, 'sample_rate': rate, 'seconds': round(granule / rate, 3)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('--extract', type=Path)
    parser.add_argument('--compare', type=Path)
    args = parser.parse_args()
    with ZipFile(args.source) as source:
        report = {}
        tracks = {}
        for number in range(1, 8):
            name = f'assets/whiteout/sounds/music_slot_{number}.ogg'
            data = source.read(name)
            report[name] = validate(data)
            tracks[name] = data
        if args.compare:
            with ZipFile(args.compare) as release:
                for name, data in tracks.items():
                    assert release.read(name) == data, name
        if args.extract:
            for name, data in tracks.items():
                target = args.extract / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
        print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
