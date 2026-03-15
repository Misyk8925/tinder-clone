import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { ThemeService } from './core/services/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <div class="app-wrapper">
      <router-outlet />
      <app-navbar />
    </div>
  `,
  styles: [`
    .app-wrapper {
      height: 100vh;
      display: flex;
      flex-direction: column;
      background: var(--bg);
      position: relative;
      overflow: hidden;
    }
  `]
})
export class App {
  // Injecting ThemeService here ensures it is instantiated at startup,
  // applying the saved/preferred theme before any component renders.
  private theme = inject(ThemeService);
}
