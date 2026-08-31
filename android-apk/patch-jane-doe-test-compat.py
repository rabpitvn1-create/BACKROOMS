from pathlib import Path

ROOT = Path(__file__).resolve().parent
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

text = TEST.read_text(encoding="utf-8")

attack_old = '''      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("Jane Doe tấn công:")) continue
      val expected = maxOf(1, (maxHp * 6 + 99) / 100)
'''
attack_new = '''      val result = CombatRuntime.resolve(state, "EXECUTE", "Cả Party cùng né tránh")
      if (!result.reply.contains("Jane Doe tấn công:")) continue
      if (result.reply.contains("Lilith Core tự kích hoạt") || result.reply.contains("Moonpiercer:") || result.reply.contains("Thorn Volley:")) continue
      val expected = maxOf(1, (maxHp * 6 + 99) / 100)
'''
if attack_new not in text:
    if text.count(attack_old) != 1:
        raise RuntimeError(f"Jane legacy attack test anchor count={text.count(attack_old)}")
    text = text.replace(attack_old, attack_new, 1)

stun_old = '''    val stunned = triggered!!
    val before = CombatRuntime.active(stunned)!!.entityHp
    val next = CombatRuntime.resolve(stunned, "EXECUTE", "Cả Party cùng tấn công")
'''
stun_new = '''    val stunned = triggered!!
    val stable = stunned.copy(metadata = stunned.metadata + ("combat.janeDoeLilithCoreActive" to "true"))
    val before = CombatRuntime.active(stable)!!.entityHp
    val next = CombatRuntime.resolve(stable, "EXECUTE", "Cả Party cùng tấn công")
'''
if stun_new not in text:
    if text.count(stun_old) != 1:
        raise RuntimeError(f"Jane legacy stun test anchor count={text.count(stun_old)}")
    text = text.replace(stun_old, stun_new, 1)

TEST.write_text(text, encoding="utf-8")
print("Jane Doe legacy-skill regressions isolated from Lilith Core and new bow damage.")
