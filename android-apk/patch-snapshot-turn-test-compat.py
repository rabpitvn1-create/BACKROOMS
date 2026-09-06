from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatCoreTest.kt"
text = TEST.read_text(encoding="utf-8")

old = '''    val focuses = result.timeline.filter { it.kind == "FOCUS" }
    assertTrue(focuses.size >= 2)
    assertEquals("kai", focuses[0].actorId)
    assertEquals("lucia", focuses[1].actorId)
'''
new = '''    val focuses = result.timeline.filter { it.kind == "FOCUS" }
    assertTrue(focuses.size >= 3)
    assertEquals("kai", focuses[0].actorId)
    assertEquals("ENTITY.HOUND", focuses[0].targetId)
    assertEquals("ENTITY.HOUND", focuses[0].enemyId)
    assertEquals("ENTITY.HOUND", focuses[1].actorId)
    assertEquals("kai", focuses[1].targetId)
    assertEquals("ENTITY.HOUND", focuses[1].enemyId)
    assertEquals("lucia", focuses[2].actorId)
    assertEquals("ENTITY.HOUND", focuses[2].targetId)
    assertEquals("ENTITY.HOUND", focuses[2].enemyId)
'''

if new not in text:
    if text.count(old) != 1:
        raise RuntimeError(f"Snapshot turn test compatibility expected one legacy FOCUS assertion block, found {text.count(old)}")
    text = text.replace(old, new, 1)

if 'assertEquals("ENTITY.HOUND", focuses[1].actorId)' not in text:
    raise RuntimeError("Snapshot turn test compatibility failed to assert Entity-owned turn")

TEST.write_text(text, encoding="utf-8")
print("Snapshot turn regression compatibility updated: party -> Entity -> next party FOCUS ordering is authoritative.")
