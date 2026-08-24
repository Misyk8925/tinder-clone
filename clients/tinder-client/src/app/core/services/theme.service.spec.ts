import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  const originalMatchMedia = window.matchMedia;
  let deviceIsDark = false;

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.style.removeProperty('color-scheme');
    document.head.innerHTML = '<meta name="theme-color" content="#fffffe">';
    deviceIsDark = false;

    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        get matches() { return deviceIsDark; },
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn()
      }))
    });
  });

  afterEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: originalMatchMedia
    });
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('Given no override, when the app starts, then it applies the current device color mode without persisting it', () => {
    deviceIsDark = true;

    const theme = new ThemeService();

    expect(theme.preference()).toBe('system');
    expect(theme.isDark()).toBe(true);
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.querySelector('meta[name="theme-color"]')?.getAttribute('content')).toBe('#171717');
    expect(localStorage.getItem('connect-theme-preference')).toBeNull();
  });

  it('Given the device mode changes between launches, when the app starts again, then it resolves the new mode', () => {
    deviceIsDark = true;
    new ThemeService();
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

    deviceIsDark = false;
    new ThemeService();

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
  });

  it('Given a user override, when the app starts, then it keeps the explicit choice', () => {
    deviceIsDark = true;
    localStorage.setItem('connect-theme-preference', 'light');

    const theme = new ThemeService();

    expect(theme.preference()).toBe('light');
    expect(theme.isDark()).toBe(false);
  });

  it('Given the device mode is active, when the user toggles twice and returns to device mode, then all states stay in sync', () => {
    deviceIsDark = false;
    const theme = new ThemeService();

    theme.toggle();
    expect(theme.preference()).toBe('dark');
    expect(localStorage.getItem('connect-theme-preference')).toBe('dark');

    theme.toggle();
    expect(theme.preference()).toBe('light');
    expect(localStorage.getItem('connect-theme-preference')).toBe('light');

    deviceIsDark = true;
    theme.useDeviceTheme();
    expect(theme.preference()).toBe('system');
    expect(theme.isDark()).toBe(true);
    expect(localStorage.getItem('connect-theme-preference')).toBeNull();
  });
});
