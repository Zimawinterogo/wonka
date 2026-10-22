"""Validate shipped geometry, not a second rendering implementation. Python stdlib only."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MODELS = ROOT / 'src/main/resources/assets/whiteout/models/block'
checks = 0

def check(ok, message):
    global checks
    checks += 1
    if not ok:
        raise AssertionError(message)

def model(y, mask):
    return json.loads((MODELS / f'barricade_connected_{y % 2}_{mask}.json').read_text())

def wood(m):
    return [e for e in m['elements'] if e['faces']['south']['texture'] == '#wood']

def edge(m, axis, high):
    result = []
    for e in wood(m):
        if e['to' if high else 'from'][axis] != (16 if high else 0):
            continue
        axes = [i for i in range(3) if i != axis]
        result.append(tuple(e[k][a] for k in ('from', 'to') for a in axes))
    return sorted(result)

def test():
    for y in range(-3, 4):
        for mask in range(16):
            m = model(y, mask)
            check(not m['ambientocclusion'], 'avoid per-cell AO shading seams')
            for e in m['elements']:
                check(all(0 <= e['from'][a] < e['to'][a] <= (16.2 if a == 2 else 16)
                          for a in range(3)), 'valid clipped geometry')
                for face, axis, high, bit in [('west',0,False,1), ('east',0,True,2),
                                              ('down',1,False,4), ('up',1,True,8)]:
                    if mask & bit and e['to' if high else 'from'][axis] == (16 if high else 0):
                        check(face not in e['faces'], 'internal cap must be absent')
                if e['faces']['south']['texture'] == '#iron':
                    x = (e['from'][0]+e['to'][0])/2
                    check((x == 1.5 and not mask & 1) or (x == 14.5 and not mask & 2),
                          'nail must be at exposed frame, never internal join')
                    check(any(b['from'][1] <= e['from'][1] and b['to'][1] >= e['to'][1]
                              and b['to'][2] == e['from'][2] for b in wood(m)), 'nail lies on a board')
            # Every possible pair of horizontal neighbours, including asymmetric vertical links.
            for other in range(16):
                a, b = model(y, mask | 2), model(y, other | 1)
                check(edge(a,0,True) == edge(b,0,False), 'matching horizontal cross-sections')
            # In a rectangular opening both rows have the same horizontal boundary conditions.
            a, b = model(y,mask | 8), model(y+1,mask | 4)
            check(edge(a,1,True) == edge(b,1,False), 'matching vertical cross-sections')
            for e in wood(a):
                if e['to'][1] != 16:
                    continue
                matches=[f for f in wood(b) if f['from'][1]==0 and f['from'][2]==e['from'][2]]
                check(len(matches)==1,'exactly one continuation across Y seam')
                check(abs(e['faces']['south']['uv'][1]-matches[0]['faces']['south']['uv'][3])<1e-10,
                      'continuous grain V at vertical join')
    # Formation, removal and re-addition over every requested size at negative/positive heights.
    for base_y in (-1,0,1):
        for width,height in ((1,1),(1,2),(2,1),(2,2)):
            full={(x,y+base_y) for x in range(width) for y in range(height)}
            def snapshot(cells):
                return {p:model(p[1],sum(bit for dx,dy,bit in ((-1,0,1),(1,0,2),(0,-1,4),(0,1,8))
                                        if (p[0]+dx,p[1]+dy) in cells)) for p in cells}
            before=snapshot(full)
            for removed in full:
                partial=full-{removed}
                snapshot(partial)
                check(snapshot(partial|{removed})==before,'re-add/reload independent of placement order')
    print(f'PASS {checks} connected-geometry checks (all masks, negative Y, 1x1/1x2/2x1/2x2).')

if __name__ == '__main__':
    test()
