/* Locally authored dialogue. Never send this catalog to the GM. */
(function(global){
  'use strict';
  global.BackroomSpecialDialogueData={
    hua_tieu_lan:{
      intro:[
        {speaker:'hua_tieu_lan',text:'Anh Tý... Anh Tý!'},
        {speaker:'cao_minh',text:'Cô là ai? Xin giữ khoảng cách.'},
        {speaker:'hua_tieu_lan',text:'Tôi xin lỗi, Cao công tử. Công tử rất giống một người tôi từng yêu.'},
        {speaker:'hua_tieu_lan',text:'Nếu công tử không phiền, xin cho tôi được đồng hành. Tôi không mong công tử phải nhớ điều gì.'}
      ],
      ambient:[
        'Cao công tử, xin cẩn trọng. Nơi này không giống bất cứ chốn nào tôi từng biết.',
        'Nếu công tử cần người trông chừng phía sau, tôi sẽ giúp.',
        'Tôi sẽ quan sát hành lang phía trước.'
      ],
      story:[
        [{speaker:'hua_tieu_lan',text:'Tôi từng nói những lời rất cay nghiệt với một người thật lòng thương tôi. Đến giờ tôi vẫn không quên.'}],
        [{speaker:'hua_tieu_lan',text:'Ngày ấy, tôi thấy chàng nhận tiền của gia nhân Hứa gia và tưởng chàng tiếp cận tôi vì tiền bạc. Tôi đã trách nhầm chàng.'}],
        [{speaker:'hua_tieu_lan',text:'Mẹ tôi sau đó nói khoản tiền ấy là để cứu giúp những người gặp nạn vì thiên tai. Tôi vội đi tìm chàng để xin lỗi.'}],
        [{speaker:'hua_tieu_lan',text:'Nhưng chàng đã gặp nạn trên đường về nhà. Tôi không kịp nói một lời xin lỗi. Cao công tử... xin thứ lỗi vì đã khiến công tử nghe chuyện buồn.'}]
      ],
      past:[
        'Tôi là con gái thứ tư của Hứa gia ở Sài Gòn – Chợ Lớn. Người nhà thường gọi tôi là Tứ tiểu thư.',
        'Ngày sinh nhật mười tám tuổi, tôi lạc vào nơi này. Không biết người nhà có còn chờ tôi không.'
      ]
    },
    luc_tram:{
      intro:[{speaker:'luc_tram',text:'Ta là Lục Trầm, đệ tử chân truyền Thiên Kiếm Môn.'},
        {speaker:'luc_tram',text:'Nếu phải cùng đi, trước hết đừng để bất cẩn làm hại cả nhóm.'}],
      ambient:['Ta vẫn đang quan sát tình hình.','Tịch Quang vẫn ở bên ta.'],
      past:['Ta vốn tu hành tại Thiên Kiếm Môn, đi theo con đường kiếm đạo.'],
      story:[[{speaker:'luc_tram',text:'Tịch Quang là bản mệnh kiếm của ta. Ta từng dành nhiều năm để rèn luyện kiếm đạo.'}]]
    },
    trac_lam:{
      intro:[{speaker:'trac_lam',text:'Tôi là Trác Lâm, đội trưởng SRU-03.'},
        {speaker:'trac_lam',text:'Nếu hợp tác, điều đầu tiên cần làm là giữ đội hình và xác nhận những gì chúng ta thật sự quan sát được.'}],
      ambient:['Có việc gì cần bàn sao?','Cần xác định đường rút trước khi tiến vào khu vực chưa kiểm tra.'],
      past:['Tôi đến từ Đà Nẵng, năm 2099. Trước đây tôi công tác trong lực lượng phản ứng dị thường SRU.'],
      story:[
        [{speaker:'trac_lam',text:'Năm 2094, ở Cảng Đông Hải, chúng tôi từng xử lý một khu kho vận có hiện tượng lặp không gian. Tôi đã cùng đội đưa được mười một dân thường ra ngoài.'}],
        [{speaker:'trac_lam',text:'Trước khi lạc vào đây, tôi chỉ huy tổ SRU-03. Hiện tôi vẫn chưa liên lạc được với đồng đội của mình.'}]
      ]
    }
  };
  global.BackroomSpecialEntityDialogue={
    diep_minh:{name:'Diệp Minh',overlay:'entity/diep_minh.webp',
      encounter:['Ngươi định đối đầu với ta sao?','Nếu bước tiếp, ngươi phải tự chịu lấy hậu quả.'],story:[]},
    luc_tram_hac_hoa:{name:'Lục Trầm Hắc Hóa',overlay:'entity/luc_tram_hac_hoa.webp',
      encounter:[
        {speaker:'cao_minh',text:'Lục Trầm… Nàng còn sống?'},
        {speaker:'luc_tram_hac_hoa',text:'Ngươi là Cao Minh, Vạn Giới Ma Tôn?'}
      ],
      // R07, stage 2 Core-confirmed confrontation: accusations are her belief.
      // Do not reveal the revival, Diệp Minh's deception or restore her memories.
      story:[
        {speaker:'luc_tram_hac_hoa',text:'Diệp Minh nói ngươi đã giết sạch tu sĩ Chính Đạo ở Lôi Thiên Vực. Ta muốn nghe ngươi trả lời.'},
        {speaker:'cao_minh',text:'Nàng không tin ta cũng được. Nhưng đừng tin chỉ vì có ai đó kể lại.'},
        {speaker:'luc_tram_hac_hoa',text:'Ta chưa từng tin hết lời Diệp Minh. Nhưng ma khí trong người ta giống hệt ngươi. Ngươi đã làm gì ta?'},
        {speaker:'cao_minh',text:'Ta cứu nàng.'},
        {speaker:'luc_tram_hac_hoa',text:'Vậy tại sao ta chẳng nhớ nổi một lần nào ta từng tin ngươi?'}
      ]}
  };
})(window);
