export const LEVEL_PROFILES = Object.freeze({
  "0": { name: "The Lobby", hazardThreshold: 400, entityThreshold: 5, lootThreshold: 35, waterThreshold: 20 },
  "1": { name: "Parking Zone", hazardThreshold: 700, entityThreshold: 200, lootThreshold: 120, waterThreshold: 70 },
  "2": { name: "Pipe Dreams", hazardThreshold: 1000, entityThreshold: 350, lootThreshold: 100, waterThreshold: 35 },
  "3": { name: "The Electrical Station", hazardThreshold: 1200, entityThreshold: 350, lootThreshold: 150, waterThreshold: 20 },
  "4": { name: "The Abandoned Office", hazardThreshold: 300, entityThreshold: 10, lootThreshold: 180, waterThreshold: 120 },
  "5": { name: "Terror Hotel", hazardThreshold: 1000, entityThreshold: 400, lootThreshold: 100, waterThreshold: 60 },
  "6": { name: "Lights Out", hazardThreshold: 1200, entityThreshold: 5, lootThreshold: 45, waterThreshold: 35 },
});

const LEVEL_VISUALS = Object.freeze({
  "0": "stale uneven yellow wallpaper, damp yellow-brown carpet, fluorescent drop-ceiling panels, empty non-Euclidean office-like rooms",
  "1": "vast concrete parking garage, pillars, ramps, hanging industrial lights, rare unbranded broken cars, pockets of cold mist",
  "2": "tight concrete utility tunnels, dense rusted pipe networks, maintenance shafts, steam and alternating hot/cold zones",
  "3": "industrial electrical station corridors, transformers, conductor coils, heavy cables, fans, switchboards and hazardous high-voltage machinery",
  "4": "abandoned office floors, cubicles, loose desks, old computers, partially failed fluorescents and windows facing unmoving rain",
  "5": "immense early-20th-century hotel interior, dark wood, thick carpet, warm aged lamps, ballroom/guest corridors and subtly impossible geometry",
  "6": "near-black open tundra, cold earth or snow, sparse dead brush, extremely limited visibility and rare distant dark obelisks only when state confirms one",
});

const WORLD_CORE = `
BACKROOMS WORLD CORE — PROJECT CANON
- Nguồn gốc thật của Backrooms không bao giờ được xác nhận. Lời kể, tài liệu, di tích và Entity có thể mâu thuẫn; không nguồn nội thế giới nào là đáp án cuối mặc định.
- Backrooms là một thực tại liên tục khổng lồ. “Level” chỉ là nhãn survivor dùng cho các vùng tương đối ổn định, không phải các hộp không gian độc lập.
- Ranh giới Level có thể mờ; hành lang có thể đổi dần sang vùng khác; lối nối có thể biến mất; bản đồ chỉ đúng cục bộ và tại thời điểm được kiểm tra.
- Không gian có thể tự tái cấu trúc khi không bị quan sát. Thời gian có thể lệch, lặp, mất đoạn hoặc chồng lớp; đồng hồ chỉ có giá trị tham khảo cục bộ.
- Ký ức có thể bị sửa, sao chép hoặc biểu hiện thành phòng, vật, âm thanh và cảnh quan. Bản sao hoàn hảo của một người có thể tồn tại; không có phép thử tuyệt đối chứng minh “bản gốc”.
- Không dùng “sanity” như thanh HP. Tác động nhận thức phải hiện ra qua thiếu ngủ, chú ý, ký ức, tri giác, lựa chọn và hành vi.
- Cơ thể người vẫn chịu đói, khát, mất máu, nhiễm trùng, nhiệt, lạnh, kiệt sức và thiếu ngủ. Người sống lâu không tự nhận thích nghi siêu nhiên.
- Cái chết không có một cơ chế chung: có thể là chấm dứt, biến mất, chuyển vùng, tạo bản sao, biến đổi thành Entity hoặc để lại sai lệch ký ức. Không tự chọn một cơ chế nếu state chưa chứng minh.
`;

const LEVELS = {
  "0": `
LEVEL 0 — THE LOBBY
- Mạng phòng vàng phi Euclid: giấy dán tường cũ không đồng màu, thảm ẩm mùi mốc/hóa chất, trần thả, đèn huỳnh quang sai quy chuẩn, ổ điện ở vị trí vô lý. Không có hai vùng hoàn toàn giống nhau.
- HUM-0A: tiếng đèn kéo dài gây đau đầu, mất ngủ, khó tập trung, nghe nhầm bước chân và cảm giác bị quan sát. Không mặc định đó là Entity.
- HUM-0B: Memory Rooms có thể mang màu, cửa, mùi, giọng hoặc đồ vật từ ký ức; chi tiết có thể sai hoặc thuộc người khác.
- HUM-0C: lập bản đồ quá chi tiết có thể làm số đo lệch, phòng biến mất và dấu đánh dấu bị chuyển/nhân đôi. Không xác nhận Backrooms có chủ ý.
- Không có Entity cư trú được xác nhận. Entity thật chỉ có thể là roaming/incursion cực hiếm và phải có bằng chứng tích lũy.
- Tài nguyên: Almond Water đóng chai/thùng nhỏ cực hiếm; dây trần/linh kiện trên trần và sợi giấy khô có thể salvage. Tuyệt đối không uống nước từ thảm.
- Chuyển sang Level 1 chỉ khi môi trường thật sự đổi dần: phòng rộng hơn, tường bê tông, tiếng đèn giảm, trần cao, cột chịu lực/vạch sơn xuất hiện. Không thể ép quá trình xảy ra.
`,
  "1": `
LEVEL 1 — PARKING ZONE
- Gara/bãi đỗ xe bê tông rộng: cột, đèn treo, cầu thang, dốc, xe hiếm và thường hỏng; nhiệt cao xen sương lạnh cục bộ.
- Blackout có thể kéo dài phút tới nhiều ngày; Entity chủ động hơn, âm thanh đi xa, nhóm dễ tách, nguồn sáng vừa có ích vừa làm lộ vị trí.
- Maintenance Halls có hộp điện, dây trần, dụng cụ, ván ép và đường ống; có thể còn điện nhưng không phải nơi trú lâu dài. Luxury Lots sạch/rộng/sương dày hơn, crate tương đối dễ gặp hơn nhưng vẫn hiếm.
- Entity: Hound, Clump, Duller, Deathmoth, Hostile Faceling, False Puddle, Paintings. Tất cả đều săn người; điểm yếu truyền miệng không tuyệt đối.
- Tài nguyên: Tripse Alloy, Supply Crate, Liquid Pain, Greek Fire cực hiếm. Crate có thể chứa đồ sống còn hoặc Liquid Pain dán nhãn sai.
- Sang Level 2 khi xe/cột biến mất, bê tông hẹp lại, đường ống và tiếng máy chiếm môi trường, không khí nóng ẩm hơn.
`,
  "2": `
LEVEL 2 — PIPE DREAMS
- Hầm kỹ thuật, shaft, crawlspace và đường ống gỉ; nhiệt dao động từ hơi nóng gây bỏng tới vùng thông khí lạnh/băng bất thường.
- Rung chấn có thể sập hành lang, vỡ ống, giải phóng hơi, cắt đường lui hoặc mở tuyến mới.
- Entity: Clump, Hound, Smiler, Skin-Stealer, Predatory Window, Biological Pipeline. Skin-Stealer có thể bắt chước sâu và sống cùng nhóm; Biological Pipeline dùng lối crawlspace giả, khóa đường rút và tiết dung dịch kiềm.
- Nước trong ống không được uống trực tiếp, kể cả trông giống Almond Water; đun không bảo đảm loại bỏ tác nhân dị thường. Có industrial salvage và DuPont–Bayer Solution cực nguy hiểm.
- Sang Level 3 khi máy biến áp, dây điện dày, quạt/tủ điện và tiếng điện cao áp trở thành đặc trưng chính.
`,
  "3": `
LEVEL 3 — THE ELECTRICAL STATION
- Hành lang công nghiệp với transformer, conductor, quạt, cuộn dây, bảng điện, đường ống nóng và dây xuyên tường; nguồn điện thật sự UNKNOWN.
- Blackout ở đây đặc biệt nguy hiểm do dây trần, máy quay, Entity dùng bóng tối và layout có thể đổi khi đèn trở lại. Tunnel tròn dưới sàn lạnh dưới 0°C có nước không đóng băng bình thường và tỷ lệ mất tích cao.
- Entity: Deathmoth, Wretch, Skin-Stealer, Cable Mimic. Cable Mimic hòa vào bó dây và có thể truyền điện qua nạn nhân.
- Tài nguyên: electrical salvage, charged cell có thể sai điện áp; không uống pipe water.
- Sang Level 4 qua tuyến thật sự chuyển dần thành văn phòng (có thể bắt đầu từ cửa OFFICE SECTOR/elevator). Một số passage tối có thể chuyển sang Level 6; không cửa nào là cổng tuyệt đối chỉ vì mang nhãn.
`,
  "4": `
LEVEL 4 — THE ABANDONED OFFICE
- Văn phòng/cubicle, máy tính cũ, cửa sổ nhìn ra trời mưa cố định; tiếng mưa gần như luôn nghe thấy. Hình học ổn định hơn nhưng không an toàn tuyệt đối.
- Không có Entity cư trú ổn định được xác nhận; Hound, Skin-Stealer, Predatory Window hoặc Deathmoth chỉ xuất hiện qua incursion khi ranh giới đổi.
- Almond Water tương đối dễ tìm hơn nhưng vẫn khan hiếm; nguồn có thể là vending machine, water cooler, phòng nghỉ hoặc tủ. Có office salvage, giấy sạch, Greek Fire cực hiếm.
- Nơi trú nhỏ có thể tồn tại; không tự biến nơi này thành thành phố/cộng đồng lớn an toàn.
- Sang Level 5 khi cửa/hành lang đổi dần sang khách sạn cổ: thảm dày, đèn vàng, gỗ tối, giấy trang trí, nhạc cổ điển/jazz và tiếng mưa biến mất.
`,
  "5": `
LEVEL 5 — TERROR HOTEL
- Khách sạn vô tận với sảnh, ballroom, phòng ngủ, nhà hàng, hồ bơi, maintenance hall và boiler; nhiều wing thuộc thời đại khác nhau, hình học phi Euclid mạnh.
- Ở lâu gây paranoia, thì thầm, bóng qua kính, cảm giác bị chạm, mất ngủ và khó phân biệt âm thanh thật/ký ức. Không quy tất cả về một thanh sanity.
- Entity: The Beast of Level 5 (apex, trí tuệ vượt người, săn dài hạn và dùng kiến trúc cô lập), Predatory Window, Skin-Stealer, Hound, Hotel Corpse Lure. The Beast không phải boss ngồi chờ đấu trực diện.
- Almond Water/đồ ăn có thể ở dining/bar/pantry/minibar nhưng phải kiểm. Brass Key có số thay đổi và không phải Level Key; một cửa từng an toàn có thể đổi tuyến.
- Sang Level 6 khi boiler/maintenance passage mất ánh sáng, nhiệt giảm, tường tan vào bóng tối và nền chuyển sang đất/tuyết.
`,
  "6": `
LEVEL 6 — LIGHTS OUT
- Canon hiện hành là tundra tối vĩnh viễn, không phải mê cung hành lang: đất lạnh, cây bụi héo, cây chết, hồ hiếm và obelisk rải rác. Tầm nhìn thường chỉ vài mét ngay cả khi có đèn.
- Nguy cơ chính: lạnh, bóng tối, thiếu nước/thức ăn, mất ngủ, microsleep, ảo giác âm thanh và lạc đường. Không có Entity cư trú được xác nhận; incursion vẫn có thể xảy ra cực hiếm.
- Obelisk có chức năng UNKNOWN; không tự gọi là cổng, mộ, thiết bị hay đài phát.
- Hồ nhỏ có thể chứa Almond Water nhưng phải kiểm nấm, xác, nhiệt, Liquid Pain và dấu sinh vật. Gỗ khô/Greek Fire là tài nguyên chiến lược nhưng ánh sáng có thể làm lộ vị trí.
- Chuyển vùng chỉ khi state và bằng chứng hỗ trợ; ánh sáng xa, hố sâu hoặc cú ngã không phải phương pháp bảo đảm.
`,
};

const ENTITY_RULES = `
ENTITY HARD LOCK
- Không có Entity thân thiện hoặc trung lập với con người; hành vi giúp đỡ chỉ có thể là một phần chiến thuật cuối cùng gây hại.
- Entity không tự tăng máu/kháng/sức mạnh chỉ để cân bằng với Kai. Một cá thể có thể học, giả điểm yếu, chia cắt nhóm, dùng giọng/xác/ký ức làm bẫy hoặc chỉ là một biểu hiện của bản thể lớn hơn.
- Không xếp hàng tấn công từng con. Không biến một lần điểm yếu có hiệu quả thành quy luật tuyệt đối.
- JEFF THE KILLER là unique roaming hunter cực hiếm trong Level 0–6, chỉ săn người, không phải đồng minh/thương nhân. Jeff có thể bị thương hoặc bị giết trong một encounter nhưng permadeath bị vô hiệu hóa: trạng thái chuyển RESPAWNING, sau độ trễ biến thiên trở lại ROAMING ở vị trí không xác định. Không respawn ngay trước mặt player và không dùng làm nguồn farm đồ.
`;

const ITEM_RULES = `
ITEM / RESOURCE HARD LOCK
- Nước, thức ăn, thuốc, súng và đạn của survivor rất khan hiếm. Không có tiền tệ chung; thông tin đáng tin cũng là tài sản sống còn.
- Almond Water bù nước và có thể hỗ trợ tỉnh táo/triệu chứng nhẹ; không chữa bách bệnh, có thể hết hạn và nguồn mở phải kiểm.
- Greek Fire cực hiếm, dùng sưởi/nấu/vũ khí cháy nhưng nguy hiểm trong không gian kín. Liquid Pain đỏ, độc, ăn mòn mô, có hơi hại và có thể bị dán nhãn sai.
- Vật từ Frontrooms có thể hoạt động bình thường rồi sai lệch, hao mòn, biến chất hoặc bị đồng hóa; không có thời hạn cố định.
- Không để tài nguyên xuất hiện đúng lúc chỉ để cứu player. Inventory NPC phải được khóa trước khi nó trở thành lợi thế cho GM.
- Rice Automatic / RA100 là SMG .45 ACP phổ biến có trọng số cao trong pool survivor đã được xác định có súng, nhất là nhóm có tổ chức; không phải survivor nào cũng có, đạn không vô hạn và sở hữu nó không tự chứng minh faction/kỹ năng/độ tin cậy.
- MadGod Set là UR+ UNIQUE gồm MadGod Armor + MadGod Magnum, tồn tại tối đa đúng 1 bộ/campaign. Trước khi spawn, discovery chance đúng 0,01% (roll 1 trên d10000) ở hành động/vị trí đủ điều kiện; không pity. Success tạo một vị trí/đường tiếp cận hợp lý, không đặt thẳng vào tay player. Sau khi spawn, procedural chance trở thành 0% dù set bị mất, bỏ lại, chuyển holder hoặc phá hủy.
- MADGOD MULTIPLIER là đúng x50 lên các thông số hiệu năng tích cực hữu hạn của người dùng và cả set; hai thành phần không stack thành x2500, không tạo năng lực/tri thức mới, không nhân các giá trị vốn vô hạn và không tự sinh weakness/cooldown/ammo limit/side effect.
- MadGod Magnum chưa có canon về đạn, cơ chế nạp, sát thương nền, tốc độ bắn, tự sửa, xuyên giáp hay hiệu ứng riêng; tuyệt đối không tự điền. Chỉ dùng ngoại hình đen–vàng cực kỳ nhiều lớp và vũ khí cực lớn khi state xác nhận set đang được trang bị.
`;

export function normalizeLevelNumber(value) {
  const number = String(value ?? "0").trim();
  return LEVEL_PROFILES[number] ? number : "0";
}

export function worldCanonFor(levelNumber) {
  const number = normalizeLevelNumber(levelNumber);
  return `${WORLD_CORE}\n${LEVELS[number]}\n${ENTITY_RULES}\n${ITEM_RULES}`;
}

export function levelVisualCanon(levelNumber) {
  return LEVEL_VISUALS[normalizeLevelNumber(levelNumber)];
}

export const WORLD_PROGRESSION_CANON = `${WORLD_CORE}\n${Object.values(LEVELS).join("\n")}\n${ENTITY_RULES}\n${ITEM_RULES}`;
