"""Patch only the recovered web travel constants and their player-facing text."""
import struct
import subprocess
from pathlib import Path


def patch(name, data):
    if name == 'com/rabpit/backroom/core/LevelTravelEngine.class':
        root = Path(__file__).resolve().parents[1]
        # ASM ships with the existing dex2jar tools; no extra dependency is needed.
        data = subprocess.run([
            'java', '-cp', str(root / 'build/web-engine-tools/dex-tools-v2.4/lib/*'),
            str(root / 'scripts/PatchLevelTravel.java'),
        ], input=data, stdout=subprocess.PIPE, check=True).stdout
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
