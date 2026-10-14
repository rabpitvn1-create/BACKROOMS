package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class LuciaFollowerTest {
  @Test fun luciaUltimateUsesOnlyFinalizedSsfAndFsfWithSixtyShots() {
    val initial = LuciaCanon.ensure(GameState.initial())
    val ready = CombatRuntime.start(initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID))), "diep_minh")
    val encounter = CombatRuntime.active(ready)!!.encounterId
    val action = PokerDiceCore.DIRECT_COMBAT_ACTION
    fun withHand(hand: String): GameState = ready.copy(metadata = ready.metadata + mapOf(
      "poker.dice.encounter" to encounter,
      "poker.dice.action" to action,
      "poker.dice.values" to if (hand == "SSF") "1,2,3,4,5" else "6,6,6,6,6",
      "poker.dice.held" to "0,0,0,0,0",
      "poker.dice.finalized" to "true",
      "poker.dice.hand" to hand
    ))
    val ssf = CombatRuntime.resolve(withHand("SSF"), "EXECUTE", action)
    val fsf = CombatRuntime.resolve(withHand("FSF"), "EXECUTE", action)
    val normal = CombatRuntime.resolve(ready, "EXECUTE", action)
    val ssfDamage = ssf.feedback.single { it.actorId == LUCIA_ID && it.skill == "Too Young To Die" }.amount
    val fsfDamage = fsf.feedback.single { it.actorId == LUCIA_ID && it.skill == "Too Young To Die" }.amount
    val base = CharacterStatCore.basicDamage(26, CharacterStatEngine.effective(ready, LUCIA_ID).str)
    val singleShot = ((base.toLong() * 115L + 50L) / 100L).toInt()
    // Damage is capped to enemy HP remaining after earlier Party members act.
    // Equipment buffs can make SSF or FSF lethal before all nominal damage lands.
    val ssfBeforeLucia = ssf.feedback.single { it.actorId == LUCIA_ID && it.skill == "Too Young To Die" }
      .let { it.amount + (it.remainingHp ?: 0) }
    val fsfBeforeLucia = fsf.feedback.single { it.actorId == LUCIA_ID && it.skill == "Too Young To Die" }
      .let { it.amount + (it.remainingHp ?: 0) }
    assertEquals(minOf(ssfBeforeLucia, 60 * singleShot), ssfDamage)
    assertEquals(minOf(fsfBeforeLucia, 120 * singleShot), fsfDamage)
    assertTrue(ssf.reply.contains("đúng 60 phát"))
    assertTrue(fsf.reply.contains("FSF x200%"))
    assertFalse(ssf.reply.contains("Toxic Burst"))
    assertTrue(normal.feedback.none { it.skill == "Too Young To Die" })
  }

  @Test fun luciaBurstsProcIndependentlyOnlyAfterAHit() {
    val initial = LuciaCanon.ensure(GameState.initial())
    val ready = CombatRuntime.start(initial.copy(party = PartyState(memberIds = listOf(CAO_MINH_ID, LUCIA_ID))), "diep_minh")
    val expected = setOf("Toxic Burst", "Armor-Piercing Burst", "Concussive Burst", "Rending Burst", "Corrosive Burst")
    val triggered = mutableSetOf<String>()
    for (counter in 1..120) {
      val state = ready.copy(metadata = ready.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "EXECUTE", PokerDiceCore.DIRECT_COMBAT_ACTION)
      triggered += result.feedback.filter { it.actorId == LUCIA_ID && it.skill in expected }.map { it.skill }
    }
    assertEquals(expected, triggered)
  }

  @Test fun luciaCatalogMatchesFiveIndependentV2BurstsAndUltimate() {
    val skills = CompanionSkillCatalog.forCharacter(LUCIA_ID)
    assertEquals(8, skills.size)
    assertEquals(listOf("Toxic Burst", "Armor-Piercing Burst", "Concussive Burst", "Rending Burst",
      "Corrosive Burst"), skills.filter { it.kind == "AUTO" }.map { it.name })
    assertEquals("Too Young To Die", skills.single { it.kind == "ULTIMATE" }.name)
  }

  @Test fun luciaUsesCanonical1193aBaseStats() {
    val state = CharacterProgressionCore.normalize(GameState.initial())
    val lucia = state.characters.getValue(LUCIA_ID)
    assertEquals(50, lucia.statProfile.baseMaxHp)
    assertEquals(listOf(5,5,5,5,5), listOf(lucia.statProfile.str,lucia.statProfile.dex,lucia.statProfile.agi,lucia.statProfile.vit,lucia.statProfile.eva))
    assertEquals(50, CharacterStatEngine.effective(state, LUCIA_ID).maxHp)
  }

  @Test fun luciaHasExactlyThreeCanonicalEquipmentSlots() {
    val state = GameState.initial()
    val slots = state.equipment.getValue(LUCIA_ID).slots
    assertEquals(3, slots.size)
    assertEquals(LUCIA_M4A1_ID, slots["weapon"])
    assertEquals(LUCIA_KNIFE_ID, slots["blade"])
    assertEquals(LUCIA_WATCH_ID, slots["wrist"])
    slots.values.forEach { id -> assertFalse(state.inventories.getValue(LUCIA_ID).items.containsKey(id)) }
    assertEquals(0, InventoryCapacityPolicy.usedSlots(state, LUCIA_ID))
  }

  @Test fun luciaGiftInventoryAllowsThreeTypesAndOneHundredEach() {
    val state = GameState.initial()
    val profile = InventoryPolicy.profileFor(state, LUCIA_ID)
    assertEquals(3, profile.maxTypes)
    assertEquals(100, profile.maxPerType)

    val three = InventoryState(LUCIA_ID, mapOf(
      "a" to ItemStack("a", "A", 100),
      "b" to ItemStack("b", "B", 1),
      "c" to ItemStack("c", "C", 1)
    ))
    assertEquals("inventory_slot_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, three, ItemStack("d", "D", 1), 1))

    val ninetyNine = InventoryState(LUCIA_ID, mapOf("a" to ItemStack("a", "A", 99)))
    assertNull(InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 1), 1))
    assertEquals("inventory_stack_limit", InventoryPolicy.validateAddition(state, LUCIA_ID, ninetyNine, ItemStack("a", "A", 2), 2))
  }

  @Test fun luciaStartsOutsidePartyAndKeepsCanonAmmoSeparateFromGiftSlots() {
    val state = GameState.initial()
    val lucia = state.characters.getValue(LUCIA_ID)
    assertFalse(LUCIA_ID in state.party.memberIds)
    assertEquals("CORE_SPAWN", lucia.metadata["encounterPolicy"])
    assertEquals("0", lucia.metadata["encounterLevels"])
    assertEquals("EXPLORE", lucia.metadata["encounterAction"])
    assertEquals("60", lucia.metadata["startingLoadedAmmo"])
    assertEquals("90", lucia.metadata["startingReserveAmmo"])
    assertEquals("150", lucia.metadata["startingTotalAmmo"])
  }
}
