import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { HealthResponse } from '../health/health.model';
import { HealthService } from '../health/health.service';

type HealthViewState =
  | { readonly kind: 'loading' }
  | { readonly kind: 'success'; readonly health: HealthResponse }
  | { readonly kind: 'error' };

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent implements OnInit {
  private readonly healthService = inject(HealthService);
  private readonly destroyRef = inject(DestroyRef);
  private hasStartedRequest = false;

  protected readonly healthState = signal<HealthViewState>({ kind: 'loading' });

  ngOnInit(): void {
    this.checkHealth();
  }

  checkHealth(): void {
    if (this.healthState().kind === 'loading' && this.hasStartedRequest) {
      return;
    }

    this.hasStartedRequest = true;
    this.healthState.set({ kind: 'loading' });

    this.healthService
      .check()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (health) => this.healthState.set({ kind: 'success', health }),
        error: () => this.healthState.set({ kind: 'error' }),
      });
  }
}
