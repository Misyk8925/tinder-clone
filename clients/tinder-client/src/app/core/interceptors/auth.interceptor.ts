import { HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { KeycloakService } from '../services/keycloak.service';

/**
 * Attaches the access token only to requests aimed at our own API.
 *
 * Photo and CDN links are presigned third-party URLs, and anything else the app fetches may
 * be off-origin too. Sending the bearer token to every host would hand a usable session
 * credential to whoever operates that host, so the destination is checked first.
 */
export function isApiRequest(url: string): boolean {
  const gateway = environment.apiGatewayUrl;

  // Relative URLs resolve against our own origin, so they are always in scope.
  if (!/^[a-z][a-z0-9+.-]*:/i.test(url) && !url.startsWith('//')) {
    return true;
  }

  try {
    const target = new URL(url, document.baseURI);
    const api = new URL(gateway, document.baseURI);
    return target.origin === api.origin;
  } catch {
    return false;
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const keycloak = inject(KeycloakService);

  if (!keycloak.isAuthenticated() || !isApiRequest(req.url)) {
    return next(req);
  }

  return from(keycloak.getToken()).pipe(
    switchMap(token => {
      if (token) {
        const cloned: HttpRequest<unknown> = req.clone({
          setHeaders: { Authorization: `Bearer ${token}` }
        });
        return next(cloned);
      }
      return next(req);
    })
  );
};
