import { APP_INITIALIZER, ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { KeycloakService } from './core/services/keycloak.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { designPreviewInterceptor } from './core/interceptors/design-preview.interceptor';
import { APP_LUCIDE_ICONS } from './core/lucide-icons';
import { LUCIDE_ICONS, LucideIconProvider } from 'lucide-angular';

function initializeKeycloak(keycloak: KeycloakService) {
  return () => keycloak.init();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([designPreviewInterceptor, authInterceptor])),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeKeycloak,
      deps: [KeycloakService],
      multi: true,
    },
    {
      provide: LUCIDE_ICONS,
      multi: true,
      useValue: new LucideIconProvider(APP_LUCIDE_ICONS)
    },
  ]
};
