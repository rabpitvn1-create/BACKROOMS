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
  // R07: episode order is presentation progress, not proof that a character has
  // recovered memories or that any investigation/quest has been completed.
  // cross is eligible only when BOTH Entity identities were Core-confirmed in
  // prior encounters; no secret fact is narrated without in-world evidence.
  global.BackroomSpecialEntityDialogue={
    diep_minh:{
      name:'Diệp Minh',overlay:'entity/diep_minh.webp',
      episodes:[
        [
          {speaker:'diep_minh',text:'Ta từng nghĩ chuyện Cao gia đã chấm dứt từ lâu.'},
          {speaker:'cao_minh',text:'Hơn bốn trăm mạng người. Ngươi gọi đó là một chuyện đã chấm dứt?'},
          {speaker:'diep_minh',text:'Người chết không trở về đòi nợ. Ta chỉ không ngờ còn sót lại ngươi.'},
          {speaker:'diep_minh',text:'Sai lầm ấy, ta sẽ không lặp lại.'}
        ],
        [
          {speaker:'cao_minh',text:'Ngươi đã ở chốn này bao lâu?'},
          {speaker:'diep_minh',text:'Đủ lâu để biết linh khí nơi đây không thuộc về bất kỳ tông môn nào của thế giới cũ.'},
          {speaker:'cao_minh',text:'Vậy ngươi định chiếm lấy nó?'},
          {speaker:'diep_minh',text:'Một nguồn lực lớn đến thế mà không có chủ nhân. Ngươi không thấy lãng phí sao?'}
        ],
        [
          {speaker:'diep_minh',text:'Ngươi nhìn nửa thân này như thể ta đã trả giá quá đắt.'},
          {speaker:'cao_minh',text:'Sinh Mệnh Đạo của ngươi cũng để lại thứ hình hài ấy?'},
          {speaker:'diep_minh',text:'Kẻ không dám trả giá thì đừng bàn đến trường sinh.'},
          {speaker:'diep_minh',text:'Còn ngươi, Cao Minh, đã đánh mất bao nhiêu thứ để đến được đây?'}
        ],
        [
          {speaker:'diep_minh',text:'Một tông môn không nhất thiết phải mọc lên trên tiên sơn. Chỉ cần người đời muốn bước qua cửa của nó.'},
          {speaker:'cao_minh',text:'Ngươi còn tưởng những hành lang này sẽ phục tùng mình?'},
          {speaker:'diep_minh',text:'Đường đi có thể biến mất. Thứ nắm được tri thức về đường đi thì không.'},
          {speaker:'diep_minh',text:'Đó là điều ta phải học trong một quãng thời gian rất dài.'}
        ],
        [
          {speaker:'cao_minh',text:'Giết sạch Cao gia vẫn chưa đủ cho ngươi sao?'},
          {speaker:'diep_minh',text:'Ta không cần thêm một cuộc huyết tẩy. Ta cần không còn một kẻ họ Cao nào có thể phá hỏng việc ta đang làm.'},
          {speaker:'cao_minh',text:'Chừng nào ta còn sống, ngươi sẽ không được yên.'},
          {speaker:'diep_minh',text:'Chính vì vậy, ta mới luôn mang kiếm.'}
        ],
        [
          {"speaker":"diep_minh","text":"Ngươi có bao giờ nghĩ vì sao những kẻ lạc vào đây cứ tranh giành một lối đi, trong khi linh khí nằm ngay dưới chân?"},
          {"speaker":"cao_minh","text":"Không phải ai cũng xem chốn này là động phủ."},
          {"speaker":"diep_minh","text":"Chính vì thế mới có người tìm đường về, còn có người hiểu mình vừa gặp một cơ duyên."},
          {"speaker":"diep_minh","text":"Cùng một hành lang. Kẻ nhìn thấy nhà tù, kẻ nhìn thấy một triều đại chưa có vua."},        ],
        [
          {"speaker":"cao_minh","text":"Ngươi từng dùng kiếm ý đánh dấu đường đi?"},
          {"speaker":"diep_minh","text":"Đã thử. Có những nơi tìm lại được, có những nơi chỉ còn dấu kiếm mà không còn con đường."},
          {"speaker":"cao_minh","text":"Vậy mà ngươi vẫn muốn lập tông?"},
          {"speaker":"diep_minh","text":"Ngươi lầm. Sơn môn không nhất thiết là một cánh cổng. Có khi nó là tri thức mà người khác buộc phải đến xin."},
          {"speaker":"diep_minh","text":"Nắm được đường đi còn hơn chiếm một bức tường."},        ],
        [
          {"speaker":"diep_minh","text":"Ngươi nhớ ngày ta tới Cao gia, nhưng hẳn không biết ta đã nghĩ gì khi quay lưng."},
          {"speaker":"cao_minh","text":"Ta chỉ cần nhớ ngươi đã làm gì."},
          {"speaker":"diep_minh","text":"Ta tin rằng mọi mầm báo thù đều đã tắt. Tin ấy hóa ra quá sớm."},
          {"speaker":"cao_minh","text":"Ngươi tính cả tính mạng một đứa trẻ vào phép toán của mình?"},
          {"speaker":"diep_minh","text":"Ta tính mọi khả năng gây ra hậu họa. Ngươi là khả năng duy nhất ta không loại bỏ được."},
          {"speaker":"cao_minh","text":"Và ngươi vẫn gọi đó là tính toán."},        ],
        [
          {"speaker":"cao_minh","text":"Hoàng Thiên Mộc Kiếm của ngươi khác trước."},
          {"speaker":"diep_minh","text":"Đời người tu đạo đâu chỉ có một lần thay kiếm."},
          {"speaker":"cao_minh","text":"Thứ khí tức ấy không còn thuần túy như ở thế giới cũ."},
          {"speaker":"diep_minh","text":"Sống qua một lần trả giá, thân và kiếm tất phải mang dấu vết."},
          {"speaker":"diep_minh","text":"Ta chưa từng nói rằng tiến cảnh của mình không để lại thương tổn."},        ],
        [
          {"speaker":"diep_minh","text":"Một thế giới có linh khí mà không có tông quy. Ngươi biết điều ấy dẫn tới đâu chứ?"},
          {"speaker":"cao_minh","text":"Ngươi muốn đặt ra tông quy cho tất cả?"},
          {"speaker":"diep_minh","text":"Kẻ đủ sức nắm đường tu luyện sẽ định nghĩa thế nào là quy củ."},
          {"speaker":"cao_minh","text":"Ngươi gọi sự phục tùng là trật tự."},
          {"speaker":"diep_minh","text":"Còn ngươi gọi sức mạnh của mình là tự do. Chúng ta chỉ khác ở cách đặt tên."},        ],
        [
          {"speaker":"cao_minh","text":"Ngươi từng theo đuổi chính đạo. Rốt cuộc còn điều gì trong ngươi không biến thành thứ để đánh đổi?"},
          {"speaker":"diep_minh","text":"Ta chưa từng coi con đường mình đi là cuộc thi xem ai nhân từ hơn."},
          {"speaker":"cao_minh","text":"Một đạo lý phải xây bằng hơn bốn trăm xác người?"},
          {"speaker":"diep_minh","text":"Ngươi có thể căm hận ta đến tận cùng. Điều ấy không làm những người kia sống lại."},
          {"speaker":"cao_minh","text":"Nhưng chừng nào ta còn cầm kiếm, ngươi sẽ phải đối diện với việc mình đã làm."},        ]
      ],
      cross:[
        [
          {speaker:'diep_minh',text:'Ngươi không phải người duy nhất mang mối thù bước vào những hành lang này.'},
          {speaker:'cao_minh',text:'Ngươi đang ám chỉ ai?'},
          {speaker:'diep_minh',text:'Có những thanh kiếm chỉ cần tin rằng mình đang bảo vệ chính đạo là đủ sắc bén.'},
          {speaker:'diep_minh',text:'Nếu một ngày chúng quay về phía ngươi, đừng hỏi ta vì sao.'}
        ],
        [
          {"speaker":"diep_minh","text":"Ngươi gọi tên một thanh kiếm cũ với vẻ rất khác khi gọi tên ta."},
          {"speaker":"cao_minh","text":"Đừng đem nàng vào chuyện giữa chúng ta."},
          {"speaker":"diep_minh","text":"Ta chẳng cần gọi tên. Ngươi đã tự nói cho ta biết mình sợ điều gì."},
          {"speaker":"diep_minh","text":"Một người quen có thể trở nên xa lạ nhanh hơn cả đường đi nơi này."},        ],
        [
          {"speaker":"cao_minh","text":"Ngươi từng đứng cạnh Lục Trầm?"},
          {"speaker":"diep_minh","text":"Ngươi hỏi như thể chỉ cần biết ai đã đứng cạnh nàng thì mọi lời nàng tin sẽ tự tan."},
          {"speaker":"cao_minh","text":"Ngươi đã nói gì?"},
          {"speaker":"diep_minh","text":"Chính câu hỏi ấy cho thấy ngươi vẫn chưa hiểu. Người ta có thể nghe một lời, nhưng cầm kiếm là quyết định của họ."},        ]
      ],
      repeat:[
        [
          {speaker:'diep_minh',text:'Đường đi đổi hướng, linh khí vẫn chảy. Ta càng ở lâu, càng thấy nơi này đáng giá.'},
          {speaker:'cao_minh',text:'Ngươi nói như thể nó đã thuộc về ngươi.'},
          {speaker:'diep_minh',text:'Chưa. Bởi vậy ta vẫn đang ở đây.'}
        ],
        [
          {speaker:'diep_minh',text:'Ngươi lẽ ra không nên sống sót sau ngày ấy.'},
          {speaker:'cao_minh',text:'Ngươi lẽ ra không nên động đến Cao gia.'},
          {speaker:'diep_minh',text:'Vậy giữa chúng ta, chỉ có kiếm là còn đủ lời để nói.'}
        ],
        [
          {speaker:'cao_minh',text:'Ngươi mạnh hơn trước, nhưng vẫn chẳng thể bước ra ngoài.'},
          {speaker:'diep_minh',text:'Ngươi lầm rồi. Ta không vội rời khỏi một nơi còn quá nhiều thứ chưa lấy được.'},
          {speaker:'diep_minh',text:'Và ngươi là thứ phiền toái ta cần giải quyết trước.'}
        ],
        [
          {"speaker":"diep_minh","text":"Người khác sợ lạc đường. Ta sợ bỏ phí một nơi có thể khiến cảnh giới thay đổi."},
          {"speaker":"cao_minh","text":"Vì thế ngươi đánh đổi cả thân thể?"},
          {"speaker":"diep_minh","text":"Nếu cần, ta sẽ tìm cách chịu một cái giá khác. Nhưng chưa bao giờ là vô giá."},        ],
        [
          {"speaker":"cao_minh","text":"Ngươi có định đưa cả những kẻ đi theo mình ra khỏi Backrooms?"},
          {"speaker":"diep_minh","text":"Nếu ngày ấy đến, ta sẽ chọn ai đủ tư cách bước theo."},
          {"speaker":"cao_minh","text":"Vậy trước ngày ấy, họ chỉ là công cụ?"},
          {"speaker":"diep_minh","text":"Một tông môn phải có lợi ích và kỷ luật. Ngươi có thể ghét từ ngữ ấy."},        ],
        [
          {"speaker":"diep_minh","text":"Đã lâu ta không thấy ai nhìn kiếm của mình mà vẫn nhắc chuyện huyết án."},
          {"speaker":"cao_minh","text":"Ngày nào ngươi còn sống, ta còn nhắc."},
          {"speaker":"diep_minh","text":"Vậy hãy giữ giọng nói ấy. Nó nhắc ta không được để sót ngươi lần nữa."},        ]
      ]
    },
    luc_tram_hac_hoa:{
      name:'Lục Trầm Hắc Hóa',overlay:'entity/luc_tram_hac_hoa.webp',
      episodes:[
        [
          {speaker:'luc_tram_hac_hoa',text:'Ma khí trong kinh mạch ta... tại sao lại giống hệt khí tức của ngươi?'},
          {speaker:'cao_minh',text:'Lục Trầm? Nàng không nhận ra ta sao?'},
          {speaker:'luc_tram_hac_hoa',text:'Ta nhận ra ma khí. Còn ngươi, ta không nhớ đã từng gặp.'},
          {speaker:'luc_tram_hac_hoa',text:'Đừng lấy tên ta để đổi lấy một khắc sơ hở.'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Ta nhớ Lôi Thiên Vực. Nhớ kiếm quang và những người đã ngã xuống.'},
          {speaker:'cao_minh',text:'Vậy nàng còn nhớ chuyện gì đã xảy ra với chính mình?'},
          {speaker:'luc_tram_hac_hoa',text:'Có một khoảng trống. Ta không biết ai đã lấy nó đi.'},
          {speaker:'luc_tram_hac_hoa',text:'Đừng mong dùng điều ta không nhớ để ép ta tin ngươi.'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Có người nói chính ngươi đã giết sạch các tu sĩ Chính Đạo ở Lôi Thiên Vực.'},
          {speaker:'cao_minh',text:'Ai đã nói với nàng điều đó?'},
          {speaker:'luc_tram_hac_hoa',text:'Ta từng thấy những người ngã xuống. Nhưng ta cũng không tin hoàn toàn kẻ đã kể chuyện ấy.'},
          {speaker:'cao_minh',text:'Vậy nàng còn nhớ điều gì dẫn đến trận chiến?'},
          {speaker:'luc_tram_hac_hoa',text:'Ta nhớ rất nhiều. Chỉ có một khoảng trống mà ta không giải thích được.'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Ngươi biết Tịch Quang. Ngươi biết cả cách ta chuyển kiếm trước khi xuất chiêu.'},
          {speaker:'cao_minh',text:'Ta đã ở bên nàng lâu hơn nàng tưởng.'},
          {speaker:'luc_tram_hac_hoa',text:'Nếu thế, tại sao trong trí nhớ của ta không có lấy một lần ngươi xuất hiện?'},
          {speaker:'luc_tram_hac_hoa',text:'Nói thật đi. Ngươi đã làm gì với ta?'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Ta không thích cách ngươi nhìn ta. Như thể ta vừa trở về từ cõi chết.'},
          {speaker:'cao_minh',text:'Có những chuyện ta không thể giải thích chỉ bằng vài câu.'},
          {speaker:'luc_tram_hac_hoa',text:'Vậy đừng bắt ta phải tin vào vài câu ấy.'},
          {speaker:'luc_tram_hac_hoa',text:'Chừng nào chưa biết sự thật, thanh kiếm này vẫn hướng về ngươi.'}
        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Diệp Minh biết tên ngươi trước cả khi ta hỏi."},
          {"speaker":"cao_minh","text":"Hắn không chỉ biết tên ta."},
          {"speaker":"luc_tram_hac_hoa","text":"Ta biết hắn là kẻ chẳng đáng tin. Nhưng vì sao mỗi lần nhắc đến Cao gia, hắn lại im lặng?"},
          {"speaker":"cao_minh","text":"Nàng biết Cao gia đã xảy ra chuyện gì không?"},
          {"speaker":"luc_tram_hac_hoa","text":"Không. Và ta sẽ không nhận câu trả lời của chỉ một phía làm kết luận."},        ],
        [
          {"speaker":"cao_minh","text":"Nàng còn nhớ những ai đứng cùng mình tại Lôi Thiên Vực?"},
          {"speaker":"luc_tram_hac_hoa","text":"Ta nhớ rất nhiều khuôn mặt. Ta nhớ Diệp Minh đã đứng cạnh Vô Lượng."},
          {"speaker":"cao_minh","text":"Vậy vì sao ký ức ấy còn mà nàng lại chẳng nhớ ta?"},
          {"speaker":"luc_tram_hac_hoa","text":"Đó cũng là điều ta phải tự hỏi. Đừng nghĩ ngươi có quyền trả lời thay ta."},
          {"speaker":"luc_tram_hac_hoa","text":"Ta nhớ mình đã nâng kiếm. Nhưng có một người đứng trước mũi kiếm ấy mà ta không sao gọi tên."},        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Mỗi lần đến gần ngươi, ma nguyên trong ta đều khiến ta bất an."},
          {"speaker":"cao_minh","text":"Nàng muốn ta tránh xa?"},
          {"speaker":"luc_tram_hac_hoa","text":"Ta muốn biết vì sao một thứ xa lạ lại có cảm giác như đã ở trong mình từ rất lâu."},
          {"speaker":"cao_minh","text":"Không phải thứ gì quen thuộc cũng dễ nhớ lại."},
          {"speaker":"luc_tram_hac_hoa","text":"Đừng nói với ta như người đã biết tất cả. Ta không chịu được giọng ấy."},        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Có kẻ nói người đứng đối diện ta là ma đầu tàn sát Chính Đạo."},
          {"speaker":"cao_minh","text":"Còn nàng tin điều gì?"},
          {"speaker":"luc_tram_hac_hoa","text":"Ta tin thương vong ở Lôi Thiên Vực là thật. Nhưng lời kể về thủ phạm không thể chỉ dựa trên một miệng người."},
          {"speaker":"cao_minh","text":"Vậy nàng sẽ thôi chĩa kiếm vào ta?"},
          {"speaker":"luc_tram_hac_hoa","text":"Chưa. Ngươi cũng chưa trả lời hết những gì ta hỏi."},        ],
        [
          {"speaker":"cao_minh","text":"Ta từng biết lúc nào nàng sẽ đổi thế với Tịch Quang."},
          {"speaker":"luc_tram_hac_hoa","text":"Điều đó không chứng minh chúng ta từng đồng hành."},
          {"speaker":"cao_minh","text":"Ta biết."},
          {"speaker":"luc_tram_hac_hoa","text":"Nhưng cũng không phải điều một người mới gặp thường có thể nói ra."},
          {"speaker":"luc_tram_hac_hoa","text":"Ta sẽ nhớ lấy chi tiết này. Chỉ là ta chưa biết nên hiểu nó như thế nào."},        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Nếu những gì ngươi nói là thật, vì sao ta tỉnh dậy mà không có ngươi trong ký ức?"},
          {"speaker":"cao_minh","text":"Ta cũng muốn biết vì sao nàng rời khỏi ta."},
          {"speaker":"luc_tram_hac_hoa","text":"Rời khỏi ngươi? Ngươi nói như thể chúng ta từng cùng đi qua một nơi nào đó."},
          {"speaker":"cao_minh","text":"Ta không thể ép nàng nhớ."},
          {"speaker":"luc_tram_hac_hoa","text":"Vậy đừng ép ta tin. Hãy để điều đã xảy ra tự chứng minh chính nó."},        ]
      ],
      cross:[
        [
          {speaker:'luc_tram_hac_hoa',text:'Ta còn nhớ Diệp Minh đã đứng cạnh Vô Lượng ở Lôi Thiên Vực.'},
          {speaker:'cao_minh',text:'Vậy nàng còn tin những gì hắn nói?'},
          {speaker:'luc_tram_hac_hoa',text:'Ta chưa từng nói mình tin hắn hoàn toàn.'},
          {speaker:'luc_tram_hac_hoa',text:'Nhưng sự đáng ngờ của hắn không đủ để chứng minh ngươi vô tội.'}
        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Diệp Minh từng nói với ta về cái giá của sinh mạng. Hắn nói như thể mọi thứ đều có thể đem ra cân."},
          {"speaker":"cao_minh","text":"Hắn đã đối xử với Cao gia như vậy."},
          {"speaker":"luc_tram_hac_hoa","text":"Ta chưa biết hết chuyện Cao gia. Nhưng giờ ta hiểu vì sao ngươi không thể nhắc đến hắn mà bình thản."},
          {"speaker":"luc_tram_hac_hoa","text":"Điều ấy chưa trả lời được chuyện Lôi Thiên Vực. Nhưng ít nhất đã có thêm một điều cần kiểm chứng."},        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Ta đã hỏi Diệp Minh về chuyện những tu sĩ Chính Đạo ngã xuống. Hắn chỉ kể những phần có lợi cho lời buộc tội."},
          {"speaker":"cao_minh","text":"Nàng bắt đầu nghi hắn?"},
          {"speaker":"luc_tram_hac_hoa","text":"Ta nghi từ trước. Giờ ta nghi cả cách hắn chọn điều để nói."},
          {"speaker":"luc_tram_hac_hoa","text":"Nhưng nghi ngờ một kẻ nói dối chưa đủ để ta tin ngay người đối diện."},        ]
      ],
      repeat:[
        [
          {speaker:'luc_tram_hac_hoa',text:'Cùng một luồng ma khí. Ngươi có thể phủ nhận mọi lời, nhưng không thể phủ nhận điều ấy.'},
          {speaker:'cao_minh',text:'Ta chưa từng phủ nhận.'},
          {speaker:'luc_tram_hac_hoa',text:'Vậy ta càng phải tìm ra nó từ đâu mà có.'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Tại sao ngươi luôn gọi ta bằng giọng ấy?'},
          {speaker:'cao_minh',text:'Bởi ta biết người đang đứng trước mặt mình là ai.'},
          {speaker:'luc_tram_hac_hoa',text:'Còn ta chỉ biết mình không thể buông kiếm lúc này.'}
        ],
        [
          {speaker:'luc_tram_hac_hoa',text:'Ta không nhớ ngươi, Cao Minh. Điều đó không đồng nghĩa ta sẽ nghe theo bất cứ ai.'},
          {speaker:'cao_minh',text:'Vậy hãy tự tìm sự thật.'},
          {speaker:'luc_tram_hac_hoa',text:'Ta sẽ làm thế. Nhưng hôm nay, chúng ta vẫn đứng ở hai đầu lưỡi kiếm.'}
        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Khi nghe tên Cao Minh, ta không thấy ký ức. Chỉ thấy một khoảng trống mà mình muốn tránh."},
          {"speaker":"cao_minh","text":"Ta không muốn nàng phải ép mình."},
          {"speaker":"luc_tram_hac_hoa","text":"Vậy hãy đứng yên khi ta hỏi. Đừng lấy sự quen thuộc làm bằng chứng."},        ],
        [
          {"speaker":"cao_minh","text":"Tịch Quang vẫn nhận ra kiếm ý của nàng."},
          {"speaker":"luc_tram_hac_hoa","text":"Kiếm không nói được quá khứ thay cho người cầm nó."},
          {"speaker":"cao_minh","text":"Ta hiểu."},
          {"speaker":"luc_tram_hac_hoa","text":"Nếu hiểu, đừng bắt ta chọn một lời giải trước khi thấy sự thật."},        ],
        [
          {"speaker":"luc_tram_hac_hoa","text":"Có lúc ta tự hỏi điều gì đáng sợ hơn: tin nhầm một người, hay giết nhầm một người."},
          {"speaker":"cao_minh","text":"Nàng không cần quyết định ngay lúc này."},
          {"speaker":"luc_tram_hac_hoa","text":"Nhưng chần chừ giữa chiến trận cũng có cái giá của nó. Ta vẫn phải chọn cách đứng vững."},        ]
      ]
    }
  };
})(window);
