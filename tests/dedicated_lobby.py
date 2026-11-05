"""Boot the packaged release twice on Fabric and exercise real block ticks and saves."""
from pathlib import Path
import hashlib
import json
import queue
import shutil
import subprocess
import threading
import time
import urllib.request

root = Path(__file__).resolve().parents[1]
server = root / "build" / "qa-dedicated"
mods = server / "mods"
mods.mkdir(parents=True, exist_ok=True)
boards = [('north',4,3,-1,0,0,1), ('east',8,3,0,-1,-1,0),
          ('south',12,3,1,0,0,-1), ('west',8,10,0,1,1,0)]
base = "https://maven.fabricmc.net/"
installer = "fabric-installer-1.0.3.jar"
urllib.request.urlretrieve(base + "net/fabricmc/fabric-installer/1.0.3/" + installer, server / installer)
subprocess.run(["java", "-jar", installer, "server", "-mcversion", "1.21.1", "-loader", "0.18.4", "-downloadMinecraft"], cwd=server, check=True)
api = "fabric-api-0.116.17+1.21.1.jar"
urllib.request.urlretrieve(base + "net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/" + api, mods / api)
shutil.copy2(next((root / "build/release").glob("Whiteout-*.jar")), mods)
(server / "eula.txt").write_text("eula=true\n")
(server / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nlevel-seed=314159\n")

def boot(first, test_duo=False):
    process = subprocess.Popen(["java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dwhiteout.testDuoTrace=true", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding="utf-8", bufsize=1)
    lines = queue.Queue()
    def drain():
        for line in process.stdout:
            print(line.rstrip(), flush=True)
            lines.put(line)
        lines.put(None)
    threading.Thread(target=drain, daemon=True).start()
    def wait(marker, timeout=120):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            line = lines.get(timeout=max(.1, end-time.monotonic()))
            if line is None:
                raise RuntimeError("Server exited before " + marker)
            if marker in line:
                return
        raise TimeoutError(marker)
    def command(value):
        process.stdin.write(value + "\n")
        process.stdin.flush()
    try:
        wait('For help, type "help"', 180)
        command("forceload add 0 0")
        if test_duo:
            exercise_duo(command, wait, test_duo == 'restart')
        elif first:
            command("tick freeze")
            for face,x,z,dx,dz,bx,bz in boards:
                for i in range(4):
                    cx,cz=x+dx*(i%2),z+dz*(i%2)
                    command(f"setblock {cx+bx} {100+i//2} {cz+bz} minecraft:stone")
                    command(f"setblock {cx} {100+i//2} {cz} whiteout:story_board[facing={face},part={i}]")
            command("tick unfreeze")
        for face,x,z,dx,dz,bx,bz in ([] if test_duo else boards):
            for i in range(4):
                marker = f"BOARD_{face}_{i}_PRESENT"
                command(f"execute if block {x+dx*(i%2)} {100+i//2} {z+dz*(i%2)} whiteout:story_board[facing={face},part={i}] run say {marker}")
                wait(marker, 15)
        if not first and not test_duo:
            for face,x,z,dx,dz,bx,bz in boards:
                command(f"setblock {x+bx} 100 {z+bz} minecraft:air")
            wait("Changed the block", 15)
            removed = ' '.join(f'if block {x+dx*(i%2)} {100+i//2} {z+dz*(i%2)} minecraft:air'
                               for face,x,z,dx,dz,bx,bz in boards for i in range(4))
            for attempt in range(25):
                command(f"execute {removed} run say BOARD_REMOVED")
                try:
                    wait("BOARD_REMOVED", 1)
                    break
                except (queue.Empty, TimeoutError):
                    if attempt == 24:
                        raise AssertionError("Board survived removal of its support")

        command("stop")
        if process.wait(timeout=60):
            raise RuntimeError("Unclean dedicated shutdown")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()

def exercise_duo(command, wait, restart):
    name = "WoutQATester"
    command("forceload add -16 -16 16 16")
    command("fill -16 63 -4 15 63 7 minecraft:stone")
    command("fill -16 64 -4 15 67 7 minecraft:air")
    command(f"execute in minecraft:overworld positioned 12 64 1 run player {name} spawn")
    wait("joined the game", 90)
    command(f"gamemode creative {name}")
    command("scoreboard objectives add qa dummy")
    command("whiteout story-authority reload")
    wait("invalid=false", 10)
    counter = 0
    def action(text, ok=True):
        nonlocal counter
        counter += 1
        token = f"QA_RESULT_{counter}_OK"
        command(f"execute store result score result qa as {name} run whiteout test-duo {text}")
        command(f"execute if score result qa matches {int(ok)} run say {token}")
        try:
            wait(token, 10)
        except (queue.Empty, TimeoutError):
            diagnostic = list((server/"world"/"whiteout-duo-tests").glob("*.json"))
            if diagnostic:
                print("Persisted test events:", json.loads(diagnostic[0].read_text(encoding="utf-8")).get("completedEvents", []), flush=True)
            raise AssertionError(f"Unexpected command result: {text}; expected {ok}")
    def move(x):
        command(f"tp {name} {x} 64 1")
        time.sleep(.5)
    def progress():
        path, = (server/"world"/"whiteout-duo-tests").glob("*.json")
        return json.loads(path.read_text(encoding="utf-8"))
    if restart:
        action("status", False)
        command('execute unless entity @e[type=minecraft:armor_stand,name="Whiteout Test Bot"] run say QA_NO_ORPHAN')
        wait("QA_NO_ORPHAN",10)
        action("enable confirm")
        action("start")
        assert "whiteout:ending/letter" in progress()["completedEvents"]
        action("stop")
        action("disable")
        for path in (server/"world").glob("whiteout-zones*.json"):
            assert "whiteout:ending/letter" not in path.read_text(encoding="utf8")
        print("PASS dedicated cold restart: development mode off, no orphan mannequin, explicit resume retains isolated ending", flush=True)
        return
    command(f"op {name}")
    wait("server operator", 20)
    action("enable confirm")
    for branch in ("loss", "mercy", "letter"):
        action("reset confirm")
        move(12)
        action("start")
        action("start", False)
        action("role second" if branch == "mercy" else "role first")
        action("launch", False)
        action("ready")
        action("launch")
        action("choose dialogue_choice:missing:0:0", False)
        action("act whiteout:example/loss_exit", False)
        action("choose dialogue_choice:final_second:0:0", False)
        action("disconnect")
        action("choose dialogue_choice:final_second:0:0", False)
        action("reconnect")
        time.sleep(1)
        if branch == "loss":
            move(8)
            action("self dialogue_choice:qa_vote:0:0")
            action("choose dialogue_choice:qa_vote:0:1")
            assert "dialogue_choice:qa_vote:0:0" not in progress()["completedEvents"]
            action("choose dialogue_choice:qa_vote:0:0")
            assert "dialogue_choice:qa_vote:0:0" in progress()["completedEvents"]
            move(12)
        if branch == "letter":
            move(-12)
            action("choose dialogue_choice:letter:0:0")
            action("read")
            move(12)
        action("stay")
        move(0)
        assert "cutscene_trigger:final_meeting" not in progress().get("completedEvents", [])
        action("follow")
        time.sleep(.6)
        if branch == "mercy":
            action("read")
            action("choose dialogue_choice:final_second:0:1", False)
        else:
            action("read-self")
        choice = "0" if branch == "loss" else "1"
        actor = "self" if branch == "mercy" else "choose"
        action(actor+" dialogue_choice:final_second:0:" + choice)
        action(actor+" dialogue_choice:final_second:0:" + choice, False)
        assert "whiteout:ending/"+branch in progress()["completedEvents"]
        action("disconnect")
        action("reconnect")
        # This headless operator never acknowledges the Director camera.
        # Verify that the bounded production fallback actually completes the canonical exit.
        if branch == "mercy":
            time.sleep(10.8)
            action("skip-self")
            assert "whiteout:example/mercy_exit" not in progress()["completedEvents"]
            action("skip")
        end = time.monotonic() + (4 if branch == "mercy" else 32)
        while "whiteout:example/"+branch+"_exit" not in progress()["completedEvents"]:
            if time.monotonic() >= end:
                raise AssertionError("Scene loading timeout did not recover")
            time.sleep(.5)
    action("disable")
    action("state", False)
    action("enable confirm")
    action("start")
    print("PASS dedicated Duo commands: isolation, readiness, movement, reconnect, three Once endings, unacknowledged camera recovery", flush=True)

boot(True)
boot(False)
# Carpet supplies only the headless operator for this CI process. The Whiteout
# participant under test is the release's UUID adapter and does not use Carpet.
carpet = mods / "carpet-qa.jar"
if not carpet.exists():
    urllib.request.urlretrieve("https://cdn.modrinth.com/data/TQTTVgYE/versions/f2mvlGrg/fabric-carpet-1.21-1.4.147%2Bv240613.jar", carpet)
assert hashlib.sha512(carpet.read_bytes()).hexdigest() == "e6f33d13406796a34e7598d997113f25f7bea3e55f9d334b73842adda52b2c5d0a86b7b12ac812d7e758861e3f468bf201c6c710c40162bb79d6818938204151"
catalog = json.loads((root/"docs/examples/duo-final-events.json").read_text(encoding="utf8"))
options = ["dialogue_choice:qa_vote:0:0","dialogue_choice:qa_vote:0:1"]
catalog["events"].append(dict(key="cutscene_trigger:qa_vote",once=True,conditions=[],actions=[],
    area=dict(dimension="minecraft:overworld",box=dict(x1=7,y1=62,z1=-1,x2=10,y2=67,z2=3)),
    trigger=dict(type="ENTER_BOX",nextChoices=options),narrative=dict(together=True)))
for key in options:
    catalog["events"].append(dict(key=key,once=True,conditions=[],actions=[],trigger=dict(type="CHOICE"),narrative=dict(voteGroup="qa_vote")))
(server/"world/whiteout-story-events.json").write_text(json.dumps(catalog),encoding="utf8")
with (server/"server.properties").open("a") as props:
    props.write("\nonline-mode=false\n")
boot(False, test_duo=True)
boot(False, test_duo="restart")
print("PASS packaged Fabric dedicated lobby board survives restart and loses support correctly")
