import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * main.ts
 *
 * Entry point of the Angular client. Bootstraps the standalone root component
 * with the application-wide providers declared in app.config.ts.
 */
bootstrapApplication(AppComponent, appConfig)
  .catch(error => console.error('The credentialing platform failed to start', error));
