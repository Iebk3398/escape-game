package be.ilyteq.escape.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Auto-test sans dépendance : javac puis java be.ilyteq.escape.core.CoreSelfTest */
public class CoreSelfTest {
    static int checks = 0;

    static void check(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError("ECHEC : " + msg);
    }

    public static void main(String[] args) {
        rotation();
        machine();
        pauseAndSkip();
        scoring();
        System.out.println("OK - " + checks + " vérifications passées");
    }

    static void rotation() {
        // Chaque équipe passe une fois dans chaque salle
        for (int t = 0; t < Rotation.TEAMS; t++) {
            Set<Integer> rooms = new HashSet<>();
            for (int r = 0; r < Rotation.ROUNDS; r++) rooms.add(Rotation.room(t, r));
            check(rooms.size() == 5, "équipe " + t + " doit voir 5 salles différentes");
        }
        // Deux équipes exactement par salle à chaque manche
        for (int r = 0; r < Rotation.ROUNDS; r++) {
            for (int[] p : Rotation.pairs(r)) {
                check(p[0] >= 0 && p[0] < 5 && p[1] >= 5 && p[1] < 10, "paire valide manche " + r);
            }
        }
        // Chaque équipe affronte 5 adversaires différents, jamais deux fois le même
        for (int t = 0; t < Rotation.TEAMS; t++) {
            Set<Integer> opps = new HashSet<>();
            for (int r = 0; r < Rotation.ROUNDS; r++) {
                int o = Rotation.opponent(t, r);
                check(o != t, "pas d'auto-duel");
                check(Rotation.opponent(o, r) == t, "adversaire symétrique");
                opps.add(o);
            }
            check(opps.size() == 5, "équipe " + t + " : 5 adversaires distincts");
        }
    }

    static void machine() {
        Durations d = new Durations();
        Machine m = new Machine();
        long t0 = 1_000_000L;
        check(m.phase == Phase.LOBBY, "départ en LOBBY");
        m.start(t0, d);
        check(m.phase == Phase.TRANSITION && m.round == 0, "start -> TRANSITION manche 0");
        check(m.remainingMs(t0) == 120_000, "transition 2 min");

        // Pas d'avance avant l'échéance
        check(m.tick(t0 + 119_999, d).isEmpty() && m.phase == Phase.TRANSITION, "toujours en transition à 1:59");
        // Après 2 min : la manche démarre toute seule
        m.tick(t0 + 120_000, d);
        check(m.phase == Phase.ROUND && m.round == 0, "ROUND démarre automatiquement");
        check(m.remainingMs(t0 + 120_000) == 600_000, "manche 10 min");

        // Fin de manche -> validation 1 min
        List<Machine.Event> ev = m.tick(t0 + 120_000 + 600_000, d);
        check(m.phase == Phase.VALIDATION && m.lastFinishedRound == 0, "VALIDATION après la manche");
        check(ev.size() == 1 && ev.get(0).type().equals("ROUND_ENDED"), "événement ROUND_ENDED");
        check(m.remainingMs(t0 + 720_000) == 60_000, "validation 1 min");

        // Rattrapage : on saute très loin, toutes les phases s'enchaînent dans l'ordre
        ev = m.tick(t0 + 10 * 3_600_000L, d);
        check(m.phase == Phase.FINISHED, "tout est terminé après 10 h");
        long rounds = ev.stream().filter(e -> e.type().equals("ROUND_ENDED")).count();
        check(rounds == 4, "4 fins de manche restantes");
        check(ev.stream().anyMatch(e -> e.type().equals("FINAL_STARTED")), "finale démarrée");

        // Durée totale théorique : 2 + 5*(10+1) + 4*2 + 2 + 20 = 87 min
        Machine m2 = new Machine();
        m2.start(0, d);
        m2.tick(87 * 60_000L - 1, d);
        check(m2.phase == Phase.FINAL, "encore en finale à 86:59");
        m2.tick(87 * 60_000L, d);
        check(m2.phase == Phase.FINISHED, "fini à 87:00 (2+55+8+2+20)");

        // Ordre des manches : on traverse bien 0,1,2,3,4
        Machine m3 = new Machine();
        m3.start(0, d);
        int last = -1;
        for (long s = 0; s <= 87 * 60; s += 5) {
            m3.tick(s * 1000, d);
            if (m3.phase == Phase.ROUND) {
                check(m3.round >= last, "les manches ne reculent jamais");
                last = m3.round;
            }
        }
        check(last == 4, "la manche 4 est atteinte");
    }

    static void pauseAndSkip() {
        Durations d = new Durations();
        Machine m = new Machine();
        m.start(0, d);
        m.tick(120_000, d); // ROUND 0
        m.pause(120_000 + 100_000); // 100 s écoulées
        check(m.paused && m.pausedRemainingMs == 500_000, "500 s restantes à la pause");
        check(m.tick(120_000 + 10_000_000L, d).isEmpty() && m.phase == Phase.ROUND, "rien n'avance en pause");
        m.resume(5_000_000L);
        check(m.phaseEndsAt == 5_500_000L, "reprise : échéance décalée");
        m.skip(5_000_100L, d);
        check(m.phase == Phase.VALIDATION, "skip termine la manche");
    }

    static void scoring() {
        Scoring s = new Scoring();
        check(s.points(Scoring.Outcome.A)[0] == 3 && s.points(Scoring.Outcome.A)[1] == 1, "A gagne : 3-1");
        check(s.points(Scoring.Outcome.B)[1] == 3, "B gagne : 3");
        check(s.points(Scoring.Outcome.DRAW)[0] == 2, "égalité : 2-2");
        check(s.points(Scoring.Outcome.REST)[0] == 0, "repos : 0");
        check(s.bonus(Scoring.Outcome.A)[0] && !s.bonus(Scoring.Outcome.A)[1], "bonus au gagnant seulement");
        check(!s.bonus(Scoring.Outcome.DRAW)[0], "pas de bonus en cas d'égalité");

        // Classement avec égalités
        int[] pts = {6, 9, 9, 3, 6, 0, 0, 0, 0, 0};
        int[] wins = {1, 2, 3, 0, 1, 0, 0, 0, 0, 0};
        List<Scoring.Row> rows = Scoring.standings(pts, wins);
        check(rows.get(0).team() == 2 && rows.get(0).rank() == 1, "équipe 2 première (9 pts, 3 victoires)");
        check(rows.get(1).team() == 1 && rows.get(1).rank() == 2, "équipe 1 deuxième");
        check(rows.get(2).team() == 0 && rows.get(2).rank() == 3, "équipe 0 troisième");
        check(rows.get(3).team() == 4 && rows.get(3).rank() == 3, "équipe 4 ex aequo avec l'équipe 0");
        check(rows.get(4).rank() == 5, "rang suivant = 5 après deux ex aequo");
    }
}
