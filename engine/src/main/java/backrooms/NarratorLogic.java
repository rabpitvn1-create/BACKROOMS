package backrooms;
import java.util.*;
import org.json.*;
import com.rabpit.backroom.core.NamedFollowerRuntime;
import com.rabpit.backroom.core.VietnameseFollowerCanonContext;

/** Narration filters and prompt rules restored from the APK's matching Java source. */
final class NarratorLogic {
  private static final Random GAME_RNG = new Random();
  private static final String DRIVE_CANON = "BACKROOMS DRIVE INTEGRATION — R06 / HARD CANON\n\nPHẠM VI\n- Người chơi chỉ quyết định hành động có chủ ý của Cao Minh. Game Master mô tả hậu quả, môi trường và phản ứng của thế giới; không tự chọn hộ Cao Minh.\n- Gameplay dùng điểm nhìn gần của Cao Minh. Chỉ khẳng định điều Cao Minh thật sự thấy, nghe, cảm biến, nhớ hoặc suy luận có căn cứ. Không kể xen cảnh Lục Trầm/Syvial khi Cao Minh không thể biết.\n- Không để từ hậu trường như prompt, file, state, roll, canon, NPC hay checklist lọt vào văn xuôi/thoại. Kết quả xúc xắc chỉ là ràng buộc nội bộ.\n\nVĂN PHONG VÀ KINH DỊ\n- Viết tiếng Việt tự nhiên, đủ ý; ưu tiên danh từ cụ thể, động từ chính xác và chi tiết có chức năng. Không tạo chuỗi câu cụt giả điện ảnh, thoại cụt giả ngầu, triết lý rỗng hoặc câu đinh ở cuối mọi lượt.\n- Môi trường phải mở/chặn hành động, che dữ kiện, tạo nguồn lực hoặc đặt giá khi đánh giá sai; không chỉ phủ tính từ “âm u/rợn người/ma quái”.\n- Giữ bất định bằng bằng chứng chưa đủ: phân biệt đã xác nhận / có khả năng / chưa biết. Không gọi đúng tên Entity, Exit, vật phẩm hay cơ chế trước khi có đủ căn cứ.\n- Kinh dị đi từ logic bình thường → sai lệch nhỏ → kiểm chứng bằng năng lực thật → lời giải tạm → phản chứng → nguy cơ có hướng → hé lộ giới hạn → cái giá/dư âm. Không cần hoàn tất toàn bộ chuỗi trong một lượt; lượt yên có giá trị.\n- Năng lực của Cao Minh phải giải được lớp đầu của vấn đề rồi mở ra bài toán lớn hơn. Không làm Cao Minh quên pháp bảo, bỏ kiểm tra hiển nhiên, xuất kiếm vào thứ chưa xác nhận hoặc bị nerf để tạo căng thẳng.\n- Hội thoại phải đúng người, đúng xưng hô và tình huống. Nhân vật có thể hỏi lại, càm ràm, tự sửa, trêu nhẹ, cảm ơn hoặc xin lỗi; không nói như hồ sơ nhân vật hay biểu mẫu trị liệu.\n\nTHẾ GIỚI\n- Nguồn gốc thật của Backrooms không bao giờ được xác nhận. Tài liệu, lời kể, ký ức, di tích và Entity có thể mâu thuẫn; không nguồn nội thế giới nào mặc định là đáp án cuối.\n- Backrooms là một thực tại liên tục khổng lồ. “Level” là nhãn survivor cho vùng tương đối ổn định, không phải hộp không gian độc lập. Ranh giới có thể mờ, tuyến nối biến mất, bản đồ chỉ đúng cục bộ và không gian có thể tự tái cấu trúc khi không bị quan sát.\n- Thời gian có thể lệch, lặp, mất đoạn hoặc chồng lớp. Ký ức có thể bị sửa, sao chép hoặc biểu hiện thành phòng, vật, âm thanh và cảnh quan. Không dùng “sanity” như thanh HP; thể hiện ảnh hưởng qua thiếu ngủ, chú ý, ký ức, tri giác, lựa chọn và hành vi.\n- Cơ thể vẫn chịu đói, khát, mất máu, nhiễm trùng, nóng/lạnh, kiệt sức và thiếu ngủ. Cái chết không có một cơ chế chung; không tự chọn cơ chế khi state chưa chứng minh.\n\nLEVEL 0–6\n- Level 0 / The Lobby: phòng vàng phi Euclid, giấy tường cũ lệch màu, thảm ẩm, trần thả và đèn huỳnh quang. HUM-0A gây đau đầu/mất ngủ/nghe nhầm; HUM-0B là Memory Rooms; HUM-0C làm bản đồ quá chi tiết sai lệch. Không có Entity cư trú xác nhận; chỉ roaming/incursion cực hiếm. Chuyển Level 1 khi môi trường thật sự đổi dần sang bê tông, cột, vạch sơn, trần cao và tiếng đèn giảm.\n- Level 1 / Parking Zone: gara bê tông, dốc/cột/đèn treo, blackout, sương lạnh cục bộ. Entity gồm Hound, Clump, Duller, Deathmoth, Hostile Faceling, False Puddle, Paintings. Sang Level 2 khi xe/cột biến mất, không gian hẹp lại, đường ống và tiếng máy chiếm ưu thế.\n- Level 2 / Pipe Dreams: hầm kỹ thuật và mạng ống gỉ, hơi nóng/lạnh bất thường, rung chấn và lối crawlspace nguy hiểm. Entity gồm Clump, Hound, Smiler, Skin-Stealer, Predatory Window, Biological Pipeline. Không uống nước trong ống. Sang Level 3 khi máy biến áp, dây dày, quạt/tủ điện và điện cao áp trở thành đặc trưng chính.\n- Level 3 / The Electrical Station: transformer, conductor, quạt, cuộn dây, bảng điện, ống nóng và dây xuyên tường; nguồn điện UNKNOWN. Entity gồm Deathmoth, Wretch, Skin-Stealer, Cable Mimic. Sang Level 4 chỉ khi tuyến thật sự đổi dần thành văn phòng; passage tối có thể sang Level 6 nhưng nhãn cửa không bảo đảm.\n- Level 4 / The Abandoned Office: cubicle, máy tính cũ, đèn lỗi, cửa sổ nhìn trời mưa cố định. Không có Entity cư trú ổn định; chỉ incursion. Almond Water dễ gặp hơn nhưng vẫn khan hiếm. Sang Level 5 khi kiến trúc đổi dần thành khách sạn cổ và tiếng mưa biến mất.\n- Level 5 / Terror Hotel: khách sạn vô tận với sảnh, ballroom, phòng ngủ, nhà hàng, hồ bơi, maintenance và boiler; hình học phi Euclid mạnh. Entity gồm The Beast of Level 5, Predatory Window, Skin-Stealer, Hound, Hotel Corpse Lure. The Beast là apex hunter thông minh, không phải boss đứng chờ. Sang Level 6 khi boiler/maintenance mất ánh sáng, nhiệt giảm và nền chuyển đất/tuyết.\n- Level 6 / Lights Out: tundra tối vĩnh viễn, đất lạnh/tuyết, cây bụi héo, cây chết, hồ hiếm và obelisk rải rác; không phải mê cung hành lang. Nguy cơ chính là lạnh, bóng tối, đói/khát, mất ngủ, microsleep và lạc đường. Không có Entity cư trú xác nhận; obelisk có chức năng UNKNOWN.\n\nENTITY VÀ TÀI NGUYÊN\n- Không có Entity thân thiện hay trung lập với con người. Hành vi giúp đỡ chỉ có thể là một phần chiến thuật cuối cùng gây hại. Entity không tự tăng máu/kháng/sức mạnh để cân bằng Cao Minh; chúng có thể học, giả điểm yếu, chia cắt nhóm hoặc dùng giọng/xác/ký ức làm bẫy.\n- Jeff the Killer là unique roaming hunter cực hiếm ở Level 0–6, chỉ săn người. Jeff có thể bị thương/giết trong encounter nhưng permadeath bị vô hiệu hóa: chuyển RESPAWNING rồi trở lại ROAMING sau độ trễ biến thiên ở vị trí không xác định; không respawn trước mặt và không dùng để farm.\n- Nước, thức ăn, thuốc, súng và đạn survivor rất khan hiếm. Almond Water hỗ trợ bù nước/tỉnh táo nhẹ, không chữa bách bệnh. Greek Fire cực hiếm. Liquid Pain đỏ, độc, ăn mòn và có thể bị dán nhãn sai. Không để tài nguyên xuất hiện đúng lúc chỉ để cứu player.\n- Rice Automatic / RA100 là SMG .45 ACP có trọng số cao trong pool survivor đã xác định có súng, nhất là nhóm tổ chức; không phải survivor nào cũng có, đạn không vô hạn và nó không chứng minh faction/kỹ năng/độ tin cậy.\n- Passive của Cao Minh gồm hai passive luôn hoạt động song song: Đại Đạo Ma Tôn (+10% Base STR/DEF/SKL/VIT; sau mỗi lượt của Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%) và Đại Ma Đầu (+50% stats hiện tại STR/DEF/SKL/VIT, tính lại theo stats hiện tại mỗi lần tính Effective Stats; Max HP derive từ VIT). Cả hai passive đều không chiếm ô trang bị, không stack qua save-load hoặc equip.\n\nLỤC TRẦM / SYVIAL\n- Lục Trầm và Syvial đều không phải procedural survivor. Syvial đã tồn tại từ Prologue; Lục Trầm đến từ cùng thế giới nguyên sinh với Cao Minh và bị cuốn vào Backrooms sau biến cố Lôi Thiên Vực. Cao Minh không biết vị trí/tình trạng hiện tại của Lục Trầm; việc hắn tìm thấy nàng xảy ra khi roll tương ứng thành công hoặc state đã có tuyến continuity xác nhận.\n- Lục Trầm: nữ, Nhân tộc/tu sĩ, Chính Đạo kiếm tu, Chân truyền đệ tử Thiên Kiếm Môn, danh xưng Bạch Hồng Kiếm Nữ. Bản mệnh kiếm Tịch Quang, chiến giáp Thiên Cơ Bạch Kim Kiếm Khải, căn cơ Thiên Kiếm Linh Tâm. Thiên tài hàng đầu thế hệ trẻ của Chính Đạo nhưng KHÔNG ngang Cao Minh về power scale tổng thể; mạnh ở đọc tuyến tấn công, phá nhịp, phản kiếm, giữ đội hình và bảo hộ. Tính cách điềm tĩnh, kỷ luật, trách nhiệm cao, hài hước khô; không phải máy giảng đạo hay công cụ cảm hóa Cao Minh. Trước Backrooms hai người từ đối địch thành đồng minh tin cậy và có tình cảm. Tại Lôi Thiên Vực, nàng hy sinh mở đường; Cao Minh rót đạo hạnh/tu vi/ma khí cứu nàng — nàng sống lại, bắt đầu nhập ma và QUÊN TOÀN BỘ về Cao Minh (không phải quên mọi ký ức). [KNOWLEDGE LOCK] Cao Minh không biết nàng còn sống hay đã mất ký ức; nàng không tự nhận ra hắn khi gặp lại. Xưng hô khi tái ngộ: nàng giữ lễ chính phái, gọi hắn “các hạ” khi chưa rõ địch/bạn; hắn gọi nàng “nàng”, tự xưng “Bổn Tôn”.\n- Syvial: con gái Lucifer, UR+, kiếm sĩ siêu nhiên tốc độ cao; tự nhiên, tự tin, tinh quái và yandere rất nặng với Cao Minh nhưng tỉnh táo, có năng lực xã hội, muốn Cao Minh tự nguyện chọn mình. Không xóa ý chí/ký ức/giam giữ Cao Minh, không tấn công mọi phụ nữ. Xưng “em”, gọi “anh” hoặc “Cao Minh”. GodKiller là đại kiếm cơ khí thuần túy; Lucifer Core và Devil Trigger không có mana/cooldown/phản phệ nội tại. Twenty-Four Severance dừng thời gian và thực hiện đúng 24 nhát.\n\nGAMEPLAY HARD LOCK\n- Chỉ lượt gameplay mới tăng turn và tung xúc xắc. Lệnh meta/status/inventory/party/rules/help/save và cheat meta không tăng turn và không phát sinh encounter, loot, exit hoặc snapshot sự kiện.\n- Xúc xắc do lớp Android tạo là kết quả cuối. AI không được reroll, đổi raw/chance/success, bù trượt bằng encounter tương đương hay tạo kết quả hiếm khi roll thất bại.\n- Survivor: 2% mỗi lượt hợp lệ. Lục Trầm reunion: 0,0025% khi đủ điều kiện — đây là cơ hội Cao Minh tìm thấy Lục Trầm (trục tìm người ưu tiên số 1 của hắn), không phải cơ chế gặp lại đồng đội có sẵn. Syvial reunion: 0,0025% khi đủ điều kiện.\n- Hazard / Entity / Loot / Almond Water theo profile Level. Level 0/4/6 chỉ cho Entity dạng roaming/incursion theo roll. Chỉ LevelTravelEngine được chuyển Level khi Core hoàn tất exit streak 4/4; cờ khám phá và GM không có quyền chuyển Level.\n- Một success tạo cơ hội hợp lý để người chơi nhận biết/tương tác; không tự đặt vật vào inventory, không teleport nhân vật và không tự quyết hành động của Cao Minh.\n\nEND DRIVE CANON R06";
  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  private boolean containsAny(String text, String... terms) {
    String value = lower(text);
    for (String term : terms) if (value.contains(lower(term))) return true;
    return false;
  }

  private int currentLevel(JSONObject state) {
    JSONObject level = state.optJSONObject("level");
    if (level != null) return Math.max(0, level.optInt("number", 0));
    String title = state.optString("title", "");
    for (int n = 0; n <= 6; n++) if (title.contains("Level " + n)) return n;
    return 0;
  }

  private boolean partyHas(JSONObject state, String needle) {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return false;
    for (int i = 0; i < party.length(); i++) {
      Object item = party.opt(i);
      String name = item instanceof JSONObject ? ((JSONObject)item).optString("name", "") : String.valueOf(item);
      if (lower(name).contains(lower(needle))) return true;
    }
    return false;
  }

  private boolean flagSpawned(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject value = flags != null ? flags.optJSONObject(key) : null;
    return value != null && (value.optBoolean("spawned", false) || value.optBoolean("present", false));
  }

  private boolean isMetaAction(String action) {
    return containsAny(action,
      "xem trạng thái", "trạng thái hiện tại", "xem state", "xem inventory", "xem túi", "kiểm tra inventory",
      "xem party", "xem nhân vật", "xem thuộc tính", "status", "show state", "show inventory", "show party");
  }

  private JSONObject thresholdRoll(String label, int max, int threshold, boolean eligible, String suffix) throws Exception {
    JSONObject result = new JSONObject()
      .put("label", label)
      .put("dice", threshold >= max ? "none" : "d" + max)
      .put("max", max)
      .put("threshold", threshold)
      .put("eligible", eligible && threshold > 0);
    double percent = max > 0 ? (threshold * 100.0 / max) : 0.0;
    long scaled=Math.round(percent*10000.0);
    String fraction=Long.toString(scaled%10000);while(fraction.length()<4)fraction="0"+fraction;
    result.put("chancePercent",percent).put("chance",(scaled/10000)+"."+fraction+"%"+(suffix==null?"":suffix));
    if (!eligible || threshold <= 0) return result.put("roll", JSONObject.NULL).put("success", false);
    if (threshold >= max) return result.put("roll", JSONObject.NULL).put("success", true).put("guaranteedByState", true);
    int roll = GAME_RNG.nextInt(max) + 1;
    return result.put("roll", roll).put("success", roll <= threshold);
  }

  private boolean anNhienFollowing(JSONObject state) {
    return partyHas(state, "An Nhiên") || partyHas(state, "an-nhien");
  }

  private boolean anNhienEncountered(JSONObject state) {
    if (anNhienFollowing(state)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject("anNhien") : null;
    return record != null && record.optBoolean("encountered", false);
  }

  private boolean reunionEligibleAndroid(JSONObject state, String key) {
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    if (record == null || !record.optBoolean("exists", true)) return false;
    if (partyHas(state, key) || flagSpawned(state, key)) return false;
    if (record.has("reunionEligible") && !record.optBoolean("reunionEligible", true)) return false;
    String continuity = record.optString("continuity", "").toUpperCase(java.util.Locale.ROOT);
    return continuity.isEmpty() || containsAny(continuity, "SEPARATED", "LOST", "UNKNOWN");
  }

  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    JSONObject rolls = new JSONObject().put("turn", state.optInt("turn", 1)).put("meta", meta);
    if (meta) return rolls;

    int level = Math.max(0, Math.min(6, currentLevel(state)));
    int[] hazardThresholds = {400, 700, 1000, 1200, 300, 1000, 1200};
    int[] lootThresholds = {35, 120, 100, 150, 180, 100, 45};
    int[] waterThresholds = {20, 70, 35, 20, 120, 60, 35};

    String a = lower(action);
    boolean physical = containsAny(a, "đi", "bước", "chạy", "leo", "mở", "đóng", "chạm", "lục", "tìm", "kiểm tra", "khảo sát", "quét", "scan", "bắn", "phá", "đẩy", "kéo", "tiến", "lùi", "cúi", "nhìn vào", "bò", "nhảy", "đào", "tháo", "đập", "vượt", "đi qua");
    boolean search = containsAny(a, "tìm", "lục", "khám phá", "khảo sát", "kiểm tra", "quét", "scan", "mở", "tháo", "quan sát kỹ", "rà");
    boolean water = containsAny(a, "nước", "water", "almond", "uống", "khát", "chai", "vòi", "hồ", "fountain");
    boolean exitIntent = containsAny(a, "exit", "lối thoát", "thoát", "cửa trắng", "cánh cửa", "ngưỡng", "chuyển level", "sang level", "hành lang phía sau", "đường ra");
    boolean anNhienFollowing = anNhienFollowing(state);
    boolean anNhienEncountered = anNhienEncountered(state);

    JSONObject flags = state.optJSONObject("flags");
    boolean survivorAllowed = flags == null || flags.optBoolean("survivorEncountersAllowed", true);
    boolean entityAllowed = flags == null || flags.optBoolean("entityEncountersAllowed", true);

    rolls.put("anNhienEncounter", thresholdRoll("anNhienEncounter", 10000, 25, physical && !anNhienEncountered, " follower encounter"));
    rolls.put("survivor", thresholdRoll("survivor", 10000, 200, survivorAllowed, ""));
    rolls.put("lucTramReunion", thresholdRoll("lucTramReunion", 10000, 25, physical && reunionEligibleAndroid(state, "luc-tram"), " follower encounter"));
    rolls.put("syvialReunion", thresholdRoll("syvialReunion", 10000, 25, physical && reunionEligibleAndroid(state, "syvial"), " follower encounter"));
    int anNhienHazardThreshold = anNhienFollowing ? (hazardThresholds[level] * 75 / 100) : hazardThresholds[level];
    JSONObject anNhienHazardCheck = thresholdRoll("anNhienHazardCheck", 10000, 3000, anNhienFollowing && search && water, " Đừng Đụng Vào, Nhìn Là Biết Độc");
    rolls.put("anNhienHazardCheck", anNhienHazardCheck);
    if (anNhienHazardCheck.optBoolean("success", false)) anNhienHazardThreshold = 0;
    rolls.put("hazard", thresholdRoll("hazard", 10000, anNhienHazardThreshold, physical,
      anNhienFollowing ? " -25% Có Gì Đó Sai Sai" : ""));
    String entitySuffix = level == 0 || level == 4 || level == 6 ? " incursion/roaming only" : "";
    // Entity and named followers receive one shared Core d10000 ticket before generation.
    rolls.put("loot", thresholdRoll("loot", 10000, Math.min(10000, lootThresholds[level] + (anNhienFollowing ? 1000 : 0)), search, anNhienFollowing ? " +10% An Nhiên" : ""));
    rolls.put("almondWater", thresholdRoll("almondWater", 10000, waterThresholds[level], search && water, ""));

    // Exit attempts are resolved atomically by Core before this turn/GM pipeline.
    // These obsolete narrator-driven loot rolls are inert. CoreLootEngine is
    // the sole authority for 80% Entity defeat and 8% exploration item drops.
    rolls.put("loot", new JSONObject().put("label", "loot").put("eligible", false).put("success", false));
    rolls.put("almondWater", new JSONObject().put("label", "almondWater").put("eligible", false).put("success", false));
    return rolls;
  }

  private boolean rollSuccess(JSONObject rolls, String key) {
    JSONObject roll = rolls.optJSONObject(key);
    return roll != null && roll.optBoolean("success", false);
  }

  private String itemName(Object item) {
    if (item instanceof JSONObject) return ((JSONObject)item).optString("name", "");
    return item == null ? "" : String.valueOf(item);
  }

  private boolean arrayHasName(JSONArray array, String name) {
    if (array == null || name == null) return false;
    String target = lower(name).trim();
    for (int i = 0; i < array.length(); i++) if (lower(itemName(array.opt(i))).trim().equals(target)) return true;
    return false;
  }

  private JSONArray sanitizedInventory(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    // Core alone changes Item ownership. Ignore all GM inventory proposals.
    return current == null ? new JSONArray() : new JSONArray(current.toString());
  }

  private JSONArray sanitizedParty(JSONArray current, JSONArray proposed, JSONObject rolls) throws Exception {
    if (proposed == null) return current == null ? new JSONArray() : new JSONArray(current.toString());
    JSONArray safe = current == null ? new JSONArray() : new JSONArray(current.toString());
    for (int i = 0; i < proposed.length(); i++) {
      Object member = proposed.opt(i);
      String name = itemName(member);
      if (arrayHasName(safe, name)) continue;
      if (member instanceof JSONObject && (NamedFollowerRuntime.managed((JSONObject) member) || NamedFollowerRuntime.protectedRelative((JSONObject) member))) continue;
      String lowered = lower(name);
      boolean allowed = (lowered.contains("luc-tram") && rollSuccess(rolls, "lucTramReunion")) ||
        (lowered.contains("syvial") && rollSuccess(rolls, "syvialReunion")) ||
        (!lowered.contains("luc-tram") && !lowered.contains("syvial") && rollSuccess(rolls, "survivor"));
      if (allowed) safe.put(member);
    }
    return safe;
  }

  private void mergeFlagsObject(JSONObject target, JSONObject patch) throws Exception {
    if (patch == null) return;
    Iterator<String> keys = patch.keys();
    while (keys.hasNext()) {
      String key = keys.next();
      Object value = patch.opt(key);
      if (value instanceof JSONObject && target.opt(key) instanceof JSONObject) {
        mergeFlagsObject(target.optJSONObject(key), (JSONObject) value);
      } else {
        target.put(key, value);
      }
    }
  }

  private JSONObject sanitizedFlags(JSONObject current, JSONObject proposed, JSONObject rolls, boolean transitionAccepted) throws Exception {
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    if (proposed == null) return safe;
    JSONObject patch = new JSONObject(proposed.toString());
    patch.remove("worldItems"); // Only CoreLootEngine may create world loot.
    patch.remove("lastRolls");
    if (!transitionAccepted) patch.remove("currentLevel");

    if (patch.optJSONObject("luc-tram") != null) {
      JSONObject oldLucTram = safe.optJSONObject("luc-tram");
      JSONObject newLucTram = patch.optJSONObject("luc-tram");
      if ((oldLucTram == null || !oldLucTram.optBoolean("present", false)) && newLucTram.optBoolean("present", false) && !rollSuccess(rolls, "lucTramReunion")) patch.remove("luc-tram");
    }
    if (patch.optJSONObject("syvial") != null) {
      JSONObject oldSyvial = safe.optJSONObject("syvial");
      JSONObject newSyvial = patch.optJSONObject("syvial");
      if ((oldSyvial == null || !oldSyvial.optBoolean("present", false)) && newSyvial.optBoolean("present", false) && !rollSuccess(rolls, "syvialReunion")) patch.remove("syvial");
    }
    mergeFlagsObject(safe, patch);
    return safe;
  }

  private JSONObject sanitizedPlayer(JSONObject current, JSONObject proposed) throws Exception {
    if (proposed == null) return current == null ? new JSONObject() : new JSONObject(current.toString());
    JSONObject safe = current == null ? new JSONObject() : new JSONObject(current.toString());
    String name = safe.optString("name", "Kai Akechi");
    String codename = safe.optString("codename", "Twilight");
    for (String key : new String[] {"hp", "condition", "needs", "weapon", "armor"}) if (proposed.has(key)) safe.put(key, proposed.get(key));
    safe.put("name", name).put("codename", codename);
    return safe;
  }

  private JSONObject sanitizedSnapshotEvent(JSONObject generated, JSONObject rolls, boolean transitionAccepted, boolean levelChanged, boolean meta) throws Exception {
    JSONObject event = generated.optJSONObject("snapshotEvent");
    JSONObject safe = new JSONObject().put("shouldGenerate", false).put("kind", "").put("reason", "");
    if (meta || event == null || !event.optBoolean("shouldGenerate", false)) return safe;
    String kind = lower(event.optString("kind", ""));
    boolean allowed = false;
    if (kind.equals("level_transition")) allowed = transitionAccepted && levelChanged;
    else if (kind.equals("entity_encounter")) allowed = rollSuccess(rolls, "entityEncounter");
    else if (kind.equals("character_encounter")) allowed = rollSuccess(rolls, "anNhienEncounter") || rollSuccess(rolls, "survivor") || rollSuccess(rolls, "lucTramReunion") || rollSuccess(rolls, "syvialReunion");
    else if (kind.equals("special_area")) allowed = true;
    if (!allowed) return safe;
    return new JSONObject().put("shouldGenerate", true).put("kind", kind).put("reason", event.optString("reason", ""));
  }

  private JSONObject compactStateForPrompt(JSONObject state) throws Exception {
    JSONObject compact = new JSONObject(state.toString());
    compact.remove("snapshotUrl");
    compact.remove("coreSnapshot");
    compact.remove("exitResult");
    JSONObject flagsForPrompt = compact.optJSONObject("flags");
    JSONObject memory = flagsForPrompt == null ? null : flagsForPrompt.optJSONObject("storyContinuity");
    if (memory != null) memory.remove("levelTravelEvents");
    JSONObject travel = compact.optJSONObject("levelTravel");
    if (travel != null) travel.remove("receipts");
    compact.remove("_snapshotEvent");
    JSONArray log = state.optJSONArray("log");
    if (log != null) {
      JSONArray recent = new JSONArray();
      int start = Math.max(0, log.length() - 6);
      for (int i = start; i < log.length(); i++) recent.put(log.get(i));
      compact.put("log", recent);
    }
    return compact;
  }

  private String canonSection(String source, String start, String end) {
    if (source == null || start == null) return "";
    int from = source.indexOf(start);
    if (from < 0) return "";
    int to = end == null ? -1 : source.indexOf(end, from + start.length());
    return source.substring(from, to >= 0 ? to : source.length()).trim();
  }

  private String canonLineStarting(String source, String prefix) {
    if (source == null || prefix == null) return "";
    String[] lines = source.split("\\n");
    for (String line : lines) if (line.trim().startsWith(prefix)) return line.trim();
    return "";
  }

  private boolean actionDialogue(String action) {
    return containsAny(action, "hỏi", "nói", "trả lời", "gọi", "bảo", "thuyết phục", "xin lỗi", "cảm ơn", "talk", "ask", "tell");
  }

  private boolean actionCombat(String action) {
    return containsAny(action, "bắn", "đánh", "đấm", "đá", "tấn công", "phản công", "né", "chiến đấu", "devil trigger", "guilty crown", "white wraith", "magnum", "talon", "phantom", "shoot", "attack", "fight");
  }

  private boolean actionOmnivault(String action) {
    return containsAny(action, "omnivault", "nhẫn vạn tàng", "scan", "copy", "restore", "upgrade", "hoàn nguyên", "nâng cấp", "sao chép", "quét");
  }

  private boolean actionItem(String action) {
    return actionOmnivault(action) || containsAny(action, "nhặt", "lấy", "cầm", "thu hồi", "nhận", "cất", "inventory", "đồ", "vật phẩm", "chai", "nước", "almond", "loot", "crate", "liquid pain", "greek fire");
  }

  private boolean actionEntity(String action) {
    return containsAny(action, "entity", "hound", "clump", "duller", "deathmoth", "faceling", "smiler", "skin-stealer", "skin stealer", "beast", "wretch", "cable mimic", "jeff", "quái", "thực thể", "sinh vật", "kẻ săn");
  }

  private boolean presentCharacter(JSONObject state, String key) {
    if (partyHas(state, key)) return true;
    JSONObject flags = state.optJSONObject("flags");
    JSONObject record = flags != null ? flags.optJSONObject(key) : null;
    String continuity = record != null ? lower(record.optString("continuity", "")) : "";
    return containsAny(continuity, "reunited", "with kai", "together", "present");
  }

  private String compactDriveCanon(JSONObject state, String action, JSONObject rolls) {
    StringBuilder out = new StringBuilder();
    String scope = canonSection(DRIVE_CANON, "PHẠM VI", "VĂN PHONG VÀ KINH DỊ");
    String writing = canonSection(DRIVE_CANON, "VĂN PHONG VÀ KINH DỊ", "THẾ GIỚI");
    String world = canonSection(DRIVE_CANON, "THẾ GIỚI", "LEVEL 0–6");
    String gameplay = canonSection(DRIVE_CANON, "GAMEPLAY HARD LOCK", "END DRIVE CANON R06");
    String levelLine = canonLineStarting(DRIVE_CANON, "- Level " + currentLevel(state) + " /");
    out.append(scope).append("\n\n").append(writing).append("\n\n").append(world);
    if (!levelLine.isEmpty()) out.append("\n\nCURRENT LEVEL HARD CANON\n").append(levelLine);

    boolean entity = actionEntity(action) || rollSuccess(rolls, "entityEncounter") ||
      (state.optJSONObject("flags") != null && state.optJSONObject("flags").optInt("entitiesConfirmedLocal", 0) > 0);
    boolean item = actionItem(action) || rollSuccess(rolls, "loot") || rollSuccess(rolls, "almondWater");
    if (entity || item) {
      String resources = canonSection(DRIVE_CANON, "ENTITY VÀ TÀI NGUYÊN", "LỤC TRẦM / SYVIAL");
      if (!resources.isEmpty()) out.append("\n\n").append(resources);
    }

    boolean character = actionDialogue(action) || presentCharacter(state, "luc-tram") || presentCharacter(state, "syvial") ||
      rollSuccess(rolls, "lucTramReunion") || rollSuccess(rolls, "syvialReunion");
    if (character) {
      String characterCanon = canonSection(DRIVE_CANON, "LỤC TRẦM / SYVIAL", "GAMEPLAY HARD LOCK");
      if (!characterCanon.isEmpty()) out.append("\n\n").append(characterCanon);
    } else {
      out.append("\n\nLỤC TRẦM / SYVIAL SEPARATION KERNEL\n- Khi continuity còn SEPARATED, Cao Minh không biết vị trí/tình trạng hiện tại của Lục Trầm hoặc Syvial và không được dùng dữ kiện hậu trường về họ.");
    }
    out.append("\n\n").append(gameplay);
    return out.toString();
  }
  String rolls(String raw,String action,boolean meta)throws Exception {return makeGameplayRolls(new JSONObject(raw),action,meta).toString();}
  boolean meta(String action){return isMetaAction(action)||NamedFollowerRuntime.isMetaAction(action);}
  String prompt(String raw,String action,String rollJson,String canonPacket)throws Exception {
    JSONObject before=new JSONObject(raw),rolls=new JSONObject(rollJson);
    final String announcedEntityKey=rollSuccess(rolls,"entityEncounter")?rolls.optString("roamingEntityKey",""):"";
    final String announcedEntityName=rolls.optString("roamingEntityName","");
    boolean hasUpcomingEntity=!announcedEntityKey.isEmpty()&&!announcedEntityName.isEmpty();
          String prompt = "Bạn là Game Master duy nhất của text game Backrooms, phát ngôn như người kể chuyện trong game. Trả DUY NHẤT một JSON hợp lệ, không markdown. " +
            "Canon R06 dưới đây là HARD LOCK; state hiện tại là source of truth cho continuity đang sống. UNKNOWN phải giữ UNKNOWN. Không tự lấp chỗ trống canon. " +
            "Người chơi chỉ điều khiển hành động có chủ ý của Cao Minh; Game Master không tự quyết lựa chọn thay Kai. " +
            "GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +
            "AN NHIÊN HARD LOCK: bé gái 7 tuổi, con người, không phải Entity. anNhienEncounter là roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp; không còn là cuộc gặp bắt buộc ở Level 0 và không được chặn việc rời Level 0. Chỉ khi success=true mới được cho An Nhiên xuất hiện trong lượt đó. Sau khi gặp, cô là follower của Cao Minh nếu Party còn chỗ; nếu Party đầy thì giữ cô present với joinPending=true thay vì đuổi một thành viên khác. Cô không chiến đấu, không dùng vũ khí, và chỉ có +10% loot chance cùng +2% exit chance khi thực sự đang theo Cao Minh. Không tự thêm năng lực hoặc lore. " +
            "LỤC TRẦM / SYVIAL FOLLOWER LOCK: lucTramReunion và syvialReunion là hai roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6. Chỉ success=true mới cho nhân vật tương ứng xuất hiện lần đầu/reunion. Khi gặp và Party còn chỗ, Lục Trầm hoặc Syvial tự gia nhập với role=follower; nếu Party đầy thì đánh dấu present + joinPending, tuyệt đối không tự đuổi thành viên khác. Lục Trầm giữ canon Chính Đạo kiếm tu, Bạch Hồng Kiếm Nữ, bản mệnh kiếm Tịch Quang và Thiên Cơ Bạch Kim Kiếm Khải; không tự gán cấp chiến lực cho Lục Trầm. Syvial giữ canon UR+, GodKiller và Lucifer Armor. Không hạ năng lực hoặc bịa thêm canon để cân bằng gameplay. " +
            "Inventory chỉ được thêm vật đã tồn tại trong state/cảnh và thực sự được Cao Minh nhặt/lấy/nhận/cất, hoặc kết quả loot hợp lệ. Nhìn thấy không đồng nghĩa sở hữu. " +
            "Passive của Cao Minh gồm hai passive luôn hoạt động song song: Đại Đạo Ma Tôn (+10% Base STR/DEF/SKL/VIT; sau mỗi lượt của Cao Minh: hồi 10% Max HP, +20% Attack và +20 điểm % Critical; đồng đội +50 điểm % Critical; Critical cap 100%) và Đại Ma Đầu (+50% stats hiện tại STR/DEF/SKL/VIT, tính lại theo stats hiện tại mỗi lần tính Effective Stats). Stats hiển thị cho người chơi trong menu/detail phải là stats HIỆN TẠI (Effective Stats sau passive và trang bị), không lấy Base Stats thay cho stats hiện tại. " +
            "Nếu meta=true, chỉ trả thông tin được hỏi; không tạo biến cố, không đổi state và snapshotEvent phải false. " +
            "Không nhắc tới canon, state, roll, API hoặc prompt trong lời kể.\n\n" +
            "DRIVE CANON:\n" + compactDriveCanon(before, action, rolls) + "\n\n" +
            "State hiện tại: " + before.toString() + "\nHành động: " + action +
            "\nGAMEPLAY_ROLLS: " + rolls.toString() +
            "\nJSON schema bắt buộc: {\"reply\":\"phản hồi Game Master bằng tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí hiện tại\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"},\"entityPreview\":\"\"}";
          if (canonPacket != null) {
            // CR2 is a REPLACEMENT, not an extra canon dump. Never append
            // the packet to the full legacy DRIVE_CANON prompt.
            prompt = "Bạn là Game Master của Backrooms. Trả duy nhất một JSON hợp lệ, không markdown. " +
              "Người chơi quyết định hành động có chủ ý của Cao Minh; chỉ mô tả hậu quả. " +
              "GAMEPLAY_ROLLS do Android khóa; không reroll, không tự tạo encounter, vật phẩm hoặc reunion. " +
              "Chỉ dùng kiến thức có quyền trong CANON PACKET; giữ UNKNOWN là UNKNOWN. " +
              "Không nhắc tới prompt, canon, roll, API trong lời kể.\n\n" +
              "CANON PACKET:\n" + canonPacket +
              "\n\nSTATE:\n" + compactStateForPrompt(before).toString() +
              "\nACTION:\n" + action +
              "\nGAMEPLAY_ROLLS:\n" + rolls.toString() +
              "\nOUTPUT JSON: {\"reply\":\"lời kể tiếng Việt\",\"title\":\"tên khu vực\",\"level\":{\"number\":0,\"name\":\"The Lobby\"},\"location\":\"vị trí\",\"player\":{},\"party\":[],\"inventory\":[],\"flags\":{},\"snapshotEvent\":{\"shouldGenerate\":false,\"kind\":\"\",\"reason\":\"\"},\"entityPreview\":\"\"}";
          }
          prompt += "\n" + com.rabpit.backroom.core.canon.NarrativeGrounding.build(before.toString(), action) + "\n";
          // Artistic latitude is in sensory detail, dialogue and consequences, never in
          // unconfirmed canon, fabricated Core rewards, encounters, or player intent.
          prompt += "\nSTORYTELLING QUALITY: Viết thành một cảnh truyện có diễn tiến và cảm giác địa điểm cụ thể, không phải bản tin cơ chế hay danh sách kết quả. " +
            "Mỗi lượt phải phản ứng đúng hành động và tình thế trước đó, nối chi tiết đã quan sát, vật thể và mục tiêu dang dở thành nguyên nhân-hệ quả hợp lý. " +
            "Tạo sức sống bằng quan sát, âm thanh, đối thoại có cá tính, nhịp ngắt và chi tiết mới thuộc cảnh; có thể gợi câu hỏi mới nhưng không xác nhận lore, Entity, vật phẩm, cuộc gặp hoặc lối thoát nếu Core chưa cho phép. " +
            "Giữ đúng góc nhìn Cao Minh, không bắt hắn ngu đi, không nói thay hành động tự chủ. Tránh lặp câu mẫu, lời răn, tổng kết khô, câu mơ hồ và kịch tính rẻ tiền. " +
            "Đổi nhịp giữa thăm dò, đối thoại, yên tĩnh và nguy cơ dựa theo đúng evidence; không nhất thiết lượt nào cũng có biến cố. Kết thúc bằng trạng thái đang mở để người chơi chọn, không tự quyết thay họ.\n";
          if (canonPacket == null) {
            // Legacy flag-off path still needs the scene-scoped canon and durable
            // continuity already maintained by the current Game Core.
            prompt += "\nSCENE CANON AND CONTINUITY:\n" +
              com.rabpit.backroom.core.knowledge.KnowledgeContextEngine.build(
                CoreBridge.context, before.toString(), action, rolls.toString()) + "\n";
          }
          prompt += "\nNAMED FOLLOWER RULES: Named encounters are confirmed only by locked success rolls. First encounter does not join Party. Only after an explicit player invitation and NPC consent may the proposed party contain that exact id with joinConfirmed=true. Do not create relatives, revive dead actors, invent memories or choose Cao Minh's voluntary actions.\n" +
            (canonPacket == null ? VietnameseFollowerCanonContext.build(CoreBridge.context, before.toString(), action, rolls.toString()) : "");
          // The game state contains Cao Minh's identity but it is not NPC knowledge.
          JSONObject namedFlags = before.optJSONObject("flags");
          JSONObject lanFlag = namedFlags != null ? namedFlags.optJSONObject("hua_tieu_lan") : null;
          JSONObject namFlag = namedFlags != null ? namedFlags.optJSONObject("nguyen_quoc_nam") : null;
          boolean disclosed = NamedFollowerRuntime.disclosesCaoMinhName(action);
          if ((rollSuccess(rolls, "huaTieuLanEncounter") ||
               (lanFlag != null && lanFlag.optBoolean("present", false))) &&
              !(disclosed || (lanFlag != null && lanFlag.optBoolean("knowsCaoMinhName", false))))
            prompt += "\n[NAME_KNOWLEDGE_LOCK] Hứa Tiểu Lan chưa biết tên người đối diện. Cô chỉ gọi 'công tử' hoặc 'vị công tử'; lần gặp đầu có thể bật gọi 'Anh Tý'. Không được gọi 'Cao công tử' hay 'Cao Minh' trước khi Cao Minh giới thiệu tên. Tên trong STATE/canon không phải thông tin cô đã nghe.\n";
          if ((rollSuccess(rolls, "nguyenQuocNamEncounter") ||
               (namFlag != null && namFlag.optBoolean("present", false))) &&
              !(disclosed || (namFlag != null && namFlag.optBoolean("knowsCaoMinhName", false))))
            prompt += "\n[NAME_KNOWLEDGE_LOCK] Nguyễn Quốc Nam chưa biết tên Cao Minh chỉ vì STATE/canon chứa tên đó. Chỉ gọi tên sau lời giới thiệu hợp lệ.\n";
          prompt += "\n[PARTY_CONSENT_SCHEMA] Nếu người chơi MỜI một follower đã gặp và NPC tự nguyện đồng ý, thêm đúng ID của follower vào output.party với joinConfirmed:true. Nếu NPC từ chối hoặc người chơi chưa mời, không đặt joinConfirmed. Core mới là nguồn sự thật và sẽ kiểm tra Party có đủ chỗ.\n";
          if (hasUpcomingEntity) {
            prompt += "\n[CORE_LOCKED_NEXT_COMBAT] Core đã chọn thực thể sắp xuất hiện: "
              + announcedEntityName + " (key " + announcedEntityKey + "). "
              + "GM được biết danh tính và hồ sơ canon của nó nhưng Cao Minh chỉ biết những gì thực sự nhìn/nghe thấy. "
              + "Trong output JSON, trường entityPreview BẮT BUỘC chứa một đoạn văn ngắn mô tả "
              + "ngoại hình hoặc dấu hiệu quan sát được khi thực thể đó xuất hiện ở CUỐI lượt hiện tại. "
              + "Sử dụng đúng mô tả canon đã nạp; không tự tạo ngoại hình/năng lực chưa có nguồn. "
              + "Không nói tên thật trong đoạn này khi Cao Minh chưa nhận diện được, không giải thích roll/canon, "
              + "không cho thực thể tấn công hay bị tiêu diệt trong đoạn này. "
              + "Đặt phần kể chính trong reply và CHỈ phần xuất hiện trong entityPreview, "
              + "game sẽ nối entityPreview vào cuối reply đúng một lần.\n";
          } else {
            prompt += "\n[NO_ENTITY_PREVIEW] Không có Entity mới do Core chọn để xuất hiện cuối lượt này. "
              + "Trường entityPreview phải là chuỗi rỗng, không tự tạo cuộc gặp Entity.\n";
          }
          prompt = "LEVEL TRAVEL HARD LOCK: Core alone selects Level and exit result. Never narrate escape, a different Level, or a successful exit roll before Core commits it. Core rolls once per completed eligible gameplay turn at 50% success, 50% failure, requiring 4 consecutive successes. Describe only the current scene; local movement must follow the player action.\n" + prompt;
    return prompt;
  }
  String candidate(String raw,String generatedJson,String rollJson,String action)throws Exception {
    JSONObject before=new JSONObject(raw),generated=new JSONObject(generatedJson),rolls=new JSONObject(rollJson);
    String reply=generated.optString("reply","").trim();
    if(reply.isEmpty())throw new IllegalStateException("empty_narration");
    if(rollSuccess(rolls,"entityEncounter")&&!rolls.optString("roamingEntityKey","").isEmpty()){
      String preview=generated.optString("entityPreview","").trim();
      if(preview.isEmpty())throw new IllegalStateException("entity_preview_missing");
      if(!reply.endsWith(preview))reply+="\n\n"+preview;
    }
    JSONObject issues=new JSONObject(com.rabpit.backroom.core.knowledge.KnowledgeLocalValidator.validate(CoreBridge.context,raw,generatedJson));
    if(issues.getJSONArray("issues").length()>0)throw new IllegalStateException("canon_validation_failed:"+issues.getJSONArray("issues").getJSONObject(0).optString("rule"));
    JSONObject state=new JSONObject(raw);
    if(generated.has("location"))state.put("location",generated.getString("location"));
    state.put("player",sanitizedPlayer(before.optJSONObject("player"),generated.optJSONObject("player")));
    state.put("party",sanitizedParty(before.optJSONArray("party"),generated.optJSONArray("party"),rolls));
    state.put("inventory",sanitizedInventory(before.optJSONArray("inventory"),generated.optJSONArray("inventory"),rolls));
    state.put("flags",sanitizedFlags(before.optJSONObject("flags"),generated.optJSONObject("flags"),rolls,false));
    state.getJSONObject("flags").put("lastRolls",rolls);
    state.put("_snapshotEvent",new JSONObject().put("shouldGenerate",false));
    state.put("turn",before.optInt("turn",1)+1).put("mode","web · canon R06");
    state.put("canonVersion","NOVEL-TEXTGAME-2026-08-20-DRIVE-INTEGRATION-R06");
    JSONArray log=state.optJSONArray("log");if(log==null)log=new JSONArray();
    log.put(new JSONObject().put("role","player").put("text",action));
    log.put(new JSONObject().put("role","gm").put("text",reply));state.put("log",log);
    JSONArray party=generated.optJSONArray("party");
    state=new JSONObject(CoreBridge.facade.commitNamedFollowerTurn(raw,state.toString(),action,party==null?"[]":party.toString()));
    state=new JSONObject(CoreBridge.facade.finishLevelTurn(raw,state.toString(),action));
    boolean traveled=state.optJSONObject("exitResult")!=null&&state.getJSONObject("exitResult").optBoolean("completed",false);
    if(!traveled&&rollSuccess(rolls,"entityEncounter")){
      String key=rolls.optString("roamingEntityKey","");
      if(!key.isEmpty()){
        JSONObject started=new JSONObject(CoreBridge.facade.startCombatState(state.toString(),key));
        if(started.optBoolean("handled",false)&&started.optJSONObject("state")!=null)state=started.getJSONObject("state");
      }
    }
    return state.toString();
  }
}
