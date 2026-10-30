/* Local BGM playlist. Android WebView is allowed to start media without a gesture. */
(function () {
  'use strict';
  if (window.__caoMinhBgmInstalled) return;
  window.__caoMinhBgmInstalled = true;

  const tracks = Object.freeze([
    'bgm/time-in-limbo.mp3',
    'bgm/empty-year.mp3',
    'bgm/10k-failures.mp3'
  ]);
  const storageKey = 'caominh.bgm.enabled';
  const audio = new Audio();
  audio.volume = 0.35;
  audio.preload = 'auto';
  audio.loop = false;

  let enabled = true;
  try { enabled = window.localStorage.getItem(storageKey) !== 'false'; } catch (_) {}
  let appPaused = false;
  let deck = [];
  let last = -1;
  let current = -1;
  let generation = 0;
  let awaitingGesture = false;
  const unavailable = new Set();
  const button = document.getElementById('bgmToggle');

  function inactive() { return appPaused || document.hidden === true; }

  function syncButton() {
    if (!button) return;
    button.textContent = enabled ? 'Nhạc nền: Bật' : 'Nhạc nền: Tắt';
    button.setAttribute('aria-pressed', String(enabled));
  }

  function shuffle() {
    const order = tracks.map((_, i) => i).filter(i => !unavailable.has(i));
    for (let i = order.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [order[i], order[j]] = [order[j], order[i]];
    }
    // The first track in the next cycle must differ from the last one played.
    if (order.length > 1 && order[order.length - 1] === last) {
      [order[0], order[order.length - 1]] = [order[order.length - 1], order[0]];
    }
    return order;
  }

  function failCurrent(attempt) {
    if (attempt !== generation || current < 0 || unavailable.has(current)) return;
    unavailable.add(current);
    generation++;
    deck = deck.filter(i => i !== current);
    if (unavailable.size === tracks.length) {
      current = -1;
      console.warn('CAO_MINH BGM: no playable local tracks');
      return;
    }
    playNext();
  }

  function rejected(error, attempt) {
    if (attempt !== generation || !enabled || inactive()) return;
    if (error && error.name === 'AbortError') return;
    if (error && error.name === 'NotAllowedError') {
      awaitingGesture = true;
      return;
    }
    failCurrent(attempt);
  }

  function playSelected() {
    if (!enabled || inactive() || current < 0) return;
    awaitingGesture = false;
    const attempt = ++generation;
    try {
      const promise = audio.play();
      if (promise && typeof promise.catch === 'function')
        promise.catch(error => rejected(error, attempt));
    } catch (error) {
      rejected(error, attempt);
    }
  }

  function playNext() {
    if (!enabled || inactive()) return;
    if (!deck.length) deck = shuffle();
    const next = deck.pop();
    if (next === undefined) return;
    current = next;
    last = next;
    audio.src = tracks[next];
    playSelected();
  }

  function resume() {
    if (!enabled || inactive()) return;
    if (current < 0) playNext();
    else if (audio.paused) playSelected();
  }

  function pause() {
    generation++;
    audio.pause();
  }

  function setEnabled(value) {
    enabled = Boolean(value);
    try { window.localStorage.setItem(storageKey, String(enabled)); } catch (_) {}
    if (enabled) resume();
    else pause();
    syncButton();
  }

  audio.addEventListener('ended', playNext);
  audio.addEventListener('error', () => failCurrent(generation));
  audio.addEventListener('playing', () => { awaitingGesture = false; });
  document.addEventListener('pointerdown', () => { if (awaitingGesture) resume(); });
  document.addEventListener('keydown', () => { if (awaitingGesture) resume(); });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) pause();
    else resume();
  });

  if (button) button.addEventListener('click', () => setEnabled(!enabled));
  window.caoMinhBgmToggle = setEnabled;
  window.caoMinhBgmNext = playNext;
  window.caoMinhBgmSuspend = () => { appPaused = true; pause(); };
  window.caoMinhBgmResume = () => { appPaused = false; resume(); };

  syncButton();
  resume();
})();
