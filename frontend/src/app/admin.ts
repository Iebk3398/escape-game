import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Api } from './api';
import { LoginBox } from './login-box';
import { PHASE_LABEL, fmt, timerColor } from './util';

@Component({
  selector: 'app-admin',
  imports: [RouterLink, LoginBox],
  template: `
    @if (!logged()) {
      <div class="wrap">
        <h1>Admin</h1>
        <login-box role="admin" label="Mot de passe admin" placeholder="Mot de passe" [secret]="true" (done)="onLogin()" />
        <a routerLink="/" class="muted">← Accueil</a>
      </div>
    } @else if (ov(); as o) {
      <div class="wrap">
        <div class="row">
          <h1 class="grow" style="margin:0">Admin</h1>
          @if (api.pub()?.demo) { <span class="pill orange">MODE DÉMO</span> }
          <button class="btn small ghost" (click)="logout()">Quitter</button>
        </div>

        <div class="card center">
          <div class="muted">{{ label() }} @if (o.phase !== 'LOBBY' && o.phase !== 'FINISHED') { · manche {{ o.round + 1 }} }</div>
          @if (hasTimer()) { <div class="timer" style="font-size:3.4rem" [style.color]="color()">{{ time() }}</div> }
          @if (o.paused) { <div class="pill orange">⏸ En pause</div> }
          <div class="muted" style="margin-top:6px">Équipes présentes (manche en cours) : {{ o.presentThisRound }}/10</div>
        </div>

        <div class="card">
          <h3>Pilotage</h3>
          <div class="big-btns">
            @if (o.phase === 'LOBBY') { <button class="btn green" (click)="act('start')">▶ Lancer le jeu</button> }
            @if (o.phase !== 'LOBBY' && o.phase !== 'FINISHED') {
              @if (o.paused) { <button class="btn green" (click)="act('resume')">▶ Reprendre</button> }
              @else { <button class="btn orange" (click)="act('pause')">⏸ Pause (tous les chronos)</button> }
              <button class="btn violet" (click)="act('skip')">⏭ Terminer la phase en cours</button>
            }
          </div>
          <div class="row" style="margin-top:12px">
            <input #rs placeholder="Tapez RESET pour tout remettre à zéro" style="font-size:.95rem">
            <button class="btn small red" (click)="act('reset', rs.value); rs.value=''">Reset</button>
          </div>
          @if (msg()) { <p [class.err]="isErr()" [class.ok]="!isErr()">{{ msg() }}</p> }
        </div>

        @if (o.pending.length) {
          <div class="card" style="border-color:var(--red)">
            <h3 style="color:var(--red)">Scores non validés ({{ o.pending.length }})</h3>
            @for (p of o.pending; track p.round + '-' + p.room) {
              <div style="padding:8px 0;border-bottom:1px solid var(--line)">
                <div><b>Manche {{ p.round + 1 }}</b> · {{ p.roomName }}</div>
                <div class="muted">{{ p.a.name }} vs {{ p.b.name }}</div>
                <div class="row" style="margin-top:6px">
                  <button class="btn small" (click)="setResult(p.round, p.room, 'A')">{{ p.a.name }}</button>
                  <button class="btn small orange" (click)="setResult(p.round, p.room, 'DRAW')">Égalité</button>
                  <button class="btn small" (click)="setResult(p.round, p.room, 'B')">{{ p.b.name }}</button>
                </div>
              </div>
            }
          </div>
        }

        <div class="card">
          <h3>Équipes et codes capitaine</h3>
          <table>
            <tr><th>#</th><th>Nom</th><th>Code</th></tr>
            @for (t of o.teams; track t.idx) {
              <tr>
                <td>{{ t.number }}</td>
                <td><input [value]="t.name" style="min-height:40px;font-size:.95rem" (change)="rename(t.idx, $any($event.target).value)"></td>
                <td><b style="letter-spacing:.15em">{{ t.code }}</b></td>
              </tr>
            }
          </table>
          <div style="margin-top:10px"><button class="btn small" (click)="copyCodes(o)">Copier les codes (WhatsApp)</button></div>
        </div>

        <div class="card">
          <h3>PIN des maîtres de salle</h3>
          <table>
            @for (r of o.masterPins; track r.idx) {
              <tr><td>{{ r.name }}</td><td><b style="letter-spacing:.15em">{{ r.pin }}</b></td></tr>
            }
            <tr><td>Maître final</td><td><b style="letter-spacing:.15em">{{ o.finalPin }}</b></td></tr>
          </table>
        </div>

        <div class="card">
          <h3>Options</h3>
          <label class="row" style="gap:10px">
            <input type="checkbox" style="width:24px;min-height:24px" [checked]="o.freezeRanking" (change)="freeze($any($event.target).checked)">
            <span>Masquer le classement pendant la finale</span>
          </label>
        </div>

        <div class="card">
          <h3>Liens utiles</h3>
          <div class="row">
            @for (r of [1,2,3,4,5]; track r) { <a class="btn small ghost" [routerLink]="['/screen', r]" style="text-decoration:none;display:flex;align-items:center">Écran salle {{ r }}</a> }
            <a class="btn small ghost" routerLink="/ranking" style="text-decoration:none;display:flex;align-items:center">Classement</a>
            <a class="btn small ghost" routerLink="/final" style="text-decoration:none;display:flex;align-items:center">Maître final</a>
          </div>
        </div>
      </div>
    } @else {
      <div class="wrap"><p class="muted">Chargement…</p></div>
    }
  `,
})
export class AdminComponent implements OnDestroy {
  api = inject(Api);
  logged = signal(!!this.api.token('admin'));
  ov = signal<any>(null);
  msg = signal('');
  isErr = signal(false);
  private timer: any;

  label = computed(() => PHASE_LABEL[this.api.phase()] ?? '');
  hasTimer = computed(() => !['LOBBY', 'FINISHED'].includes(this.api.phase()));
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
  logout() { this.api.clearToken('admin'); this.logged.set(false); this.ov.set(null); }

  private async load() {
    try { this.ov.set(await this.api.get<any>('/api/admin/overview', 'admin')); }
    catch (e: any) { if (e?.status === 401 || e?.status === 403) this.logout(); }
  }

  private say(ok: boolean, m: string) { this.isErr.set(!ok); this.msg.set(m); setTimeout(() => this.msg.set(''), 4000); }

  async act(action: string, confirm?: string) {
    try {
      await this.api.post('/api/admin/action', { action, confirm }, 'admin');
      this.say(true, 'OK');
      await this.load();
      this.api.refresh();
    } catch (e: any) { this.say(false, e?.message ?? 'Erreur'); }
  }

  async rename(idx: number, name: string) {
    try { await this.api.post('/api/admin/team', { idx, name }, 'admin'); await this.load(); }
    catch (e: any) { this.say(false, e?.message ?? 'Erreur'); await this.load(); }
  }

  async freeze(value: boolean) {
    try { await this.api.post('/api/admin/freeze', { value }, 'admin'); await this.load(); }
    catch (e: any) { this.say(false, e?.message ?? 'Erreur'); }
  }

  async setResult(round: number, room: number, outcome: string) {
    try {
      await this.api.post('/api/admin/result', { round, room, outcome }, 'admin');
      this.say(true, 'Score enregistré');
      await this.load();
    } catch (e: any) { this.say(false, e?.message ?? 'Erreur'); }
  }

  async copyCodes(o: any) {
    const url = location.origin + '/#/captain';
    const text = 'Escape Game – votre code capitaine (' + url + ') :\n' +
      o.teams.map((t: any) => t.name + ' : ' + t.code).join('\n');
    try { await navigator.clipboard.writeText(text); this.say(true, 'Codes copiés'); }
    catch { this.say(false, 'Copie impossible : sélectionnez les codes à la main.'); }
  }
}
