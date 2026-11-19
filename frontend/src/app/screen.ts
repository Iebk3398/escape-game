import { Component, computed, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Api } from './api';
import { PHASE_COLOR, fmt, timerColor } from './util';
import { RankingComponent } from './ranking';

/** Écran projecteur d'une salle : /#/screen/1 … /#/screen/5 */
@Component({
  selector: 'app-screen',
  imports: [RankingComponent],
  template: `
    @if (api.pub(); as p) {
     @if (isRest(p)) {
      <app-ranking />
      @if (p.paused) { <div class="pause">⏸ PAUSE</div> }
     } @else {
      <div class="stage">
        <div class="top">
          <div class="ev">{{ p.event }}</div>
          <div class="roomname">{{ roomName() }}</div>
          <div class="rnd">
            @if (['TRANSITION','ROUND','VALIDATION'].includes(p.phase)) { Manche {{ p.round + 1 }} / {{ p.totalRounds }} }
          </div>
        </div>

        @switch (p.phase) {
          @case ('LOBBY') {
            <div class="mid"><div class="msg" style="color:var(--cyan)">Le jeu va bientôt commencer</div>
              <div class="sub">Première manche : {{ here() }}</div></div>
          }
          @case ('TRANSITION') {
            <div class="mid">
              <div class="msg" style="color:var(--blue)">Changez de salle !</div>
              <div class="timer big" [style.color]="color()">{{ time() }}</div>
              <div class="sub">À la fin du chrono, la manche démarre toute seule.</div>
              <div class="moves">
                @for (a of p.assignments; track a.room) {
                  <div class="move" [class.me]="a.room === idx">
                    <b>{{ p.rooms[a.room].name }}</b>
                    <span>{{ a.a ? a.a.name + ' · ' + a.b.name : '—' }}</span>
                  </div>
                }
              </div>
            </div>
          }
          @case ('ROUND') {
            <div class="mid">
              <div class="timer big" [style.color]="color()">{{ time() }}</div>
              <div class="vs">
                @if (assign(); as a) {
                  <span>{{ a.a.name }}</span><i>VS</i><span>{{ a.b.name }}</span>
                }
              </div>
            </div>
          }
          @case ('VALIDATION') {
            <div class="mid">
              <div class="msg" style="color:var(--violet)">Temps écoulé !</div>
              <div class="sub">Le maître du jeu valide les scores…</div>
              <div class="timer" style="font-size:7vw;color:var(--violet)">{{ time() }}</div>
            </div>
          }
          @case ('FINAL_TRANSIT') {
            <div class="mid">
              <div class="msg" style="color:var(--orange)">Rendez-vous en {{ p.finalRoomName }}</div>
              <div class="timer big" [style.color]="color()">{{ time() }}</div>
              <div class="sub">Emportez tous vos indices.</div>
            </div>
          }
          @case ('FINAL') {
            <div class="mid">
              <div class="timer big" [style.color]="color()">{{ time() }}</div>
              <div class="sub" style="max-width:80vw">{{ p.finalRiddle }}</div>
            </div>
          }
          @case ('FINISHED') {
            <div class="mid">
              @if (p.finalWinner) {
                <div class="msg" style="color:var(--cyan)">🏆 {{ p.finalWinner.name }}</div>
                <div class="sub">a trouvé le code en premier !</div>
              } @else {
                <div class="msg" style="color:var(--cyan)">Partie terminée</div>
              }
            </div>
          }
        }

        @if (p.paused) { <div class="pause">⏸ PAUSE</div> }
        @if (!api.online()) { <div class="offline">Connexion perdue… le chrono continue</div> }
      </div>
     }
    } @else {
      <div class="stage"><div class="mid"><div class="msg">Connexion…</div></div></div>
    }
  `,
  styles: [`
    .stage { min-height: 100vh; display: flex; flex-direction: column; padding: 2vw 3vw; position: relative; }
    .top { display: flex; justify-content: space-between; align-items: baseline; color: var(--muted); font-size: 2vw; }
    .roomname { color: var(--text); font-weight: 800; font-size: 3vw; }
    .mid { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 1.5vw; }
    .msg { font-size: 6vw; font-weight: 800; line-height: 1.1; }
    .sub { font-size: 2.4vw; color: var(--muted); }
    .big { font-size: 24vw; }
    .vs { display: flex; align-items: center; gap: 3vw; font-size: 5vw; font-weight: 800; }
    .vs i { font-style: normal; color: var(--muted); font-size: 3vw; }
    .moves { display: grid; grid-template-columns: repeat(5, 1fr); gap: 1vw; width: 100%; margin-top: 1vw; }
    .move { background: var(--panel); border: 1px solid var(--line); border-radius: 1vw; padding: 1vw; display: flex; flex-direction: column; gap: .4vw; font-size: 1.6vw; }
    .move.me { border-color: var(--cyan); background: var(--panel2); }
    .move span { color: var(--muted); }
    .pause { position: fixed; inset: 0; z-index: 5; background: rgba(15,25,56,.75); display: flex; align-items: center; justify-content: center; font-size: 12vw; font-weight: 800; color: var(--orange); }
    .offline { position: absolute; bottom: 1vw; left: 0; right: 0; text-align: center; color: var(--red); font-size: 1.6vw; }
  `],
})
export class ScreenComponent {
  api = inject(Api);
  idx = Number(inject(ActivatedRoute).snapshot.paramMap.get('room')) - 1;

  roomName = computed(() => this.api.pub()?.rooms?.[this.idx]?.name ?? 'Salle ' + (this.idx + 1));
  time = computed(() => fmt(this.api.remainingMs()));
  color = computed(() => timerColor(this.api.phase(), this.api.remainingMs()));
  assign = computed(() => this.api.pub()?.assignments?.find((a: any) => a.room === this.idx && a.a));
  here = computed(() => {
    const a = this.assign();
    return a ? a.a.name + ' · ' + a.b.name : '';
  });
  isRest = (p: any) => !!p.rooms?.[this.idx]?.rest;
  readonly _ = PHASE_COLOR;
}
