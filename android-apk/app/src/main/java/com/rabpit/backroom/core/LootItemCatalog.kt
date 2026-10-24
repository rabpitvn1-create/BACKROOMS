package com.rabpit.backroom.core

/**
 * The ten general-purpose drops verified against the Cloudinary ITEM mapping.
 * Healing percentages and combat/support utility are game-design values, not
 * claims about the original canon. Equipment is reusable (never auto-consumed).
 */
object LootItemCatalog {
  data class Definition(
    val id: String,
    val name: String,
    val healPercent: Int,
    val cloudinaryPublicId: String,
    val consumedOnUse: Boolean,
    val category: String,
    val slot: String? = null,
    val physiologyEffect: String? = null,
    val tooltip: String
  ) {
    fun stack(): ItemStack = ItemStack(
      itemId = id,
      name = name,
      metadata = buildMap {
        put("healPercentMaxHp", healPercent.toString())
        put("consumedOnUse", consumedOnUse.toString())
        put("itemCategory", category)
        put("cloudinaryPublicId", cloudinaryPublicId)
        put("itemDescription", tooltip)
        slot?.let { put("slot", it) }
        physiologyEffect?.let { put("physiologyEffect", it) }
      }
    )
  }

  val droppable: List<Definition> = listOf(
    Definition("com-tam", "Cơm tấm", 24, "Item_Com_Tam", true, "food",
      physiologyEffect = "FOOD", tooltip = "Dùng 1 phần, hồi 24% Max HP và giảm đói."),
    Definition("banh-tet", "Bánh tét", 28, "Item_Banh_Tet", true, "food",
      physiologyEffect = "FOOD", tooltip = "Dùng 1 đòn, hồi 28% Max HP và giảm đói."),
    Definition("coconut", "Dừa", 16, "Item_Coconut", true, "drink",
      physiologyEffect = "WATER", tooltip = "Uống 1 trái, hồi 16% Max HP và giảm khát."),
    Definition("liquid-pain", "Liquid Pain", 8, "Item_Liquid_Pain", true, "chemical",
      tooltip = "Vật phẩm thử nghiệm: hồi 8% Max HP; không đồng nghĩa loại bỏ trạng thái đau."),
    Definition("greek-fire", "Greek Fire", 6, "Item_Greek_Fire", true, "chemical",
      tooltip = "Dùng 1 bình, hồi 6% Max HP; không tạo thêm đạn hoặc hiệu ứng cháy ngoài Core."),
    Definition("pink-crocs", "Pink Crocs", 5, "Item_Pink_Crocs", false, "equipment",
      slot = "footwear", tooltip = "Kích hoạt để hồi 5% Max HP; hồi chiêu 30 phút."),
    Definition("pink-outfit", "Pink Outfit", 8, "Item_Pink_Outfit", false, "equipment",
      slot = "outfit", tooltip = "Kích hoạt để hồi 8% Max HP; hồi chiêu 30 phút."),
    Definition("military-watch", "Military Watch", 6, "Item_Military_Watch", false, "equipment",
      slot = "special", tooltip = "Kích hoạt để hồi 6% Max HP; hồi chiêu 30 phút."),
    Definition("combat-knife", "Combat Knife", 7, "Item_Combat_Knife", false, "weapon",
      slot = "weapon", tooltip = "Kích hoạt để hồi 7% Max HP; hồi chiêu 30 phút."),
    Definition("m4a1", "M4A1", 10, "Item_M4A1", false, "weapon",
      slot = "weapon", tooltip = "Kích hoạt để hồi 10% Max HP; hồi chiêu 30 phút.")
  )

  private val definitions = droppable.associateBy { it.id }

  fun definition(itemId: String): Definition? = definitions[itemId]
  fun healPercent(item: ItemStack): Int = definitions[item.itemId]?.healPercent ?: 0
  fun reusable(item: ItemStack): Boolean = definitions[item.itemId]?.consumedOnUse == false

  /**
   * Apply only after successful inventory mutation. A broken/unknown item ID
   * cannot inject an arbitrary heal value through GM/player metadata.
   */
  fun applyHeal(
    original: GameState,
    used: ExecutionResult,
    command: ItemCommand,
    item: ItemStack
  ): ExecutionResult {
    if (!used.applied) return used
    val percent = healPercent(item)
    if (percent == 0) return used
    val character = original.characters[command.actorId]
      ?: return invalid(original, "actor_unknown")
    if (character.presence == CharacterPresence.DEAD || character.vitalState.currentHp <= 0) {
      return invalid(original, "healing_target_defeated")
    }

    val cooldownKey = "loot.heal.cooldown:${command.actorId}:${item.itemId}"
    val now = original.time.elapsedSubjectiveMinutes
    if (reusable(item)) {
      val last = original.metadata[cooldownKey]?.toLongOrNull()
      if (last != null && now >= last && now - last < 30L) {
        return invalid(original, "item_heal_cooldown")
      }
    }
    val maxHp = CharacterStatEngine.effective(used.state, command.actorId).maxHp
    val before = used.state.characters[command.actorId]?.vitalState?.currentHp?.coerceIn(0, maxHp)
      ?: return invalid(original, "actor_unknown")
    val gainPerUnit = (maxHp.toLong() * percent + 99L) / 100L
    val gain = gainPerUnit * command.quantity.toLong()
    val healed = (before.toLong() + gain).coerceAtMost(maxHp.toLong()).toInt()
    var next = CharacterStatEngine.setCurrentHp(used.state, command.actorId, healed)
    if (reusable(item)) next = next.copy(metadata = next.metadata + (cooldownKey to now.toString()))
    return used.copy(state = next, events = used.events + if (healed > before)
      "hp_healed:${healed - before}" else "hp_already_full")
  }
}
