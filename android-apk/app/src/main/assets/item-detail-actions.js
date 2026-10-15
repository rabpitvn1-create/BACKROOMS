(function () {
  const view = document.getElementById('characterInventoryView');
  const modal = document.getElementById('equipmentDetailModal');
  if (!view || !modal) return;
  const section = document.createElement('section');
  section.innerHTML = '<h3>THAO TÁC</h3><label>Số lượng <input id="itemActionQuantity" type="number" min="1" step="1" value="1" style="width:80px;background:#11171b;color:inherit;border:1px solid #46525a;padding:8px"></label><label id="itemActionTargetLabel" style="display:block;margin:10px 0">Người nhận <select id="itemActionTarget" style="max-width:100%;background:#11171b;color:inherit;padding:8px"></select></label><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px"><button type="button" id="itemActionUse">Dùng</button><button type="button" id="itemActionTransfer">Chuyển</button><button type="button" id="itemActionDrop" class="danger">Bỏ</button></div><p id="itemActionMessage" role="status" style="color:#c7b38b;font-size:12px"></p>';
  modal.querySelector('.equipment-detail-sheet').appendChild(section);
  const q = id => document.getElementById(id);
  let selected = null;
  let dropArmed = false;
  function members() { return state?.partyDetails?.members || []; }
  function update() {
    const locked = busy || !selected || selected.item.equipped === true;
    q('itemActionUse').disabled = locked;
    q('itemActionDrop').disabled = locked;
    q('itemActionTransfer').disabled = locked || !q('itemActionTarget').options.length;
    q('itemActionQuantity').disabled = locked;
    q('itemActionTarget').disabled = locked;
    q('itemActionDrop').textContent = dropArmed ? 'Xác nhận bỏ' : 'Bỏ';
  }
  view.addEventListener('click', event => {
    const card = event.target.closest('[data-item-id]');
    if (!card) return;
    const owner = members().find(member => String(member.id) === view.dataset.characterId);
    const id = card.getAttribute('data-item-id');
    const item = (owner?.inventory || []).find(item => String(item.id) === id) || (owner?.equipmentItems || []).find(item => String(item.id) === id);
    selected = owner && item ? { actorId: owner.id, item } : null;
    dropArmed = false;
    q('itemActionQuantity').value = '1';
    q('itemActionQuantity').max = String(item?.quantity || 1);
    const target = q('itemActionTarget');
    target.replaceChildren();
    members().filter(member => member.id !== owner?.id && member.presence === 'ACTIVE').forEach(member => {
      const option = document.createElement('option');
      option.value = member.id; option.textContent = member.name || member.id; target.appendChild(option);
    });
    q('itemActionMessage').textContent = item?.equipped ? 'Tháo trang bị trước khi thao tác vật phẩm.' : '';
    update();
  });
  function submit(operation) {
    if (busy || !selected) return;
    const quantity = Number(q('itemActionQuantity').value);
    if (!Number.isSafeInteger(quantity) || quantity < 1 || quantity > selected.item.quantity) {
      q('itemActionMessage').textContent = 'Số lượng phải từ 1 đến ' + selected.item.quantity + '.'; return;
    }
    if (!window.Android || typeof Android.itemAction !== 'function') {
      q('itemActionMessage').textContent = 'Không tìm thấy kết nối thao tác item.'; return;
    }
    const targetId = operation === 'TRANSFER' ? q('itemActionTarget').value : undefined;
    if (operation === 'TRANSFER' && !targetId) { q('itemActionMessage').textContent = 'Chọn người nhận.'; return; }
    if (operation === 'DROP' && !dropArmed) {
      dropArmed = true; q('itemActionMessage').textContent = 'Bỏ ' + quantity + ' ' + selected.item.name + '? Nhấn Xác nhận bỏ để tiếp tục.'; update(); return;
    }
    busy = true; dropArmed = false; update(); syncPrimaryActions();
    q('itemActionMessage').textContent = 'Đang xử lý…';
    try { Android.itemAction(JSON.stringify(state), JSON.stringify({ operation, actorId: selected.actorId, itemId: selected.item.id, quantity, targetId })); }
    catch (error) { busy = false; update(); syncPrimaryActions(); q('itemActionMessage').textContent = 'Không gửi được thao tác item.'; }
  }
  q('itemActionUse').addEventListener('click', () => submit('USE'));
  q('itemActionTransfer').addEventListener('click', () => submit('TRANSFER'));
  q('itemActionDrop').addEventListener('click', () => submit('DROP'));
  q('itemActionQuantity').addEventListener('input', () => { dropArmed = false; update(); });
  q('itemActionTarget').addEventListener('change', () => { dropArmed = false; update(); });
  window.backroomItemAction = json => {
    try {
      const result = JSON.parse(json);
      busy = false;
      if (result.handled && result.state) { modal.hidden = true; window.backroomTurn(JSON.stringify(result.state)); }
      else { q('itemActionMessage').textContent = result.reply || ('Không thể thực hiện: ' + (result.error || 'item_action_failed')); }
    } catch (error) { busy = false; q('itemActionMessage').textContent = 'Phản hồi thao tác item không hợp lệ.'; }
    syncPrimaryActions(); update();
  };
})();
