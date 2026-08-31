#!/usr/bin/env python3
"""Build the reviewed Vietnamese seed corpus.

Training rows use controlled variation. Test rows are separately authored and never
generated from the training templates, preventing paraphrase-family leakage.
"""
import csv, itertools, pathlib

ITEMS = [
 "Đèn pin", "Bật lửa", "Nước Hạnh Nhân", "Thực phẩm đóng hộp", "Pin",
 "Nhiên liệu bật lửa", "Băng gạc", "Thuốc sát trùng", "Thuốc giảm đau",
 "Cá Mòi Ba Cô Gái", "Nước suối La Vie",
]
PEOPLE = ["Iris", "Syvial", "An Nhiên", "Lucia", "người đàn ông"]

TEMPLATES = {
 "PICKUP_ITEM": ["Kai nhặt {i}", "nhặt {i} lên", "lượm {i} dưới sàn", "cầm {i} lên", "hãy lấy {i} lên"],
 "DROP_ITEM": ["Kai vứt {i} xuống", "thả {i} ra", "bỏ {i} xuống đất", "để {i} lại đây", "ném {i} đi"],
 "USE_ITEM": ["Kai dùng {i}", "sử dụng {i}", "hãy dùng {i} ngay", "kích hoạt {i}", "Kai thử dùng {i}"],
 "TRANSFER_ITEM": ["Kai đưa {p} một {i}", "trao {i} cho {p}", "chuyển hai {i} sang {p}", "đưa {p} {i}", "hãy giao {i} cho {p}"],
 "EQUIP_ITEM": ["trang bị {i}", "Kai đeo {i}", "Kai mặc {i}", "cầm {i} làm vũ khí", "gắn {i} vào trang bị"],
 "UNEQUIP_ITEM": ["tháo {i} ra", "cởi {i}", "bỏ trang bị {i}", "gỡ {i} khỏi người", "cất vũ khí {i} đang cầm"],
 "OMNIVAULT_STORE": ["cất {i} vào nhẫn", "bỏ {i} trong Omnivault", "lưu {i} vào Nhẫn Vạn Tàng", "cho {i} vào kho của Kai", "nhẫn hãy giữ {i}"],
 "OMNIVAULT_WITHDRAW": ["lấy {i} ra khỏi nhẫn", "rút {i} từ Omnivault", "triệu hồi {i} từ kho", "đưa {i} ra khỏi Nhẫn Vạn Tàng", "Kai gọi {i} từ nhẫn ra"],
 "OMNIVAULT_SCAN": ["quét {i}", "scan {i}", "nhẫn quét {i}", "tạo mẫu quét từ {i}", "dùng Omnivault scan {i}"],
 "OMNIVAULT_COPY": ["sao chép {i}", "copy {i}", "nhân bản {i}", "tạo thêm hai bản {i}", "Omnivault sao chép {i}"],
 "OMNIVAULT_RESTORE": ["hoàn nguyên {i}", "restore {i}", "khôi phục {i} về trạng thái tốt nhất", "nhẫn sửa lại {i}", "Hoàn nguyên vật phẩm {i}"],
}

FIXED_TRAIN = {
 "INVENTORY_QUERY": ["xem inventory", "kiểm tra túi đồ", "kho đồ hiện có gì", "Kai đang mang những gì", "mở danh sách vật phẩm", "cho tôi xem đồ của Kai", "inventory của Kai", "hiện vật phẩm đang sở hữu", "xem hành trang", "kiểm kê đồ đang có"],
 "PARTY_JOIN_REQUEST": ["Iris vào party", "cho Syvial gia nhập đội", "mời Iris tham gia nhóm", "Iris hãy đi cùng Kai", "thêm Iris vào party", "Syvial nhập đội", "mời cô ấy làm đồng đội", "cho Iris theo nhóm", "Iris tham gia party", "đề nghị Syvial gia nhập"],
 "PARTY_REMOVE": ["Iris rời party", "loại Syvial khỏi đội", "đuổi người đó khỏi nhóm", "xóa Iris khỏi party", "cho Syvial rời đội", "tách Iris khỏi nhóm", "Iris không còn trong party", "loại cô ấy khỏi đội hình", "yêu cầu Iris rời nhóm", "bỏ Syvial khỏi party"],
 "PARTY_QUERY": ["party hiện có ai", "xem đội hình", "kiểm tra nhóm hiện tại", "ai đang đi cùng Kai", "danh sách thành viên party", "đội còn những ai", "mở party", "cho xem các thành viên", "nhóm của Kai gồm ai", "kiểm tra đồng đội"],
 "CHARACTER_QUERY": ["xem thông tin Iris", "Kai hiện ra sao", "kiểm tra nhân vật Syvial", "mở hồ sơ Kai", "cho xem chi tiết Iris", "thông tin nhân vật hiện tại", "xem trạng thái nhân vật Kai", "Iris là ai", "kiểm tra hồ sơ Syvial", "mở character detail"],
 "STATUS_QUERY": ["xem status của Kai", "Iris đang bị trạng thái gì", "kiểm tra tình trạng hiện tại", "Kai có hiệu ứng nào", "xem thương tích của Iris", "mở bảng status", "tình trạng Kai thế nào", "kiểm tra hiệu ứng trạng thái", "Kai có bị thương không", "hiện status effects"],
}

PARAPHRASE_TRAIN = {
 "PICKUP_ITEM": ["gom chiếc hộp lên", "Kai cúi nhặt món đồ", "cầm lấy vật trước mặt", "nhấc bình nước khỏi nền", "thu món đồ dưới đất", "gom radio khỏi mặt đất", "nhặt radio nằm trên sàn"],
 "DROP_ITEM": ["đặt chai xuống nền", "buông món đồ đang cầm", "để vật đó lại", "Kai đặt khẩu súng xuống", "thả tay khỏi chiếc hộp", "Kai để chai rỗng xuống", "đặt vật lại tại chỗ", "buông đồ khỏi tay", "thả vật đang cầm xuống"],
 "USE_ITEM": ["bật chiếc radio lên", "Kai bật đèn pin", "mở radio để sử dụng", "uống một ngụm Almond Water", "áp dụng bộ cứu thương lên vết thương", "mở và dùng vật phẩm", "kích hoạt công cụ đang cầm"],
 "TRANSFER_ITEM": ["chia Iris một chai", "giao radio sang tay Syvial", "chuyển vật đó cho cô ấy", "đặt viên pin vào tay Iris", "Kai giao món đồ cho đồng đội"],
 "EQUIP_ITEM": ["rút súng cầm sẵn", "mang mặt nạ bảo hộ", "lắp dao vào ô vũ khí", "đưa radio vào slot trang bị", "Kai cầm vũ khí trong tay"],
 "UNEQUIP_ITEM": ["hạ súng và cất khỏi tay", "gỡ mặt nạ đang đeo", "tháo vật khỏi ô vũ khí", "Kai bỏ vũ khí đang cầm", "cất món đang trang bị"],
 "OMNIVAULT_STORE": ["Nhẫn Vạn Tàng thu radio vào kho", "cho món đó biến vào Omnivault", "đưa vật vào không gian nhẫn", "Omnivault cất giữ chiếc hộp", "Kai chuyển vật sở hữu vào Nhẫn Vạn Tàng"],
 "OMNIVAULT_WITHDRAW": ["gọi radio từ Nhẫn Vạn Tàng ra", "lấy lại món vừa cất trong Omnivault", "cho vật xuất hiện từ kho nhẫn", "nhẫn triệu hồi món đã lưu", "Kai đưa vật lưu trữ trở lại tay"],
 "OMNIVAULT_SCAN": ["ghi mẫu radio vào scan slot", "Omnivault đọc mẫu vật thể", "chiếu luồng quét lên kim loại", "nhẫn ghi nhận cấu trúc vật", "tạo template từ vật gốc"],
 "OMNIVAULT_COPY": ["tạo bản thứ hai từ mẫu nhẫn", "sinh thêm bản sao radio", "Omnivault dựng lại hai bản", "nhân thêm vật từ scan slot", "tạo một copy từ template"],
 "OMNIVAULT_RESTORE": ["đưa radio về trạng thái nguyên vẹn", "nhẫn phục hồi chai móp", "trả vật hỏng về tình trạng tốt nhất", "sửa vật thể bằng Hoàn nguyên", "khôi phục trạng thái tốt nhất từng có"],
 "INVENTORY_QUERY": ["Kai còn sở hữu món nào", "liệt kê hành trang hiện giờ", "trong túi có bao nhiêu thứ", "cho biết đồ đang mang", "mở danh sách tài sản của Kai"],
 "PARTY_JOIN_REQUEST": ["đề nghị Iris đồng hành", "kết nạp Syvial làm thành viên", "mời cô ấy đi chung đội", "cho Iris trở thành đồng đội", "yêu cầu Syvial theo cùng"],
 "PARTY_REMOVE": ["yêu cầu Iris tách khỏi đội hình", "cho Syvial ngừng đồng hành", "gạch cô ấy khỏi danh sách nhóm", "kết thúc việc Iris đi cùng", "đưa Syvial ra khỏi đội hình"],
 "PARTY_QUERY": ["những người đang đồng hành", "đội hình bốn người ra sao", "liệt kê đồng đội bên cạnh Kai", "ai là thành viên hiện tại", "cho biết nhóm đang gồm những ai"],
 "CHARACTER_QUERY": ["mở trang chi tiết Iris", "cho biết hồ sơ Kai", "xem dữ liệu nhân vật đang chọn", "hiện character profile", "đọc thông tin riêng của Syvial"],
 "STATUS_QUERY": ["liệt kê hiệu ứng tác động lên Iris", "tình trạng cơ thể Kai", "mở phần thương tích và trạng thái", "xem các status effect hiện hành", "Kai đang chịu ảnh hưởng gì"],
}

CONTEXT_TRAIN = {
 "USE_ITEM": [
  "uống nước hạnh nhân", "uống nước", "ăn cá mòi", "dùng cái vừa nhặt", "dùng nó",
  "cho Lucia dùng băng gạc", "dùng băng gạc cho Lucia", "cho Iris uống nước hạnh nhân",
  "băng bó cho Syvial bằng băng gạc", "dùng thuốc giảm đau cho An Nhiên",
  "bật đèn pin", "châm bật lửa", "nạp pin cho đèn pin", "đổ nhiên liệu vào bật lửa",
  "Kai uống chai nước vừa nhặt", "uống chai vừa nhặt", "uống món nước vừa nhặt",
  "Kai băng bó cho Lucia", "băng bó Lucia bằng băng gạc", "băng bó vết thương cho Iris",
  "cho Lucia uống nước", "cho Syvial dùng thuốc", "dùng thuốc sát trùng cho Lucia",
  "bôi thuốc sát trùng cho Iris", "ăn thực phẩm đóng hộp", "ăn cá mòi ba cô gái",
  "Kai dùng vật vừa nhặt", "dùng món vừa nhặt ngay", "sử dụng nó ngay",
 ],
 "TRANSFER_ITEM": [
  "đưa cho Lucia", "đưa cho Iris", "trao cho Syvial", "chuyển cho An Nhiên", "cho cô ấy",
  "đưa cái vừa nhặt cho Lucia", "trao món vừa nhặt cho Iris", "giao nó cho Syvial",
  "chuyển băng gạc sang Lucia", "đưa nước hạnh nhân cho Iris", "cho Lucia thuốc giảm đau",
  "đặt viên pin vào tay An Nhiên", "nhường món đồ này cho Lucia", "chuyển vật đang cầm cho đồng đội",
  "giao cá mòi cho cô ấy", "Kai chuyển một chai cho Iris", "Kai chuyển một chai cho Syvial",
  "Kai chuyển một chai cho Lucia", "Kai chuyển món đồ cho cô ấy", "chuyển chai nước cho cô ấy",
  "nhường chai nước cho Syvial", "nhường viên pin cho Iris", "nhường món vừa nhặt cho An Nhiên",
  "trao món đang cầm cho Lucia", "giao vật vừa nhặt sang Iris", "đưa nó sang Lucia",
  "cho Lucia món vừa nhặt",
 ],
}

NO_ACTION_TRAIN = [
 "Kai nhìn Iris lấy chai nước", "Kai nhớ lần trước mình bỏ súng vào nhẫn", "Kai không nhặt chai nước",
 "Iris nói nhặt chai nước lên", "đừng bỏ súng vào nhẫn", "Kai chưa dùng bộ sơ cứu", "nếu lấy nó ra thì sao",
 "Kai suýt nhặt chiếc đèn pin", "Kai không định đưa nước cho Iris", "khẩu súng đã được cất từ trước",
 "Kai thấy Syvial trang bị mặt nạ", "Iris hỏi có nên vào party không", "Kai nhớ đã hoàn nguyên chai rỗng",
 "đừng quét vật đó", "Kai nghe ai đó bảo thả súng xuống", "giả sử copy thêm một chai thì sao",
 "Kai quan sát chiếc chìa khóa nằm dưới sàn", "Iris từng trao đèn cho Kai", "không được dùng chai nước",
 "Kai nói rằng anh sẽ lấy nó sau", "liệu có nên cất nó vào nhẫn", "Kai nhìn khẩu súng đã được tháo ra",
 "Syvial kể rằng cô đã nhặt cuộn băng", "Kai không muốn Iris rời party", "đừng mời người lạ vào nhóm",
 "Kai nhớ lời dặn hãy dùng bộ sơ cứu", "Iris nhìn Kai sao chép viên pin", "nếu hoàn nguyên thất bại thì sao",
 "Kai tưởng tượng mình triệu hồi khẩu súng", "chưa cần mở inventory", "Kai không hỏi status của Iris",
 "Kai nhớ mình từng rút vật từ nhẫn", "Kai hồi tưởng lần đã lấy radio khỏi Omnivault", "trước đây Kai từng gọi súng từ kho ra",
 "nếu Kai trao nước cho Iris thì sao", "giả sử Kai giao viên pin cho cô ấy", "liệu đưa radio sang Syvial có ổn không",
 "Kai chưa hề sử dụng radio", "Kai vẫn chưa uống chai nước", "chưa dùng món đồ đang cầm",
 "Kai chỉ nghĩ đến việc nhặt chiếc hộp", "Iris kể chuyện cô từng rời party", "Syvial hỏi liệu cô có thể gia nhập đội",
 "đừng triệu hồi món đó từ nhẫn", "không được sao chép vật này", "Kai không muốn hoàn nguyên chai nước",
 "Iris quan sát Kai chuyển đồ", "Kai thấy radio được cất vào kho", "món đồ đã nằm trong inventory từ trước",
 "khẩu súng hiện không được trang bị", "mặt nạ chưa được đeo", "radio không nằm trong slot trang bị",
 "nếu Kai đưa món đồ cho cô ấy thì sẽ thế nào", "nếu Kai chuyển nước sang Iris thì sao", "giả sử Kai trao chai nước cho cô ấy",
 "đừng đưa cho Lucia", "nếu đưa cho Lucia thì sao", "Kai chưa đưa cho Lucia", "Iris bảo Kai đưa cho Lucia",
 "đừng dùng băng gạc cho Lucia", "nếu dùng thuốc giảm đau cho Iris thì sao", "Kai chưa dùng cái vừa nhặt",
 "Lucia hỏi có nên uống nước hạnh nhân không", "đừng đưa món đó cho Lucia", "đừng chuyển chai nước cho Iris",
 "đừng trao băng gạc cho Syvial", "không được đưa cho Lucia", "nếu chuyển chai nước cho Lucia thì sao",
 "nếu trao băng gạc cho Iris thì sao", "Kai chưa chuyển món đó cho Lucia", "Kai vẫn chưa trao chai nước cho Syvial",
 "đừng dùng thuốc giảm đau cho Iris", "không được dùng băng gạc cho Lucia", "đừng cho Syvial uống nước",
 "nếu cho Lucia dùng băng gạc thì sao", "Kai chưa băng bó cho Lucia", "Kai chưa uống chai vừa nhặt",
]

UNKNOWN_TRAIN = [
 "Kai quan sát hành lang im lặng", "Tôi muốn suy nghĩ thêm", "Chuyện gì đang xảy ra", "Kai bước về phía ánh đèn",
 "lắng nghe tiếng động", "mùi ẩm mốc nặng hơn", "Kai gọi tên Iris", "đợi một lát", "tiếp tục tiến về phía trước",
 "kiểm tra căn phòng", "Kai áp tai vào bức tường", "có tiếng chân ở xa", "tìm đường thoát", "Kai dừng lại suy nghĩ",
 "quan sát dấu vết trên sàn", "nói chuyện với Iris", "Kai hỏi người đàn ông là ai", "giữ im lặng", "ẩn sau góc tường",
 "đi theo hành lang bên trái", "Kai kiểm tra cánh cửa", "thử nhớ bản đồ", "chờ xem chuyện gì xảy ra",
 "hỏi Iris về quá khứ", "Kai hỏi Syvial cô nhớ được gì", "trò chuyện với Iris về ký ức", "đề nghị Iris giải thích chuyện vừa rồi",
 "Kai hỏi người đồng hành về Backrooms", "lắng nghe câu trả lời của Syvial", "thảo luận kế hoạch với Iris",
 "Kai chuẩn bị đối phó thực thể", "Kai chuẩn bị tinh thần trước nguy hiểm", "Kai sẵn sàng nếu có chuyện",
 "Kai quan sát và chuẩn bị phản ứng",
]

TEST = {
 "PICKUP_ITEM": ["Kai cúi xuống nhặt lấy bình nước", "gom chiếc radio lên khỏi sàn", "cầm lấy món đồ trước mặt"],
 "DROP_ITEM": ["đặt chiếc radio lại xuống nền", "buông vật đang cầm", "Kai để chai rỗng ở lại"],
 "USE_ITEM": ["Kai bật chiếc radio", "uống một ngụm nước", "áp dụng bộ cứu thương", "cho Syvial dùng thuốc giảm đau", "uống chai nước vừa nhặt", "băng bó cho Lucia bằng cuộn băng"],
 "TRANSFER_ITEM": ["chia cho Iris một viên pin", "giao chiếc radio sang tay Syvial", "Kai chuyển một chai cho cô ấy", "đưa cho Lucia", "trao món vừa nhặt sang An Nhiên", "nhường chai nước cho Iris"],
 "EQUIP_ITEM": ["Kai rút súng cầm sẵn trên tay", "mang mặt nạ bảo hộ lên", "lắp món đồ vào ô vũ khí"],
 "UNEQUIP_ITEM": ["Kai hạ súng và cất khỏi tay", "gỡ mặt nạ đang đeo", "tháo món đồ khỏi ô vũ khí"],
 "OMNIVAULT_STORE": ["Nhẫn Vạn Tàng thu chiếc radio vào kho", "Kai cho món đó biến vào Omnivault", "đưa vật đang cầm vào không gian nhẫn"],
 "OMNIVAULT_WITHDRAW": ["gọi chiếc radio từ Nhẫn Vạn Tàng ra", "Kai lấy lại món vừa cất trong Omnivault", "cho vật đó xuất hiện từ kho nhẫn"],
 "OMNIVAULT_SCAN": ["ghi mẫu chiếc radio vào một scan slot", "Omnivault đọc mẫu vật thể này", "chiếu luồng quét lên tấm kim loại"],
 "OMNIVAULT_COPY": ["tạo một bản thứ hai từ mẫu trong nhẫn", "sinh thêm bản sao chiếc radio", "Kai cho Omnivault dựng lại hai bản"],
 "OMNIVAULT_RESTORE": ["đưa chiếc radio về trạng thái nguyên vẹn nhất", "nhẫn phục hồi chai móp", "trả vật thể hỏng về tình trạng tốt nhất trước đây"],
 "INVENTORY_QUERY": ["Kai còn sở hữu món đồ nào", "liệt kê hành trang hiện giờ", "trong túi đang có bao nhiêu thứ"],
 "PARTY_JOIN_REQUEST": ["đề nghị Iris đồng hành cùng Kai", "kết nạp Syvial làm thành viên", "mời cô ấy đi chung đội"],
 "PARTY_REMOVE": ["yêu cầu Iris tách khỏi đội hình", "cho Syvial ngừng đồng hành", "gạch cô ấy khỏi danh sách nhóm"],
 "PARTY_QUERY": ["những người nào đang đồng hành", "đội hình bốn người hiện ra sao", "liệt kê đồng đội bên cạnh Kai"],
 "CHARACTER_QUERY": ["mở trang chi tiết của Iris", "cho biết hồ sơ của Kai", "xem dữ liệu nhân vật đang chọn"],
 "STATUS_QUERY": ["liệt kê hiệu ứng đang tác động lên Iris", "tình trạng cơ thể Kai hiện giờ", "mở phần thương tích và trạng thái"],
 "NO_ACTION": ["Kai nhớ mình từng lấy radio khỏi nhẫn", "Iris nói: đừng nhặt vật đó", "nếu Kai đưa nước cho cô ấy thì chuyện gì xảy ra", "Kai nhìn Syvial cất súng", "Kai chưa hề dùng món đồ", "khẩu súng không được trang bị", "nếu đưa cái vừa nhặt cho Lucia thì sao", "đừng cho Iris dùng băng gạc", "Kai vẫn chưa trao món đó cho Syvial"],
 "UNKNOWN": ["Kai nghiên cứu âm thanh bất thường", "thử mở cánh cửa cuối hành lang", "hỏi Iris về ký ức của cô ấy", "tiếp tục câu chuyện", "Kai chuẩn bị đối phó nguy hiểm"],
}

def expand_templates():
    rows=[]
    for intent, templates in TEMPLATES.items():
        for template in templates:
            people = PEOPLE if "{p}" in template else [""]
            for item, person in itertools.product(ITEMS, people):
                text=template.format(i=item,p=person)
                rows.append((text,intent,"train",f"template-{templates.index(template)}"))
    for intent, texts in FIXED_TRAIN.items():
        rows.extend((text,intent,"train",f"manual-{idx}") for idx,text in enumerate(texts))
    for intent, texts in PARAPHRASE_TRAIN.items():
        rows.extend((text,intent,"train",f"paraphrase-{idx}") for idx,text in enumerate(texts))
    for intent, texts in CONTEXT_TRAIN.items():
        rows.extend((text,intent,"train",f"context-{idx}") for idx,text in enumerate(texts))
    rows.extend((text,"NO_ACTION","train",f"hard-negative-{idx}") for idx,text in enumerate(NO_ACTION_TRAIN))
    rows.extend((text,"UNKNOWN","train",f"unknown-{idx}") for idx,text in enumerate(UNKNOWN_TRAIN))
    for intent,texts in TEST.items(): rows.extend((text,intent,"test",f"heldout-{idx}") for idx,text in enumerate(texts))
    return rows

def main():
    output=pathlib.Path(__file__).with_name("intent_dataset.csv")
    rows=expand_templates()
    with output.open("w",encoding="utf-8",newline="") as handle:
        writer=csv.writer(handle,lineterminator="\n"); writer.writerow(["text","intent","split","family"]); writer.writerows(rows)
    print({"rows":len(rows),"train":sum(r[2]=="train" for r in rows),"test":sum(r[2]=="test" for r in rows)})

if __name__ == "__main__": main()
