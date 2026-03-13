import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';

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
      background: #f5f5f5;
      position: relative;
      overflow: hidden;
    }
  `]
})
export class App {}
