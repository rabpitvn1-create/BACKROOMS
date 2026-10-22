(function () {
  const view = document.getElementById('characterInventoryView');
  const modal = document.getElementById('equipmentDetailModal');
  if (!view || !modal) return;
  const section = document.createElement('section');
  section.innerHTML = '<h3>THAO TÁC</h3><label>Số lượng <input id="itemActionQuantity" type="number" min="1" step="1" value="1" style="width:80px;background:#11171b;color:inherit;border:1px solid #46525a;padding:8px"></label><label id="itemActionTargetLabel" style="display:block;margin:10px 0">Người nhận <select id="itemActionTarget" style="max-width:100%;background:#11171b;color:inherit;padding:8px"></select></label><div style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px"><button type="button" id="itemActionUse">Dùng</button><button type="button" id="itemActionTransfer">Chuyển</button><button type="button" id="itemActionDrop" class="danger">Bỏ</button></div><p id="itemActionMessage" role="status" style="color:#c7b38b;font-size:12px"></p>';
  modal.querySelector('.equipment-detail-sheet').appendChild(section);
  const statPicker = document.createElement('div');
  statPicker.id = 'statBookPicker'; statPicker.hidden = true;
  statPicker.setAttribute('role', 'group'); statPicker.setAttribute('aria-labelledby', 'statBookPickerTitle');
  statPicker.innerHTML = '<h3 id="statBookPickerTitle">CHỌN STAT +1</h3><p id="statBookOwner"></p><div id="statBookOptions"></div><p id="statBookChoice" role="status"></p><div class="stat-book-actions"><button type="button" id="statBookConfirm" disabled>Xác nhận · Dùng 1 cuốn</button><button type="button" id="statBookCancel">Hủy</button></div>';
  section.appendChild(statPicker);
  const q = id => document.getElementById(id);
  let selected = null;
  let dropArmed = false;
  let selectedStat = null;
  const statBookId = 'training:stat-point-book';
  const statLabels = { STR: 'Sức mạnh', DEX: 'Khéo léo', AGI: 'Nhanh nhẹn', VIT: 'Thể chất', EVA: 'Né tránh' };
  function isStatBook() { return selected?.item.id === statBookId; }
  function closeStatPicker() { statPicker.hidden = true; selectedStat = null; }
  function openStatPicker() {
    if (busy || !isStatBook()) return;
    selectedStat = null; statPicker.hidden = false;
    q('itemActionQuantity').value = '1';
    const owner = members().find(member => member.id === selected.actorId);
    q('statBookOwner').textContent = 'Cộng vĩnh viễn cho ' + (owner?.name || selected.actorId) + '. Mỗi cuốn chỉ cộng một điểm gốc.';
    q('statBookChoice').textContent = 'Chọn một chỉ số; sách chỉ bị tiêu hao khi xác nhận thành công.';
    const options = q('statBookOptions'); options.replaceChildren();
    Object.entries(statLabels).forEach(([stat, label]) => {
      const base = owner?.stats?.[stat]?.base;
      const button = document.createElement('button');button.type = 'button';button.dataset.stat = stat;
      button.setAttribute('aria-pressed', 'false');button.dataset.capped = String(!Number.isInteger(base) || base >= 999);button.disabled = button.dataset.capped === 'true';
      button.textContent = stat + ' · ' + label + (Number.isInteger(base) ? ' · ' + base + (base < 999 ? ' → ' + (base + 1) : ' (tối đa)') : '');
      button.addEventListener('click', () => {
        if (busy || button.disabled) return;
        selectedStat = stat;
        options.querySelectorAll('button').forEach(choice => choice.setAttribute('aria-pressed', String(choice === button)));
        q('statBookChoice').textContent = 'Dùng 1 cuốn: +1 ' + stat + ' cho ' + owner.name + '.';
        update();
      });
      options.appendChild(button);
    });
    update();q('statBookCancel').focus();
    statPicker.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  }
  function members() { return state?.partyDetails?.members || []; }
  function update() {
    const locked = busy || !selected || selected.item.equipped === true;
    q('itemActionUse').disabled = locked;
    q('itemActionDrop').disabled = locked;
    q('itemActionTransfer').disabled = locked || !q('itemActionTarget').options.length;
    q('itemActionQuantity').disabled = locked || !statPicker.hidden;
    q('itemActionTarget').disabled = locked;
    q('itemActionDrop').textContent = dropArmed ? 'Xác nhận bỏ' : 'Bỏ';
    q('statBookConfirm').disabled = locked || !selectedStat;
    q('statBookCancel').disabled = busy;
    q('statBookOptions').querySelectorAll('button').forEach(button => { button.disabled = locked || button.dataset.capped === 'true'; });
  }
  view.addEventListener('click', event => {
    const card = event.target.closest('[data-item-id]');
    if (!card) return;
    const owner = members().find(member => String(member.id) === view.dataset.characterId);
    const id = card.getAttribute('data-item-id');
    const item = (owner?.inventory || []).find(item => String(item.id) === id) || (owner?.equipmentItems || []).find(item => String(item.id) === id);
    selected = owner && item ? { actorId: owner.id, item } : null;
    dropArmed = false;closeStatPicker();
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
    if (operation === 'USE' && isStatBook() && !selectedStat) { openStatPicker(); return; }
    const quantity = operation === 'USE' && isStatBook() ? 1 : Number(q('itemActionQuantity').value);
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
    if (operation !== 'USE') closeStatPicker();
    busy = true; dropArmed = false; update(); syncPrimaryActions();
    q('itemActionMessage').textContent = 'Đang xử lý…';
    try { Android.itemAction(JSON.stringify(state), JSON.stringify({ operation, actorId: selected.actorId, itemId: selected.item.id, quantity, targetId, targetStat: operation === 'USE' && isStatBook() ? selectedStat : undefined })); }
    catch (error) { busy = false; update(); syncPrimaryActions(); q('itemActionMessage').textContent = 'Không gửi được thao tác item.'; }
  }
  q('itemActionUse').addEventListener('click', () => isStatBook() ? openStatPicker() : submit('USE'));
  q('statBookConfirm').addEventListener('click', () => { if (selectedStat) submit('USE'); });
  q('statBookCancel').addEventListener('click', () => { closeStatPicker(); update(); q('itemActionUse').focus(); });
  q('itemActionTransfer').addEventListener('click', () => submit('TRANSFER'));
  q('itemActionDrop').addEventListener('click', () => submit('DROP'));
  q('itemActionQuantity').addEventListener('input', () => { dropArmed = false; update(); });
  q('itemActionTarget').addEventListener('change', () => { dropArmed = false; update(); });
  window.backroomItemAction = json => {
    try {
      const result = JSON.parse(json);
      busy = false;
      if (result.handled && result.state) { closeStatPicker(); modal.hidden = true; window.backroomTurn(JSON.stringify(result.state)); }
      else { q('itemActionMessage').textContent = result.reply || ('Không thể thực hiện: ' + (result.error || 'item_action_failed')); }
    } catch (error) { busy = false; q('itemActionMessage').textContent = 'Phản hồi thao tác item không hợp lệ.'; }
    syncPrimaryActions(); update();
  };
})();
