# BACKROOMS Entity Assets

Danh sách này phản ánh các sprite Entity hiện đang có trong thư mục `ENTITY` trên Google Drive và được `entity_manifest.json` ánh xạ cho runtime.

Manifest Drive: [entity_manifest.json](https://drive.google.com/file/d/1hJ506mvF4SJ84449ktLW6Js0PmSvUTGc/view)

| Entity ID | Tên canon / asset | File trên Drive | Trạng thái |
|---|---|---|---|
| `ENT-1A` | Hound | [ENT-1A_Hound.png](https://drive.google.com/file/d/1iVod4TXfRjKhEeOKdMYbMkCkPxYrj0AP/view) | Có sprite |
| `ENT-1B` | Clump | [ENT-1B](https://drive.google.com/file/d/1o_E6MCDCC4pZu_HpQGTXGNx_evoGw5wU/view) | Có sprite |
| `ENT-1C` | Duller | [ENT-1C.png](https://drive.google.com/file/d/1swd7CwscCiCcJcvPXvB54XgJDpvJW658/view) | Có sprite |
| `ENT-1D` | Deathmoth | [ENT-1D.png](https://drive.google.com/file/d/1JcA7Xw3Uy-Vm_l9me73xKlMTevjKiuBM/view) | Có sprite |
| `ENT-1E` | Hostile Faceling | [ENT-1E.png](https://drive.google.com/file/d/1uNbLNw8nEiqFCnyaFzUOgTsyDcoduEqN/view) | Có sprite |
| `ENT-1F` | False Puddle | [ENT-1F.png](https://drive.google.com/file/d/1Rhi-1oRjcUjz_pJZ-EXTW99vIiWhrLv0/view) | Có sprite |
| `ENT-1G` | Paintings | [ENT-1G.png](https://drive.google.com/file/d/1EG9mdZAVa-YMTlhN9KV8GXGxdVMXB1cr/view) | Có sprite |
| `ENT-2A` | Clump | [ENT-2A.png](https://drive.google.com/file/d/1vV9uwnn-yTNwj2Tu7Owjdnh0KtVnNdaa/view) | Có sprite |
| `ENT-2B` | Hound | [ENT-2B.png](https://drive.google.com/file/d/1O8HOPb5hu9z40j0Ct59hMsCaK6elHuCs/view) | Có sprite |
| `ENT-2C` | Smiler | [ENT-2C.png](https://drive.google.com/file/d/1tN2s9n7ZTrFiKKUk08PmGpvNTXT_66fO/view) | Có sprite |
| `ENT-2D` | Skin-Stealer | [ENT-2D.png](https://drive.google.com/file/d/1svH2ohpMy4V6winJMFLyqRSrGud8Tzrb/view) | Có sprite |
| `ENT-2E` | Predatory Window | [ENT-2E.png](https://drive.google.com/file/d/1hLV8gKjCXcB7VnueDDjg9M8CJ_LyezsN/view) | Có sprite |
| `ENT-2F` | Biological Pipeline | [ENT-2F.png](https://drive.google.com/file/d/1cKWnaMSs00bpOM9W1pr1DvC1AFhGJQwy/view) | Có sprite |
| `ENT-3A` | Deathmoth | [ENT-3A.png](https://drive.google.com/file/d/1YQP_PgLtPeV2bWedhS_8QK_DUUMs1CyX/view) | Có sprite |
| `ENT-3B` | Wretch | [ENT-3B.png](https://drive.google.com/file/d/1AeN0HOtHMoNbYWnHp8w6o8NEgLLcalHM/view) | Có sprite |
| `ENT-3C` | Skin-Stealer | [ENT-3C.png](https://drive.google.com/file/d/1RkGccT8DeWOZ6dluRDEiINLrnDEYxHmr/view) | Có sprite |
| `ENT-3D` | Cable Mimic | [ENT-3D.png](https://drive.google.com/file/d/17M-G_htGBwKEiXpJWWmHMvggHfbQjKtx/view) | Có sprite |
| `ENT-5A` | The Beast of Level 5 | [ENT-5A.png](https://drive.google.com/file/d/1AwSmNRVUtngIJo-hvhmMpMiZJNv7FXt3/view) | Có sprite |
| `ENT-5B` | Predatory Window | [ENT-5B.png](https://drive.google.com/file/d/1j-zfsc1_L405kwZRLTKFogzXirENweZ8/view) | Có sprite |
| `ENT-5C` | Skin-Stealer | [ENT-5C.png](https://drive.google.com/file/d/1uKKAIqD0YjSsyHqorFKUvPYJe5rtmEwI/view) | Có sprite |
| `ENT-5D` | Hound | [ENT-5D.png](https://drive.google.com/file/d/1ppO0FK43WMMjszfjZFJy_Rr6KvI6kWLp/view) | Có sprite |
| `ENT-5E` | Hotel Corpse Lure | [ENT-5E.png](https://drive.google.com/file/d/14Sl7i6YNFCSlZV8jGIsQCAwqar4k1lKe/view) | Có sprite |
| `ENT-R01` | Jeff the Killer | [ENT-R01.png](https://drive.google.com/file/d/1_9uNtlk1TGBF6iTUew_IzE2GENflXc4C/view) | Có sprite |
| `ENT-R02` | Jane the Killer | [ENT-R02.png](https://drive.google.com/file/d/1-9yGgrlNx7RPCw2QmmtFbzG_tjGibHNK/view) | Có sprite |

## Runtime

Game dùng manifest Drive để đổi `Entity ID` thành `fileId`, sau đó tải sprite trực tiếp cho lớp Entity trong Snapshot. Các Entity chưa có mục trong manifest sẽ không có sprite Drive để hiển thị.

Nguồn canon Entity vẫn là `01_WORLD/entity.md`; file này chỉ theo dõi asset hình ảnh hiện có.
