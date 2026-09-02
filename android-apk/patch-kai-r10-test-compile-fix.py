from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/SruEquipmentIntegrationTest.kt"

text = TEST.read_text(encoding="utf-8")
old = 'it.name == "Technical Spec R10" && it.effect.contains("700–950")'
new = 'it.name == "Technical Spec R10" && it.description.contains("700–950")'

if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Kai R10 equipment regression compile fix: expected one anchor, found {count}")
    text = text.replace(old, new, 1)

if 'it.name == "Technical Spec R10" && it.description.contains("700–950")' not in text:
    raise RuntimeError("Kai R10 equipment regression must assert EquipmentAbility.description")

TEST.write_text(text, encoding="utf-8")
print("Kai R10 equipment regression compile fix applied: EquipmentAbility.description is asserted.")
