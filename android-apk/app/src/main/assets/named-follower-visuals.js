(function () {
  'use strict';
  const artwork = {
    nguyen_quoc_nam: {src: 'NguyenQuocNam.webp', name: 'Nguyễn Quốc Nam'},
    hua_tieu_lan: {src: 'HuaTieuLan.webp', name: 'Hứa Tiểu Lan'}
  };
  function renderOverlay(state) {
    const box = document.getElementById('snapshot');
    if (!box) return;
    const members = state && state.partyDetails && Array.isArray(state.partyDetails.members)
      ? state.partyDetails.members : state && Array.isArray(state.party) ? state.party : [];
    const encounters = state && Array.isArray(state.followerEncounters) ? state.followerEncounters : [];
    Object.keys(artwork).forEach(id => {
      const present = members.concat(encounters).some(m => m && m.id === id && m.presence === 'ACTIVE');
      const current = box.querySelector('[data-named-follower="' + id + '"]');
      if (!present) { if (current) current.remove(); return; }
      if (current) return;
      const image = document.createElement('img');
      image.className = 'snapshot-named-follower';
      image.setAttribute('data-named-follower', id);
      image.src = artwork[id].src;
      image.alt = artwork[id].name;
      image.draggable = false;
      image.style.cssText = 'position:absolute;bottom:0;width:auto;height:94%;object-fit:contain;object-position:center bottom;pointer-events:none;z-index:2';
      box.style.position = 'relative';
      box.appendChild(image);
    });
    // Allocate one bounded column per visible follower, including Lucia's existing layer.
    const layers = Array.from(box.querySelectorAll('.snapshot-lucia,.snapshot-named-follower'));
    layers.forEach((image, index) => {
      image.style.left = (index * 100 / layers.length) + '%';
      image.style.right = 'auto';
      image.style.maxWidth = (100 / layers.length) + '%';
    });
  }
  window.backroomNamedFollowers = {renderOverlay: renderOverlay};
})();
