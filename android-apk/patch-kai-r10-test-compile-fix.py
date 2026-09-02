from pathlib import Path

ROOT = Path(__file__).resolve().parent
EQUIPMENT_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/SruEquipmentIntegrationTest.kt"
SKILL_TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CompanionSkillCatalogTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Kai R10 test compatibility {label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)


equipment = EQUIPMENT_TEST.read_text(encoding="utf-8")
equipment = replace_once(
    equipment,
    'it.name == "Technical Spec R10" && it.effect.contains("700–950")',
    'it.name == "Technical Spec R10" && it.description.contains("700–950")',
    "EquipmentAbility field",
)
equipment = replace_once(
    equipment,
    'assertTrue("SRU-SG Shotgun" in names)',
    'assertTrue("SRU Assault Rifle MK19" in names)',
    "current Kai weapon name",
)
equipment = replace_once(
    equipment,
    'assertEquals("Demon Shell ∞ / Physical Shell finite", EquipmentCatalog.definition(KAI_SRU_SG_ID)!!.weapon!!.ammoDisplay)',
    'assertEquals("30 viên 5.56×45 NATO / Sparda 5.56×45 ∞", EquipmentCatalog.definition(KAI_SRU_SG_ID)!!.weapon!!.ammoDisplay)',
    "current Kai ammo projection",
)
EQUIPMENT_TEST.write_text(equipment, encoding="utf-8")

skills = SKILL_TEST.read_text(encoding="utf-8")
skills = skills.replace(
    'skills.getValue(name).effect.contains("SRU-SG")',
    'skills.getValue(name).effect.contains("SRU Assault Rifle MK19")',
)
skills = replace_once(
    skills,
    'org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(KAI_ID).first().effect.contains("Kai ghìm nhịp giật của SRU-SG"))',
    'org.junit.Assert.assertTrue(CompanionSkillCatalog.forCharacter(KAI_ID).first().effect.contains("SRU Assault Rifle MK19") && CompanionSkillCatalog.forCharacter(KAI_ID).first().effect.contains("12 viên"))',
    "natural Vietnamese Kai R10 expectation",
)
SKILL_TEST.write_text(skills, encoding="utf-8")

for marker in (
    'it.name == "Technical Spec R10" && it.description.contains("700–950")',
    'assertTrue("SRU Assault Rifle MK19" in names)',
    '30 viên 5.56×45 NATO / Sparda 5.56×45 ∞',
):
    if marker not in EQUIPMENT_TEST.read_text(encoding="utf-8"):
        raise RuntimeError("Kai R10 equipment test compatibility missing: " + marker)

skill_text = SKILL_TEST.read_text(encoding="utf-8")
if 'skills.getValue(name).effect.contains("SRU-SG")' in skill_text:
    raise RuntimeError("Kai R10 stale shotgun skill regression survived")
if 'first().effect.contains("Kai ghìm nhịp giật của SRU-SG")' in skill_text:
    raise RuntimeError("Kai R10 stale natural-language shotgun regression survived")

print("Kai R10 regression compatibility applied: stale SRU-SG assertions now validate MK19, R10 ammo and 12/12/6/72 skill prose.")
