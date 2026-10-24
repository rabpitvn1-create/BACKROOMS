"""Final post-patch guard for whole-unit items (no FULL/LOW/EMPTY variants).

All earlier build patches may add HealingItems/Omnivault behavior. This script
runs after them, keeping the one-state contract visible to the GM and failing
the build if an old generated mutation path resurfaces.
"""
from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
ITEM = CORE / "ItemContent.kt"
ENGINES = CORE / "Engines.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

item = ITEM.read_text(encoding="utf-8")
engines = ENGINES.read_text(encoding="utf-8")
if "enum class ContentState { NONE }" not in item:
    raise RuntimeError("Item contract: expected exactly one content state")
for invalid in ("ContentState.FULL", "ContentState.LOW", "ContentState.EMPTY", "variantId(", "profileFor("):
    if invalid in item or invalid in engines:
        raise RuntimeError(f"Item contract: retired variant survived: {invalid}")
if "val consumedOnUse = owned.metadata[\"consumedOnUse\"].equals(\"true\", true)" not in engines:
    raise RuntimeError("Item contract: USE must consume whole units from normalized metadata")
if "val normalized = HealingItems.normalize(item) ?: item" not in item:
    raise RuntimeError("Item contract: healing normalization was overwritten")
if "physicalInstanceIds" not in item:
    raise RuntimeError("Item contract: Omnivault physical identity was lost")

main = MAIN.read_text(encoding="utf-8")
anchor = "          JSONObject generated = parseModelJson(\n"
directive = (
    '          prompt = "ITEM SINGLE STATE HARD LOCK: Mỗi Item có một trạng thái duy nhất. '
    'Item tiêu hao chỉ tính bằng đơn vị nguyên và giảm quantity khi USE. '
    'Không tạo FULL/LOW/EMPTY, không tạo biến thể còn ít, nửa chai hoặc vỏ rỗng '
    'sau khi dùng. Item tái sử dụng không tự hao hụt. '
    'GM không tự quyết định thay đổi quyền sở hữu và số lượng vật phẩm.\\n" + prompt;\n'
)
if "ITEM SINGLE STATE HARD LOCK:" not in main:
    if main.count(anchor) != 1:
        raise RuntimeError("Item state GM directive anchor changed")
    main = main.replace(anchor, directive + anchor, 1)
    MAIN.write_text(main, encoding="utf-8")
print("Single-state Item contract validated: no partial/empty variants, whole-unit consumption, GM directive attached.")

# The final loot gate must run after the one-state and Omnivault transformations.
runpy.run_path(str(ROOT / "patch-core-loot-authority-finalize.py"), run_name="__main__")
