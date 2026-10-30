/* Canon-safe visuals: only verified static Cloudinary mappings may be rendered. */
(function () {
  'use strict';
  const root = 'https://res.cloudinary.com/csz2me5b/image/upload/';
  const assets = Object.freeze({
    'com-tam': ['v1792832683/Item_Com_Tam.webp', 'Cơm tấm', 24],
    'banh-tet': ['v1792832684/Item_Banh_Tet.webp', 'Bánh tét', 28],
    coconut: ['v1792832685/Item_Coconut.webp', 'Dừa', 16],
    'liquid-pain': ['v1792832688/Item_Liquid_Pain.webp', 'Liquid Pain', 8],
    'greek-fire': ['v1792832689/Item_Greek_Fire.webp', 'Greek Fire', 6],
    'medical:bandage': ['v1792832686/Item_Bandage.webp', 'Băng gạc', 5],
    'medical:antiseptic': ['v1792832685/Item_Antiseptic.webp', 'Thuốc sát trùng', 10],
    'almond-water': ['v1792832689/Item_Almond_Water.webp', 'Almond Water', null],
    'food-ration': ['v1792903586/Item_Hop_Thuc_An.webp', 'Hộp thức ăn', 15, 50]
  });
  const styles = document.createElement('style');
  styles.textContent = '.core-loot-image{width:100%;height:100%;object-fit:contain;display:block}.core-loot-pill{display:inline-flex;align-items:center;gap:6px}.core-loot-pill img{width:25px;height:25px;object-fit:contain}.core-loot-world{margin-top:10px;display:grid;gap:7px}.core-loot-world strong{font-size:11px;letter-spacing:.09em;color:#c4d2d1}.core-loot-world-entry{display:flex;gap:10px;align-items:center;border:1px solid #354342;background:#0e1717;padding:7px}.core-loot-world-entry img{width:42px;height:42px;object-fit:contain}.core-loot-world-entry small{display:block;color:#94a5a1;margin-top:3px}';
  document.head.appendChild(styles);

  function imageFor(itemId) {
    const info = Object.prototype.hasOwnProperty.call(assets, itemId) ? assets[itemId] : null;
    if (!info) return null;
    const image = document.createElement('img');
    image.className = 'core-loot-image';
    image.src = root + info[0];
    image.alt = info[1];
    image.loading = 'lazy';
    image.addEventListener('error', () => { image.remove(); }, { once: true });
    return image;
  }

  function updateCharacterItems() {
    document.querySelectorAll('.inventory-item-card[data-item-id],.equipment-card[data-item-id]').forEach(card => {
      const id = card.getAttribute('data-item-id');
      const image = imageFor(id);
      if (!image) return;
      const icon = card.querySelector('.equipment-card-icon');
      if (!icon) return;
      // Avoid refetching/replacing an already displayed Cloudinary image.
      if (!icon.querySelector('.core-loot-image')) icon.replaceChildren(image);
      const percent = assets[id][2];
      const description = card.querySelector('.equipment-card-main');
      if (percent && description && !description.querySelector('.core-loot-heal')) {
        const badge = document.createElement('small');
        badge.className = 'core-loot-heal';
        badge.textContent = 'Hồi ' + percent + '% Max HP' + (assets[id][3] ? ' · +' + assets[id][3] + ' Hungry' : '') + ' khi dùng';
        description.appendChild(badge);
      }
    });
  }

  function updateInventory() {
    const node = document.getElementById('inventory');
    if (!node || typeof state === 'undefined') return;
    const rows = Array.isArray(state.inventory) ? state.inventory : [];
    node.replaceChildren();
    if (!rows.length) { const empty = document.createElement('span'); empty.textContent = 'Trống.'; node.appendChild(empty); return; }
    rows.forEach(item => {
      const chip = document.createElement('span');
      chip.className = 'core-loot-pill';
      const id = item && String(item.id || '');
      const image = imageFor(id);
      if (image) chip.appendChild(image);
      const label = document.createElement('span');
      label.textContent = typeof item === 'string' ? item : String(item?.name || item?.id || '—') + (Number(item?.quantity) > 1 ? ' ×' + item.quantity : '');
      chip.appendChild(label);
      node.appendChild(chip);
    });
  }

  function updateWorldLoot() {
    const target = document.getElementById('inventory');
    if (!target || typeof state === 'undefined') return;
    let section = document.getElementById('coreLootWorld');
    if (!section) {
      section = document.createElement('section');
      section.id = 'coreLootWorld';
      section.className = 'core-loot-world';
      target.insertAdjacentElement('afterend', section);
    }
    const found = Array.isArray(state.flags?.worldItems)
      ? state.flags.worldItems.filter(item => item && item.available !== false && Number(item.quantity) > 0)
      : [];
    section.replaceChildren();
    section.hidden = found.length === 0;
    if (!found.length) return;
    const heading = document.createElement('strong');
    heading.textContent = 'VẬT PHẨM PHÁT HIỆN · CHƯA NHẶT';
    section.appendChild(heading);
    found.forEach(item => {
      const entry = document.createElement('div');
      entry.className = 'core-loot-world-entry';
      const image = imageFor(String(item.id || ''));
      if (image) entry.appendChild(image);
      const text = document.createElement('div');
      const title = document.createElement('b');
      title.textContent = String(item.name || item.id || 'Vật phẩm');
      text.appendChild(title);
      const note = document.createElement('small');
      const info = assets[String(item.id || '')];
      const heal = info?.[2], hunger = info?.[3];
      note.textContent = '×' + Math.max(1, Number(item.quantity) || 1) +
        (heal ? ' · Hồi ' + heal + '% Max HP' : '') +
        (hunger ? ' · +' + hunger + ' Hungry' : '') + ' · Chưa nhặt';
      text.appendChild(note);
      entry.appendChild(text);
      section.appendChild(entry);
    });
  }

  function showDetailImage(event) {
    const card = event.target.closest('[data-item-id]');
    if (!card) return;
    const image = imageFor(card.getAttribute('data-item-id'));
    const icon = document.getElementById('equipmentDetailIcon');
    if (icon && image) icon.replaceChildren(image);
    const itemId = card.getAttribute('data-item-id');
    const percent = assets[itemId]?.[2];
    const stats = document.getElementById('equipmentDetailStats');
    if (percent && stats && !stats.querySelector('.core-loot-heal')) {
      const row = document.createElement('div');
      row.className = 'equipment-detail-row core-loot-heal';
      const label = document.createElement('span');
      label.textContent = 'HP RESTORE';
      const value = document.createElement('strong');
      value.textContent = '+' + percent + '% Max HP';
      row.append(label, value);
      stats.appendChild(row);
    }
    const hunger = assets[itemId]?.[3];
    if (hunger && stats && !stats.querySelector('.core-loot-hunger')) {
      const row = document.createElement('div');
      row.className = 'equipment-detail-row core-loot-hunger';
      const label = document.createElement('span');
      label.textContent = 'HUNGRY';
      const value = document.createElement('strong');
      value.textContent = '+' + hunger + ' điểm';
      row.append(label, value);
      stats.appendChild(row);
    }
  }
  document.getElementById('characterInventoryView')?.addEventListener('click', showDetailImage);

  function renderLoot() {
    updateInventory();
    updateCharacterItems();
    updateWorldLoot();
  }
  if (typeof window.render === 'function') {
    const originalRender = window.render;
    window.render = function () {
      const result = originalRender.apply(this, arguments);
      renderLoot();
      return result;
    };
  }
  renderLoot();
})();
