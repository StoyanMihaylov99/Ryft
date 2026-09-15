import { TestBed } from '@angular/core/testing';
import { ThemeService } from './theme.service';

/**
 * This test environment doesn't provide a working `localStorage` global (Node's experimental
 * implementation is disabled without a CLI flag), so it's stubbed here with an in-memory Map —
 * mirroring the real API that ThemeService's try/catch guards are written to tolerate.
 */
function installFakeLocalStorage(): Storage {
  const store = new Map<string, string>();
  const fake: Storage = {
    getItem: (key) => (store.has(key) ? store.get(key)! : null),
    setItem: (key, value) => void store.set(key, value),
    removeItem: (key) => void store.delete(key),
    clear: () => store.clear(),
    key: (index) => Array.from(store.keys())[index] ?? null,
    get length() {
      return store.size;
    },
  };
  Object.defineProperty(globalThis, 'localStorage', { value: fake, configurable: true });
  return fake;
}

describe('ThemeService', () => {
  beforeEach(() => {
    installFakeLocalStorage();
    document.documentElement.removeAttribute('data-theme');
  });

  it('defaults to dark and does not set the data-theme attribute', () => {
    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
  });

  it('reads a previously stored light preference on creation', () => {
    localStorage.setItem('ryft.theme', 'light');

    const service = TestBed.inject(ThemeService);

    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('toggle switches theme, updates the DOM attribute and persists to localStorage', () => {
    const service = TestBed.inject(ThemeService);

    service.toggle();

    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(localStorage.getItem('ryft.theme')).toBe('light');

    service.toggle();

    expect(service.theme()).toBe('dark');
    expect(document.documentElement.hasAttribute('data-theme')).toBe(false);
    expect(localStorage.getItem('ryft.theme')).toBe('dark');
  });

  it('setTheme sets an explicit theme', () => {
    const service = TestBed.inject(ThemeService);

    service.setTheme('light');

    expect(service.theme()).toBe('light');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });
});
