import { Routes } from '@angular/router';
import { HomeComponent } from './home';
import { CaptainComponent } from './captain';
import { MasterComponent } from './master';
import { ScreenComponent } from './screen';
import { RankingComponent } from './ranking';
import { AdminComponent } from './admin';
import { FinalComponent } from './final';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'captain', component: CaptainComponent },
  { path: 'master', component: MasterComponent },
  { path: 'screen/:room', component: ScreenComponent },
  { path: 'ranking', component: RankingComponent },
  { path: 'final', component: FinalComponent },
  { path: 'admin', component: AdminComponent },
  { path: '**', redirectTo: '' },
];
