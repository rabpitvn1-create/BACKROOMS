"""Public APK release gate: never package shared repository provider credentials."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
gradle = (root / "android-apk/app/build.gradle").read_text(encoding="utf-8")
names = [
    "GEHIHI_API_KEY", "GEHIHI_BASE_URL", "GEHIHI_MODEL", "SOL_API_KEY",
    "HAKU_API_KEY", "HAKU_BASE_URL", "HAKU_MODEL",
] + [f"GEMINI_API_KEY_{i}" for i in range(1, 6)]
for name in names:
    pattern = r'buildConfigField\s+"String",\s+"' + name + r'",\s*\'""\''
    if not re.search(pattern, gradle):
        raise SystemExit("Public APK contains a non-empty or dynamic BuildConfig field: " + name)
if "System.getenv" in gradle or "secret(" in gradle:
    raise SystemExit("Public APK must not interpolate repository environment secrets")

main = (root / "android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
if "BuildConfig.GEHIHI_API_KEY" in main or "BuildConfig.GEMINI_API_KEY" in main:
    raise SystemExit("Provider routing must use encrypted runtime credentials")
for name in ["android-targeted-tests.yml", "publish-android-release.yml"]:
    workflow = (root / ".github/workflows" / name).read_text(encoding="utf-8")
    if re.search(r"secrets\.(?:GEHIHI_|GEMINI_API_KEY_|HAKU_|SOL_)", workflow):
        raise SystemExit("Public APK workflow still injects repository credentials: " + name)
print("PASS: public APK contains no configured repository provider credentials")
