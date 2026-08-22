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
| `ENT-R01` | Jeff the Killer | [ENT-R01.png](https://drive.google.com/file/d/1_9uNtlk1TGBF6iTUew_IzE2GENflXc4C/view) | Có sprite |
| `ENT-R02` | Jane the Killer | [ENT-R02.png](https://drive.google.com/file/d/1-9yGgrlNx7RPCw2QmmtFbzG_tjGibHNK/view) | Có sprite |

## Runtime

Game dùng manifest Drive để đổi `Entity ID` thành `fileId`, sau đó tải sprite trực tiếp cho lớp Entity trong Snapshot. Các Entity chưa có mục trong manifest sẽ không có sprite Drive để hiển thị.

Nguồn canon Entity vẫn là `01_WORLD/entity.md`; file này chỉ theo dõi asset hình ảnh hiện có.