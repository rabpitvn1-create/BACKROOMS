from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "patch-an-nhien-follower.py"
code = SOURCE.read_text(encoding="utf-8")
old = "GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll."
new = "Người chơi chỉ điều khiển hành động có chủ ý của Kai; GM không tự chọn thay. GAMEPLAY_ROLLS do Android sinh là bất biến."
if code.count(old) != 1:
    raise RuntimeError(f"An Nhien wrapper expected one legacy prompt marker, found {code.count(old)}")
code = code.replace(old, new, 1)
exec(compile(code, str(SOURCE), "exec"), {"__name__": "__main__", "__file__": str(SOURCE)})
