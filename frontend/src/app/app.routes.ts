import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/auth.guard';

/**
 * app.routes.ts
 *
 * The client-side route table. Components are loaded lazily so the sign-in
 * screen downloads only what it needs, and each protected route carries the
 * guard appropriate to it.
 *
 * Every route declares a `title`, which Angular writes into the browser tab.
 * That is not decoration: a screen reader announces the document title on
 * navigation, so it is often the first thing a user hears after moving between
 * screens.
 */
export const routes: Routes = [
  {
    path: 'sign-in',
    title: 'Sign in | Provider Credentialing Platform',
    loadComponent: () => import('./features/login/login.component')
      .then(m => m.LoginComponent)
  },
  {
    path: 'dashboard',
    title: 'Verification dashboard | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard.component')
      .then(m => m.DashboardComponent)
  },
  {
    path: 'providers',
    title: 'Provider queue | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/providers/providers.component')
      .then(m => m.ProvidersComponent)
  },
  {
    path: 'providers/:id',
    title: 'Provider record | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/provider-detail/provider-detail.component')
      .then(m => m.ProviderDetailComponent)
  },
  {
    path: 'review',
    title: 'Review queue | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/review/review-queue.component')
      .then(m => m.ReviewQueueComponent)
  },
  {
    path: 'upload',
    title: 'Upload a document | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/upload/upload.component')
      .then(m => m.UploadComponent)
  },
  {
    path: 'audit',
    title: 'Audit trail | Provider Credentialing Platform',
    canActivate: [authGuard],
    loadComponent: () => import('./features/audit/audit.component')
      .then(m => m.AuditComponent)
  },
  {
    path: 'admin',
    title: 'Administration | Provider Credentialing Platform',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/admin/admin.component')
      .then(m => m.AdminComponent)
  },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: '**', redirectTo: 'dashboard' }
];
