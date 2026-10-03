// Refiner's Fire: works offline once opened, and takes new versions as soon as they're online.
// Network first (so an update is picked up on the next launch), falling back to the last copy when offline.
const CACHE = 'refinersfire-v1';

self.addEventListener('install', () => self.skipWaiting());
// Each release has its own cache name (set when it's published), so the old copy is cleared out.
self.addEventListener('activate', (event) => event.waitUntil(
  caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()),
));

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  // Only the game's own files; the game server is never cached.
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req).then((hit) => hit || caches.match('./index.html'))),
  );
});

// Notifications from the game server (chats, challenges, pokes, friend requests, a friend's Manna).
self.addEventListener('push', (event) => {
  let msg = {};
  try { msg = event.data ? event.data.json() : {}; } catch (e) {}
  const title = msg.title || "Refiner's Fire";
  event.waitUntil(
    self.registration.showNotification(title, {
      body: msg.body || '',
      icon: 'icons/icon-192.png',
      badge: 'icons/icon-192.png',
      // One notice per kind and friend: a second chat from the same friend replaces the first.
      tag: (msg.kind || 'note') + ':' + (msg.from || ''),
    }),
  );
});

// Tapping a notification brings the game forward, or opens it.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((wins) => {
      for (const w of wins) if ('focus' in w) return w.focus();
      return self.clients.openWindow('./');
    }),
  );
});
