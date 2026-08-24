import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../../environments/environment';
import { ChatHistoryCache } from './chat-history.cache';

export const PREMIUM_REALM_ROLE = 'USER_PREMIUM';

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private keycloak: Keycloak;

  constructor(private chatHistoryCache: ChatHistoryCache) {
    this.keycloak = new Keycloak({
      url: environment.keycloak.url,
      realm: environment.keycloak.realm,
      clientId: environment.keycloak.clientId,
    });
  }

  async init(): Promise<boolean> {
    if (environment.designPreview) return true;
    const authenticated = await this.keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    });
    return authenticated;
  }

  login(): void {
    if (environment.designPreview) return;
    this.keycloak.login();
  }

  logout(): void {
    if (environment.designPreview) return;
    this.chatHistoryCache.clear();
    this.keycloak.logout({ redirectUri: window.location.origin });
  }

  async getToken(): Promise<string | undefined> {
    if (environment.designPreview) return undefined;
    if (this.keycloak.isTokenExpired(30)) {
      await this.keycloak.updateToken(30);
    }
    return this.keycloak.token;
  }

  isAuthenticated(): boolean {
    if (environment.designPreview) return true;
    return !!this.keycloak.authenticated;
  }

  getUserInfo(): { id: string; username: string; email: string } | null {
    if (!this.keycloak.tokenParsed) return null;
    const parsed = this.keycloak.tokenParsed as Record<string, unknown>;
    return {
      id: (parsed['sub'] as string) ?? '',
      username: (parsed['preferred_username'] as string) ?? '',
      email: (parsed['email'] as string) ?? '',
    };
  }

  getRoles(): string[] {
    return this.keycloak.realmAccess?.roles ?? [];
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  hasPremium(): boolean {
    if (environment.designPreview) return true;
    return this.hasRole(PREMIUM_REALM_ROLE);
  }

  async refreshRoles(): Promise<void> {
    if (environment.designPreview) return;
    await this.keycloak.updateToken(-1);
  }
}
