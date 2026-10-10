package com.rabpit.backroom.core.companion

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/**
 * Proactive actor-owned intent, not a gameplay turn. No RNG/commit/GM prose,
 * and no fabricated observation. An actual Core turn uses submit() separately.
 */
internal object CompanionNativeSuggestion {
  fun interface Model { fun suggest(privatePrompt: String): String }
  fun interface Auditor { fun audit(privatePrompt: String): String }

  private const val TRUSTED_INPUT =
    "Hãy tự cân nhắc tình hình hiện tại và đề xuất hành động hợp lệ nếu cần."
  @Throws(IOException::class)
  fun propose(context: Context,store: CompanionSlotStore,
              model: Model,auditor: Auditor): String {
    val bound=CompanionNativeDecisionContext.bind(context,store,
      "suggest-" + store.currentRevision(),TRUSTED_INPUT)
    val prompt="Bạn là ý chí độc lập của CAO MINH. " +
      "Không chờ chỉ thị của người chơi; tự cân nhắc từ canon, ký ức riêng và cảnh hiện tại. " +
      "Không tiết lộ phần ký ức/canon bí mật, không bịa dữ kiện hay kết quả chưa xảy ra. " +
      "Đây là Ý ĐỊNH, KHÔNG PHẢI LƯỢT ĐÃ THỰC HIỆN. " +
      "Private actor packet: " + ActorContextBuilder.canonical(bound.packet) +
      "\nScene: " + bound.scope.sceneId +
      "\nLegal targets: " + bound.scope.legalTargetIds.sorted() +
      "\nNative capabilities: " + bound.scope.capabilities.sorted() +
      "\nChỉ trả JSON {\"intent\":\"SEARCH\",\"targetId\":null,\"itemId\":null}, " +
      "chọn intent riêng của Cao Minh. Không bắt buộc SEARCH."
    val proposal=try { model.suggest(prompt) } catch (e: Exception) {
      throw IOException("suggestion_provider_failure",e)
    }
    val result=CompanionActorIntentGateway.select(proposal,bound.scope,bound.interaction)
    if (result !is CompanionActorIntentGateway.Result.Accepted)
      throw IOException("suggestion_not_authorized")
    val choice=result.selected.proposal
    // A suggestion may mention an unsupported intent, but it must never
    // be silently converted to a supported action.
    val check=try { JSONObject(auditor.audit(
      "Kiểm toán riêng, không tạo ra sự thật mới. Cho phép đúng ý định " +
      "trong cảnh đã xác nhận, không bịa canon hay năng lực. " +
      "Private actor packet: " + ActorContextBuilder.canonical(bound.packet) +
      "\nBinding: " + result.selected.binding.proposalDigest +
      "\nIntent: " + choice.intent.name + "\nTarget: " + (choice.targetId ?: "none") +
      "\nTrả JSON {\"verdict\":\"PASS\"} hoặc {\"verdict\":\"HARD\"}."
    )) } catch (e: Exception) { throw IOException("suggestion_audit_unavailable",e) }
    if (check.keys().asSequence().toSet() != setOf("verdict") ||
        check.optString("verdict") != "PASS")
      throw IOException("suggestion_audit_rejected")
    if (!CompanionNativeDecisionContext.exactSameSnapshot(store,bound))
      throw IOException("suggestion_stale")
    return JSONObject().put("version","companion_suggestion.v1")
      .put("slotId",store.slotId).put("revision",bound.revision)
      .put("actor","cao_minh").put("intent",choice.intent.name)
      .put("targetId",choice.targetId ?: JSONObject.NULL)
      .put("committed",false).toString()
  }
}
