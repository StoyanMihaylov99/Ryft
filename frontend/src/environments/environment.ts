export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  // Proxied by proxy.conf.json's "/ws" entry (ws: true) straight through to the backend's plain
  // (no SockJS) STOMP endpoint during `ng serve`. Derived from the current page's own origin (not
  // hardcoded to localhost:8080) so this also works when the dev server is reached over a tunnel/LAN.
  wsUrl: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`,
};
