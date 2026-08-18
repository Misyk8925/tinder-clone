import { APP_INITIALIZER, ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { KeycloakService } from './core/services/keycloak.service';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { designPreviewInterceptor } from './core/interceptors/design-preview.interceptor';
import {
  Activity,
  BadgeCheck,
  Bike,
  BookOpen,
  Check,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Compass,
  Coffee,
  Crown,
  Dumbbell,
  Heart,
  HeartHandshake,
  Images,
  LUCIDE_ICONS,
  LogOut,
  LockKeyhole,
  LucideIconProvider,
  MapPin,
  MessageCircle,
  Moon,
  Mountain,
  Music2,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Star,
  Sun,
  Trash2,
  UserRound,
  UserRoundPlus,
  UserRoundSearch,
  X,
} from 'lucide-angular';

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
      useValue: new LucideIconProvider({
        Activity,
        BadgeCheck,
        Bike,
        BookOpen,
        Check,
        ChevronDown,
        ChevronRight,
        ChevronUp,
        Compass,
        Coffee,
        Crown,
        Dumbbell,
        Heart,
        HeartHandshake,
        Images,
        LockKeyhole,
        LogOut,
        MapPin,
        MessageCircle,
        Moon,
        Mountain,
        Music2,
        Pencil,
        Plus,
        RefreshCw,
        Search,
        ShieldCheck,
        SlidersHorizontal,
        Sparkles,
        Star,
        Sun,
        Trash2,
        UserRound,
        UserRoundPlus,
        UserRoundSearch,
        X,
      })
    },
  ]
};
