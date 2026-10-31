/* Four registered Cao Minh visuals, stored offline in IndexedDB after first online sync.
 * Artwork is referenced by URL in Git, never committed as an image or base64 payload.
 * Web/PWA and Android WebView use the same character asset database. */
(function () {
  'use strict';
  const registry = window.BACKROOMS_CAO_MINH_ASSET_DATABASE;
  if (!registry || !Array.isArray(registry.assets) || registry.assets.length !== 4) {
    console.error('Cao Minh asset registry must contain exactly four entries.');
    return;
  }
  const byRole = Object.create(null);
  const blobUrls = new Map();
  let warmPromise = null;
  registry.assets.forEach(asset => { byRole[asset.role] = asset; });

  function resolve(role) {
    const asset = byRole[role];
    return asset ? blobUrls.get(role) || asset.url : null;
  }
  function status() {
    return {
      cached: blobUrls.size,
      total: registry.assets.length,
      offlineReady: blobUrls.size === registry.assets.length,
      roles: Object.fromEntries(registry.assets.map(asset => [asset.role, blobUrls.has(asset.role)]))
    };
  }
  function notify() {
    if (typeof window.backroomRefreshCloudinary === 'function') window.backroomRefreshCloudinary();
    if (window.backroomSnapshotStage && typeof window.backroomSnapshotStage.render === 'function' &&
        typeof state !== 'undefined' && state) window.backroomSnapshotStage.render(state);
    window.dispatchEvent(new Event('backrooms-cao-minh-assets'));
  }
  function openDatabase() {
    return new Promise((resolveDb, reject) => {
      if (!window.indexedDB) { reject(new Error('IndexedDB unavailable')); return; }
      const request = indexedDB.open('backrooms-cao-minh-offline-art', 1);
      request.onupgradeneeded = () => {
        if (!request.result.objectStoreNames.contains('images')) {
          request.result.createObjectStore('images', {keyPath:'id'});
        }
      };
      request.onsuccess = () => resolveDb(request.result);
      request.onerror = () => reject(request.error || new Error('IndexedDB open failed'));
      request.onblocked = () => reject(new Error('IndexedDB blocked'));
    });
  }
  function read(db, id) {
    return new Promise((resolveEntry, reject) => {
      const request = db.transaction('images', 'readonly').objectStore('images').get(id);
      request.onsuccess = () => resolveEntry(request.result || null);
      request.onerror = () => reject(request.error);
    });
  }
  function store(db, entry) {
    return new Promise((resolveEntry, reject) => {
      const request = db.transaction('images', 'readwrite').objectStore('images').put(entry);
      request.onsuccess = () => resolveEntry();
      request.onerror = () => reject(request.error);
    });
  }
  function attach(asset, blob) {
    if (!blob || !blob.size || typeof URL.createObjectURL !== 'function') return;
    const previous = blobUrls.get(asset.role);
    const url = URL.createObjectURL(blob);
    blobUrls.set(asset.role, url);
    notify();
    if (previous) URL.revokeObjectURL(previous);
  }
  async function hydrate(db, asset) {
    try {
      const existing = await read(db, asset.id);
      if (existing && existing.url === asset.url && existing.blob &&
          existing.blob.size === asset.bytes && existing.blob.type.startsWith('image/')) {
        attach(asset, existing.blob);
        return;
      }
    } catch (error) {
      console.warn('Cao Minh cache read:', asset.role, error);
    }
    try {
      const response = await fetch(asset.url, {mode:'cors', cache:'no-store'});
      if (!response.ok) throw new Error('HTTP ' + response.status);
      const blob = await response.blob();
      if (!blob.type.startsWith('image/') || blob.size !== asset.bytes) {
        throw new Error('Cao Minh image format/size mismatch: ' + asset.role);
      }
      await store(db, {id:asset.id, url:asset.url, blob:blob});
      attach(asset, blob);
    } catch (error) {
      // Online rendering still uses the exact version-pinned CDN URL.
      // A later online event retries without erasing successfully cached art.
      console.warn('Cao Minh offline sync pending:', asset.role, error);
    }
  }
  function warm() {
    if (warmPromise) return warmPromise;
    warmPromise = (async () => {
      let db;
      try { db = await openDatabase(); }
      catch (error) {
        console.warn('Cao Minh persistent art cache unavailable:', error);
        return status();
      }
      try {
        await Promise.all(registry.assets.map(asset => hydrate(db, asset)));
      } finally {
        db.close();
      }
      return status();
    })().finally(() => { warmPromise = null; });
    return warmPromise;
  }
  window.backroomOfflineCharacterAssets = Object.freeze({
    registry: registry,
    resolve: resolve,
    status: status,
    warm: warm
  });
  // Service Worker protects Pages app-shell reloads. APK file:// uses IndexedDB only.
  if ('serviceWorker' in navigator && /^https?:$/.test(location.protocol)) {
    navigator.serviceWorker.register('service-worker.js', {scope:'./'})
      .catch(error => console.warn('Backrooms offline shell:', error));
  }
  window.addEventListener('online', () => { warm(); });
  warm();
})();
