"""Patch only the recovered web travel constants and their player-facing text."""
import hashlib
import struct


def patch(name, data):
    if name == 'com/rabpit/backroom/core/LevelTravelEngine.class':
        # Pinned to the recovered class: preserve all receipt/save/transition logic.
        assert hashlib.sha256(data).hexdigest() == '5aa4872fb19c6dfd7645283cf437ee83c6dc8dd96655858393810989a9f31d38'
        edits = [
            (6744, struct.pack('>d', 0.4), struct.pack('>d', 0.5)),
            (9608, b'\x06', b'\x07'),  # exitStreak < 4
            (9665, b'\x06', b'\x07'),  # completed at 4
            (9742, b'\x28', b'\x32'),  # chancePercent = 50
            (9750, b'\x3c', b'\x32'),  # failurePercent = 50
            (9765, b'\x06', b'\x07'),  # requiredStreak = 4
        ]
        for offset, old, new in edits:
            assert data[offset:offset + len(old)] == old
            data = data[:offset] + new + data[offset + len(old):]
    elif name == 'com/rabpit/backroom/core/GameCoreFacade.class':
        # Match UTF-8 entries exactly; constant-pool ordering can vary across JDKs.
        messages = [
            ('Thoát thành công 3/3. Cao Minh đã đến một nơi mới, chưa xác định danh tính. Chuỗi thoát mới: 0/3.',
             'Thoát thành công 4/4. Cao Minh đã no-clip sang một nơi mới, chưa xác định danh tính. Chuỗi thoát mới: 0/4.'),
            ('/3. Cao Minh vẫn ở địa điểm hiện tại.', '/4. Cao Minh vẫn ở địa điểm hiện tại.'),
            ('Lần thử thoát thất bại. Chuỗi trở về 0/3; Cao Minh vẫn ở địa điểm hiện tại.',
             'Lần thử thoát thất bại. Chuỗi trở về 0/4; Cao Minh vẫn ở địa điểm hiện tại.'),
            ('Không gian quanh Cao Minh đột ngột lệch khỏi vị trí cũ. Hắn đã sang một nơi khác, chưa xác định danh tính.',
             'Không gian quanh Cao Minh đột ngột lệch khỏi vị trí cũ. Hắn đã no-clip sang một nơi khác, chưa xác định danh tính.'),
        ]
        for old, new in messages:
            old, new = old.encode('utf-8'), new.encode('utf-8')
            old = struct.pack('>H', len(old)) + old
            new = struct.pack('>H', len(new)) + new
            assert data.count(old) == 1
            data = data.replace(old, new)
    return data
