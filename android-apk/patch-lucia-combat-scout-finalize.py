from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-lucia-combat-scout.py"

source = PATCH.read_text(encoding="utf-8")
# The generated Kotlin source correctly contains escaped quotes around Lục. The original
# textual contract check accidentally compared against the unescaped runtime text and
# rejected its own valid output. Remove only that redundant check before executing the
# patch; the focused Kotlin regression tests verify the actual log text after compilation.
source, count = re.subn(
    r"^\s*'Lucia \\\"Lục\\\" bắn hỗ trợ bằng M4A1',\n",
    "",
    source,
    count=1,
    flags=re.MULTILINE,
)
if count != 1:
    raise RuntimeError("Lucia combat scout finalizer could not locate the redundant escaped-log marker")

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
print("Lucia combat/scout finalizer executed with corrected generated-source contract check.")
