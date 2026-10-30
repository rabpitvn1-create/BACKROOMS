(function () {
  'use strict';
  const artwork = {
    'luc-tram': {name: 'Lục Trầm', mirror: true},
    nguyen_quoc_nam: {name: 'Nguyễn Quốc Nam', mirror: true},
    hua_tieu_lan: {name: 'Hứa Tiểu Lan', mirror: true},
    syvial: {name: 'Syvial', mirror: true},
    catherine: {name: 'Catherine', mirror: true},
    janet: {name: 'Janet', mirror: true},
    trac_lam: {name: 'Trác Lam', mirror: true}
  };
  function withAvatar(member) {
    member = window.backroomLuciaVisuals.withAvatar(member);
    if (!member) return member;
    const avatar = window.backroomCloudinary?.characterAvatar(member.id);
    return avatar ? Object.assign({}, member, {avatar, avatarRef: avatar}) : member;
  }
  function renderOverlay(state) {
    const box = document.getElementById('snapshot');
    if (!box) return;
    const members = state && state.partyDetails && Array.isArray(state.partyDetails.members)
      ? state.partyDetails.members : state && Array.isArray(state.party) ? state.party : [];
    const encounters = state && Array.isArray(state.followerEncounters) ? state.followerEncounters : [];
    Object.keys(artwork).forEach(id => {
      // Party membership takes precedence even if the old encounter stays ACTIVE.
      const recruited = members.some(m => m && m.id === id);
      const present = !recruited && encounters.some(m => m && m.id === id && m.presence === 'ACTIVE');
      const current = box.querySelector('[data-named-follower="' + id + '"]');
      if (!present) { if (current) current.remove(); return; }
      const source = window.backroomCloudinary?.characterOverlay(id, false);
      // No fake or broken image when a follower has no curated Cloudinary art.
      if (!source) { if (current) current.remove(); return; }
      // All registered follower overlays face Cao Minh at stage-right.
      const facing = artwork[id].mirror ? 'scaleX(-1)' : '';
      if (current) { if (current.src !== source) current.src = source; current.style.transform = facing; return; }
      const image = document.createElement('img');
      image.className = 'snapshot-named-follower';
      image.setAttribute('data-named-follower', id);
      image.src = source;
      image.alt = artwork[id].name;
      image.draggable = false;
      image.style.cssText = 'position:absolute;bottom:0;width:auto;height:94%;object-fit:contain;object-position:center bottom;pointer-events:none;z-index:2';
      image.style.transform = facing;
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
  window.backroomNamedFollowers = {renderOverlay: renderOverlay, withAvatar: withAvatar};
})();
