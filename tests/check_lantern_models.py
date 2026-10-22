# Validate exact vanilla model paths used by the 1.21.1 lantern block.
import json
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"src/main/resources/assets/whiteout/blockstates/linked_lantern.json"
data=json.loads(p.read_text())
assert len(data["variants"])==8
for state, model in data["variants"].items():
    expected="minecraft:block/lantern_hanging" if "hanging=true" in state else "minecraft:block/lantern"
    assert model["model"]==expected,(state,model["model"],expected)
print("PASS: 8 lantern variants reference valid vanilla 1.21.1 model names")
