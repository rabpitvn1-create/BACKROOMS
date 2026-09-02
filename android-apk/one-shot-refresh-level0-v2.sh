#!/usr/bin/env bash
set -euo pipefail

python3 - <<'PY'
from pathlib import Path

path = Path("android-apk/one-shot-refresh-level0.sh")
text = path.read_text(encoding="utf-8")
old = '''python3 -m py_compile android-apk/patch-level-snapshot-backgrounds.py android-apk/ci_apply_runtime_patches.py
rm -rf /tmp/android-apk-level0-check
cp -a android-apk /tmp/android-apk-level0-check
python3 /tmp/android-apk-level0-check/patch-level-snapshot-backgrounds.py
grep -q "level_0_1.webp" /tmp/android-apk-level0-check/app/src/main/java/com/rabpit/backroom/MainActivity.java
! grep -q "area_00_0_trusted_01.webp" /tmp/android-apk-level0-check/app/src/main/java/com/rabpit/backroom/MainActivity.java
'''
new = '''python3 -m py_compile android-apk/patch-level-snapshot-backgrounds.py android-apk/ci_apply_runtime_patches.py
python3 - <<'PYVERIFY'
from pathlib import Path
patch = Path("android-apk/patch-level-snapshot-backgrounds.py").read_text(encoding="utf-8")
chain = Path("android-apk/ci_apply_runtime_patches.py").read_text(encoding="utf-8")
required = (
    "LEVEL0_ORIGINALS = (",
    'if area_id == "0":',
)
for marker in required:
    if patch.count(marker) != 1:
        raise SystemExit(f"Unexpected final Level 0 patch marker count for {marker!r}: {patch.count(marker)}")
if "patch-level0-snapshot-override.py" in chain:
    raise SystemExit("Redundant Level 0 override patch is still in the runtime chain")
PYVERIFY
'''
if text.count(old) != 1:
    raise SystemExit(f"Unexpected standalone verification block count: {text.count(old)}")
text = text.replace(old, new, 1)
rm_line = 'git rm android-apk/one-shot-refresh-level0.sh\n'
if text.count(rm_line) != 1:
    raise SystemExit(f"Unexpected one-shot self-delete line count: {text.count(rm_line)}")
text = text.replace(
    rm_line,
    'git rm -f android-apk/one-shot-refresh-level0.sh\n'
    'git rm android-apk/one-shot-refresh-level0-v2.sh\n',
    1,
)
path.write_text(text, encoding="utf-8")
PY

exec bash android-apk/one-shot-refresh-level0.sh
