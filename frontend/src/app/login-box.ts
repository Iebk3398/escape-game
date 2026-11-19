import { Component, input, output, signal } from '@angular/core';
import { Api } from './api';

@Component({
  selector: 'login-box',
  template: `
    <div class="card">
      <h2>{{ label() }}</h2>
      <input class="code" [type]="secret() ? 'password' : 'text'" #inp [placeholder]="placeholder()"
             (keyup.enter)="go(inp.value)" autocomplete="off" autocapitalize="characters" autocorrect="off">
      <button class="btn" style="margin-top:10px" (click)="go(inp.value)" [disabled]="busy()">Entrer</button>
      @if (error()) { <p class="err">{{ error() }}</p> }
    </div>
  `,
})
export class LoginBox {
  role = input.required<string>();
  room = input<number | undefined>(undefined);
  label = input('Connexion');
  placeholder = input('Code');
  secret = input(false);
  done = output<void>();
  busy = signal(false);
  error = signal('');

  constructor(private api: Api) {}

  async go(code: string) {
    if (!code.trim()) return;
    this.busy.set(true);
    this.error.set('');
    try {
      const body: any = { role: this.role(), code };
      if (this.room() !== undefined) body.room = this.room();
      const res = await this.api.post<any>('/api/login', body);
      this.api.setToken(this.role(), res.token);
      this.done.emit();
    } catch (e: any) {
      this.error.set(e?.message ?? 'Erreur');
    } finally {
      this.busy.set(false);
    }
  }
}
