import { Component, computed, inject } from '@angular/core';
import { Api } from './api';
import { PHASE_LABEL, fmt } from './util';

/** Classement en direct, à projeter dans une salle dédiée : /#/ranking */
@Component({
  selector: 'app-ranking',
  template: `
    <div class="page">
      <div class="head">
        <h1>🏆 Classement</h1>
        <div class="muted st">{{ status() }}</div>
      </div>

      @if (api.pub(); as p) {
        @if (p.rankingFrozen) {
          <div class="frozen">
            <div class="msg">Classement masqué</div>
            <div class="muted">Place à la finale… suspense !</div>
          </div>
        } @else if (p.ranking) {
          <table>
            @for (r of p.ranking; track r.idx) {
              <tr [class.top3]="r.rank <= 3" [class.win]="p.finalWinner?.idx === r.idx">
                <td class="rk">{{ r.rank }}</td>
                <td class="nm">{{ r.name }} @if (p.finalWinner?.idx === r.idx) { <span>🏆</span> }</td>
                <td class="w">{{ r.wins }} 🏅</td>
                <td class="pt">{{ r.points }}</td>
              </tr>
            }
          </table>
        }
      } @else {
        <p class="muted">Connexion…</p>
      }
    </div>
  `,
  styles: [`
    .page { min-height: 100vh; padding: 2vw 4vw; }
    .head { display: flex; justify-content: space-between; align-items: baseline; }
    h1 { font-size: 4vw; margin: 0 0 1vw; }
    .st { font-size: 1.8vw; }
    table { width: 100%; }
    td { font-size: 2.8vw; padding: .7vw 1.5vw; border-bottom: 1px solid var(--line); }
    .rk { width: 8vw; color: var(--muted); font-weight: 800; }
    .nm { font-weight: 700; }
    .w { text-align: right; color: var(--muted); font-size: 2vw; }
    .pt { text-align: right; font-weight: 800; color: var(--cyan); width: 12vw; }
    tr.top3 .rk { color: var(--orange); }
    tr.top3 .nm { color: #fff; }
    tr.win td { background: rgba(34,211,238,.12); }
    .frozen { text-align: center; margin-top: 12vh; }
    .frozen .msg { font-size: 7vw; font-weight: 800; color: var(--orange); }
    .frozen .muted { font-size: 2.6vw; }
  `],
})
export class RankingComponent {
  api = inject(Api);
  status = computed(() => {
    const p = this.api.pub();
    if (!p) return '';
    const base = PHASE_LABEL[p.phase] ?? '';
    const ms = this.api.remainingMs();
    return ms > 0 ? base + ' · ' + fmt(ms) : base;
  });
}
