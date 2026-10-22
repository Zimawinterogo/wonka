"""Resource smoke checks for the attached red emergency fixture (no Minecraft runtime)."""
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/whiteout"
states = json.loads((ASSETS / "blockstates/emergency_lamp.json").read_text())
assert set(states["variants"]) == {"lit=false", "lit=true"}
for lit in (False, True):
    asset = f"emergency_lamp_{'on' if lit else 'off'}"
    assert states["variants"][f"lit={str(lit).lower()}"]["model"] == f"whiteout:block/{asset}"
    model = json.loads((ASSETS / f"models/block/{asset}.json").read_text())
    assert model["textures"]["panel"] == f"whiteout:block/{asset}"
    assert model["textures"]["case"] == "whiteout:block/emergency_lamp_steel"
    assert model["ambientocclusion"] is False
    assert any(element["from"] != [0, 0, 0] or element["to"] != [16, 16, 16] for element in model["elements"])
    assert (ASSETS / f"textures/block/{asset}.png").is_file()

on = (ASSETS / "textures/block/emergency_lamp_on.png").read_bytes()
off = (ASSETS / "textures/block/emergency_lamp_off.png").read_bytes()
for png in (on, off):
    assert png.startswith(b"\x89PNG\r\n\x1a\n")
    assert struct.unpack(">II", png[16:24]) == (16, 16)
assert on != off, "emergency panel never changes appearance"

java = (ROOT / "src/main/java/dev/whiteout/power/BackupLampBlock.java").read_text()
assert "getCullingShape" in java and "VoxelShapes.empty()" in java, "supporting block would be culled"
print("PASS emergency fixture: valid and distinct 16px ON/OFF textures, models and support-face culling")
