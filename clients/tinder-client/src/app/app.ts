import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <div class="app-wrapper">
      <app-navbar />
      <main class="page-content">
        <router-outlet />
      </main>
    </div>
  `,
  styles: [`
    .app-wrapper {
      height: 100dvh;
      background: transparent;
      display: flex;
      position: relative;
    }

    .page-content {
      flex: 1;
      height: 100dvh;
      overflow: hidden;
      min-width: 0;
      position: relative;
    }

    .page-content > *:not(router-outlet) {
      width: 100%;
    }

    @media (min-width: 768px) {
      .page-content {
        overflow: hidden;
      }

      .page-content > *:not(router-outlet) {
        max-width: 1100px;
        margin: 0 auto;
      }
    }
  `]
})
export class App {
  // Injecting ThemeService here ensures it is instantiated at startup,
  // applying the saved/preferred theme before any component renders.
  private theme = inject(ThemeService);
}
