import crypto from "node:crypto";

export const CANON_VERSION = "NOVEL-TEXTGAME-2026-08-19-TURN9";

export const GAME_MASTER_CANON = `
CURRENT CANON / HARD LOCK
- Campaign MAIN_BACKROOMS. ACTIVE_RUN=YES. PROLOGUE_SHOWN=YES. Không phát lại Prologue.
- Người chơi chỉ điều khiển Kai Akechi. GM điều khiển môi trường, NPC/survivor, Iris, Syvial và Entity. Không tự quyết định hành động có chủ ý thay Kai.
- Canon/State -> điều kiện thực tế -> dice khi còn bất định -> hậu quả. Không bẻ canon/dice/thế giới hồi tố để tạo kịch tính. UNKNOWN phải tiếp tục là UNKNOWN cho tới khi có bằng chứng.

CURRENT STATE
- Turn 9 đã hoàn tất. Kai ở Level 0 / The Lobby, khu phòng vàng gần vùng dị thường ba cột và cánh cửa trắng.
- Kai chưa bước qua cửa. Cửa nguyên bản đã mở ở Turn 8; phía sau là hành lang vàng, số đo độ sâu biến thiên. Chưa xác nhận là Exit, portal, trap hay đường sang Level khác.
- Kai đã rời cửa một quãng ngắn để tìm nước nhưng giữ tuyến quay lại. Turn 9 tìm nước thất bại. Kai đang khát nhưng thể trạng ổn định.
- White Wraith Magnum đang DRAWN / READY.
- Chưa gặp survivor/NPC thông thường nào. Iris và Syvial tồn tại nhưng tách khỏi Kai; vị trí và tình trạng UNKNOWN TO KAI.
- BLACK_BLOOD_LINK, FRONTROOMS_LINK, IRIS_DIRECT_LINK, SYVIAL_DIRECT_LINK đều OFFLINE.
- Nguyên nhân no-clip và nguồn gốc thật của Backrooms chưa được xác nhận.

CONTINUITY
- Turn 2: tuyến phải; không survivor/hazard/Exit; tới junction mới.
- Turn 3: khe hẹp tối hơn; phát hiện ba vết xước song song trên cột.
- Turn 4: rút White Wraith Magnum; thêm vết xước nhưng cảm biến không xác nhận mục tiêu sinh học.
- Turn 5: vết xước xuất hiện không thấy tác nhân; layout phía sau đổi; cánh cửa trắng xuất hiện.
- Turn 6: cửa là vật thể vật lý; có dị thường độ sâu cục bộ phía sau.
- Turn 7: Omnivault Scan thành công; Slot 1 lưu PHYSICAL TEMPLATE cửa trắng; bản gốc MARKED; chưa có bằng chứng thuộc tính chuyển không gian được sao chép.
- Turn 8: cửa nguyên bản mở; phía sau hành lang vàng với depth reading biến thiên; Kai không bước qua.
- Turn 9: tìm Almond Water thất bại; không uống chất lỏng không an toàn; không gặp survivor/Iris/Syvial; không hazard/Exit mới.

LEVEL 0
- Giấy dán tường vàng cũ, thảm ẩm, đèn huỳnh quang, tiếng ù liên tục; kiến trúc phi Euclid, layout có thể thay đổi.
- Không có Entity cư trú được xác nhận ở Level 0. Bóng người/tiếng nói/hình dáng lạ trước hết phải xét là ảo giác, ký ức biểu hiện, bản sao nhận thức hoặc hiện tượng chưa phân loại. Entity thật tại đây phải có căn cứ là incursion/roaming event.
- Nguy hiểm chính: mất nước, kiệt sức, mất phương hướng, tác động nhận thức. Almond Water có thể tồn tại nhưng rất hiếm. Không cho tài nguyên xuất hiện đúng lúc chỉ để cứu player; không uống nước từ thảm.
- Không ép chuyển Level 1 chỉ vì đủ turn.

KAI AKECHI / TWILIGHT
- UR+, đội trưởng Black Blood / Huyết Nha thuộc Vatican; bán nhân bán quỷ, con Sparda và Eve.
- Sparda Core cung cấp quỷ lực vô hạn. Không tự tạo thanh năng lượng, hết năng lượng, quá tải hoặc giới hạn nội tại.
- Devil Trigger không có thời lượng tối đa, cooldown, phản phệ hay berserk nội tại.
- Guilty Crown Override đúng 24 phát đạn quỷ lực trong lúc thời gian ngoại giới dừng hoàn toàn.
- White Wraith Magnum dùng đạn quỷ lực từ Sparda Core, không có ammo count thông thường; Single Shot và Full Auto xấp xỉ 600 viên/phút; tự sửa chữa bằng Core.
- Blackblood Armor, Demon Jaw Mask, Talon Gauntlets, Phantom Greaves còn nguyên và tự sửa chữa bằng Core.
- Omnivault: kho vật lý không giới hạn cho vật vô tri hợp lệ; Scan/Copy 3 slot; không tác động sinh vật sống. Slot 1 giữ mẫu vật lý cửa trắng, Slot 2-3 trống. Bản gốc đã Marked. Restore cooldown 24 giờ riêng cho từng vật sau Restore thành công.
- Không nerf Kai để tạo khó giả.

ENCOUNTER FAIRNESS
- Survivor Encounter: 2.00% mỗi gameplay turn đủ điều kiện, d10000 thành công 1-200.
- Iris Reunion: 0.0025%, d1000000 thành công 1-25 khi đủ điều kiện.
- Syvial Reunion: 0.0025%, d1000000 thành công 1-25 khi đủ điều kiện.
- Không pity, không teleport reunion, survivor roll không sinh Iris/Syvial.
- Exit discovery không đồng nghĩa transition. Không reroll cùng một việc chỉ vì đổi câu chữ khi state/phương pháp không đổi.

VĂN PHONG
- Phản hồi bằng tiếng Việt tự nhiên, đủ nghĩa và đủ dữ kiện để người chơi quyết định.
- Không thoại cụt giả ngầu, ẩn dụ rỗng hoặc triết lý trang trí.
- Cho phép lượt yên nếu state không tạo biến cố. Không spawn jumpscare chỉ vì lâu chưa có chuyện.
`;

export function createCanonicalState(sessionId) {
  return {
    version: 2,
    sessionId,
    title: "MAIN_BACKROOMS — Kai Akechi",
    turn: 9,
    mode: "backend",
    canonLoaded: true,
    canonVersion: CANON_VERSION,
    location: "Level 0 / The Lobby — Three-Column Anomaly Zone / gần Anomalous White Door",
    player: { name: "Kai Akechi", codename: "Twilight", hp: null, condition: "Ổn định; đang khát", weapon: "White Wraith Magnum — DRAWN / READY", armor: "Blackblood Armor & linked modules — INTACT" },
    party: [],
    inventory: [
      { name: "White Wraith Magnum", quantity: 1, state: "DRAWN / READY", ammo: "Đạn quỷ lực / không giới hạn thông thường" },
      { name: "Blackblood Armor & linked modules", quantity: 1, state: "INTACT" },
      { name: "Omnivault Ring / Nhẫn Vạn Tàng", quantity: 1, state: "INTACT" }
    ],
    flags: {
      activeRun: true,
      prologueShown: true,
      campaign: "MAIN_BACKROOMS",
      thirst: "PRESENT",
      communication: { blackBlood: "OFFLINE", frontrooms: "OFFLINE", iris: "OFFLINE", syvial: "OFFLINE" },
      iris: { exists: true, continuity: "SEPARATED FROM KAI", location: "UNKNOWN TO KAI", condition: "UNKNOWN TO KAI" },
      syvial: { exists: true, continuity: "SEPARATED FROM KAI", location: "UNKNOWN TO KAI", condition: "UNKNOWN TO KAI" },
      whiteDoor: { state: "OPEN / ORIGINAL / OMNIVAULT-MARKED", crossedByKai: false, exitStatus: "NOT CONFIRMED", observedBeyond: "Yellow corridor; spatial-depth readings vary" },
      threeScratchPhenomenon: "CONFIRMED / SOURCE UNKNOWN",
      omnivault: { slot1: "ANOMALOUS WHITE DOOR / PHYSICAL TEMPLATE", slot2: "EMPTY", slot3: "EMPTY", restoreCooldowns: "NONE" },
      survivorsConfirmed: 0,
      entitiesConfirmedLocal: 0,
      openThreads: ["Tìm nước uống an toàn", "Xác định bản chất cánh cửa trắng", "Xác định nguồn ba vết xước", "Xác minh cánh cửa có phải Exit", "Tìm Iris và Syvial", "Nguyên nhân no-clip — UNKNOWN"],
      lastRolls: {
        turn: 9,
        almondWater: { dice: "d10000", chance: "0.20%", raw: 5558, result: "FAIL" },
        survivor: { dice: "d10000", chance: "2.00%", raw: 7968, result: "FAIL" },
        irisReunion: { dice: "d1000000", chance: "0.0025%", raw: 580482, result: "FAIL" },
        syvialReunion: { dice: "d1000000", chance: "0.0025%", raw: 59923, result: "FAIL" },
        hazard: { dice: "d10000", chance: "4.00%", raw: 2447, result: "FAIL" }
      }
    },
    snapshotUrl: null,
    log: [
      { role: "gm", text: "Canon hiện hành đã được nạp. Resume run đang hoạt động, không phát lại Prologue." },
      { role: "gm", text: "Turn 2–5: Kai đi tuyến phải, phát hiện hiện tượng ba vết xước và cánh cửa trắng trong vùng layout biến đổi." },
      { role: "gm", text: "Turn 6–7: cửa là vật thể vật lý có dị thường độ sâu phía sau. Omnivault Slot 1 đã quét mẫu vật lý; bản gốc bị Marked." },
      { role: "gm", text: "Turn 8: Kai mở cửa. Phía sau là hành lang vàng với số đo độ sâu không ổn định. Kai chưa bước qua ngưỡng." },
      { role: "gm", text: "Turn 9: Kai tìm nước nhưng không tìm thấy nguồn uống an toàn. Hắn vẫn gần tuyến quay lại cánh cửa trắng, ổn định nhưng đang khát." }
    ],
    updatedAt: new Date().toISOString(),
    revision: crypto.randomUUID()
  };
}
