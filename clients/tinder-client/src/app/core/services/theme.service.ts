import { Injectable, signal } from '@angular/core';

export type ThemePreference = 'system' | 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'connect-theme-preference';
  private readonly deviceTheme = window.matchMedia('(prefers-color-scheme: dark)');

  isDark = signal(false);
  preference = signal<ThemePreference>('system');

  constructor() {
    const preference = this.readPreference();
    this.preference.set(preference);
    this.applyTheme(this.resolve(preference));
    this.deviceTheme.addEventListener?.('change', this.onDeviceThemeChange);
  }

  toggle(): void {
    const preference: ThemePreference = this.isDark() ? 'light' : 'dark';
    this.preference.set(preference);
    this.writePreference(preference);
    this.applyTheme(preference === 'dark');
  }

  useDeviceTheme(): void {
    this.preference.set('system');
    try {
      localStorage.removeItem(this.STORAGE_KEY);
    } catch {
      // The current device preference still applies when storage is unavailable.
    }
    this.applyTheme(this.deviceTheme.matches);
  }

  private readonly onDeviceThemeChange = (event: MediaQueryListEvent): void => {
    if (this.preference() === 'system') this.applyTheme(event.matches);
  };

  private readPreference(): ThemePreference {
    try {
      const saved = localStorage.getItem(this.STORAGE_KEY);
      return saved === 'light' || saved === 'dark' ? saved : 'system';
    } catch {
      return 'system';
    }
  }

  private writePreference(preference: Exclude<ThemePreference, 'system'>): void {
    try {
      localStorage.setItem(this.STORAGE_KEY, preference);
    } catch {
      // Keep the chosen theme for this session when storage is unavailable.
    }
  }

  private resolve(preference: ThemePreference): boolean {
    return preference === 'dark' || (preference === 'system' && this.deviceTheme.matches);
  }

  private applyTheme(dark: boolean): void {
    this.isDark.set(dark);
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
    document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
    document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
      ?.setAttribute('content', dark ? '#171717' : '#fffffe');
  }
}
