"""Smoke-check 1.1.93a WebPs and exactly 2% non-overlapping Entity spawn bands."""
from pathlib import Path
from hashlib import sha1

ROOT = Path(__file__).resolve().parent
assets = ROOT / "app/src/main/assets/entity"
java = (ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java").read_text(encoding="utf-8")
engine = (ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatChoiceEngine.java").read_text(encoding="utf-8")
ids = {
    "the_lifeform_bacteria_01": ("Bacterial Stalker", "521831fafa3a4d581580d48ae202039057d79a03"),
    "the_lifeform_bacteria_02": ("Bacterial Strider", "f74724c20338a13953c688e1a4c419806f0148ad"),
    "the_lifeform_bacteria_03": ("Bacterial Weaver", "f8bdaf08d0ae9d02b31601e11bc0a5fcab5be463"),
    "async_member_rifle_aim_right_01": ("Research Async Member", "1064e3e8b3333ddcf27861d36b2dfcdde03b6cc5"),
}

for key, (name, historical_blob_sha) in ids.items():
    raw = (assets / (key + ".webp")).read_bytes()
    assert raw[:4] == b"RIFF" and raw[8:12] == b"WEBP", key
    assert int.from_bytes(raw[4:8], "little") + 8 == len(raw), key
    git_sha = sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()
    assert git_sha == historical_blob_sha, (key, git_sha)
    assert f'case "{key}":' in java, key
    assert f'case "{key}": name = "{name}"; break;' in java, key
    assert f"'{key}'" in java, key
    assert f'entity("{key}",' in engine, key
    assert f'entitySkills("{key}",' in engine, key

for contract in (
    'String[] restoredEntityKeys = {',
    'String[] restoredRollLabels = {',
    'int rangeStart = 301 + i * 200, rangeEnd = rangeStart + 199;',
    '.put("threshold", 200)',
    '.put("chancePercent", 2.0)',
    'diepMinhRoll.optBoolean("eligible")',
    'rareEntityRoll >= rangeStart && rareEntityRoll <= rangeEnd',
    'normalEntityRoll.put("success", true).put("selectedBy", restoredRollLabels[i]);',
):
    assert contract in java, contract

# Disjoint from Diệp Minh 1..100 and dark Lục Trầm 101..300.
intervals = [set(range(301 + 200*i, 501 + 200*i)) for i in range(4)]
assert all(len(band) == 200 for band in intervals)
assert len(set.union(*intervals)) == 800
assert min(set.union(*intervals)) == 301 and max(set.union(*intervals)) == 1100
print("PASS: four original 1.1.93a assets, Combat93 registries and 4 x 2% spawn windows")
