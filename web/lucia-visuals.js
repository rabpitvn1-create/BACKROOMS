(function () {
  'use strict';

  // Reuse already-curated Cloudinary artwork, never an unbundled local path.
  const avatar = () => window.backroomCloudinary?.characterAvatar('lucia') || '';
  const overlay = () => window.backroomCloudinary?.characterOverlay('lucia', false) || '';

  function withAvatar(member) {
    return member && member.id === 'lucia'
      ? Object.assign({}, member, {avatar: avatar(), avatarRef: avatar()})
      : member;
  }

  function renderOverlay(state) {
    const box = document.getElementById('snapshot');
    if (!box) return;
    const current = box.querySelector('.snapshot-lucia');
    // Core's party projection is authoritative, including an empty party.
    const members = state && state.partyDetails && Array.isArray(state.partyDetails.members)
      ? state.partyDetails.members
      : state && Array.isArray(state.party) ? state.party : [];
    const encounters = state && Array.isArray(state.followerEncounters) ? state.followerEncounters : [];
    // Lucia belongs to the scene only until she joins Party.
    const recruited = members.some(member => member && member.id === 'lucia');
    const present = !recruited && encounters.some(member => member && member.id === 'lucia'
      && member.presence === 'ACTIVE');
    if (!present) {
      if (current) current.remove();
      return;
    }
    if (current) return;
    const image = document.createElement('img');
    image.className = 'snapshot-lucia';
    const source = overlay();
    if (!source) return;
    image.src = source;
    image.alt = 'Lucia “Lục”';
    image.draggable = false;
    // Keep the full artwork inside the scene without intercepting controls.
    image.style.cssText = 'position:absolute;right:0;bottom:0;width:auto;height:94%;max-width:55%;object-fit:contain;object-position:right bottom;pointer-events:none;z-index:2';
    image.style.transform = 'scaleX(-1)';
    box.style.position = 'relative';
    box.appendChild(image);
  }

  window.backroomLuciaVisuals = {withAvatar: withAvatar, renderOverlay: renderOverlay};
})();
