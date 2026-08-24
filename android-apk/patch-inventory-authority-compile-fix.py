from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

text = FACADE.read_text(encoding="utf-8")
old = r'Regex("\s+")'
new = r'Regex("\\s+")'
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Inventory authority compile fix expected exactly 1 invalid regex escape, found {count}")
text = text.replace(old, new, 1)
if old in text:
    raise RuntimeError("Invalid Kotlin regex escape survived inventory authority compile fix")
FACADE.write_text(text, encoding="utf-8")
print("Inventory authority compile fix applied: Kotlin whitespace regex escape corrected.")
