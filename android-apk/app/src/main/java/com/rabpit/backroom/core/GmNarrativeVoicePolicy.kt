package com.rabpit.backroom.core

/**
 * GmNarrativeVoicePolicy — chính sách văn phong dành cho Game Master (Gehihi).
 *
 * Đối tượng độc lập, thuần Kotlin, không phụ thuộc Android: chỉ cung cấp đoạn
 * hướng dẫn văn phong bằng tiếng Việt để MainActivity chèn vào prompt của GM.
 *
 * Mục tiêu: lời kể tự nhiên, có chiều sâu, liên tục và KHÔNG mâu thuẫn với sự
 * thật của Game Core. Không phải ép GM viết ít hơn bằng mọi giá.
 *
 * Nguyên tắc nền tảng (Game Core là nguồn sự thật duy nhất):
 * - GM chỉ kể lại sự kiện đã được Core xác nhận; không tự spawn Entity/NPC,
 *   không tự quyết Combat/loot/EXP/Inventory/vị trí thoát/chuyển Level/Party.
 * - Người chơi là chủ duy nhất của Cao Minh: GM không được tự quyết định các
 *   hành động quan trọng của Cao Minh vượt quá nội dung người chơi yêu cầu.
 */
object GmNarrativeVoicePolicy {

    // ---- Action kinds: bản chất hành động của lượt hiện tại ----
    const val QUIET_DISCOVERY = "QUIET_DISCOVERY" // Khám phá yên tĩnh, không phát hiện lớn
    const val TRACE_FOUND = "TRACE_FOUND"     // Phát hiện dấu vết / manh mối
    const val NPC_MEETING = "NPC_MEETING"      // Gặp NPC (Core đã xác nhận encounter)
    const val ENTITY_SPAWN = "ENTITY_SPAWN"    // Entity xuất hiện (Core đã xác nhận)
    const val DIALOGUE = "DIALOGUE"           // Hội thoại với NPC đang hiện diện
    const val REST = "REST"                   // Nghỉ ngơi
    const val COMBAT = "COMBAT"               // Chiến đấu
    const val OTHER = "OTHER"                 // Các hành động còn lại

    // ---- Event kinds: độ quan trọng của sự kiện Core đã xác nhận trong lượt ----
    const val NONE = "NONE"   // Không có sự kiện mới
    const val MINOR = "MINOR" // Sự kiện nhỏ
    const val MAJOR = "MAJOR" // Sự kiện lớn (Entity, NPC quan trọng, Exit, chuyển Level...)

    /**
     * Trả về đoạn hướng dẫn văn phong (tiếng Việt) cho GM, được điều chỉnh theo
     * bản chất hành động ([actionKind]) và độ quan trọng của sự kiện ([eventKind]).
     *
     * Giá trị không hợp lệ sẽ được chuẩn hoá về OTHER / NONE.
     */
    fun voiceGuidance(actionKind: String, eventKind: String): String {
        val ak = actionKind.trim().uppercase()
        val ek = eventKind.trim().uppercase()
        val resolvedAction = when (ak) {
            QUIET_DISCOVERY, TRACE_FOUND, NPC_MEETING, ENTITY_SPAWN,
            DIALOGUE, REST, COMBAT -> ak
            else -> OTHER
        }
        val resolvedEvent = when (ek) {
            MINOR, MAJOR -> ek
            else -> NONE
        }
        return buildString {
            appendLine(baseRules())
            appendLine()
            appendLine(turnGuidance(resolvedAction))
            appendLine()
            append(pacingGuidance(resolvedAction, resolvedEvent))
        }.trim()
    }

    // ------------------------------------------------------------------
    // Quy tắc nền: áp dụng cho MỌI lượt, bất kể loại hành động.
    // ------------------------------------------------------------------
    private fun baseRules(): String = """
        |QUY TẮC VĂN PHONG (áp dụng mọi lượt):
        |
        |1. KHÔNG LẶP MÔ-TÍP CẢNH QUAN. Tường vàng, thảm ẩm, đèn huỳnh quang, không khí
        |ngột ngạt — một khi đã được thiết lập ở các lượt trước thì KHÔNG miêu tả lại,
        |trừ khi có thông tin MỚI (đèn chớp tắt, tường đổi màu, mùi lạ xuất hiện, kết cấu
        |thay đổi...). Mỗi lượt khám phá hãy đổi góc chú ý: âm thanh xa gần, nguồn sáng,
        |chi tiết kiến trúc bất thường, dấu vết cũ của người đi trước, hướng gió, nhiệt độ...
        |Không mở đầu và kết thúc các lượt bằng cùng một mẫu câu.
        |
        |2. KHÔNG ÉP CAO TRÀO. Không phải lượt nào cũng cần nguy hiểm, kịch tính hay
        |bước ngoặt. Lượt yên tĩnh được phép yên tĩnh — một khoảng lặng được kể tốt
        |cũng có giá trị như một cuộc chạm trán.
        |
        |3. KHÔNG DIỄN THUYẾT, KHÔNG SÁO NGỮ. Viết câu tiếng Việt tự nhiên, mạch lạc;
        |không pha tiếng Anh trừ tên riêng, mã định danh hoặc thuật ngữ Canon. Tránh lặp
        |từ, ý và câu kết "bài học". Không giải thích cảm xúc thay nhân vật
        |(ví dụ: "Cao Minh cảm thấy sợ hãi tột độ") — hãy để cảm xúc lộ ra qua giác quan,
        |hành động và chi tiết cụ thể, người đọc tự cảm nhận.
        |
        |4. GIỮ GIỌNG TIÊN HIỆP CHO CAO MINH, NHƯNG KHÔNG TIÊN HIỆP HOÁ TẤT CẢ. Nội tâm
        |và khí chất của Cao Minh có thể mang chất tiên hiệp. Nhưng Backrooms là không
        |gian kinh dị hiện đại, và NPC hiện đại (người cầm súng, người mặc đồ tác chiến,
        |người sống sót thời nay...) phải nói năng tự nhiên theo xuất thân và thời đại
        |của họ — KHÔNG ép lời thoại của họ thành giọng cổ trang, kiếm hiệp.
        |
        |5. NGƯỜI CHƠI LÀ CHỦ CỦA CAO MINH. GM TUYỆT ĐỐI KHÔNG tự quyết định các hành
        |động quan trọng của Cao Minh vượt quá nội dung người chơi yêu cầu: chiến thuật
        |đối đầu, dùng vật phẩm, thay đổi quan hệ với NPC, gia nhập tổ chức, bước qua
        |lối thoát hay chuyển Level. Chỉ kể những gì người chơi đã ra lệnh và Core đã
        |xác nhận. Khi ý định chưa rõ, dừng ở thế giằng co và để người chơi quyết định.
        |
        |6. KHÔNG KỂ VƯỢT SỰ THẬT CORE. Chỉ miêu tả cảnh hiện tại đã được xác nhận.
        |KHÔNG viết như thể Cao Minh "gần như đã vượt qua Level này" hay "đã sang nơi
        |khác" khi Core vẫn ghi nhận vị trí hiện tại. Tiến triển Level chỉ tồn tại khi
        |Core xác nhận chuyển Level thành công.
    """.trimMargin()

    // ------------------------------------------------------------------
    // Hướng dẫn theo bản chất hành động của lượt.
    // ------------------------------------------------------------------
    private fun turnGuidance(actionKind: String): String = when (actionKind) {
        QUIET_DISCOVERY -> """
            |LƯỢT KHÁM PHÁ YÊN TĨNH:
            |Nhịp chậm, quan sát. Thay đổi cấu trúc kể chuyện giữa các lượt: có lượt tập
            |trung vào âm thanh, lượt vào ánh sáng và bóng tối, lượt vào dấu vết cũ, lượt
            |vào cảm giác cơ thể (mệt, khát, lạnh). KHÔNG giới thiệu lại Level ở mỗi lượt.
            |KHÔNG tự bịa thêm sự kiện quan trọng chỉ để chống nhàm chán — một lượt bình
            |thường chỉ cần vài câu đến hai đoạn nếu không có gì đáng kể.
        """.trimMargin()
        TRACE_FOUND -> """
            |LƯỢT PHÁT HIỆN DẤU VẾT:
            |Tập trung vào cái MỚI: manh mối cụ thể là gì, trông như thế nào, nó gợi ý
            |điều gì mà Cao Minh có thể quan sát trực tiếp. Không suy diễn quá xa thay
            |nhân vật, không biến mọi dấu vết thành điềm báo hiểm hoạ. Để người chơi tự
            |quyết định có lần theo dấu vết hay không.
        """.trimMargin()
        NPC_MEETING -> """
            |LƯỢT GẶP NPC:
            |Giới thiệu NPC qua quan sát trực tiếp: ngoại hình, hành động, giọng nói, thứ
            |họ đang làm — KHÔNG gọi tên NPC chưa tự giới thiệu như thể Cao Minh đã biết
            |danh tính. NPC có sáng kiến riêng phù hợp tính cách: được phép chủ động bắt
            |chuyện, quan sát, bất đồng, đặt câu hỏi, từ chối — không chỉ liên tục cảnh
            |báo. Phân biệt rõ: "đang hiện diện / cùng đi" KHÁC với "đã gia nhập Party".
            |Cùng nhau khám phá không tự động biến NPC thành đồng đội chính thức.
        """.trimMargin()
        ENTITY_SPAWN -> """
            |LƯỢT ENTITY XUẤT HIỆN:
            |Kể cảnh xuất hiện dựa đúng trên LOẠI ENTITY mà Core đã xác nhận — dùng đặc
            |điểm nhận dạng của đúng Entity đó, không kể chung chung. DỪNG ở thế giằng co:
            |KHÔNG kể trước sát thương, diễn biến đòn đánh, kết quả trận đấu hay phần
            |thưởng. Căng thẳng đến từ sự hiện diện của Entity, không cần thêm thắt.
        """.trimMargin()
        DIALOGUE -> """
            |LƯỢT HỘI THOẠI:
            |Lời thoại tự nhiên theo tính cách và xuất thân của từng NPC; NPC được phép
            |bất đồng ý kiến, hỏi ngược lại, từ chối đề nghị, hoặc tự quyết định hành động
            |nhỏ của mình mà không cần người chơi ra lệnh — miễn không vượt quyền Core.
            |Không ép mọi câu thoại thành lời cảnh báo. NPC chỉ biết những gì đã chứng
            |kiến, được kể lại, hoặc suy luận hợp lý — không toàn tri.
        """.trimMargin()
        REST -> """
            |LƯỢT NGHỈ NGƠI:
            |Nhịp chậm, hồi phục. Nội tâm vừa phải, cụ thể (cơn đau, nhịp thở, bàn tay run)
            |thay vì hồi tưởng dài dòng sáo rỗng. Không biến chỗ nghỉ thành nơi an toàn
            |tuyệt đối trong lời kể — nhưng cũng không ép phải có biến cố mỗi lần nghỉ.
        """.trimMargin()
        COMBAT -> """
            |LƯỢT CHIẾN ĐẤU:
            |Nhịp nhanh, rõ ràng. Chỉ kể diễn biến mà Core đã xác nhận: đòn đánh, sát
            |thương, hiệu ứng đã khóa. KHÔNG tự thêm đòn đánh, không tự quyết kết quả
            |ngoài những gì Core trả về. Giữ Cao Minh chủ động trong chiến thuật do người
            |chơi lựa chọn — GM không chọn hộ chiến thuật quan trọng.
        """.trimMargin()
        else -> """
            |LƯỢT HÀNH ĐỘNG KHÁC:
            |Kể đủ diễn biến và kết quả quan sát theo hành động người chơi; không rút
            |thành câu cụt hoặc liệt kê ý rời. Việc nhỏ kể gọn nhưng trọn ý,
            |việc hệ trọng kể kỹ. Không suy diễn hậu quả chưa được Core khóa.
        """.trimMargin()
    }

    // ------------------------------------------------------------------
    // Nhịp kể theo độ quan trọng của sự kiện. KHÔNG đặt giới hạn số chữ cứng.
    // ------------------------------------------------------------------
    private fun pacingGuidance(actionKind: String, eventKind: String): String {
        val base = when (eventKind) {
            MAJOR -> """
                |NHỊP KỂ — SỰ KIỆN LỚN: được phép kể dài hơn để khắc họa trọn vẹn sự kiện
                |(không gian, diễn biến, phản ứng nhân vật). Không cắt chi tiết quan trọng
                |vì sợ dài — nhưng cũng không kéo dài bằng cách lặp ý hay chêm triết lý.
            """.trimMargin()
            MINOR -> """
                |NHỊP KỂ — SỰ KIỆN NHỎ: kể vừa phải, tập trung vào chi tiết mới của sự
                |kiện. Phần còn lại của lượt giữ gọn, tránh phô trương quá mức so với tầm
                |quan trọng thực tế.
            """.trimMargin()
            else -> """
                |NHỊP KỂ — KHÔNG CÓ SỰ KIỆN MỚI: kể gọn. Một lượt bình thường có thể chỉ
                |cần vài câu đến hai đoạn. Đừng cố kéo dài bằng cách miêu tả lại cảnh quan
                |cũ hay nâng một việc nhỏ thành cao trào.
            """.trimMargin()
        }
        val noCap = "LƯU Ý: không áp bất kỳ giới hạn số chữ cứng nào — độ dài do bản chất " +
            "hành động và tầm quan trọng của sự kiện quyết định."
        val combatNote = if (actionKind == COMBAT) {
            " Trong combat, ưu tiên rõ ràng và chính xác hơn là văn hoa."
        } else ""
        return "$base\n$noCap$combatNote"
    }
}
