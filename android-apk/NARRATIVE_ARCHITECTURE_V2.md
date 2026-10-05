# BACKROOMS THE GAME — KIẾN TRÚC NARRATIVE AI MỚI

**Trạng thái:** Architecture / Design Specification  
**Phạm vi:** Gameplay narrative, Mission System, Đạo Diễn Cảnh, Skeleton, GM, Survivor, Entity/Chest spawn, Memory  
**Mục tiêu:** Biến mỗi Level thành một chương truyện được AI biên tập theo từng hồi, trong đó người chơi đưa ra lựa chọn, Đạo Diễn Cảnh chủ động gây khó cho Cao Minh, còn Core giữ toàn bộ sự thật và luật gameplay.

---

## 1. Tuyên bố thiết kế

Backrooms The Game không còn đi theo hướng:
- free-form Player Action;
- route streak;
- board traversal;
- roll dice để di chuyển;
- RNG quyết định toàn bộ diễn biến theo từng turn.

Thay vào đó, game được định hình như một **AI-driven narrative game theo từng chương**.

Mỗi **Level = một chương truyện**.

Mỗi Level được chia thành nhiều **Giai đoạn / Hồi (Act)**.

Trong mỗi hồi:
- Hệ Thống giao và theo dõi nhiệm vụ;
- RNG xác định số lượng Entity và Chest được phép xuất hiện trong hồi;
- Đạo Diễn Cảnh đọc toàn bộ trạng thái và biên tập chuỗi biến cố;
- Skeleton lưu cấu trúc của hồi;
- GM kể lại thành câu chuyện;
- người chơi chọn một trong ba lựa chọn hợp lý;
- Core commit hậu quả thật;
- đến màn hình loading tiếp theo, phần tương lai của câu chuyện được biên tập lại.

Kết thúc Level:
- hệ thống đánh giá toàn bộ kết quả;
- xác định Good / Neutral / Bad ending;
- skeleton tạm được cô đọng thành một memo dài hạn ngắn;
- memo này trở thành context của các Level sau.

---

## 2. Triết lý cốt lõi

### 2.1. Core là sự thật

AI không được tự quyết định sự thật gameplay.

Core là authority tuyệt đối đối với:
- Level hiện tại;
- HP;
- inventory;
- NPC/Survivor còn sống hay đã chết;
- Entity đang tồn tại;
- Chest đã xuất hiện hay chưa;
- nhiệm vụ đã hoàn thành hay thất bại;
- lựa chọn người chơi đã thực hiện;
- hậu quả đã commit;
- thread nào còn tồn tại;
- ending nào thực sự đạt được.

AI có thể đề xuất, sắp xếp và kể.

Core mới là nơi xác nhận:

> “Điều này thực sự đã xảy ra.”

### 2.2. Đạo Diễn Cảnh là đối thủ vô hình

Đạo Diễn Cảnh không trung lập.

Nó **không ưa Cao Minh**.

Mục tiêu của nó là:

> Làm cho việc hoàn thành nhiệm vụ của Cao Minh khó nhất có thể, nhưng vẫn phải hợp lý, công bằng, đúng canon và không phá luật.

Đạo Diễn Cảnh được phép:
- làm các nhiệm vụ xung đột với nhau;
- sử dụng hậu quả cũ để gây khó ở hồi sau;
- tận dụng điểm yếu hiện tại của Cao Minh;
- tạo Survivor để tiêu hao tài nguyên;
- đặt Entity và Chest vào những tình huống khó xử;
- buộc Cao Minh phải đánh đổi;
- kéo câu chuyện về hướng Bad ending.

Nhưng Đạo Diễn Cảnh **không được phép**:
- sửa quá khứ;
- phủ nhận một thành công đã được Core commit;
- tự sinh thêm Entity/Chest ngoài budget;
- tạo fact không hợp canon;
- làm một lựa chọn hợp lý trở thành vô nghĩa chỉ vì muốn Cao Minh thua;
- thay đổi luật sau khi Cao Minh đã giải được bài toán.

Nguyên tắc:

> **Đạo Diễn được quyền độc ác, nhưng không được quyền bất công.**

### 2.3. Người chơi không chọn Good / Bad / Neutral trực tiếp

Người chơi luôn nhận **3 lựa chọn hợp lý theo hoàn cảnh**.

Good / Neutral / Bad chỉ là kết quả cuối Level dựa trên:
- bảng nhiệm vụ;
- trạng thái thật;
- những người còn sống;
- những gì đã mất;
- những việc đã hoàn thành;
- hậu quả dài hạn.

---

## 3. Level = Chapter

Mỗi Level được xem như một chương truyện hoàn chỉnh.

Một Level cần có:
- trạng thái đầu chương;
- bảng nhiệm vụ;
- các hồi;
- biến cố;
- character arc;
- Survivor/Entity threads;
- climax;
- kết thúc;
- memo dài hạn.

Cấu trúc tổng quát:

```text
LEVEL START
   ↓
MISSION BOARD
   ↓
ACT I
   ↓
LOADING / EDIT
   ↓
ACT II
   ↓
LOADING / EDIT
   ↓
ACT III
   ↓
...
   ↓
CLIMAX
   ↓
LEVEL RESULT
   ↓
GOOD / NEUTRAL / BAD
   ↓
LONG-TERM MEMO
```

Số hồi không cần cố định.

---

## 4. Bảng nhiệm vụ — “Hệ Thống” của Cao Minh

Cao Minh có một **Hệ Thống** hiển thị bảng nhiệm vụ.

Bảng nhiệm vụ là gameplay-facing và người chơi được nhìn thấy.

Ví dụ:

```text
╔════════════════════════════════╗
          NHIỆM VỤ — LEVEL 0
╠════════════════════════════════╣

[MAIN]
■ Thoát khỏi Level 0

[MISSION]
□ Giữ Lucia Lục sống sót
□ Thu thập bằng chứng về lối thoát
□ Tiêu diệt Entity đang kiểm soát khu vực

[HIDDEN]
???
╚════════════════════════════════╝
```

Mission AI có thể tạo bảng nhiệm vụ khi Level bắt đầu dựa trên:
- canon của Level;
- Character có thể xuất hiện;
- Entity hợp lệ;
- trạng thái Cao Minh;
- memo từ Level trước;
- thread dài hạn;
- khả năng của Level hiện tại.

### 4.1. Loại nhiệm vụ

**MAIN** — điều kiện để hoàn thành Level.  
**MISSION** — mục tiêu quan trọng của run.  
**OPTIONAL** — mục tiêu phụ ảnh hưởng reward/lore/quan hệ.  
**HIDDEN** — điều kiện ẩn, chỉ reveal khi phát hiện hoặc hoàn thành.

---

## 5. Giai đoạn / Hồi

Một Level được chia thành nhiều hồi.

Mỗi hồi có:
- mục đích narrative;
- spawn budget;
- các thread được phép dùng;
- scene sequence;
- choice points;
- consequence;
- exit condition của hồi.

Ví dụ:

```text
ACT I — Setup
ACT II — Complication
ACT III — Escalation
ACT IV — Climax
```

Đây chỉ là mẫu, không phải format bắt buộc.

---

## 6. Loading Screen = Phòng Biên Tập

Mỗi lần chuyển hồi sẽ có màn hình loading.

Đây là **Narrative Editing Boundary**.

Trong loading:
1. Core khóa lại hồi trước.
2. Situation Reader đọc toàn bộ state mới.
3. Mission state được cập nhật.
4. RNG roll spawn budget cho hồi tiếp theo.
5. Đạo Diễn Cảnh đọc toàn bộ nguyên liệu.
6. Đạo Diễn Cảnh viết lại phần tương lai.
7. Skeleton Builder tạo skeleton mới.
8. Validator kiểm tra canon và luật.
9. Asset cần thiết được load.
10. Hồi mới bắt đầu.

Nguyên tắc:

> **Quá khứ không được sửa. Chỉ phần tương lai được biên tập lại.**

---

## 7. Situation Reader

Situation Reader không sáng tác.

Nó chỉ trả lời:

> “Hiện tại câu chuyện có những gì?”

Input:
- current Level;
- current Act;
- Mission Board;
- HP;
- inventory;
- active Character;
- active Survivor;
- active Entity;
- resolved events;
- unresolved threads;
- relationships;
- evidence;
- recent choices;
- long-term memo.

Output ví dụ:

```text
LEVEL: 0
ACT: 2

PLAYER:
- Cao Minh
- HP thấp
- thiếu thuốc

CHARACTERS:
- Lucia còn sống
- đang bị thương

SURVIVORS:
- Survivor A đã phản bội
- đã bỏ chạy
- đang giữ một phần vật tư bị cướp

ENTITIES:
- Entity X chưa bị tiêu diệt

MISSIONS:
- Giữ Lucia sống: ACTIVE
- Tiêu diệt Entity X: ACTIVE
- Tìm bằng chứng: PARTIAL

UNRESOLVED THREADS:
- Survivor A escaped
- Entity X stalking
- Exit evidence incomplete
```

Situation Reader tuyệt đối không được thêm fact mới.

---

## 8. RNG trong kiến trúc mới

RNG vẫn tồn tại, nhưng RNG không còn là người viết câu chuyện.

RNG chỉ quyết định **nguyên liệu**:
- số lượng Entity trong hồi;
- số lượng Chest trong hồi;
- identity được chọn từ pool hợp lệ;
- loot cụ thể;
- các chi tiết thật sự cần randomness.

---

## 9. Act Spawn Budget

Ở đầu mỗi hồi, trong loading, Core roll:

```text
ACT SPAWN BUDGET

Entity Count: N
Chest Count: M
```

Ví dụ:

```text
ACT II
Entity: 2
Chest: 1
```

Sau đó Core chọn Entity/Chest hợp lệ từ pool của Level.

Từ thời điểm này:
- Đạo Diễn không được tạo thêm Entity;
- Đạo Diễn không được tạo thêm Chest;
- số lượng đã được Core khóa.

Ranh giới trách nhiệm:

> **RNG quyết định có bao nhiêu.**  
> **Canon quyết định cái gì được phép tồn tại.**  
> **Đạo Diễn quyết định dùng chúng như thế nào.**

Entity không nhất thiết đồng nghĩa combat ngay. Nó có thể được dùng cho stalk, hint, dấu vết, ambush, chase, pressure, combat hoặc climax.

Chest cũng có thể là reward, temptation, mục tiêu tranh chấp, thứ nằm sau Entity hoặc nguồn tài nguyên cần thiết.

---

## 10. Đạo Diễn Cảnh

Đạo Diễn Cảnh là trung tâm sáng tác của mỗi hồi.

Input:
- Situation Reader output;
- Mission Board;
- Spawn Budget;
- long-term memo;
- unresolved threads;
- Character state;
- Survivor state;
- Level canon;
- ending trajectory hiện tại.

Nó phải trả lời:

> “Với những gì đang có, chuỗi biến cố tiếp theo nên diễn ra thế nào để gây khó cho Cao Minh nhưng vẫn hợp lý?”

---

## 11. Đạo Diễn sử dụng nhiệm vụ như thế nào

Ví dụ:

```text
MISSION A:
Giữ Lucia sống

MISSION B:
Tiêu diệt Entity X

MISSION C:
Tìm bằng chứng
```

Đạo Diễn có thể tạo:

```text
Lucia phát hiện bằng chứng
        ↓
Entity X xuất hiện
        ↓
Lucia bị tách khỏi Cao Minh
        ↓
Entity rút về hướng ngược lại
        ↓
Cao Minh phải chọn:
- đuổi Entity;
- cứu Lucia;
- giữ bằng chứng.
```

Director không auto-fail. Nó chỉ làm nhiều mục tiêu trở nên khó hoàn thành cùng lúc.

---

## 12. Hostile Director

Các chiến thuật chính:

### Attack Strength
Đánh vào chiến thuật Cao Minh đang dựa vào.

### Create Trade-offs
Một lựa chọn không giải quyết mọi vấn đề cùng lúc.

### Reuse Consequences
Hậu quả hồi trước phải có cơ hội quay lại.

### Interleave Missions
Không để nhiệm vụ được giải sạch lần lượt.

### Favor Bad Outcome
Director có thể cố kéo câu chuyện về Bad ending, nhưng Core mới có quyền xác nhận kết quả thật.

---

## 13. Survivor System

Survivor là công cụ narrative đặc biệt.

Đạo Diễn được phép tạo Survivor tự do trong giới hạn Level/canon.

Người chơi không biết Survivor thuộc loại nào.

Một Survivor có thể là:
- tốt thật;
- tuyệt vọng;
- opportunist;
- hostile;
- unstable.

### 13.1. Survivor tốt

Có thể:
- cung cấp bằng chứng;
- chỉ lối;
- cảnh báo Entity;
- đưa tài nguyên;
- mở hidden mission;
- cứu Cao Minh sau này;
- trở thành Character dài hạn.

### 13.2. Survivor xấu / opportunist

Có thể:
- giả vờ bị thương;
- xin thức ăn;
- theo Cao Minh;
- dò hỏi inventory;
- chờ Cao Minh mất cảnh giác;
- tấn công;
- cướp tài nguyên;
- bỏ chạy.

Hậu quả ambush:

```text
Cao Minh:
- mất HP
- mất một phần vật tư

Survivor:
- cướp tài nguyên hợp lý
- bỏ chạy
- trở thành unresolved thread
```

Survivor không nhất thiết combat đến chết.

### 13.3. Cướp tài nguyên phải có logic

Nếu đói:

```text
food > water > medicine > other
```

Nếu bị thương:

```text
medicine > food > weapon
```

Nếu là kẻ cướp có kinh nghiệm:

```text
weapon > medicine > valuable supplies
```

Core quyết định chính xác thứ nào có thể mất.

### 13.4. Survivor phải persist

Một Survivor bỏ chạy không biến mất khỏi thế giới.

Ví dụ:

```text
SURVIVOR_A
status: escaped
relationship: hostile
stolen:
- bandage x1
- food x1
```

Director có thể dùng lại thread đó ở hồi sau hoặc Level sau.

### 13.5. Không để Survivor = Enemy mặc định

Nếu tỷ lệ phản bội quá cao, người chơi sẽ học rằng cứu người luôn sai.

Do đó Survivor phải đủ đa dạng để câu hỏi thật sự là:

> “Người này có đáng tin không?”

---

## 14. Skeleton Builder

Đạo Diễn Cảnh sáng tác.

Skeleton Builder không sáng tác.

Nó chỉ biến kế hoạch của Đạo Diễn thành cấu trúc máy đọc được.

Ví dụ:

```json
{
  "act": 2,
  "scenePurpose": "merge_entity_hunt_and_evidence_thread",
  "events": [
    "entity_x_retreats",
    "survivor_trace_found",
    "evidence_location_revealed"
  ],
  "missionLinks": [
    "eliminate_entity_x",
    "collect_exit_evidence"
  ],
  "choiceRequired": true
}
```

Skeleton cần chứa:
- scene purpose;
- actors;
- event sequence;
- dependencies;
- mission links;
- unresolved threads;
- allowed facts;
- prohibited facts;
- choice point;
- current beat;
- planned beats;
- climax target.

---

## 15. FACTS và PLAN phải tách riêng

Skeleton phải có hai vùng:

```text
FACTS
- những gì đã xảy ra
- bất biến

PLAN
- những gì Director muốn xảy ra
- có thể bị sửa
```

Nếu người chơi làm một lựa chọn khiến PLAN không còn hợp lý:
- FACTS giữ nguyên;
- PLAN bị viết lại tại lần biên tập tiếp theo.

---

## 16. GM Narrator

GM không quyết định gameplay.

GM nhận:
- Core facts;
- current skeleton;
- Character voice;
- environment;
- current choice result;
- mission context.

GM có nhiệm vụ:

> biến skeleton thành văn kể chuyện tự nhiên.

GM không được tự:
- spawn Entity;
- spawn Chest;
- giết NPC;
- thêm item;
- hoàn thành mission;
- sửa HP;
- thay đổi fact.

---

## 17. Ba lựa chọn

Mỗi choice point đưa ra đúng 3 lựa chọn.

Ba lựa chọn phải:
1. hợp lý với scene;
2. không có “nút ngu” rõ ràng;
3. không lộ Good/Bad/Neutral;
4. có hậu quả khác nhau;
5. tác động tới mission/state/thread;
6. tạo đủ vật liệu cho Director biên tập tiếp.

Ví dụ:

```text
[A] Quay lại kéo Lucia ra.
[B] Giữ cửa mở và gọi Lucia chạy tới.
[C] Rời khỏi khu vực trước khi Entity tới.
```

---

## 18. Choice Resolution

Sau khi người chơi chọn:

```text
PLAYER SELECTS
      ↓
CORE VALIDATES
      ↓
CORE COMMITS CONSEQUENCE
      ↓
MISSION UPDATE
      ↓
THREAD UPDATE
      ↓
GM NARRATES RESULT
```

GM không tự quyết định consequence authoritative.

---

## 19. Ending System

Level có ba vùng kết thúc:
- GOOD;
- NEUTRAL;
- BAD.

Ending được suy ra từ:
- MAIN mission;
- mission completion;
- Character survival;
- Survivor outcome;
- resources;
- evidence;
- unresolved threats;
- consequences.

Ví dụ:

```text
✓ Thoát Level
✓ Lucia sống
✓ Entity X bị tiêu diệt
✓ Đủ bằng chứng
→ GOOD
```

```text
✓ Thoát Level
✓ Lucia sống
✕ Entity X còn sống
△ Bằng chứng không đầy đủ
→ NEUTRAL
```

```text
✓ Thoát Level
✕ Lucia chết
✕ Entity X còn sống
✕ Mất bằng chứng
→ BAD
```

---

## 20. Director và Ending Trajectory

Director có thể biết trajectory hiện tại:

```text
GOOD
NEUTRAL
BAD
```

Player không thấy.

Director có thể cố kéo về BAD.

Nhưng:

> **Director không được quyền commit ending.**

Core/System mới quyết định ending từ state thật.

---

## 21. Biên tập giữa các hồi

Ví dụ kế hoạch cũ:

```text
ACT I:
Survivor appears

ACT II:
Survivor provides evidence

ACT III:
Entity appears
```

Nhưng Act I thực tế:

```text
Survivor betrays Cao Minh
→ Cao Minh mất HP
→ mất thuốc
→ Survivor chạy
```

Trong loading, Director phải bỏ kế hoạch cũ.

Plan mới có thể là:

```text
ACT II:
Dấu vết Survivor dẫn tới khu vực mới
→ Survivor đang giữ một evidence
→ Entity hoạt động quanh khu vực đó
→ Cao Minh phải cân nhắc truy đuổi
```

Nguyên tắc:

> **AI không bảo vệ outline. AI bảo vệ continuity.**

---

## 22. Long-Term Memo

Khi Level kết thúc:
- skeleton tạm không cần giữ toàn bộ;
- narration không cần lưu toàn bộ;
- chỉ facts quan trọng được cô đọng.

Input:
- resolved facts;
- mission results;
- Character relationships;
- Survivor outcomes;
- important discoveries;
- unresolved thread;
- ending.

Output ví dụ:

```text
LEVEL 0 — GOOD

- Cao Minh thoát khỏi Level 0.
- Lucia sống và bắt đầu tin tưởng Cao Minh.
- Cao Minh xác nhận hành lang có hiện tượng loop.
- Entity X đã bị tiêu diệt.
- Survivor A từng phản bội và bỏ chạy.
```

Memo phải ngắn.

---

## 23. Quy tắc Memory

Chỉ những gì **đã xảy ra thật** mới được lưu.

Không được lưu:
- planned event chưa xảy ra;
- beat bị hủy;
- alternate path;
- giả thuyết chưa xác nhận;
- lựa chọn người chơi không chọn.

Chỉ lưu:
- committed fact;
- outcome;
- relationship;
- discovery;
- unresolved thread cần carry forward.

---

## 24. Kiến trúc tổng thể

```text
                     LEVEL START
                          │
                          ▼
                 MISSION GENERATOR
                          │
                          ▼
                    MISSION BOARD
                          │
                          ▼
                  SITUATION READER
                          │
                          ▼
                 ACT SPAWN ROLLS
                 Entity N / Chest M
                          │
                          ▼
                 HOSTILE DIRECTOR
                          │
                          ▼
                 SKELETON BUILDER
                          │
                          ▼
                     VALIDATOR
                          │
                          ▼
                     GM NARRATOR
                          │
                          ▼
                    3 CHOICES
                          │
                          ▼
                    PLAYER SELECTS
                          │
                          ▼
                       CORE
           state / HP / items / mission /
           NPC / Survivor / Entity / facts
                          │
                          ▼
                    GM CONSEQUENCE
                          │
                          ▼
                  MORE SCENES IN ACT
                          │
                          ▼
                    ACT COMPLETE
                          │
                          ▼
                      LOADING
                          │
             ┌────────────┴────────────┐
             │                         │
      summarize past             roll next act
             │                         │
             └────────────┬────────────┘
                          ▼
                 DIRECTOR RE-EDIT
                          │
                          ▼
                     NEXT ACT
```

Khi Level kết thúc:

```text
LEVEL END
   ↓
MISSION RESULT
   ↓
GOOD / NEUTRAL / BAD
   ↓
MEMORY CONSOLIDATOR
   ↓
SHORT LONG-TERM MEMO
   ↓
NEXT LEVEL
```

---

## 25. Phân quyền hệ thống

### Core
Sở hữu:
- truth;
- state;
- mission progress;
- HP;
- inventory;
- Entity/Chest allowance;
- consequence;
- ending result.

### Mission AI
Sở hữu:
- tạo bảng nhiệm vụ đầu Level;
- đề xuất hidden/optional mission trong giới hạn cho phép.

### Situation Reader
Sở hữu:
- đọc và tóm tắt state;
- không sáng tác.

### RNG
Sở hữu:
- Act Entity count;
- Act Chest count;
- selection ngẫu nhiên từ pool hợp lệ;
- loot randomness nếu cần.

### Hostile Director
Sở hữu:
- pacing;
- event sequencing;
- mission conflict;
- thread reuse;
- pressure;
- scene ordering;
- act-level narrative planning.

### Skeleton Builder
Sở hữu:
- structured plan;
- dependency;
- beat;
- mission link;
- choice point.

### GM
Sở hữu:
- prose;
- atmosphere;
- dialogue;
- narrative presentation.

### Memory Consolidator
Sở hữu:
- nén sự kiện đã xảy ra thành long-term memo.

---

## 26. Quy tắc fairness

Đạo Diễn phải tuân thủ:
1. Không thay đổi fact đã commit.
2. Không thêm Entity/Chest ngoài spawn budget.
3. Không tạo item không tồn tại.
4. Không giết Character nếu không có consequence hợp lệ.
5. Không auto-fail mission vì lý do không được thiết lập.
6. Không retcon.
7. Không dùng hidden knowledge trái với giới hạn của thế giới khi điều đó ảnh hưởng trực tiếp đến hành vi.
8. Không dùng cùng một thủ đoạn quá nhiều lần.
9. Luôn giữ ít nhất một con đường hợp lý để xử lý tình huống, trừ khi người chơi đã thực sự tạo trạng thái không thể cứu.
10. Một thành công hợp lệ phải được công nhận.

---
## 27. Quy tắc chống lặp

Director cần tránh:
- Entity xuất hiện liên tục;
- Survivor phản bội liên tục;
- Chest luôn nằm cạnh Entity;
- cùng một kiểu dilemma;
- cùng một kiểu cứu NPC;
- cùng một pattern lựa chọn dễ đoán.

Director phải theo dõi:
- recent event type;
- recent threat type;
- recent choice shape;
- recent Survivor role;
- recent Entity behavior;
- recent reward pattern.

---

## 28. Replayability

Replayability đến từ sự kết hợp của:
- Mission Board khác nhau;
- spawn budget khác nhau;
- Entity khác nhau;
- Survivor khác nhau;
- lựa chọn người chơi;
- hậu quả carry-over;
- Director re-edit;
- memo từ Level trước;
- Character relationship.

Cùng một Level có thể tạo ra hai chương hoàn toàn khác.

---

## 29. Những hệ thống bị loại khỏi hướng mới

Không còn là core gameplay:
- free-form Player Action;
- route streak;
- exploration streak;
- dice movement;
- board dot traversal;
- exact landing;
- roll route từng turn;
- RNG event theo từng turn như engine kể chuyện chính.

Có thể giữ RNG ở tầng thấp cho:
- spawn budget;
- loot;
- variation;
- một số combat/system mechanics.

---

## 30. Phân biệt SceneDirector hiện tại và Đạo Diễn Cảnh mới

Trong implementation hiện tại đã có `SceneDirector`, nhưng vai trò của nó là presentation frame cho GM sau khi Core đã commit facts.

Khái niệm **Đạo Diễn Cảnh mới** là một tầng khác:

### Existing SceneDirector
- nhận fact đã commit;
- dựng authoritative scene frame;
- phục vụ narration.

### New Hostile Director
- đọc state;
- lập kế hoạch narrative;
- sắp xếp biến cố;
- biên tập từng hồi;
- tạo áp lực;
- tạo skeleton.

Khi triển khai nên tránh nhập nhằng trách nhiệm. Có thể dùng tên như:
- `NarrativeDirector`;
- `ActDirector`;
- `HostileSceneDirector`.

---

## 31. Dữ liệu tối thiểu đề xuất

Một Act state có thể cần:

```text
levelKey
actIndex
missionBoard
spawnBudget
activeEntities
activeChests
activeCharacters
activeSurvivors
facts
threads
relationships
evidence
skeleton
currentBeat
endingTrajectory
```

Một Survivor cần tối thiểu:

```text
id
name
condition
dispositionHidden
relationship
resourceNeed
knowledge
status
stolenItems
threadState
```

Một skeleton cần:

```text
facts
plan
scenePurpose
beats
currentBeat
missionLinks
threadLinks
choicePoint
constraints
climaxTarget
```

---

## 32. Execution model

### Khi bắt đầu Level

```text
Core load Level
→ read memo
→ Mission AI tạo Mission Board
→ roll Act I Spawn Budget
→ Situation Reader
→ Director
→ Skeleton
→ Validator
→ GM
```

### Trong hồi

```text
GM scene
→ 3 choices
→ Player selects
→ Core commits
→ Mission update
→ Skeleton advances
→ GM next scene
```

Không cần replan toàn hồi sau mỗi choice nếu chưa cần.

### Khi hết hồi

```text
Core locks Act
→ Loading
→ summarize
→ roll next Spawn Budget
→ Director re-edit
→ new Skeleton
→ next Act
```

### Khi hết Level

```text
Core evaluates Mission Board
→ Good / Neutral / Bad
→ Memory Consolidator
→ long-term memo
→ next Level
```

---

## 33. Mục tiêu cuối cùng

Kiến trúc này nhằm tạo cảm giác:

> **Mỗi Level là một chương truyện đang được viết trong lúc người chơi trải nghiệm nó.**

Người chơi không biết:
- Director đang nhắm tới điều gì;
- Survivor nào đáng tin;
- Entity nào sẽ được dùng ở đâu;
- Chest nào sẽ trở thành cơ hội hay cái bẫy;
- lựa chọn nào đang kéo ending về hướng nào.

Người chơi chỉ biết:
- Hệ Thống giao nhiệm vụ;
- Cao Minh phải sống sót;
- mỗi lựa chọn có hậu quả;
- những gì đã xảy ra có thể quay lại ở hồi sau.

Tóm tắt trách nhiệm:

> **Mission Board cho Cao Minh mục tiêu.**  
> **RNG cấp nguyên liệu cho từng hồi.**  
> **Đạo Diễn Cảnh biến nguyên liệu thành bài toán khó.**  
> **Skeleton giữ cấu trúc.**  
> **GM biến cấu trúc thành truyện.**  
> **Core giữ sự thật.**  
> **Memo giữ ký ức giữa các chương.**

---

## 34. Kết luận

Kiến trúc mới không còn là một hệ thống “random event + AI kể lại”.

Nó là một hệ thống nhiều tầng:

```text
SYSTEM
giao nhiệm vụ

RNG
cấp Entity / Chest cho hồi

DIRECTOR
đạo diễn và gây khó

SKELETON
lưu kế hoạch

GM
kể chuyện

PLAYER
chọn

CORE
quyết định sự thật

LOADING
biên tập lại

MEMO
ghi nhớ dài hạn
```

Mục tiêu cuối cùng:
- mỗi Level có cấu trúc như một chương truyện;
- mỗi lần chơi có thể khác;
- AI không phá canon;
- RNG không điều khiển narrative;
- lựa chọn thật sự có hậu quả;
- Đạo Diễn Cảnh trở thành đối thủ vô hình của Cao Minh;
- Good Ending chỉ đạt được khi người chơi thật sự vượt qua được những gì Director đặt ra.

---

## 35. Follower / Party Member trong Loading Boundary

Khi Loading / Edit đã roll và khóa tài nguyên của hồi mới, asset preload không chỉ xét Entity/Chest.

Core phải đưa vào loading manifest:
- toàn bộ Entity đã được cấp trong Act Spawn Budget;
- Chest asset nếu Act có Chest;
- Cao Minh;
- **mọi Party Member hiện đang `joined=true`**, kể cả khi người đó chưa phải actor của combat đầu hồi.

Loading chỉ kết thúc sau khi đã thử preload các asset cần thiết của Entity và toàn bộ Party Member nói trên. Việc preload là presentation concern; nó không tự spawn, join, despawn hay thay đổi Party authority.

Nguyên tắc:

> **Nếu một follower đã ở trong Party khi Act được biên tập, Act mới phải sẵn sàng hiển thị follower đó cùng mọi Entity đã được load.**
