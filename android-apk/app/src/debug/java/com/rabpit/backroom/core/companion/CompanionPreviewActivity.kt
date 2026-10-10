package com.rabpit.backroom.core.companion

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Debug-only, explicitly launched preview; no gameplay input until qualified runtime integration. */
class CompanionPreviewActivity : Activity() {
  private val io = Executors.newSingleThreadExecutor()
  private lateinit var session: CompanionPreviewSession
  private lateinit var status: TextView
  private lateinit var slot: EditText
  private lateinit var alias: EditText

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    session = CompanionPreviewSession(CompanionPreviewBindings.factory)
    val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
    status = TextView(this).apply { text = if (session.available) "Companion preview — chọn NEW GAME hoặc tải slot."
      else "Chế độ thử nghiệm chưa sẵn sàng. Chưa thể tạo hoặc tải campaign." }
    slot = EditText(this).apply { hint = "Slot ID"; isSingleLine = true }
    alias = EditText(this).apply { hint = "Request alias"; isSingleLine = true }
    layout.addView(status); layout.addView(slot); layout.addView(alias)
    fun button(label: String, operation: (String, String) -> CompanionPreviewSession.Projection?) {
      layout.addView(Button(this).apply {
        text = label; isEnabled = session.available
        setOnClickListener {
          val submitted = session.scope()
          val slotId = slot.text.toString()
          val requestAlias = alias.text.toString()
          io.execute {
            if (!session.isCurrent(submitted)) return@execute
            try {
              val projection = operation(slotId, requestAlias); val completed = session.scope()
              runOnUiThread { if (session.isCurrent(completed)) {
                status.text = if (projection == null) "Đã đóng scope; không tự tải lại slot."
                  else "Slot ${projection.slotId}\nRevision ${projection.revision}\nPending: ${projection.pendingPhase ?: "không"}\nCommitted revision: ${projection.committedRevision ?: "chưa có"}"
                if (projection != null) slot.setText(projection.slotId)
              } }
            } catch (failure: Exception) {
              val completed = session.scope()
              val message = when (failure.message) {
                "slot_missing" -> "Không tìm thấy slot. Không tạo campaign thay thế."
                "storage_version_unsupported" -> "Slot có phiên bản không tương thích. Không tự chuyển đổi."
                "qualified_foundation_unavailable" -> "Chế độ thử nghiệm chưa sẵn sàng."
                "slot_identity_invalid", "slot_id_invalid" -> "Slot ID không hợp lệ."
                "slot_not_open" -> "Chưa tải slot."
                "request_unknown" -> "Không tìm thấy request alias trong slot."
                "slot_process_busy", "slot_lifecycle_busy" -> "Slot đang được một phiên khác sử dụng."
                "slot_delete_failed" -> "Không xóa được slot. Phiên đã đóng; cần tải lại rõ ràng."
                else -> "Slot hoặc thao tác không vượt qua xác minh native. Không tạo campaign thay thế."
              }
              runOnUiThread { if (session.isCurrent(completed)) status.text = message }
            }
          }
        }
      })
    }
    button("NEW GAME") { _, _ -> session.createFresh() }
    button("TẢI / CHUYỂN SLOT") { slotId, _ -> session.load(slotId) }
    button("ĐỌC TRẠNG THÁI") { _, requestAlias -> session.project(requestAlias.takeIf { it.isNotBlank() }) }
    button("RESUME") { _, requestAlias -> session.resume(requestAlias) }
    button("CANCEL") { _, requestAlias -> session.cancel(requestAlias) }
    button("ĐÓNG SLOT") { _, _ -> session.closeSlot(); null }
    button("XÓA SLOT") { _, _ -> session.deleteCurrent(); null }
    setContentView(layout)
  }
  override fun onDestroy() { session.close(); io.shutdownNow(); super.onDestroy() }
}

/** Qualified native bootstrap adapter must be installed explicitly by the debug integration host. */
internal object CompanionPreviewBindings {
  @Volatile var factory: CompanionPreviewSession.Factory? = null
}
