package be.ilyteq.escape.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning de rotation : 10 équipes (indices 0..9), 5 salles (indices 0..4), 5 manches (0..4).
 * Les équipes 0..4 forment le groupe A, les équipes 5..9 le groupe B.
 * Salle du groupe A à la manche r : (i + r) % 5. Salle du groupe B : (j + 2r) % 5.
 * Résultat : chaque équipe passe une fois dans chaque salle, et chaque équipe A
 * affronte chacune des 5 équipes B exactement une fois.
 */
public final class Rotation {
    public static final int TEAMS = 10;
    public static final int ROOMS = 5;
    public static final int ROUNDS = 5;

    private Rotation() {}

    /** Salle (0..4) de l'équipe (0..9) à la manche (0..4). */
    public static int room(int team, int round) {
        if (team < 0 || team >= TEAMS) throw new IllegalArgumentException("team " + team);
        if (team < ROOMS) return (team + round) % ROOMS;
        int j = team - ROOMS;
        return (j + 2 * round) % ROOMS;
    }

    /** Pour chaque salle (index), les deux équipes {a, b} (a du groupe A, b du groupe B). */
    public static List<int[]> pairs(int round) {
        List<int[]> res = new ArrayList<>();
        for (int r = 0; r < ROOMS; r++) res.add(new int[] {-1, -1});
        for (int t = 0; t < TEAMS; t++) {
            int[] p = res.get(room(t, round));
            if (t < ROOMS) p[0] = t; else p[1] = t;
        }
        return res;
    }

    /** Adversaire de l'équipe à la manche donnée. */
    public static int opponent(int team, int round) {
        int[] p = pairs(round).get(room(team, round));
        return p[0] == team ? p[1] : p[0];
    }
}
