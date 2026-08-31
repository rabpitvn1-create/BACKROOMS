package com.rabpit.backroom.core

data class ForwardProgressionDecision(
  val allowed: Boolean,
  val reason: String? = null
)

/**
 * Authoritative monotonic guard for entering a different catalogued Level.
 *
 * IDs are treated as opaque strings. Reachability is derived only from explicit catalog edges;
 * numeric parsing of Level IDs is deliberately forbidden. The legacy campaign still owns its
 * exact one-step route, while this guard prevents any registered/procedural runtime path from
 * moving backward or leaving an unfinished Level.
 */
object ForwardProgressionPolicy {
  fun evaluate(
    catalog: LevelCatalog,
    currentLevelId: String?,
    currentCompleted: Boolean,
    requestedLevelId: String
  ): ForwardProgressionDecision {
    val requestedId = requestedLevelId.trim()
    if (requestedId.isEmpty()) return deny("progression_target_missing")

    val requested = catalog.get(requestedId)
      ?: return deny("progression_target_not_catalogued:$requestedId")

    val currentId = currentLevelId?.trim().orEmpty()
    if (currentId.isEmpty()) return ForwardProgressionDecision(allowed = true)
    if (currentId == requestedId) return ForwardProgressionDecision(allowed = true)
    if (!currentCompleted) return deny("progression_current_level_incomplete:$currentId")

    val current = catalog.get(currentId)
      ?: return deny("progression_current_not_catalogued:$currentId")
    val currentCampaign = current.campaignId?.takeIf(String::isNotBlank)
      ?: return deny("progression_current_campaign_missing:$currentId")
    val requestedCampaign = requested.campaignId?.takeIf(String::isNotBlank)
      ?: return deny("progression_target_campaign_missing:$requestedId")
    if (currentCampaign != requestedCampaign) {
      return deny("progression_cross_campaign_forbidden:$currentId:$requestedId")
    }

    val currentOrder = current.campaignOrder
      ?: return deny("progression_current_order_missing:$currentId")
    val requestedOrder = requested.campaignOrder
      ?: return deny("progression_target_order_missing:$requestedId")
    if (requestedOrder <= currentOrder) {
      return deny("progression_not_forward:$currentId:$requestedId")
    }
    if (!catalog.canTransition(currentId, requestedId)) {
      return deny("progression_transition_not_declared:$currentId:$requestedId")
    }

    return ForwardProgressionDecision(allowed = true)
  }

  private fun deny(reason: String) = ForwardProgressionDecision(allowed = false, reason = reason)
}
