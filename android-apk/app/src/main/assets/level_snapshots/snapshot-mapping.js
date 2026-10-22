/* Static offline snapshot mapping for the game environment registry. */
(function(root){
"use strict";
const images={
  "0": [
    "level_00__liminal_hall.webp",
    "level_00__snapshot_001.webp",
    "level_00__snapshot_002.webp",
    "level_00__snapshot_003.webp",
    "level_00__snapshot_004.webp",
    "level_00__snapshot_005.webp",
    "level_00__snapshot_006.webp",
    "level_00__snapshot_007.webp"
  ],
  "1": [
    "level_01__snapshot_001.webp",
    "level_01__snapshot_002.webp",
    "level_01__snapshot_003.webp"
  ],
  "2": [
    "level_02__snapshot_001.webp",
    "level_02__snapshot_002.webp",
    "level_02__snapshot_003.webp"
  ],
  "3": [
    "level_03__snapshot_001.webp",
    "level_03__snapshot_002.webp",
    "level_03__snapshot_003.webp"
  ],
  "4": [
    "level_04__snapshot_001.webp",
    "level_04__snapshot_002.webp",
    "level_04__snapshot_003.webp"
  ],
  "5": [
    "level_05__snapshot_001.webp",
    "level_05__snapshot_002.webp",
    "level_05__snapshot_003.webp"
  ],
  "6": [
    "level_06__snapshot_001.webp",
    "level_06__snapshot_002.webp",
    "level_06__snapshot_003.webp"
  ],
  "0.1": [
    "level_00p1__snapshot_001.webp",
    "level_00p1__snapshot_002.webp",
    "level_00p1__snapshot_003.webp"
  ],
  "0.2": [
    "level_00p2__snapshot_001.webp",
    "level_00p2__snapshot_002.webp",
    "level_00p2__snapshot_003.webp"
  ],
  "0.5": [
    "level_00p5__snapshot_001.webp",
    "level_00p5__snapshot_002.webp",
    "level_00p5__snapshot_003.webp"
  ],
  "0.7": [
    "level_00p7__snapshot_001.webp",
    "level_00p7__snapshot_002.webp",
    "level_00p7__snapshot_003.webp"
  ],
  "manila_room": [
    "area_00__manila_room__snapshot_001.webp",
    "area_00__manila_room__snapshot_002.webp",
    "area_00__manila_room__snapshot_003.webp"
  ],
  "the_torment": [
    "area_00__the_torment__snapshot_001.webp",
    "area_00__the_torment__snapshot_002.webp",
    "area_00__the_torment__snapshot_003.webp"
  ],
  "red_rooms": [
    "area_00__red_rooms__snapshot_001.webp",
    "area_00__red_rooms__snapshot_002.webp",
    "area_00__red_rooms__snapshot_003.webp"
  ],
  "dullness": [
    "area_00__dullness__snapshot_001.webp",
    "area_00__dullness__snapshot_002.webp",
    "area_00__dullness__snapshot_003.webp"
  ],
  "epsilon": [
    "area_00__epsilon__snapshot_001.webp",
    "area_00__epsilon__snapshot_002.webp",
    "area_00__epsilon__snapshot_003.webp"
  ],
  "ls_2": [
    "area_00__ls_2__reference_snapshot_001.webp",
    "area_00__ls_2__reference_snapshot_002.webp",
    "area_00__ls_2__reference_snapshot_003.webp"
  ],
  "1.2": [
    "level_01p2__snapshot_001.webp",
    "level_01p2__snapshot_002.webp",
    "level_01p2__snapshot_003.webp"
  ],
  "1.5": [
    "level_01p5__snapshot_001.webp"
  ],
  "traders_vault": [
    "area_01__traders_vault__reference_snapshot_001.webp"
  ],
  "base_alpha": [
    "area_01__base_alpha__snapshot_001.webp"
  ],
  "office_space_el3a": [
    "area_02__office_space_el3a__reference_snapshot_001.webp"
  ],
  "office_market": [
    "area_04__the_office_market__snapshot_001.webp"
  ],
  "5.1": [
    "level_05p1__snapshot_001.webp"
  ],
  "6.1": [
    "level_06p1__snapshot_001.webp"
  ]
};
const environments={
  "yellow_rot": {
    "file": "level_00__zone_01__yellow_rot.webp",
    "name": "Vàng Úa",
    "zoneId": "yellow_rot"
  },
  "deadlight": {
    "file": "level_00__zone_02__deadlight.webp",
    "name": "Đèn Chết",
    "zoneId": "deadlight"
  },
  "wetfloor": {
    "file": "level_00__zone_03__wetfloor.webp",
    "name": "Thảm Sũng",
    "zoneId": "wetfloor"
  },
  "silent_hall": {
    "file": "level_00__zone_04__silent_hall.webp",
    "name": "Lối Câm",
    "zoneId": "silent_hall"
  },
  "hollow_ceiling": {
    "file": "level_00__zone_05__hollow_ceiling.webp",
    "name": "Trần Rỗng",
    "zoneId": "hollow_ceiling"
  },
  "afterimage": {
    "file": "level_00__zone_06__afterimage.webp",
    "name": "Hồi Ảnh",
    "zoneId": "afterimage"
  },
  "the_loop": {
    "file": "level_00__zone_07__the_loop.webp",
    "name": "Vòng Lặp",
    "zoneId": "the_loop"
  },
  "the_fracture": {
    "file": "level_00__zone_08__the_fracture.webp",
    "name": "Rạn Nứt",
    "zoneId": "the_fracture"
  },
  "phong_moc": {
    "file": "level_00__zone_01__sub_01__phong_moc.webp",
    "name": "Phòng Mốc",
    "zoneId": "yellow_rot"
  },
  "hanh_lang_dai": {
    "file": "level_00__zone_01__sub_02__hanh_lang_dai.webp",
    "name": "Hành Lang Dài",
    "zoneId": "yellow_rot"
  },
  "goc_khuat": {
    "file": "level_00__zone_01__sub_03__goc_khuat.webp",
    "name": "Góc Khuất",
    "zoneId": "yellow_rot"
  },
  "phong_trong": {
    "file": "level_00__zone_01__sub_04__phong_trong.webp",
    "name": "Phòng Trống",
    "zoneId": "yellow_rot"
  },
  "phong_chop": {
    "file": "level_00__zone_02__sub_01__phong_chop.webp",
    "name": "Phòng Chớp",
    "zoneId": "deadlight"
  },
  "hanh_lang_toi": {
    "file": "level_00__zone_02__sub_02__hanh_lang_toi.webp",
    "name": "Hành Lang Tối",
    "zoneId": "deadlight"
  },
  "tram_dien": {
    "file": "level_00__zone_02__sub_03__tram_dien.webp",
    "name": "Trạm Điện",
    "zoneId": "deadlight"
  },
  "vung_den": {
    "file": "level_00__zone_03__sub_01__vung_den.webp",
    "name": "Vũng Đen",
    "zoneId": "wetfloor"
  },
  "tham_phong": {
    "file": "level_00__zone_03__sub_02__tham_phong.webp",
    "name": "Thảm Phồng",
    "zoneId": "wetfloor"
  },
  "phong_ngap": {
    "file": "level_00__zone_03__sub_03__phong_ngap.webp",
    "name": "Phòng Ngập",
    "zoneId": "wetfloor"
  },
  "goc_thoi": {
    "file": "level_00__zone_03__sub_04__goc_thoi.webp",
    "name": "Góc Thối",
    "zoneId": "wetfloor"
  },
  "hanh_lang_cam": {
    "file": "level_00__zone_04__sub_01__hanh_lang_cam.webp",
    "name": "Hành Lang Câm",
    "zoneId": "silent_hall"
  },
  "phong_thi_tham": {
    "file": "level_00__zone_04__sub_02__phong_thi_tham.webp",
    "name": "Phòng Thì Thầm",
    "zoneId": "silent_hall"
  },
  "nga_ba_chet": {
    "file": "level_00__zone_04__sub_03__nga_ba_chet.webp",
    "name": "Ngã Ba Chết",
    "zoneId": "silent_hall"
  },
  "tran_vo": {
    "file": "level_00__zone_05__sub_01__tran_vo.webp",
    "name": "Trần Vỡ",
    "zoneId": "hollow_ceiling"
  },
  "khoang_ky_thuat": {
    "file": "level_00__zone_05__sub_02__khoang_ky_thuat.webp",
    "name": "Khoang Kỹ Thuật",
    "zoneId": "hollow_ceiling"
  },
  "gieng_tran": {
    "file": "level_00__zone_05__sub_03__gieng_tran.webp",
    "name": "Giếng Trần",
    "zoneId": "hollow_ceiling"
  },
  "phong_treo": {
    "file": "level_00__zone_05__sub_04__phong_treo.webp",
    "name": "Phòng Treo",
    "zoneId": "hollow_ceiling"
  },
  "phong_ky_uc": {
    "file": "level_00__zone_06__sub_01__phong_ky_uc.webp",
    "name": "Phòng Ký Ức",
    "zoneId": "afterimage"
  },
  "tuong_mat": {
    "file": "level_00__zone_06__sub_02__tuong_mat.webp",
    "name": "Tường Mặt",
    "zoneId": "afterimage"
  },
  "phong_tieng_goi": {
    "file": "level_00__zone_06__sub_03__phong_tieng_goi.webp",
    "name": "Phòng Tiếng Gọi",
    "zoneId": "afterimage"
  },
  "hanh_lang_lap": {
    "file": "level_00__zone_07__sub_01__hanh_lang_lap.webp",
    "name": "Hành Lang Lặp",
    "zoneId": "the_loop"
  },
  "phong_ban_sao": {
    "file": "level_00__zone_07__sub_02__phong_ban_sao.webp",
    "name": "Phòng Bản Sao",
    "zoneId": "the_loop"
  },
  "nga_tu_vo_tan": {
    "file": "level_00__zone_07__sub_03__nga_tu_vo_tan.webp",
    "name": "Ngã Tư Vô Tận",
    "zoneId": "the_loop"
  },
  "phong_dao": {
    "file": "level_00__zone_07__sub_04__phong_dao.webp",
    "name": "Phòng Đảo",
    "zoneId": "the_loop"
  },
  "tuong_nut": {
    "file": "level_00__zone_08__sub_01__tuong_nut.webp",
    "name": "Tường Nứt",
    "zoneId": "the_fracture"
  },
  "san_lech": {
    "file": "level_00__zone_08__sub_02__san_lech.webp",
    "name": "Sàn Lệch",
    "zoneId": "the_fracture"
  },
  "khe_toi": {
    "file": "level_00__zone_08__sub_03__khe_toi.webp",
    "name": "Khe Tối",
    "zoneId": "the_fracture"
  },
  "phong_giao_thoa": {
    "file": "level_00__zone_08__sub_04__phong_giao_thoa.webp",
    "name": "Phòng Giao Thoa",
    "zoneId": "the_fracture"
  }
};
// Exact AREA identities from Level0AreaEnvironments; never share a frame across environments.
const areaEnvironments={
  "area_DU_z1": {
    "file": "area_00__dullness__zone_01__area_du_z1.webp",
    "name": "Dãy Tỷ Lệ Trôi",
    "zoneId": "area_DU_z1",
    "nodeId": "dullness"
  },
  "area_DU_z1_s1": {
    "file": "area_00__dullness__zone_01__sub_01__area_du_z1_s1.webp",
    "name": "Ngách Trần Quá Cao",
    "zoneId": "area_DU_z1",
    "nodeId": "dullness"
  },
  "area_DU_z1_s2": {
    "file": "area_00__dullness__zone_01__sub_02__area_du_z1_s2.webp",
    "name": "Phòng Cửa Ngắn",
    "zoneId": "area_DU_z1",
    "nodeId": "dullness"
  },
  "area_DU_z1_s3": {
    "file": "area_00__dullness__zone_01__sub_03__area_du_z1_s3.webp",
    "name": "Mép Tường Co",
    "zoneId": "area_DU_z1",
    "nodeId": "dullness"
  },
  "area_DU_z1_s4": {
    "file": "area_00__dullness__zone_01__sub_04__area_du_z1_s4.webp",
    "name": "Bậc Sàn Rộng",
    "zoneId": "area_DU_z1",
    "nodeId": "dullness"
  },
  "area_DU_z2": {
    "file": "area_00__dullness__zone_02__area_du_z2.webp",
    "name": "Giao Lộ Nhập Khối",
    "zoneId": "area_DU_z2",
    "nodeId": "dullness"
  },
  "area_DU_z2_s1": {
    "file": "area_00__dullness__zone_02__sub_01__area_du_z2_s1.webp",
    "name": "Hai Cửa Chung Khung",
    "zoneId": "area_DU_z2",
    "nodeId": "dullness"
  },
  "area_DU_z2_s2": {
    "file": "area_00__dullness__zone_02__sub_02__area_du_z2_s2.webp",
    "name": "Góc Vách Dính",
    "zoneId": "area_DU_z2",
    "nodeId": "dullness"
  },
  "area_DU_z2_s3": {
    "file": "area_00__dullness__zone_02__sub_03__area_du_z2_s3.webp",
    "name": "Hành Lang Phân Thân",
    "zoneId": "area_DU_z2",
    "nodeId": "dullness"
  },
  "area_DU_z2_s4": {
    "file": "area_00__dullness__zone_02__sub_04__area_du_z2_s4.webp",
    "name": "Nút Thắt Hình Học",
    "zoneId": "area_DU_z2",
    "nodeId": "dullness"
  },
  "area_DU_z3": {
    "file": "area_00__dullness__zone_03__area_du_z3.webp",
    "name": "Vòng Lặp Méo",
    "zoneId": "area_DU_z3",
    "nodeId": "dullness"
  },
  "area_DU_z3_s1": {
    "file": "area_00__dullness__zone_03__sub_01__area_du_z3_s1.webp",
    "name": "Ngã Rẽ Đồng Mẫu",
    "zoneId": "area_DU_z3",
    "nodeId": "dullness"
  },
  "area_DU_z3_s2": {
    "file": "area_00__dullness__zone_03__sub_02__area_du_z3_s2.webp",
    "name": "Phòng Mẫu Phóng To",
    "zoneId": "area_DU_z3",
    "nodeId": "dullness"
  },
  "area_DU_z3_s3": {
    "file": "area_00__dullness__zone_03__sub_03__area_du_z3_s3.webp",
    "name": "Khung Đèn Nhân Bản",
    "zoneId": "area_DU_z3",
    "nodeId": "dullness"
  },
  "area_DU_z3_s4": {
    "file": "area_00__dullness__zone_03__sub_04__area_du_z3_s4.webp",
    "name": "Dãy Cửa Sai Tầm",
    "zoneId": "area_DU_z3",
    "nodeId": "dullness"
  },
  "area_DU_z4": {
    "file": "area_00__dullness__zone_04__area_du_z4.webp",
    "name": "Đầu Mút Mất Thước",
    "zoneId": "area_DU_z4",
    "nodeId": "dullness"
  },
  "area_DU_z4_s1": {
    "file": "area_00__dullness__zone_04__sub_01__area_du_z4_s1.webp",
    "name": "Vách Chia Bất Chợt",
    "zoneId": "area_DU_z4",
    "nodeId": "dullness"
  },
  "area_DU_z4_s2": {
    "file": "area_00__dullness__zone_04__sub_02__area_du_z4_s2.webp",
    "name": "Điểm Khâu Thảm",
    "zoneId": "area_DU_z4",
    "nodeId": "dullness"
  },
  "area_DU_z4_s3": {
    "file": "area_00__dullness__zone_04__sub_03__area_du_z4_s3.webp",
    "name": "Hộp Đèn Lệch",
    "zoneId": "area_DU_z4",
    "nodeId": "dullness"
  },
  "area_DU_z4_s4": {
    "file": "area_00__dullness__zone_04__sub_04__area_du_z4_s4.webp",
    "name": "Ngõ Hẹp Khó Ước",
    "zoneId": "area_DU_z4",
    "nodeId": "dullness"
  },
  "area_EP_z1": {
    "file": "area_00__epsilon__zone_01__area_ep_z1.webp",
    "name": "Sảnh Cao Vàng",
    "zoneId": "area_EP_z1",
    "nodeId": "epsilon"
  },
  "area_EP_z1_s1": {
    "file": "area_00__epsilon__zone_01__sub_01__area_ep_z1_s1.webp",
    "name": "Vòm Đèn Xa",
    "zoneId": "area_EP_z1",
    "nodeId": "epsilon"
  },
  "area_EP_z1_s2": {
    "file": "area_00__epsilon__zone_01__sub_02__area_ep_z1_s2.webp",
    "name": "Thảm Giữa Sảnh",
    "zoneId": "area_EP_z1",
    "nodeId": "epsilon"
  },
  "area_EP_z1_s3": {
    "file": "area_00__epsilon__zone_01__sub_03__area_ep_z1_s3.webp",
    "name": "Chân Cột Dài",
    "zoneId": "area_EP_z1",
    "nodeId": "epsilon"
  },
  "area_EP_z1_s4": {
    "file": "area_00__epsilon__zone_01__sub_04__area_ep_z1_s4.webp",
    "name": "Góc Sảnh Lùi",
    "zoneId": "area_EP_z1",
    "nodeId": "epsilon"
  },
  "area_EP_z2": {
    "file": "area_00__epsilon__zone_02__area_ep_z2.webp",
    "name": "Hệ Cầu Treo",
    "zoneId": "area_EP_z2",
    "nodeId": "epsilon"
  },
  "area_EP_z2_s1": {
    "file": "area_00__epsilon__zone_02__sub_01__area_ep_z2_s1.webp",
    "name": "Nhịp Cầu Trống",
    "zoneId": "area_EP_z2",
    "nodeId": "epsilon"
  },
  "area_EP_z2_s2": {
    "file": "area_00__epsilon__zone_02__sub_02__area_ep_z2_s2.webp",
    "name": "Sàn Tầng Trung",
    "zoneId": "area_EP_z2",
    "nodeId": "epsilon"
  },
  "area_EP_z2_s3": {
    "file": "area_00__epsilon__zone_02__sub_03__area_ep_z2_s3.webp",
    "name": "Giao Cầu Bất Đối",
    "zoneId": "area_EP_z2",
    "nodeId": "epsilon"
  },
  "area_EP_z2_s4": {
    "file": "area_00__epsilon__zone_02__sub_04__area_ep_z2_s4.webp",
    "name": "Bệ Lan Can Mòn",
    "zoneId": "area_EP_z2",
    "nodeId": "epsilon"
  },
  "area_EP_z3": {
    "file": "area_00__epsilon__zone_03__area_ep_z3.webp",
    "name": "Mạng Đường Hầm",
    "zoneId": "area_EP_z3",
    "nodeId": "epsilon"
  },
  "area_EP_z3_s1": {
    "file": "area_00__epsilon__zone_03__sub_01__area_ep_z3_s1.webp",
    "name": "Hầm Đèn Trùng",
    "zoneId": "area_EP_z3",
    "nodeId": "epsilon"
  },
  "area_EP_z3_s2": {
    "file": "area_00__epsilon__zone_03__sub_02__area_ep_z3_s2.webp",
    "name": "Khúc Hầm Dốc",
    "zoneId": "area_EP_z3",
    "nodeId": "epsilon"
  },
  "area_EP_z3_s3": {
    "file": "area_00__epsilon__zone_03__sub_03__area_ep_z3_s3.webp",
    "name": "Giếng Thông Tầng",
    "zoneId": "area_EP_z3",
    "nodeId": "epsilon"
  },
  "area_EP_z3_s4": {
    "file": "area_00__epsilon__zone_03__sub_04__area_ep_z3_s4.webp",
    "name": "Ngõ Đổi Độ Dài",
    "zoneId": "area_EP_z3",
    "nodeId": "epsilon"
  },
  "area_EP_z4": {
    "file": "area_00__epsilon__zone_04__area_ep_z4.webp",
    "name": "Thang Đa Cao Độ",
    "zoneId": "area_EP_z4",
    "nodeId": "epsilon"
  },
  "area_EP_z4_s1": {
    "file": "area_00__epsilon__zone_04__sub_01__area_ep_z4_s1.webp",
    "name": "Vế Thang Cụt",
    "zoneId": "area_EP_z4",
    "nodeId": "epsilon"
  },
  "area_EP_z4_s2": {
    "file": "area_00__epsilon__zone_04__sub_02__area_ep_z4_s2.webp",
    "name": "Chiếu Nghỉ Treo",
    "zoneId": "area_EP_z4",
    "nodeId": "epsilon"
  },
  "area_EP_z4_s3": {
    "file": "area_00__epsilon__zone_04__sub_03__area_ep_z4_s3.webp",
    "name": "Hành Lang Trên Đầu",
    "zoneId": "area_EP_z4",
    "nodeId": "epsilon"
  },
  "area_EP_z4_s4": {
    "file": "area_00__epsilon__zone_04__sub_04__area_ep_z4_s4.webp",
    "name": "Vị Trí Đèn Ngược",
    "zoneId": "area_EP_z4",
    "nodeId": "epsilon"
  },
  "area_LS_z1": {
    "file": "area_00__ls_2__zone_01__area_ls_z1.webp",
    "name": "Thư Viện Mép Trống",
    "zoneId": "area_LS_z1",
    "nodeId": "ls_2"
  },
  "area_LS_z1_s1": {
    "file": "area_00__ls_2__zone_01__sub_01__area_ls_z1_s1.webp",
    "name": "Kệ Số Bỏ Trống",
    "zoneId": "area_LS_z1",
    "nodeId": "ls_2"
  },
  "area_LS_z1_s2": {
    "file": "area_00__ls_2__zone_01__sub_02__area_ls_z1_s2.webp",
    "name": "Tủ Nhãn Đảo",
    "zoneId": "area_LS_z1",
    "nodeId": "ls_2"
  },
  "area_LS_z1_s3": {
    "file": "area_00__ls_2__zone_01__sub_03__area_ls_z1_s3.webp",
    "name": "Lối Giấy Than",
    "zoneId": "area_LS_z1",
    "nodeId": "ls_2"
  },
  "area_LS_z1_s4": {
    "file": "area_00__ls_2__zone_01__sub_04__area_ls_z1_s4.webp",
    "name": "Phòng Ô Đứt Quãng",
    "zoneId": "area_LS_z1",
    "nodeId": "ls_2"
  },
  "area_LS_z2": {
    "file": "area_00__ls_2__zone_02__area_ls_z2.webp",
    "name": "Xưởng Đồng Hồ Rỗng",
    "zoneId": "area_LS_z2",
    "nodeId": "ls_2"
  },
  "area_LS_z2_s1": {
    "file": "area_00__ls_2__zone_02__sub_01__area_ls_z2_s1.webp",
    "name": "Bàn Bánh Răng",
    "zoneId": "area_LS_z2",
    "nodeId": "ls_2"
  },
  "area_LS_z2_s2": {
    "file": "area_00__ls_2__zone_02__sub_02__area_ls_z2_s2.webp",
    "name": "Trục Không Kim",
    "zoneId": "area_LS_z2",
    "nodeId": "ls_2"
  },
  "area_LS_z2_s3": {
    "file": "area_00__ls_2__zone_02__sub_03__area_ls_z2_s3.webp",
    "name": "Khoang Nhịp Gõ",
    "zoneId": "area_LS_z2",
    "nodeId": "ls_2"
  },
  "area_LS_z2_s4": {
    "file": "area_00__ls_2__zone_02__sub_04__area_ls_z2_s4.webp",
    "name": "Phòng Vành Răng",
    "zoneId": "area_LS_z2",
    "nodeId": "ls_2"
  },
  "area_LS_z3": {
    "file": "area_00__ls_2__zone_03__area_ls_z3.webp",
    "name": "Hành Lang Sai Số",
    "zoneId": "area_LS_z3",
    "nodeId": "ls_2"
  },
  "area_LS_z3_s1": {
    "file": "area_00__ls_2__zone_03__sub_01__area_ls_z3_s1.webp",
    "name": "Sàn Tấm Chẵn",
    "zoneId": "area_LS_z3",
    "nodeId": "ls_2"
  },
  "area_LS_z3_s2": {
    "file": "area_00__ls_2__zone_03__sub_02__area_ls_z3_s2.webp",
    "name": "Góc Đếm Lệch",
    "zoneId": "area_LS_z3",
    "nodeId": "ls_2"
  },
  "area_LS_z3_s3": {
    "file": "area_00__ls_2__zone_03__sub_03__area_ls_z3_s3.webp",
    "name": "Lối Phản Chiếu Chéo",
    "zoneId": "area_LS_z3",
    "nodeId": "ls_2"
  },
  "area_LS_z3_s4": {
    "file": "area_00__ls_2__zone_03__sub_04__area_ls_z3_s4.webp",
    "name": "Bệ Bốn Hướng",
    "zoneId": "area_LS_z3",
    "nodeId": "ls_2"
  },
  "area_LS_z4": {
    "file": "area_00__ls_2__zone_04__area_ls_z4.webp",
    "name": "Nút Chốt Không Tên",
    "zoneId": "area_LS_z4",
    "nodeId": "ls_2"
  },
  "area_LS_z4_s1": {
    "file": "area_00__ls_2__zone_04__sub_01__area_ls_z4_s1.webp",
    "name": "Khung Chốt Đôi",
    "zoneId": "area_LS_z4",
    "nodeId": "ls_2"
  },
  "area_LS_z4_s2": {
    "file": "area_00__ls_2__zone_04__sub_02__area_ls_z4_s2.webp",
    "name": "Nền Mốc Chồng",
    "zoneId": "area_LS_z4",
    "nodeId": "ls_2"
  },
  "area_LS_z4_s3": {
    "file": "area_00__ls_2__zone_04__sub_03__area_ls_z4_s3.webp",
    "name": "Buồng Đối Chiếu",
    "zoneId": "area_LS_z4",
    "nodeId": "ls_2"
  },
  "area_LS_z4_s4": {
    "file": "area_00__ls_2__zone_04__sub_04__area_ls_z4_s4.webp",
    "name": "Ngõ Số Không",
    "zoneId": "area_LS_z4",
    "nodeId": "ls_2"
  }
};
const specialParents={
  "manila_room": 0,
  "the_torment": 0,
  "red_rooms": 0,
  "dullness": 0,
  "epsilon": 0,
  "ls_2": 0,
  "traders_vault": 1,
  "base_alpha": 1,
  "office_space_el3a": 2,
  "office_market": 4
};
const assetPrefix="file:///android_asset/level_snapshots/";
const owns=(record,key)=>Object.prototype.hasOwnProperty.call(record,key);
function nodeId(state){
  const level=state&&state.level;
  if(level&&level.id!=null&&owns(images,String(level.id).trim()))return String(level.id).trim();
  if(level&&level.number!=null&&owns(images,String(level.number).trim()))return String(level.number).trim();
  return "0";
}
const satelliteCodes=Object.freeze({
  "0.1":"01","0.2":"02","0.5":"05","0.7":"07",
  "manila_room":"MN","the_torment":"TM","red_rooms":"RR"
});
function environment(state){
  const node=nodeId(state);
  if(!state||!state.scene||state.scene.nodeId!==node)return null;
  const e=state.scene.environment;
  const id=e&&e.id;
  if(typeof id!=="string")return null;
  if(owns(areaEnvironments,id)){
    const current=areaEnvironments[id];
    return current.nodeId===node?{id:id,...current}:null;
  }
  if(node==="0")return owns(environments,id)?{id:id,...environments[id]}:null;
  if(!owns(satelliteCodes,node))return null;
  const prefix="sat_"+satelliteCodes[node]+"_z";
  const match=id.startsWith(prefix) && id.slice(prefix.length).match(/^([1-4])(?:_s([1-4]))?$/);
  if(!match)return null;
  // Fixed identity-to-image mapping. Reuse only frames belonging to this exact
  // Canon node; never rotate by turn, location, counter or random choice.
  const z=Number(match[1]),sub=match[2]?Number(match[2]):0;
  const variants=images[node];
  const file=variants[(z+sub-1)%variants.length];
  return {id:id,name:typeof e.name==="string"?e.name:"",
    zoneId:prefix+z,file:file};
}
function sceneKey(state){
  const current=environment(state);
  return nodeId(state)+"|"+(current?current.id:"unassigned");
}
function resolve(state){
  const id=nodeId(state);
  const parent=owns(specialParents,id)?specialParents[id]:parseInt(id,10);
  const variants=images[id];
  const dedicated=variants.length>0;
  const current=environment(state);
  // Snapshot identity follows Core's exact environment only. Never rotate by
  // turn, action, room name, mini index, hash, or visual progress counters.
  const index=dedicated?0:-1;
  const file=current?current.file:(dedicated?variants[0]:"level_"+parent+".webp");
  return {nodeId:id,parent:parent,file:file,src:assetPrefix+file,
    environmentId:current?current.id:null,environmentName:current?current.name:null,
    hasDedicatedImage:!!current||dedicated,fallback:!current&&!dedicated,
    variantIndex:index,variantCount:1};
}
const api=Object.freeze({nodeId:nodeId,sceneKey:sceneKey,resolve:resolve,
  environmentIds:function(node){
    if(node==null||node==="0")return Object.keys(environments);
    return Object.keys(areaEnvironments).filter(id=>areaEnvironments[id].nodeId===node);
  },
  environmentFile:function(id){
    if(owns(environments,id))return environments[id].file;
    return owns(areaEnvironments,id)?areaEnvironments[id].file:null;
  },
  knownIds:function(){return Object.keys(images);},
  filesFor:function(id){return owns(images,id)?images[id].slice():[];}});
root.BackroomSnapshotAssets=api;
if(typeof module==="object"&&module&&module.exports)module.exports=api;
})(typeof window!=="undefined"?window:globalThis);
