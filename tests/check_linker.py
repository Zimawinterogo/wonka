"""Linker resource contract only. Runtime network rules are Java regressions."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[1]
a=root/'src/main/resources/assets/whiteout'
item=json.loads((a/'models/item/circuit_linker.json').read_text(encoding='utf-8'))
assert item['textures']['layer0']=='whiteout:item/circuit_linker'
assert (a/'textures/item/circuit_linker.png').is_file()
for lang in ('ru_ru','en_us'):
 assert 'item.whiteout.circuit_linker' in json.loads((a/f'lang/{lang}.json').read_text(encoding='utf-8'))
print('PASS linker assets and both translations')
