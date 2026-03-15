import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from '../services/keycloak.service';
import { GeoLocationService } from '../services/geo-location.service';

export const authGuard: CanActivateFn = (route) => {
  const keycloak = inject(KeycloakService);
  const router = inject(Router);
  const geo = inject(GeoLocationService);

  if (!keycloak.isAuthenticated()) {
    keycloak.login();
    return false;
  }

  // Redirect to location-permission on discover if the user hasn't granted location
  // or explicitly chosen to skip. Denied-then-skipped users are re-prompted.
  if (!geo.hasCoords() && !geo.hasSkipped() && route.routeConfig?.path === 'discover') {
    return router.createUrlTree(['/location-permission']);
  }

  return true;
};
