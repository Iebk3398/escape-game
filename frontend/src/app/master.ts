import { Component, ElementRef, OnDestroy, computed, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import jsQR from 'jsqr';
import { Api } from './api';
import { LoginBox } from './login-box';
import { PHASE_LABEL, fmt, timerColor } from './util';

@Component({
  selector: 'app-master',
  imports: [RouterLink, LoginBox],
  template: `
    @if (!logged()) {
      <div class="wrap">
        <h1>Maître du jeu</h1>
        <div class="card">
          <h3>Votre salle</h3>
          <select #sel (change)="roomChoice.set(+sel.value)">
            <option value="-1">Choisir…</option>
            @for (r of rooms(); track r.idx) { <option [value]="r.idx">{{ r.name }}</option> }
          </select>
        </div>
        @if (roomChoice() >= 0) {
          <login-box role="master" [room]="roomChoice()" label="Code PIN de la salle" placeholder="••••" [secret]="true" (done)="onLogin()" />
        }
        <a routerLink="/" class="muted">← Accueil</a>
      </div>
    } @else if (me(); as m) {
      <div class="wrap">
        <div class="row">
          <div class="grow">
            <h1 style="margin:0;font-size:1.4rem">{{ m.room.name }}</h1>
            <span class="muted">Manche {{ api.pub()?.round + 1 }}/{{ api.pub()?.totalRounds }}</span>
          </div>
          <button class="btn small ghost" (click)="logout()">Quitter</button>
        </div>

        @if (m.pending && api.phase() === 'VALIDATION') {
          <div class="card center blink" style="border-color:var(--violet);background:#2a1f5a">
            <h2 style="margin:0">⚠ Validez les scores !</h2>
            <div class="timer" style="font-size:2.6rem;color:var(--violet)">{{ time() }}</div>
          </div>
        } @else if (m.pending) {
          <div class="card center" style="border-color:var(--orange)">
            <b style="color:var(--orange)">Score de la manche {{ m.pending.round + 1 }} en attente de validation</b>
          </div>
        }

        @if (!(m.pending && api.phase() === 'VALIDATION')) {
          <div class="card center">
            <div class="muted">{{ label() }}</div>
            @if (hasTimer()) {
              <div class="timer" style="font-size:3.6rem" [style.color]="color()">{{ time() }}</div>
            }
            @if (api.pub()?.paused) { <div class="pill orange blink">⏸ En pause</div> }
          </div>
        }

        @if (!m.room.rest) {
          <div class="card">
            <h3>Équipes attendues (manche {{ m.round + 1 }})</h3>
            @if (m.expected.length === 0) { <p class="muted">Aucune équipe dans cette salle pour cette manche.</p> }
            @for (t of m.expected; track t.idx) {
              <div class="row" style="padding:6px 0">
                <div class="grow"><b>{{ t.name }}</b></div>
                @if (t.present) { <span class="pill green">✓ présente</span> }
                @else if (m.canScan) { <button class="btn small ghost" (click)="manual(t.number)">Marquer présente</button> }
                @else { <span class="pill">absente</span> }
              </div>
            }
            @if (m.canScan && m.expected.length) {
              <div style="margin-top:10px"><button class="btn" (click)="startScan()">📷 Scanner une équipe</button></div>
            } @else {
              <p class="muted">Le scan est ouvert pendant la transition et la manche.</p>
            }
          </div>
        } @else {
          <div class="card">
            <h3>Salle de repos</h3>
            <p class="muted">Rien à valider ici. Scannez les équipes pour suivre leur présence.</p>
            @for (t of m.expected; track t.idx) {
              <div class="row" style="padding:6px 0">
                <div class="grow"><b>{{ t.name }}</b></div>
                @if (t.present) { <span class="pill green">✓ présente</span> }
                @else if (m.canScan) { <button class="btn small ghost" (click)="manual(t.number)">Marquer présente</button> }
              </div>
            }
            @if (m.canScan) { <button class="btn" (click)="startScan()">📷 Scanner une équipe</button> }
          </div>
        }

        @if (m.pending) {
          <div class="card" style="border-color:var(--violet)">
            <h3>Résultat de la manche {{ m.pending.round + 1 }}</h3>
            <div class="big-btns">
              <button class="btn" [class.sel]="choice() === 'A'" (click)="choice.set('A')">🏆 {{ m.pending.a.name }} gagne</button>
              <button class="btn orange" [class.sel]="choice() === 'DRAW'" (click)="choice.set('DRAW')">🤝 Égalité</button>
              <button class="btn" [class.sel]="choice() === 'B'" (click)="choice.set('B')">🏆 {{ m.pending.b.name }} gagne</button>
            </div>
            <p class="muted" style="margin:10px 0 0">{{ preview(m) }}</p>
            <div style="margin-top:10px">
              <button class="btn green" [disabled]="!choice() || sending()" (click)="validate()">✔ Valider le score</button>
            </div>
          </div>
        }

        @if (m.done.length) {
          <div class="card">
            <h3>Déjà validé</h3>
            @for (d of m.done; track d.round) {
              <div class="row" style="padding:4px 0">
                <span class="pill">Manche {{ d.round + 1 }}</span>
                <span>{{ summary(d) }}</span>
              </div>
            }
          </div>
        }
        @if (toast(); as t) {
          <div class="card center" [style.border-color]="t.ok ? 'var(--green)' : 'var(--red)'">
            <b [class.ok]="t.ok" [class.err]="!t.ok">{{ t.msg }}</b>
          </div>
        }
      </div>

      @if (scanning()) {
        <div style="position:fixed;inset:0;background:#000;z-index:10;display:flex;flex-direction:column">
          <video #video playsinline muted style="flex:1;width:100%;object-fit:cover"></video>
          <div style="position:absolute;top:12px;left:12px;right:12px;text-align:center">
            @if (toast(); as t) {
              <div class="card" [style.border-color]="t.ok ? 'var(--green)' : 'var(--red)'"><b [class.ok]="t.ok" [class.err]="!t.ok">{{ t.msg }}</b></div>
            } @else {
              <div class="card"><b>Cadrez le QR de l'équipe</b></div>
            }
          </div>
          <div style="padding:12px"><button class="btn red" (click)="stopScan()">Fermer la caméra</button></div>
        </div>
      }
    } @else {
      <div class="wrap"><p class="muted">Chargement…</p>
        @if (error()) { <p class="err">{{ error() }}</p><button class="btn small ghost" (click)="logout()">Se reconnecter</button> }
      </div>
    }
  `,
})
export class MasterComponent implements OnDestroy {
  api = inject(Api);
  logged = signal(!!this.api.token('master'));
  me = signal<any>(null);
  roomChoice = signal(-1);
  choice = signal<'A' | 'B' | 'DRAW' | ''>('');
  sending = signal(false);
  scanning = signal(false);
  toast = signal<{ ok: boolean; msg: string } | null>(null);
  error = signal('');
  video = viewChild<ElementRef<HTMLVideoElement>>('video');

  private timer: any;
  private stream?: MediaStream;
  private raf = 0;
  private busy = false;
  private canvas = document.createElement('canvas');
  private toastTimer: any;
  private warned = false;

  rooms = computed<any[]>(() => this.api.pub()?.rooms ?? []);
  label = computed(() => PHASE_LABEL[this.api.phase()] ?? '');
  hasTimer = computed(() => !['LOBBY', 'FINISHED'].includes(this.api.phase()));
  time = computed(() => fmt(this.api.remainingMs()));
  color = computed(() => timerColor(this.api.phase(), this.api.remainingMs()));

  constructor() {
    effect(() => {
      this.api.pub()?.version;
      if (this.logged()) untracked(() => this.load());
    });
    // Rappel : vibration quand la validation s'ouvre, puis à 30 s de la fin
    effect(() => {
      const ph = this.api.phase();
      if (ph === 'VALIDATION') untracked(() => { this.warned = false; this.vibrate([300, 150, 300]); });
    });
    effect(() => {
      const ms = this.api.remainingMs();
      if (this.api.phase() === 'VALIDATION' && ms <= 30_000 && !this.warned && this.me()?.pending) {
        this.warned = true;
        untracked(() => this.vibrate([500, 200, 500, 200, 500]));
      }
    });
    this.timer = setInterval(() => { if (this.logged()) this.load(); }, 3000);
  }

  ngOnDestroy() {
    clearInterval(this.timer);
    this.stopScan();
  }

  private vibrate(p: number[]) {
    try {
      if (navigator.userActivation?.hasBeenActive) navigator.vibrate?.(p);
    } catch { /* ignoré */ }
  }

  onLogin() { this.logged.set(true); this.load(); }

  logout() { this.api.clearToken('master'); this.logged.set(false); this.me.set(null); this.error.set(''); this.stopScan(); }

  private async load() {
    try {
      const m = await this.api.get<any>('/api/master/me', 'master');
      if (!m.pending) this.choice.set('');
      this.me.set(m);
      this.error.set('');
    } catch (e: any) {
      if (e?.status === 401 || e?.status === 403) this.logout();
      else this.error.set(e?.message ?? 'Erreur');
    }
  }

  private flash(ok: boolean, msg: string) {
    this.toast.set({ ok, msg });
    clearTimeout(this.toastTimer);
    this.toastTimer = setTimeout(() => this.toast.set(null), 3500);
  }

  async manual(teamNumber: number) {
    try {
      const r = await this.api.post<any>('/api/master/present', { team: teamNumber }, 'master');
      this.flash(true, r.team.name + ' ✓ présente');
      await this.load();
    } catch (e: any) { this.flash(false, e?.message ?? 'Erreur'); }
  }

  preview(m: any): string {
    const c = this.choice();
    if (!c) return 'Choisissez le résultat puis validez.';
    if (c === 'DRAW') return 'Égalité : 2 pts chacune, 1 indice chacune.';
    const w = c === 'A' ? m.pending.a.name : m.pending.b.name;
    const l = c === 'A' ? m.pending.b.name : m.pending.a.name;
    return w + ' : 3 pts + 1 indice + 1 indice bonus. ' + l + ' : 1 pt + 1 indice.';
  }

  summary(d: any): string {
    if (d.outcome === 'A') return d.a.name + ' gagne vs ' + d.b.name;
    if (d.outcome === 'B') return d.b.name + ' gagne vs ' + d.a.name;
    if (d.outcome === 'DRAW') return 'Égalité ' + d.a.name + ' / ' + d.b.name;
    return 'Repos';
  }

  async validate() {
    if (!this.choice()) return;
    this.sending.set(true);
    try {
      await this.api.post('/api/master/result', { outcome: this.choice() }, 'master');
      this.flash(true, 'Score validé ✔');
      this.choice.set('');
      await this.load();
    } catch (e: any) {
      this.flash(false, e?.message ?? 'Erreur');
    } finally {
      this.sending.set(false);
    }
  }

  // ---- scanner ------------------------------------------------------------------
  async startScan() {
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
    } catch {
      this.flash(false, "Caméra indisponible : utilisez « Marquer présente ».");
      return;
    }
    this.scanning.set(true);
    setTimeout(() => {
      const v = this.video()?.nativeElement;
      if (!v || !this.stream) return;
      v.srcObject = this.stream;
      v.setAttribute('playsinline', 'true');
      v.play().catch(() => undefined);
      this.loop();
    }, 150);
  }

  stopScan() {
    this.scanning.set(false);
    cancelAnimationFrame(this.raf);
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = undefined;
  }

  private loop = () => {
    const v = this.video()?.nativeElement;
    if (!this.scanning() || !v) return;
    if (v.readyState === v.HAVE_ENOUGH_DATA && !this.busy) {
      const w = v.videoWidth, h = v.videoHeight;
      if (w && h) {
        this.canvas.width = w;
        this.canvas.height = h;
        const ctx = this.canvas.getContext('2d', { willReadFrequently: true });
        if (ctx) {
          ctx.drawImage(v, 0, 0, w, h);
          const img = ctx.getImageData(0, 0, w, h);
          const code = jsQR(img.data, w, h, { inversionAttempts: 'dontInvert' });
          if (code?.data) { this.busy = true; this.handleQr(code.data); }
        }
      }
    }
    this.raf = requestAnimationFrame(this.loop);
  };

  private async handleQr(data: string) {
    try {
      const r = await this.api.post<any>('/api/master/scan', { qr: data }, 'master');
      this.flash(true, r.team.name + (r.already ? ' (déjà scannée)' : ' ✓ présente'));
      this.vibrate([120]);
    } catch (e: any) {
      this.flash(false, e?.message ?? 'Erreur');
      this.vibrate([400]);
    }
    setTimeout(() => { this.busy = false; }, 1800);
    this.load();
  }
}
