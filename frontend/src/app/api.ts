import { Injectable, computed, signal } from '@angular/core';

export class ApiError extends Error {
  constructor(public code: string, message: string, public status: number) {
    super(message);
  }
}

/** Accès à l'API + état public synchronisé avec l'horloge du serveur. */
@Injectable({ providedIn: 'root' })
export class Api {
  readonly pub = signal<any>(null);
  readonly online = signal(true);
  private readonly now = signal(Date.now());
  private readonly offset = signal(0);

  /** Heure du serveur estimée (ms). */
  readonly serverNow = computed(() => this.now() + this.offset());

  /** Temps restant dans la phase en cours (ms), identique sur tous les écrans à ~100 ms près. */
  readonly remainingMs = computed(() => {
    const p = this.pub();
    if (!p || p.phase === 'LOBBY' || p.phase === 'FINISHED') return 0;
    if (p.paused) return p.remainingMs;
    return Math.max(0, p.phaseEndsAt - this.serverNow());
  });

  readonly phase = computed<string>(() => this.pub()?.phase ?? 'LOBBY');

  private lastRefresh = 0;

  constructor() {
    setInterval(() => {
      this.now.set(Date.now());
      this.refreshIfPhaseOver();
    }, 250);
    this.refresh();
    this.connect();
    setInterval(() => this.refresh(), 2500);
  }

  /** Quand le chrono arrive à 0, on interroge le serveur tout de suite (sans attendre le prochain rafraîchissement). */
  private refreshIfPhaseOver() {
    const p = this.pub();
    if (!p || p.paused || p.phase === 'LOBBY' || p.phase === 'FINISHED') return;
    if (this.remainingMs() <= 0 && Date.now() - this.lastRefresh > 800) this.refresh();
  }

  private connect() {
    try {
      const es = new EventSource('/api/stream');
      es.addEventListener('state', () => this.refresh());
    } catch {
      // le polling toutes les 4 s prend le relais
    }
  }

  async refresh() {
    this.lastRefresh = Date.now();
    try {
      const t0 = Date.now();
      const p = await this.get<any>('/api/public');
      const t1 = Date.now();
      this.offset.set(p.serverNow - (t0 + t1) / 2);
      this.pub.set(p);
      this.online.set(true);
    } catch {
      this.online.set(false);
    }
  }

  // ---- jetons (un par rôle) --------------------------------------------------
  token(role: string): string | null {
    try { return localStorage.getItem('escape.' + role); } catch { return null; }
  }
  setToken(role: string, token: string) {
    try { localStorage.setItem('escape.' + role, token); } catch { /* ignoré */ }
  }
  clearToken(role: string) {
    try { localStorage.removeItem('escape.' + role); } catch { /* ignoré */ }
  }

  // ---- HTTP ---------------------------------------------------------------------
  async call<T>(method: string, path: string, body?: unknown, role?: string): Promise<T> {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const tok = role ? this.token(role) : null;
    if (tok) headers['Authorization'] = 'Bearer ' + tok;
    let res: Response;
    try {
      res = await fetch(path, {
        method, headers, cache: 'no-store',
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      throw new ApiError('NETWORK', 'Pas de connexion au serveur.', 0);
    }
    const text = await res.text();
    let json: any = null;
    try { json = text ? JSON.parse(text) : null; } catch { /* corps non JSON */ }
    if (!res.ok) throw new ApiError(json?.error ?? 'HTTP', json?.message ?? 'Erreur ' + res.status, res.status);
    return json as T;
  }
  get<T>(path: string, role?: string) { return this.call<T>('GET', path, undefined, role); }
  post<T>(path: string, body: unknown, role?: string) { return this.call<T>('POST', path, body, role); }
}
