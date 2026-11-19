import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { LoginBox } from './login-box';
import { PHASE_LABEL, fmt, timerColor } from './util';

/** Maître final : saisit les codes proposés par les équipes. */
@Component({
  selector: 'app-final',
  imports: [RouterLink, LoginBox],
  template: `
    @if (!logged()) {
      <div class="wrap">
        <h1>Maître final</h1>
        <login-box role="final" label="Code PIN du maître final" placeholder="••••" [secret]="true" (done)="onLogin()" />
        <a routerLink="/" class="muted">← Accueil</a>
      </div>
    } @else if (ov(); as o) {
      <div class="wrap">
        <div class="row">
          <h1 class="grow" style="margin:0">Finale</h1>
          <button class="btn small ghost" (click)="logout()">Quitter</button>
        </div>
        <div class="card center">
          <div class="muted">{{ label() }}</div>
          @if (o.open) { <div class="timer" style="font-size:3.4rem" [style.color]="color()">{{ time() }}</div> }
          @else { <p class="muted">La saisie s'ouvre au début de la finale.</p> }
        </div>

        @if (toast(); as t) {
          <div class="card center" [style.border-color]="t.ok ? 'var(--green)' : 'var(--red)'">
            <b [class.ok]="t.ok" [class.err]="!t.ok">{{ t.msg }}</b>
          </div>
        }

        <div class="card">
          @for (t of o.teams; track t.idx) {
            <div style="padding:8px 0;border-bottom:1px solid var(--line)">
              <div class="row">
                <div class="grow"><b>{{ t.name }}</b></div>
                @if (t.solved) { <span class="pill green">✔ n°{{ t.arrival }}</span> }
                @else if (t.attemptsLeft === 0) { <span class="pill red">Plus d'essai</span> }
                @else {
                  <span class="pill">{{ t.attemptsLeft }} essai(s)</span>
                  <button class="btn small" [disabled]="!o.open" (click)="select(t.number)">Saisir</button>
                }
              </div>
              @if (sel() === t.number) {
                <div class="row" style="margin-top:8px">
                  <input #inp class="code" placeholder="CODE" (keyup.enter)="submit(inp.value)" autocomplete="off" autofocus>
                  <button class="btn small green" [disabled]="sending()" (click)="submit(inp.value)">Valider</button>
                </div>
              }
            </div>
          }
        </div>
      </div>
    } @else {
      <div class="wrap"><p class="muted">Chargement…</p></div>
    }
  `,
})
export class FinalComponent implements OnDestroy {
  api = inject(Api);
  logged = signal(!!this.api.token('final'));
  ov = signal<any>(null);
  sel = signal<number | null>(null);
  sending = signal(false);
  toast = signal<{ ok: boolean; msg: string } | null>(null);
  private timer: any;
  private toastTimer: any;

  label = computed(() => PHASE_LABEL[this.api.phase()] ?? '');
  time = computed(() => fmt(this.api.remainingMs()));
  color = computed(() => timerColor(this.api.phase(), this.api.remainingMs()));

  constructor() {
    effect(() => {
      this.api.pub()?.version;
      if (this.logged()) untracked(() => this.load());
    });
    this.timer = setInterval(() => { if (this.logged()) this.load(); }, 3000);
  }
  ngOnDestroy() { clearInterval(this.timer); }

  onLogin() { this.logged.set(true); this.load(); }
  logout() { this.api.clearToken('final'); this.logged.set(false); this.ov.set(null); }
  select(n: number) { this.sel.set(this.sel() === n ? null : n); }

  private async load() {
    try { this.ov.set(await this.api.get<any>('/api/final/overview', 'final')); }
    catch (e: any) { if (e?.status === 401 || e?.status === 403) this.logout(); }
  }

  private flash(ok: boolean, msg: string) {
    this.toast.set({ ok, msg });
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toast.set(null), 5000);
  }

  async submit(code: string) {
    const team = this.sel();
    if (!team || !code.trim()) return;
    this.sending.set(true);
    try {
      const r = await this.api.post<any>('/api/final/attempt', { team, code }, 'final');
      if (r.ok) this.flash(true, '🎉 ' + r.team.name + ' a trouvé le code ! Arrivée n°' + r.rank);
      else this.flash(false, '✗ ' + r.team.name + ' : mauvais code. Essais restants : ' + r.attemptsLeft);
      this.sel.set(null);
      await this.load();
    } catch (e: any) {
      this.flash(false, e?.message ?? 'Erreur');
    } finally {
      this.sending.set(false);
    }
  }
}
