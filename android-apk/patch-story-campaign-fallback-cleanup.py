from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

main = MAIN.read_text(encoding="utf-8")
old = '''    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: năm 2267 Kai, Iris và Syvial chủ động đi qua cùng một cổng nhiệm vụ điều tra Async rồi bị tách khỏi nhau; "
        + "Hứa Thuý Lan vẫn là người mất tích mà Kai tin có thể ở Backrooms, chưa phải sự hiện diện hay vị trí đã được xác nhận.";
    }
'''
new = '''    } catch (Exception ignored) {
      return "MAIN STORY HARD LOCK: năm 2299 đội SRU của Kai, Iris và Syvial chủ động đi qua cùng một cổng để điều tra Async rồi bị phân tán tới các Level khác nhau; "
        + "giữ Iris và Syvial ở trạng thái chưa xác định vị trí cho tới khi story continuity xác nhận reunion.";
    }
'''
count = main.count(old)
if count != 1:
    raise RuntimeError(f"campaign story fallback cleanup: expected exactly one anchor, found {count}")
main = main.replace(old, new, 1)
if "Hứa Thuý Lan vẫn là người mất tích" in main:
    raise RuntimeError("obsolete private-target fallback survived")
MAIN.write_text(main, encoding="utf-8")
print("Aligned campaign story fallback with 2299 SRU companion continuity.")
