export function fmt(ms: number): string {
  const s = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(s / 60);
  const r = s % 60;
  return String(m).padStart(2, '0') + ':' + String(r).padStart(2, '0');
}

export const PHASE_LABEL: Record<string, string> = {
  LOBBY: 'En attente du lancement',
  TRANSITION: 'Changement de salle',
  ROUND: 'Épreuve en cours',
  VALIDATION: 'Validation des scores',
  FINAL_TRANSIT: 'Direction la salle finale',
  FINAL: 'Épreuve finale',
  FINISHED: 'Terminé',
};

export const PHASE_COLOR: Record<string, string> = {
  LOBBY: 'var(--muted)',
  TRANSITION: 'var(--blue)',
  ROUND: 'var(--green)',
  VALIDATION: 'var(--violet)',
  FINAL_TRANSIT: 'var(--blue)',
  FINAL: 'var(--orange)',
  FINISHED: 'var(--cyan)',
};

/** Couleur du chrono : rouge sous 10 s, orange sous 1 min pendant une manche. */
export function timerColor(phase: string, ms: number): string {
  if ((phase === 'ROUND' || phase === 'FINAL' || phase === 'VALIDATION') && ms <= 10_000) return 'var(--red)';
  if (phase === 'ROUND' && ms <= 60_000) return 'var(--orange)';
  return PHASE_COLOR[phase] ?? 'var(--text)';
}
