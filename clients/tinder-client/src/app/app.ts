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
      background: var(--bg);
      display: flex;
    }

    .page-content {
      flex: 1;
      height: 100dvh;
      overflow: hidden;
      min-width: 0;
    }

    @media (min-width: 768px) {
      .page-content {
        margin-left: 72px;
      }
    }
  `]
})
export class App {
  // Injecting ThemeService here ensures it is instantiated at startup,
  // applying the saved/preferred theme before any component renders.
  private theme = inject(ThemeService);
}
