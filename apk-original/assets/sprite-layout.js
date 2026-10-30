/* Version-pinned Cloudinary sprites: measured alpha bounds (alpha > 16),
 * plus full dimensions of four pretrimmed Entity derivatives. No runtime
 * canvas readback; every sprite keeps its natural aspect ratio and ground
 * line. Party keeps its historical 94%-height fit, while Entity silhouettes
 * share a bounded optical footprint on the left. */
(function () {
  'use strict';
  const bounds = {"https://res.cloudinary.com/csz2me5b/image/upload/v1792832579/Entity_Brownies.webp":[1000,1333,86,16,910,1298],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832580/Entity_Luckybear.webp":[900,1200,111,24,885,1160],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832581/Entity_927.webp":[1086,1448,135,21,1060,1420],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832582/Entity_926_Whistlers.webp":[1086,1448,323,5,1006,1438],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832584/Entity_Hound.webp":[941,1672,12,199,941,1556],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832585/Entity_Luc_Tram_Hac_Hoa_Overlay.webp":[941,1672,3,6,939,1653],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832593/Entity_Biological_Pipeline.webp":[864,1536,31,104,857,1463],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832594/Entity_Deathmoth.webp":[864,1536,19,260,864,1279],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832595/Entity_Cable_Mimic.webp":[864,1536,7,0,860,1522],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832597/Entity_Clump.webp":[864,1536,36,267,841,1230],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832598/Entity_Duller.webp":[864,1536,229,132,592,1404],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832600/Entity_False_Puddle.webp":[864,1536,7,93,855,1461],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832604/Entity_Hostile_Faceling.webp":[864,1536,16,154,849,1375],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832605/Entity_Hotel_Corpse_Lure.webp":[864,1536,107,192,864,1317],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832606/Entity_Jane_The_Killer.webp":[864,1536,63,234,847,1298],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832607/Entity_Jeff_The_Killer.webp":[864,1536,16,40,850,1498],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832608/Entity_Paintings.webp":[864,1536,15,82,839,1489],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832609/Entity_Predatory_Window.webp":[864,1536,4,275,859,1230],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832610/Entity_Skin_Stealer.webp":[864,1536,67,148,794,1363],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832611/Entity_Slenderman.webp":[864,1536,10,195,864,1328],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832611/Entity_Smiler.webp":[864,1536,228,200,676,1335],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832613/Entity_Wretch.webp":[864,1536,11,207,856,1331],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832614/Entity_The_Beast_Of_Level_5.webp":[864,1536,18,0,864,1492],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832615/Entity_Diep_Minh.webp":[864,1536,2,214,860,1319],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832705/Character_Cao_Minh_Avatar.jpg":[1536,1536,0,0,1536,1536],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832708/Character_Cao_Minh_Overlay_Entity_Encounter.png":[1122,1402,1,0,1122,1385],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832709/Character_Cao_Minh_Overlay_Explorer_Idle.png":[1122,1402,54,16,1019,1388],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832711/Character_Luc_Tram_Avatar.png":[1254,1254,0,0,1254,1254],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832713/Character_Luc_Tram_Overlay.png":[941,1672,6,9,941,1648],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832714/Character_Trac_Lam_Avatar.jpg":[350,347,0,0,350,347],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832715/Character_Trac_Lam_Overlay.png":[1145,1374,76,9,1128,1358],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832718/Character_Lucia_Avatar.png":[1254,1254,0,0,1254,1254],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832719/Character_Lucia_Overlay.png":[1024,1536,7,11,1024,1528],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832721/Character_Catherine_Avatar.webp":[512,512,0,0,512,512],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832722/Character_Catherine_Portrait.webp":[941,1672,2,113,941,1611],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832723/Character_Janet_Avatar.webp":[512,512,0,0,512,512],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832724/Character_Janet_Portrait.webp":[941,1672,15,2,936,1652],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832725/Character_Hua_Tieu_Lan_Avatar.webp":[512,512,0,0,512,512],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832729/Character_Hua_Tieu_Lan_Portrait.webp":[1024,1536,11,63,1023,1490],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832730/Character_Nguyen_Quoc_Nam_Avatar.webp":[512,512,0,0,512,512],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832734/Character_Nguyen_Quoc_Nam_Portrait.webp":[941,1672,33,30,941,1646],"https://res.cloudinary.com/csz2me5b/image/upload/v1792832736/Character_Syvial_Portrait.png":[1024,1536,11,15,1010,1505],"https://res.cloudinary.com/csz2me5b/image/upload/e_trim/v1792832592/Entity_The_Lifeform_Bacteria_01.webp":[555,1637,0,0,555,1637],"https://res.cloudinary.com/csz2me5b/image/upload/e_trim/v1792832591/Entity_The_Lifeform_Bacteria_02.webp":[717,1392,0,0,717,1392],"https://res.cloudinary.com/csz2me5b/image/upload/e_trim/v1792832589/Entity_The_Lifeform_Bacteria_03.webp":[784,1532,0,0,784,1532],"https://res.cloudinary.com/csz2me5b/image/upload/e_trim/v1792832616/Entity_Copx.webp":[841,1141,0,0,841,1141]};
  function geometry(b, width, height, side) {
    if (!b || b.length !== 6 || !Number.isFinite(width) || !Number.isFinite(height) ||
        width <= 0 || height <= 0 || b.some(v => !Number.isFinite(v)) ||
        b[0] <= 0 || b[1] <= 0 || b[2] < 0 || b[3] < 0 ||
        b[4] > b[0] || b[5] > b[1] ||
        b[4] <= b[2] || b[5] <= b[3]) return null;
    const visibleWidth = b[4] - b[2];
    const visibleHeight = b[5] - b[3];
    let scale;
    if (side === 'left') {
      // Normalize the *visible* Entity, never the padded image canvas.
      // Wide shapes are kept within the left lane; thinner forms may be
      // taller but never exceed 92% of the stage height. The optical
      // correction is mild and preserves native x/y proportions.
      const aspect = visibleWidth / visibleHeight;
      const opticalHeight = height * Math.max(.64,
        Math.min(.92, .86 * Math.pow(.57 / aspect, .35)));
      scale = Math.min(opticalHeight / visibleHeight,
        width * .455 / visibleWidth);
    } else {
      // Character / companion geometry is unchanged.
      scale = height * .94 / visibleHeight;
    }
    return {
      width: b[0] * scale,
      height: b[1] * scale,
      bottom: height * .03 - (b[1] - b[5]) * scale,
      offset: width * .01 - (side === 'left' ? b[2] : b[0] - b[4]) * scale
    };
  }
  function fit(image, snapshot, side) {
    if (!image || !snapshot || !image.style) return;
    const source = image.getAttribute('src') || image.src;
    const b = bounds[source] || (image.naturalWidth && image.naturalHeight
      ? [image.naturalWidth, image.naturalHeight, 0, 0, image.naturalWidth, image.naturalHeight] : null);
    const g = geometry(b, snapshot.clientWidth, snapshot.clientHeight, side);
    if (!g) return;
    for (const key of ['width', 'height', 'bottom'])
      image.style.setProperty('--sprite-' + key, g[key] + 'px');
    image.style.setProperty('--sprite-offset', g.offset + 'px');
  }
  function fitAll() {
    const snapshot = document.getElementById('snapshot');
    if (!snapshot) return;
    snapshot.querySelectorAll('.snapshot-player-overlay,.snapshot-entity,.combat-turn-avatar,.combat-turn-exit,.snapshot-lucia')
      .forEach(image => fit(image, snapshot, image.classList.contains('snapshot-entity') ? 'left' : 'right'));
  }
  window.backroomSpriteLayout = Object.freeze({geometry, fit});
  const snapshot = document.getElementById('snapshot');
  if (snapshot && typeof ResizeObserver === 'function') new ResizeObserver(fitAll).observe(snapshot);
})();
