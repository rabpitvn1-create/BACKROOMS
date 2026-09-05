#!/usr/bin/env python3
"""Build the Inventory V2 LiteRT intent corpus.

LiteRT classifies user intent only. It never creates item ownership and never needs a
new label when a new catalog item is added.
"""
from __future__ import annotations

import csv
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent
OUTPUT = ROOT / "intent_dataset.csv"

ITEMS = ["chai Almond Water", "băng cứu thương", "đèn pin", "viên pin", "hộp khẩu phần", "mảnh kim loại"]
PEOPLE = ["Iris", "Syvial", "Lucia", "An Nhiên"]

TEMPLATES = {
    "DISCARD_ITEM": [
        "vứt {i} đi", "Kai bỏ {i} đi", "quăng {i}", "ném {i} đi", "loại bỏ {i}",
    ],
    "USE_ITEM": [
        "Kai dùng {i}", "sử dụng {i}", "uống {i}", "ăn {i}", "kích hoạt {i}",
    ],
    "TRANSFER_ITEM": [
        "Kai đưa {i} cho {p}", "trao {i} cho {p}", "chuyển {i} sang {p}", "giao {i} cho {p}",
    ],
    "GIVE_AND_USE_ITEM": [
        "Kai đưa {i} cho {p} dùng", "đưa {p} {i} để dùng", "trao {i} cho {p} rồi bảo dùng", "đưa {i} cho {p} uống",
    ],
    "REQUEST_ITEM": [
        "Kai xin {i} từ {p}", "xin {p} đưa {i} cho Kai", "lấy {i} từ kho của {p}", "yêu cầu {p} chuyển {i} cho Kai",
    ],
    "EQUIP_ITEM": [
        "trang bị {i}", "Kai đeo {i}", "Kai cầm {i} làm trang bị", "gắn {i} vào ô trang bị",
    ],
    "UNEQUIP_ITEM": [
        "tháo {i}", "bỏ trang bị {i}", "gỡ {i} khỏi ô trang bị", "Kai cất {i} đang trang bị",
    ],
    "OMNIVAULT_STORE": [
        "cất {i} vào Omnivault", "đưa {i} vào Nhẫn Vạn Tàng", "lưu {i} trong nhẫn", "cho {i} vào kho Omnivault",
    ],
    "OMNIVAULT_WITHDRAW": [
        "lấy {i} ra khỏi Omnivault", "rút {i} từ Nhẫn Vạn Tàng", "lấy lại {i} đã cất", "đưa {i} từ nhẫn về kho Kai",
    ],
    "OMNIVAULT_RESTORE": [
        "hoàn nguyên {i}", "khôi phục trang bị {i}", "Omnivault sửa {i} về trạng thái tốt nhất", "restore trang bị {i}",
    ],
}

FIXED = {
    "INVENTORY_QUERY": ["xem kho đồ", "kiểm tra inventory", "Kai đang có vật phẩm gì", "mở hành trang", "xem đồ của Iris"],
    "OMNIVAULT_QUERY": ["xem Omnivault", "nhẫn đang cất gì", "kiểm tra kho Omnivault", "xem vật phẩm trong Nhẫn Vạn Tàng"],
    "PARTY_JOIN_REQUEST": ["mời Iris vào party", "cho Lucia gia nhập đội", "đề nghị Syvial đi cùng", "mời An Nhiên tham gia nhóm"],
    "PARTY_REMOVE": ["Iris rời party", "loại Lucia khỏi đội", "cho Syvial rời nhóm", "bỏ Iris khỏi party"],
    "PARTY_QUERY": ["party có ai", "xem đội hình", "kiểm tra thành viên nhóm", "ai đang đi cùng Kai"],
    "CHARACTER_QUERY": ["xem thông tin Iris", "mở hồ sơ Kai", "kiểm tra nhân vật Lucia", "xem chi tiết Syvial"],
    "STATUS_QUERY": ["xem status Kai", "Iris đang bị hiệu ứng gì", "kiểm tra tình trạng Lucia", "xem thương tích hiện tại"],
    "NO_ACTION": [
        "Kai nhìn chai nước nằm trên sàn", "Kai nhặt chai nước dưới đất", "lấy chiếc hộp trên bàn", "cầm món đồ vừa thấy lên",
        "đừng vứt băng cứu thương", "Kai chưa dùng đèn pin", "nếu đưa nước cho Iris thì sao", "Kai nhớ đã cất vật vào nhẫn",
        "sao chép vật phẩm bằng Omnivault", "scan vật phẩm bằng nhẫn", "tạo thêm một chai bằng Omnivault", "nhân bản viên pin",
    ],
    "UNKNOWN": [
        "Kai đi tiếp dọc hành lang", "quan sát căn phòng", "nghe tiếng động phía sau", "mở cánh cửa trước mặt",
        "nói chuyện với Iris", "đứng yên chờ", "quay lại lối cũ", "kiểm tra trần nhà",
    ],
}


def expand(template: str) -> list[str]:
    rows = []
    if "{i}" in template and "{p}" in template:
        for item in ITEMS:
            for person in PEOPLE:
                rows.append(template.format(i=item, p=person))
    elif "{i}" in template:
        rows.extend(template.format(i=item) for item in ITEMS)
    elif "{p}" in template:
        rows.extend(template.format(p=person) for person in PEOPLE)
    else:
        rows.append(template)
    return rows


rows: list[tuple[str, str, str, str]] = []
for label, templates in TEMPLATES.items():
    expanded = []
    for template in templates:
        expanded.extend(expand(template))
    # Deterministic split by authored position. Test rows are withheld from training.
    for index, text in enumerate(expanded):
        split = "test" if index % 7 == 0 else "train"
        rows.append((text, label, split, "inventory-v2-template"))

for label, texts in FIXED.items():
    for index, text in enumerate(texts):
        split = "test" if index % 4 == 0 else "train"
        rows.append((text, label, split, "inventory-v2-fixed"))

labels = {label for _, label, _, _ in rows}
expected = {
    "DISCARD_ITEM", "USE_ITEM", "TRANSFER_ITEM", "GIVE_AND_USE_ITEM", "REQUEST_ITEM",
    "EQUIP_ITEM", "UNEQUIP_ITEM", "OMNIVAULT_STORE", "OMNIVAULT_WITHDRAW", "OMNIVAULT_RESTORE",
    "INVENTORY_QUERY", "OMNIVAULT_QUERY", "PARTY_JOIN_REQUEST", "PARTY_REMOVE", "PARTY_QUERY",
    "CHARACTER_QUERY", "STATUS_QUERY", "NO_ACTION", "UNKNOWN",
}
if labels != expected:
    raise SystemExit(f"intent label mismatch: missing={sorted(expected-labels)} extra={sorted(labels-expected)}")

with OUTPUT.open("w", encoding="utf-8", newline="") as handle:
    writer = csv.writer(handle)
    writer.writerow(["text", "label", "split", "source"])
    writer.writerows(rows)

print(f"Wrote {len(rows)} Inventory V2 intent rows to {OUTPUT}")
