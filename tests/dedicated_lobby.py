"""Boot the packaged release twice on Fabric and exercise real block ticks and saves."""
from pathlib import Path
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
base = "https://maven.fabricmc.net/"
installer = "fabric-installer-1.0.3.jar"
urllib.request.urlretrieve(base + "net/fabricmc/fabric-installer/1.0.3/" + installer, server / installer)
subprocess.run(["java", "-jar", installer, "server", "-mcversion", "1.21.1", "-loader", "0.18.4", "-downloadMinecraft"], cwd=server, check=True)
api = "fabric-api-0.116.17+1.21.1.jar"
urllib.request.urlretrieve(base + "net/fabricmc/fabric-api/fabric-api/0.116.17+1.21.1/" + api, mods / api)
shutil.copy2(next((root / "build/release").glob("Whiteout-*.jar")), mods)
(server / "eula.txt").write_text("eula=true\n")
(server / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nlevel-seed=314159\n")

def boot(first):
    process = subprocess.Popen(["java", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"], cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
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
        if first:
            command("fill 3 100 4 4 101 4 minecraft:stone")
            for i in range(4):
                command(f"setblock {4-i%2} {100+i//2} 3 whiteout:story_board[facing=north,part={i}]")
        for i in range(4):
            marker = f"BOARD_CELL_{i}_PRESENT"
            command(f"execute if block {4-i%2} {100+i//2} 3 whiteout:story_board[part={i}] run say {marker}")
            wait(marker, 15)
        if not first:
            command("setblock 4 100 4 minecraft:air")
            time.sleep(1)
            command("execute if block 3 101 3 minecraft:air run say BOARD_REMOVED")
            wait("BOARD_REMOVED", 15)
        command("stop")
        if process.wait(timeout=60):
            raise RuntimeError("Unclean dedicated shutdown")
    finally:
        if process.poll() is None:
            process.kill()
            process.wait()

boot(True)
boot(False)
print("PASS packaged Fabric dedicated lobby board survives restart and loses support correctly")
