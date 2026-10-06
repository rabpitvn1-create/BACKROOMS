from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

# This patch used to depend on the retired pickup/Restore reconciliation patch. That dependency is
# intentionally gone: player pickup is unavailable and Restore is narrative-only in the Kotlin
# authoritative core. Keep this build-time patch limited to prompt-policy alignment so it cannot
# recreate the old Java inventory mutation path.
legacy_prompt = '''      "Inventory chỉ đổi khi Kai thật sự lấy/nhận/copy/trao/mất/tiêu thụ vật; nhìn thấy không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''
current_prompt = '''      "INVENTORY AUTHORITY: Player prose như nhặt/lượm/lấy lên/cầm lên không được tự tạo quyền sở hữu; Inventory chỉ tăng từ story/drop/SYSTEM đã được xác thực hoặc từ Copy/transfer hợp lệ. " +
      "RESTORE NARRATIVE-ONLY: Hoàn Nguyên chỉ là năng lực kể chuyện cho vật thể/NPC trong narrative; không được mutate Inventory, Omnivault storedItems, quantity, content state, resource amount hay cooldown gameplay. " +
      "KAI LOADOUT: Kai mang tối đa 9 loại vật phẩm thường, mỗi loại tối đa x999; vũ khí ban đầu, giáp ban đầu và Omnivault Ring là Equipment riêng, không chiếm Inventory slot và không được Scan/Copy/Transfer/Store như vật phẩm thường. " +
      "Nhìn thấy vật phẩm không đồng nghĩa sở hữu. MadGod roll success chỉ mở discovery route, không tự đưa set vào inventory. " +'''

marker = "RESTORE NARRATIVE-ONLY"
if marker not in text and legacy_prompt in text:
    text = text.replace(legacy_prompt, current_prompt, 1)

# Never fail just because an upstream hardening patch changed Java helper text. The authoritative
# restrictions live in StateReducer/InventoryPolicy/OmnivaultEngine; this script must not couple
# itself to obsolete pickupCandidateAndroid or reconciliation helpers again.
MAIN.write_text(text, encoding="utf-8")
print("Kai resource policy aligned: authoritative acquisition, narrative-only Restore, 9x999 Kai inventory, signature equipment protected.")
