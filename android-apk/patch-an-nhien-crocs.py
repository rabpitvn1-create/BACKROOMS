from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"

canon_path = CORE / "AnNhienCanon.kt"
canon = canon_path.read_text(encoding="utf-8")

old_id = 'const val AN_NHIEN_FOOTWEAR_ID = "an-nhien:baby-tree-pink-slippers"'
new_id = 'const val AN_NHIEN_FOOTWEAR_ID = "an-nhien:pink-crocs"'
old_name = 'const val FOOTWEAR_NAME = "Đôi dép màu hồng có hình Baby Tree"'
new_name = 'const val FOOTWEAR_NAME = "Đôi dép Crocs màu hồng"'

if old_id in canon:
    canon = canon.replace(old_id, new_id, 1)
elif new_id not in canon:
    raise RuntimeError("An Nhien footwear ID anchor missing")

if old_name in canon:
    canon = canon.replace(old_name, new_name, 1)
elif new_name not in canon:
    raise RuntimeError("An Nhien footwear name anchor missing")

canon_path.write_text(canon, encoding="utf-8")
print("An Nhiên footwear updated to pink Crocs.")

# Special follower encounter policy and authoritative Iris/Syvial follower definitions.
runpy.run_path(str(ROOT / "patch-special-followers-025.py"), run_name="__main__")

# Link the uploaded Iris/Syvial avatars and add instant developer Party shortcuts.
runpy.run_path(str(ROOT / "patch-special-follower-cheats-avatars.py"), run_name="__main__")
