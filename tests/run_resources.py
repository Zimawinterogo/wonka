"""Run every maintained resource test. Requires the full release build first."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent
tests=sorted(root.glob('check_*.py'))
failed=[]
for test in tests:
    result=subprocess.run([sys.executable,'-X','utf8',str(test)],cwd=root.parent)
    if result.returncode:failed.append(test.name)
if failed:raise SystemExit('FAILED: '+', '.join(failed))
print(f'PASS all {len(tests)} resource suites (no Minecraft launched)')
