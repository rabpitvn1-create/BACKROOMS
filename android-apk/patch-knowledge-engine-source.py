from pathlib import Path

ENGINE = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/core/knowledge/KnowledgeContextEngine.kt"
text = ENGINE.read_text(encoding="utf-8")

old = '        references = strings(json.optJSONArray("references")),'
new = '        references = rawStrings(json.optJSONArray("references")),'
if old not in text:
    raise RuntimeError("Knowledge references parser anchor not found")
text = text.replace(old, new, 1)

old = '''      if (hasAny(actionText, "devil trigger")) {
        direct += "CHAR.KAI.DEVIL_TRIGGER"
        if ("syvial" in presentActors) direct += "CHAR.SYVIAL.DEVIL_TRIGGER"
      }
      direct.forEach { add(it, "direct structured lookup") }

      // Exact entity/item terms use tag indexes, not semantic retrieval.
      tokenizeTags(actionText).forEach { tag ->
        db.tagIndex[tag].orEmpty().forEach { id ->
          val r = db.records[id] ?: return@forEach
          if (r.domain == "ENTITY" || r.domain == "ITEM") add(id, "explicit structured tag: $tag")
        }
      }
'''
new = '''      if (hasAny(actionText, "devil trigger")) {
        direct += "CHAR.KAI.DEVIL_TRIGGER"
        if ("syvial" in presentActors) direct += "CHAR.SYVIAL.DEVIL_TRIGGER"
      }
      if (hasAny(actionText, "nói", "hỏi", "trả lời", "trò chuyện", "nói chuyện", "dialogue", "talk", "tell")) {
        direct += "WRITING.DIALOGUE"
      }
      direct.forEach { add(it, "direct structured lookup") }

      // Registry-driven exact tags. Adding a new Entity/Item record with tags makes it
      // discoverable without adding a new prompt branch or hardcoded name here.
      db.tagIndex.entries.asSequence()
        .filter { (tag, _) -> tag.length >= 3 && actionText.contains(tag) }
        .forEach { (tag, ids) ->
          ids.forEach { id ->
            val r = db.records[id] ?: return@forEach
            if (r.domain == "ENTITY" || r.domain == "ITEM") add(id, "explicit structured tag: $tag")
          }
        }
'''
if old not in text:
    raise RuntimeError("Structured registry lookup anchor not found")
text = text.replace(old, new, 1)

old = '''      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
      }
      if (hasAny(sceneText, "loot", "vật phẩm", "inventory", "almond", "liquid pain", "greek fire", "nước", "thuốc")) {
'''
new = '''      if (confirmedEntities > 0 || entityRoll || hasAny(sceneText, "entity", "thực thể", "quái", "hound", "smiler", "skin-stealer", "jeff")) {
        add("ENTITY.GLOBAL_HARD_LOCK", "entity state/scene requires entity rules")
      }
      // Entity records already persisted in the state registry are resolved through the
      // same database tag index. Database growth does not expand context unless a current
      // registry value actually names/tags that record.
      val registryText = normalize(flags?.opt("entityRegistry")?.toString().orEmpty())
      if (registryText.isNotEmpty()) {
        db.tagIndex.entries.asSequence()
          .filter { (tag, _) -> tag.length >= 3 && registryText.contains(tag) }
          .forEach { (tag, ids) ->
            ids.forEach { id ->
              val r = db.records[id] ?: return@forEach
              if (r.domain == "ENTITY") add(id, "current entity registry tag: $tag")
            }
          }
      }
      if (hasAny(sceneText, "loot", "vật phẩm", "inventory", "almond", "liquid pain", "greek fire", "nước", "thuốc")) {
'''
if old not in text:
    raise RuntimeError("State-driven entity registry anchor not found")
text = text.replace(old, new, 1)

old = '''      return iris.contains("separated") || syvial.contains("separated") ||
        (presentActors.size == 1 && state.optInt("turn", 1) <= 3)
'''
new = '''      return iris.contains("separated") || syvial.contains("separated")
'''
if old not in text:
    raise RuntimeError("Main campaign separation heuristic anchor not found")
text = text.replace(old, new, 1)

old = '''  private fun strings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += normalize(value)
    }
    return out
  }
'''
new = '''  private fun rawStrings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += value
    }
    return out
  }

  private fun strings(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val out = linkedSetOf<String>()
    for (i in 0 until array.length()) {
      val value = array.optString(i, "").trim()
      if (value.isNotEmpty()) out += normalize(value)
    }
    return out
  }
'''
if old not in text:
    raise RuntimeError("Knowledge strings helper anchor not found")
text = text.replace(old, new, 1)

ENGINE.write_text(text, encoding="utf-8")
print("Knowledge engine hardened: stable references, dialogue authority, registry-driven Entity/Item lookup, explicit story gating.")
