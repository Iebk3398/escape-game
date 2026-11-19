import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { PHASE_LABEL, fmt } from './util';

@Component({
  selector: 'app-home',
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <h1>{{ api.pub()?.event ?? 'Escape Game' }}</h1>
      @if (api.pub()?.demo) { <p><span class="pill orange">MODE DÉMO · 2 équipes · codes DEMO1 / DEMO2</span></p> }
      <p class="muted">
        {{ label() }}
        @if (!api.online()) { <span class="err"> · hors ligne</span> }
      </p>

      <a routerLink="/captain" class="btn" style="display:flex;align-items:center;justify-content:center;text-decoration:none">Je suis capitaine d'équipe</a>
      <div style="height:10px"></div>
      <a routerLink="/master" class="btn violet" style="display:flex;align-items:center;justify-content:center;text-decoration:none">Je suis maître du jeu</a>

      <div class="card">
        <h3>Écrans</h3>
        <div class="row">
          @for (r of [1,2,3,4,5]; track r) {
            <a class="btn small ghost" [routerLink]="['/screen', r]" style="text-decoration:none;display:flex;align-items:center">Salle {{ r }}</a>
          }
          <a class="btn small ghost" routerLink="/ranking" style="text-decoration:none;display:flex;align-items:center">Classement</a>
        </div>
      </div>

      <div class="card">
        <h3>Organisation</h3>
        <div class="row">
          <a class="btn small ghost" routerLink="/final" style="text-decoration:none;display:flex;align-items:center">Maître final</a>
          <a class="btn small ghost" routerLink="/admin" style="text-decoration:none;display:flex;align-items:center">Admin</a>
        </div>
      </div>
    </div>
  `,
})
export class HomeComponent {
  api = inject(Api);
  label = computed(() => {
    const p = this.api.pub();
    if (!p) return 'Connexion…';
    const base = PHASE_LABEL[p.phase] ?? p.phase;
    return this.api.remainingMs() > 0 ? base + ' · ' + fmt(this.api.remainingMs()) : base;
  });
}
