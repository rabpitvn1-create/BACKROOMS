from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
# Run after the existing release patch chain has finished rewriting core, bridge and WebView files.
# CI retry marker: writerPrompt return now prepends ActionRuntime context without a duplicate return.
for script in (
    "patch-three-action-core.py",
    "patch-three-action-bridge.py",
    "patch-three-action-ui-only.py",
):
    runpy.run_path(str(ROOT / script), run_name="__main__")

print("Step 2 applied: Search / Execute / Explore share one ActionRuntime pipeline.")
