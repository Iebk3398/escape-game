import { Component, OnDestroy, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toDataURL } from 'qrcode';
import { Api } from './api';
import { LoginBox } from './login-box';
import { PHASE_LABEL, fmt, timerColor } from './util';

@Component({
  selector: 'app-captain',
  imports: [RouterLink, LoginBox],
  template: `
    @if (!logged()) {
      <div class="wrap">
        <h1>Capitaine</h1>
        <login-box role="captain" label="Code de votre équipe" placeholder="EX. K7P2M" (done)="onLogin()" />
        <a routerLink="/" class="muted">← Accueil</a>
      </div>
    } @else if (me(); as m) {
      <div class="wrap">
        <div class="row">
          <div class="grow">
            <h1 style="margin:0">{{ m.team.name }}</h1>
            @if (m.points !== null) {
              <span class="pill cyan">{{ m.points }} pts</span>
              <span class="pill violet">n° {{ m.rank }}</span>
            } @else {
              <span class="pill orange">Classement masqué</span>
            }
          </div>
          <button class="btn small ghost" (click)="logout()">Quitter</button>
        </div>

        <div class="card center">
          <div class="muted">{{ label() }}
            @if (api.pub()?.phase === 'ROUND' || api.pub()?.phase === 'TRANSITION') {
              · manche {{ api.pub().round + 1 }}/{{ api.pub().totalRounds }}
            }
          </div>
          @if (hasTimer()) {
            <div class="timer" style="font-size:4.5rem" [style.color]="color()">{{ time() }}</div>
          }
          @if (api.pub()?.paused) { <div class="pill orange blink">⏸ En pause</div> }
        </div>

        @switch (api.phase()) {
          @case ('LOBBY') {
            <div class="card">
              <h2>Bienvenue !</h2>
              <p>Le jeu n'a pas encore commencé. Gardez cet écran ouvert et branchez votre téléphone si possible.</p>
              <p class="muted">Première salle : <b>{{ m.assignment.room.name }}</b></p>
            </div>
          }
          @case ('TRANSITION') {
            <div class="card center" style="border-color:var(--blue)">
              <div class="muted">Direction</div>
              <h1 style="font-size:2rem;color:var(--blue)">{{ m.assignment.room.name }}</h1>
              @if (m.assignment.room.rest) {
                <p>Prenez une vraie pause, vous l'avez méritée.</p>
              } @else {
                <p>Face à <b>{{ m.assignment.opponent.name }}</b></p>
              }
              <p class="muted">Montrez ce QR au maître du jeu en entrant.</p>
              @if (qrImg()) { <img [src]="qrImg()" alt="QR" style="width:min(80vw,320px);background:#fff;border-radius:12px;padding:8px"> }
            </div>
          }
          @case ('ROUND') {
            <div class="card center">
              <div class="muted">Vous êtes en</div>
              <h2 style="color:var(--green)">{{ m.assignment.room.name }}</h2>
              @if (!m.assignment.room.rest) { <p>Face à <b>{{ m.assignment.opponent.name }}</b></p> }
              <button class="btn small ghost" (click)="showQr.set(!showQr())">{{ showQr() ? 'Masquer' : 'Afficher' }} mon QR</button>
              @if (showQr() && qrImg()) {
                <div style="margin-top:10px"><img [src]="qrImg()" alt="QR" style="width:min(70vw,280px);background:#fff;border-radius:12px;padding:8px"></div>
              }
            </div>
          }
          @case ('VALIDATION') {
            <div class="card center" style="border-color:var(--violet)">
              <h2>Temps écoulé !</h2>
              <p>Le maître du jeu valide les scores.</p>
              @if (m.next) {
                <p class="muted">Préparez-vous : prochaine salle</p>
                <h2 style="color:var(--blue)">{{ m.next.room.name }}</h2>
                @if (!m.next.room.rest) { <p>Face à <b>{{ m.next.opponent.name }}</b></p> }
              } @else {
                <p class="muted">Prochaine étape</p>
                <h2 style="color:var(--orange)">{{ m.finalRoomName }}</h2>
              }
            </div>
          }
          @case ('FINAL_TRANSIT') {
            <div class="card center" style="border-color:var(--orange)">
              <h2 style="color:var(--orange)">Rendez-vous : {{ m.finalRoomName }}</h2>
              <p>Emportez tous vos indices.</p>
            </div>
          }
          @case ('FINAL') {
            <div class="card" style="border-color:var(--orange)">
              <h2 style="color:var(--orange)">Trouvez le code !</h2>
              @if (m.riddle) { <p>{{ m.riddle }}</p> }
              @if (m.solved) {
                <p class="ok">✔ Code trouvé ! Bravo.</p>
              } @else {
                <p>Essais restants : <b>{{ m.attemptsLeft }}</b></p>
                @if (m.cooldownMs > 0) { <p class="muted">Prochain essai possible dans un instant.</p> }
                <p class="muted">Donnez votre code au maître final.</p>
              }
            </div>
          }
          @case ('FINISHED') {
            <div class="card center">
              <h2>Partie terminée</h2>
              @if (m.solved) { <p class="ok">✔ Votre équipe a trouvé le code.</p> }
              @else { <p>Merci d'avoir joué !</p> }
              <a routerLink="/ranking" class="btn small">Voir le classement</a>
            </div>
          }
        }

        <div class="card">
          <h3>Vos indices ({{ m.clues.length }})</h3>
          @if (m.clues.length === 0) { <p class="muted">Aucun indice pour l'instant.</p> }
          @for (c of m.clues; track c.round) {
            <div class="clue">
              <div class="sym">{{ c.symbol }}</div>
              <div class="frag">{{ c.fragment }}</div>
              <div class="grow">
                <div class="muted">{{ c.roomName }}</div>
                @if (c.bonus) { <div class="bonus">★ Bonus : {{ c.bonus }}</div> }
              </div>
            </div>
          }
        </div>
      </div>
    } @else {
      <div class="wrap"><p class="muted">Chargement…</p>
        @if (error()) { <p class="err">{{ error() }}</p><button class="btn small ghost" (click)="logout()">Se reconnecter</button> }
      </div>
    }
  `,
})
export class CaptainComponent implements OnDestroy {
  api = inject(Api);
  logged = signal(!!this.api.token('captain'));
  me = signal<any>(null);
  qrImg = signal('');
  showQr = signal(false);
  error = signal('');
  private timer: any;
  private lastQr = '';

  label = computed(() => PHASE_LABEL[this.api.phase()] ?? '');
  hasTimer = computed(() => !['LOBBY', 'FINISHED'].includes(this.api.phase()));
  time = computed(() => fmt(this.api.remainingMs()));
  color = computed(() => timerColor(this.api.phase(), this.api.remainingMs()));

  constructor() {
    effect(() => {
      this.api.pub()?.version;
      if (this.logged()) untracked(() => this.load());
    });
    effect(() => {
      const q = this.me()?.qr;
      if (q && q !== this.lastQr) untracked(() => this.makeQr(q));
    });
    effect(() => {
      this.api.phase();
      untracked(() => {
        try {
          if (navigator.userActivation?.hasBeenActive) navigator.vibrate?.([200, 100, 200]);
        } catch { /* ignoré */ }
      });
    });
    this.timer = setInterval(() => { if (this.logged()) this.load(); }, 3000);
  }

  ngOnDestroy() { clearInterval(this.timer); }

  onLogin() { this.logged.set(true); this.load(); }

  logout() { this.api.clearToken('captain'); this.logged.set(false); this.me.set(null); this.error.set(''); }

  private async makeQr(q: string) {
    this.lastQr = q;
    try { this.qrImg.set(await toDataURL(q, { width: 320, margin: 1 })); } catch { /* ignoré */ }
  }

  private async load() {
    try {
      this.me.set(await this.api.get<any>('/api/captain/me', 'captain'));
      this.error.set('');
    } catch (e: any) {
      if (e?.status === 401 || e?.status === 403) this.logout();
      else this.error.set(e?.message ?? 'Erreur');
    }
  }
}
