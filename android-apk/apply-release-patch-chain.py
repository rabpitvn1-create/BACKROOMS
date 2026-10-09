"""Execute the authoritative workflow's ordered patch array; no duplicated script list."""
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
workflow = (ROOT / '.github/workflows/build-backroom-apk.yml').read_text()
arrays = re.findall(r'^\s+scripts=\(([^\n]*)\)\s*$', workflow, re.M)
if len(arrays) != 1:
    raise RuntimeError('Expected one authoritative release patch array')
scripts = arrays[0].split()
if not scripts or len(set(scripts)) != len(scripts):
    raise RuntimeError('Empty or duplicated release patch array')
for script in scripts:
    if not re.fullmatch(r'patch-[a-z0-9.-]+\.py', script):
        raise RuntimeError('Unexpected patch array entry')
    print('==> ' + script, flush=True)
    subprocess.run([sys.executable, str(ROOT / 'android-apk' / script)], cwd=ROOT, check=True)
print(f'Applied {len(scripts)} authoritative top-level patches', flush=True)
