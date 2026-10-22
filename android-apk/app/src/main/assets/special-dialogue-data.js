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
    luc_tram:{ambient:['Ta vẫn đang quan sát tình hình.'],story:[],past:[]},
    trac_lam:{ambient:['Có việc gì cần bàn sao?'],story:[],past:[]}
  };
  global.BackroomSpecialEntityDialogue={
    diep_minh:{name:'Diệp Minh',overlay:'entity/diep_minh.webp',
      encounter:['Ngươi định đối đầu với ta sao?'],story:[]},
    luc_tram_hac_hoa:{name:'Lục Trầm Hắc Hóa',overlay:'entity/luc_tram_hac_hoa.webp',
      encounter:['Đừng mong ta nhường đường.'],story:[]}
  };
})(window);
