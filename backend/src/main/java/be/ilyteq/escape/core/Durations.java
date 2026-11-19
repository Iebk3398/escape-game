package be.ilyteq.escape.core;

/** Durées en secondes. Toutes configurables (application.yml / variables d'environnement). */
public class Durations {
    public int roundSec = 600;        // 10 min par manche
    public int validationSec = 60;    // 1 min pour valider
    public int transitionSec = 120;   // 2 min de transition
    public int finalTransitSec = 120; // 2 min pour rejoindre la finale
    public int finalSec = 1200;       // 20 min de finale
    public int rounds = Rotation.ROUNDS;

    public Durations() {}

    public Durations(int roundSec, int validationSec, int transitionSec, int finalTransitSec, int finalSec) {
        this.roundSec = roundSec;
        this.validationSec = validationSec;
        this.transitionSec = transitionSec;
        this.finalTransitSec = finalTransitSec;
        this.finalSec = finalSec;
    }
}
