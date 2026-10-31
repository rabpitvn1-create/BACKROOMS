/* GitHub Pages offline shell. Generated Pages builds prepend the exact asset list. */
'use strict';
const CACHE_PREFIX = 'backrooms-cao-minh-shell-';
const CACHE_NAME = CACHE_PREFIX + (self.__BACKROOMS_SHELL_VERSION || 'fallback-v1');
const SHELL = self.__BACKROOMS_PRECACHE || [
  './', './index.html', './cloudinary-gameplay.js',
  './offline-character-assets.js', './cao-minh-asset-db.js'
];
self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    await Promise.allSettled(SHELL.map(path => cache.add(new Request(
      new URL(path, self.registration.scope), {cache:'reload'}
    ))));
    await self.skipWaiting();
  })());
});
self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
      .map(key => caches.delete(key)));
    await self.clients.claim();
  })());
});
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin ||
      !url.pathname.startsWith(new URL(self.registration.scope).pathname)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE_NAME);
    try {
      const response = await fetch(request);
      if (response.ok && response.type === 'basic') {
        await cache.put(request, response.clone());
      }
      return response;
    } catch (error) {
      const saved = await cache.match(request, {ignoreSearch:true});
      if (saved) return saved;
      if (request.mode === 'navigate') {
        const home = await cache.match(new URL('./index.html', self.registration.scope));
        if (home) return home;
      }
      throw error;
    }
  })());
});
