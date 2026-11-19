package be.ilyteq.escape.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Machine à états du jeu, pilotée par le temps du serveur (aucune horloge client).
 * LOBBY -> TRANSITION(0) -> ROUND(0) -> VALIDATION(0) -> TRANSITION(1) -> ... -> VALIDATION(4)
 *       -> FINAL_TRANSIT -> FINAL -> FINISHED.
 * Le timer enchaîne tout seul : aucune action humaine n'est nécessaire entre deux phases.
 */
public class Machine {
    public record Event(String type, int round) {}

    public Phase phase = Phase.LOBBY;
    public int round = 0;
    public long phaseEndsAt = 0;
    public boolean paused = false;
    public long pausedRemainingMs = 0;
    /** Dernière manche terminée (-1 si aucune) : les scores de ces manches peuvent être validés. */
    public int lastFinishedRound = -1;

    public Machine() {}

    public boolean isRunning() {
        return phase != Phase.LOBBY && phase != Phase.FINISHED;
    }

    public void start(long now, Durations d) {
        if (phase != Phase.LOBBY) return;
        phase = Phase.TRANSITION;
        round = 0;
        paused = false;
        phaseEndsAt = now + d.transitionSec * 1000L;
    }

    public long remainingMs(long now) {
        if (!isRunning()) return 0;
        if (paused) return pausedRemainingMs;
        return Math.max(0, phaseEndsAt - now);
    }

    public void pause(long now) {
        if (!isRunning() || paused) return;
        paused = true;
        pausedRemainingMs = Math.max(0, phaseEndsAt - now);
    }

    public void resume(long now) {
        if (!isRunning() || !paused) return;
        paused = false;
        phaseEndsAt = now + pausedRemainingMs;
    }

    /** Termine immédiatement la phase en cours (admin). */
    public List<Event> skip(long now, Durations d) {
        if (!isRunning()) return new ArrayList<>();
        paused = false;
        phaseEndsAt = now;
        return tick(now, d);
    }

    /** Avance les phases dont l'échéance est dépassée ; renvoie les événements survenus dans l'ordre. */
    public List<Event> tick(long now, Durations d) {
        List<Event> events = new ArrayList<>();
        if (paused) return events;
        while (isRunning() && now >= phaseEndsAt) {
            advance(phaseEndsAt, d, events);
        }
        return events;
    }

    private void advance(long base, Durations d, List<Event> events) {
        switch (phase) {
            case TRANSITION -> {
                phase = Phase.ROUND;
                phaseEndsAt = base + d.roundSec * 1000L;
                events.add(new Event("ROUND_STARTED", round));
            }
            case ROUND -> {
                phase = Phase.VALIDATION;
                lastFinishedRound = round;
                phaseEndsAt = base + d.validationSec * 1000L;
                events.add(new Event("ROUND_ENDED", round));
            }
            case VALIDATION -> {
                events.add(new Event("VALIDATION_ENDED", round));
                if (round + 1 < d.rounds) {
                    round++;
                    phase = Phase.TRANSITION;
                    phaseEndsAt = base + d.transitionSec * 1000L;
                } else {
                    phase = Phase.FINAL_TRANSIT;
                    phaseEndsAt = base + d.finalTransitSec * 1000L;
                }
            }
            case FINAL_TRANSIT -> {
                phase = Phase.FINAL;
                phaseEndsAt = base + d.finalSec * 1000L;
                events.add(new Event("FINAL_STARTED", round));
            }
            case FINAL -> {
                phase = Phase.FINISHED;
                phaseEndsAt = 0;
                events.add(new Event("FINISHED", round));
            }
            default -> { }
        }
    }

    /** Remet le jeu à zéro. */
    public void reset() {
        phase = Phase.LOBBY;
        round = 0;
        phaseEndsAt = 0;
        paused = false;
        pausedRemainingMs = 0;
        lastFinishedRound = -1;
    }
}
