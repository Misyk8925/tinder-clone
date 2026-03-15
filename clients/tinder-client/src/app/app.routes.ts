import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'discover',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'location-permission',
    loadComponent: () => import('./features/location-permission/location-permission.component').then(m => m.LocationPermissionComponent)
  },
  {
    path: 'discover',
    canActivate: [authGuard],
    loadComponent: () => import('./features/discover/discover.component').then(m => m.DiscoverComponent)
  },
  {
    path: 'matches',
    canActivate: [authGuard],
    loadComponent: () => import('./features/matches/matches.component').then(m => m.MatchesComponent)
  },
  {
    path: 'chat/:conversationId',
    canActivate: [authGuard],
    loadComponent: () => import('./features/chat/chat.component').then(m => m.ChatComponent)
  },
  {
    path: 'likes',
    canActivate: [authGuard],
    loadComponent: () => import('./features/likes/likes.component').then(m => m.LikesComponent)
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent)
  },
  {
    path: 'profile/edit',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/edit/profile-edit.component').then(m => m.ProfileEditComponent)
  },
  {
    path: '**',
    redirectTo: 'discover'
  }
];
