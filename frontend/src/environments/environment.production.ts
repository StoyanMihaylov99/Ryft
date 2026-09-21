export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  // Same-origin deployment, same derivation as environment.ts — see that file's comment.
  wsUrl: `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`,
};
