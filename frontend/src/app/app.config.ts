import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';

/**
 * app.config.ts
 *
 * Application-wide providers for the standalone bootstrap: the router, the HTTP
 * client with the authentication interceptor, and change detection.
 *
 * `scrollPositionRestoration` is set to 'top' so that navigating to a new
 * screen starts at the top of it. Without this the browser keeps the previous
 * scroll offset, which lands a user part-way down an unfamiliar page.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withInterceptors([authInterceptor]))
  ]
};
