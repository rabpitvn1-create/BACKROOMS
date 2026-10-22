# Level 0: 37 fixed environment snapshots

Source Canon: `LEVEL_0_EIGHT_ZONES_CANON.md` and `LEVEL_0_TWENTY_NINE_SUBZONES_CANON.md`, author confirmed 2026-10-18. These are game Canon; no external wiki was substituted.

Generated with the built-in image_gen tool. Full prompts and reference relationships: `art/level0-generation-prompts.json`. Deliverables are in `app/src/main/assets/level_snapshots/`. No source PNGs or discarded variants are packaged. WebP compression changes encoding only.

Drive: https://drive.google.com/drive/folders/1BVhYAe5GdgaZBObK6_lH3LT9oNqEsQkL (Novel/new level 0).

## Runtime contract

- Schema v5 requires a validated `rooms[].environmentId`. It is an environment identity, never a mini-map index or route rule.
- Core projects only the current room identity and numeric Canon references; pending MOVE keeps the old identity until commit. GM receives the staged destination environment.
- One ID selects exactly one image. Turns, action labels, movement counters, SEARCH, DEVICE and reload do not rotate images. All other existing node snapshots are also fixed to their first dedicated frame.
- Before a script exists, the unassigned Level 0 lobby uses its fixed general background. A failed image load does not substitute an image of another area.
- Client-supplied scene fields are discarded by CoreClientProjection. Unknown IDs are rejected, never converted to paths.
- New game/current schema only. No old-save migration or compatibility adapter.

## Visual review

Each selected image was inspected for its Canon subject and continuous foreground standing floor at both left and right. Failed house/window/wood-floor variants and collage outputs were rejected and replaced. Silhouettes and face-like stains are atmospheric imagery, not a Core entity/NPC spawn. A static image cannot prove an audible or temporal phenomenon. Live Android WebView and AI-generated scene prose still require device acceptance.

## Files

| Canon | ID | File | Bytes |
| --- | --- | --- | ---: |
| 1 Vàng Úa | `yellow_rot` | `level_00__zone_01__yellow_rot.webp` | 243476 |
| 2 Đèn Chết | `deadlight` | `level_00__zone_02__deadlight.webp` | 257984 |
| 3 Thảm Sũng | `wetfloor` | `level_00__zone_03__wetfloor.webp` | 244180 |
| 4 Lối Câm | `silent_hall` | `level_00__zone_04__silent_hall.webp` | 246976 |
| 5 Trần Rỗng | `hollow_ceiling` | `level_00__zone_05__hollow_ceiling.webp` | 257088 |
| 6 Hồi Ảnh | `afterimage` | `level_00__zone_06__afterimage.webp` | 245390 |
| 7 Vòng Lặp | `the_loop` | `level_00__zone_07__the_loop.webp` | 244894 |
| 8 Rạn Nứt | `the_fracture` | `level_00__zone_08__the_fracture.webp` | 260154 |
| 1.1 Phòng Mốc | `phong_moc` | `level_00__zone_01__sub_01__phong_moc.webp` | 254668 |
| 1.2 Hành Lang Dài | `hanh_lang_dai` | `level_00__zone_01__sub_02__hanh_lang_dai.webp` | 251830 |
| 1.3 Góc Khuất | `goc_khuat` | `level_00__zone_01__sub_03__goc_khuat.webp` | 257202 |
| 1.4 Phòng Trống | `phong_trong` | `level_00__zone_01__sub_04__phong_trong.webp` | 253792 |
| 2.1 Phòng Chớp | `phong_chop` | `level_00__zone_02__sub_01__phong_chop.webp` | 257800 |
| 2.2 Hành Lang Tối | `hanh_lang_toi` | `level_00__zone_02__sub_02__hanh_lang_toi.webp` | 240938 |
| 2.3 Trạm Điện | `tram_dien` | `level_00__zone_02__sub_03__tram_dien.webp` | 257326 |
| 3.1 Vũng Đen | `vung_den` | `level_00__zone_03__sub_01__vung_den.webp` | 256718 |
| 3.2 Thảm Phồng | `tham_phong` | `level_00__zone_03__sub_02__tham_phong.webp` | 253800 |
| 3.3 Phòng Ngập | `phong_ngap` | `level_00__zone_03__sub_03__phong_ngap.webp` | 241444 |
| 3.4 Góc Thối | `goc_thoi` | `level_00__zone_03__sub_04__goc_thoi.webp` | 250634 |
| 4.1 Hành Lang Câm | `hanh_lang_cam` | `level_00__zone_04__sub_01__hanh_lang_cam.webp` | 249072 |
| 4.2 Phòng Thì Thầm | `phong_thi_tham` | `level_00__zone_04__sub_02__phong_thi_tham.webp` | 246682 |
| 4.3 Ngã Ba Chết | `nga_ba_chet` | `level_00__zone_04__sub_03__nga_ba_chet.webp` | 248146 |
| 5.1 Trần Vỡ | `tran_vo` | `level_00__zone_05__sub_01__tran_vo.webp` | 244570 |
| 5.2 Khoang Kỹ Thuật | `khoang_ky_thuat` | `level_00__zone_05__sub_02__khoang_ky_thuat.webp` | 246160 |
| 5.3 Giếng Trần | `gieng_tran` | `level_00__zone_05__sub_03__gieng_tran.webp` | 252720 |
| 5.4 Phòng Treo | `phong_treo` | `level_00__zone_05__sub_04__phong_treo.webp` | 246352 |
| 6.1 Phòng Ký Ức | `phong_ky_uc` | `level_00__zone_06__sub_01__phong_ky_uc.webp` | 250336 |
| 6.2 Tường Mặt | `tuong_mat` | `level_00__zone_06__sub_02__tuong_mat.webp` | 244450 |
| 6.3 Phòng Tiếng Gọi | `phong_tieng_goi` | `level_00__zone_06__sub_03__phong_tieng_goi.webp` | 242998 |
| 7.1 Hành Lang Lặp | `hanh_lang_lap` | `level_00__zone_07__sub_01__hanh_lang_lap.webp` | 244668 |
| 7.2 Phòng Bản Sao | `phong_ban_sao` | `level_00__zone_07__sub_02__phong_ban_sao.webp` | 239406 |
| 7.3 Ngã Tư Vô Tận | `nga_tu_vo_tan` | `level_00__zone_07__sub_03__nga_tu_vo_tan.webp` | 252098 |
| 7.4 Phòng Đảo | `phong_dao` | `level_00__zone_07__sub_04__phong_dao.webp` | 251024 |
| 8.1 Tường Nứt | `tuong_nut` | `level_00__zone_08__sub_01__tuong_nut.webp` | 245230 |
| 8.2 Sàn Lệch | `san_lech` | `level_00__zone_08__sub_02__san_lech.webp` | 258708 |
| 8.3 Khe Tối | `khe_toi` | `level_00__zone_08__sub_03__khe_toi.webp` | 257916 |
| 8.4 Phòng Giao Thoa | `phong_giao_thoa` | `level_00__zone_08__sub_04__phong_giao_thoa.webp` | 256566 |

Hashes and dimensions are recorded in `level_snapshots/level0-environments.json`.

## Verification

Local: 18 targeted Node tests PASS (snapshot routing, native renderer, image-load failure, transition behavior). 37 unique WebP files decoded and checked for landscape dimensions and approximately 250 kB size (239406–260154 bytes). `git diff --check` PASS. JVM/Android checks run on the PR CI; no local Gradle/Android SDK is installed in this workspace.
