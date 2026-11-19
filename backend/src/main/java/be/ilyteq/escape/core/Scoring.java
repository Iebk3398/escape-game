package be.ilyteq.escape.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Règles de points et de classement. */
public class Scoring {
    public enum Outcome { A, B, DRAW, REST }

    public int winPoints = 3;
    public int losePoints = 1;
    public int drawPoints = 2;
    /** Points bonus de la finale selon l'ordre d'arrivée (1er, 2e, 3e...). */
    public int[] finalBonus = {10, 7, 5, 4, 3, 2, 1, 1, 1, 1};

    public Scoring() {}

    /** {points de A, points de B}. Repos : 0 pour tout le monde. */
    public int[] points(Outcome o) {
        return switch (o) {
            case A -> new int[] {winPoints, losePoints};
            case B -> new int[] {losePoints, winPoints};
            case DRAW -> new int[] {drawPoints, drawPoints};
            case REST -> new int[] {0, 0};
        };
    }

    /** Indice bonus : uniquement pour le gagnant d'un duel (pas en cas d'égalité, pas au repos). */
    public boolean[] bonus(Outcome o) {
        return switch (o) {
            case A -> new boolean[] {true, false};
            case B -> new boolean[] {false, true};
            default -> new boolean[] {false, false};
        };
    }

    public int finalPoints(int arrivalRank) {
        if (arrivalRank < 1) return 0;
        return arrivalRank <= finalBonus.length ? finalBonus[arrivalRank - 1] : 0;
    }

    public record Row(int team, int points, int wins, int rank) {}

    /**
     * Classement : points décroissants, puis nombre de victoires, puis numéro d'équipe.
     * Deux équipes ayant les mêmes points ET les mêmes victoires partagent le même rang.
     */
    public static List<Row> standings(int[] points, int[] wins) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < points.length; i++) idx.add(i);
        idx.sort(Comparator.<Integer>comparingInt(i -> -points[i])
                .thenComparingInt(i -> -wins[i])
                .thenComparingInt(i -> i));
        List<Row> rows = new ArrayList<>();
        int rank = 0;
        for (int k = 0; k < idx.size(); k++) {
            int i = idx.get(k);
            if (k == 0) rank = 1;
            else {
                int p = idx.get(k - 1);
                if (points[p] != points[i] || wins[p] != wins[i]) rank = k + 1;
            }
            rows.add(new Row(i, points[i], wins[i], rank));
        }
        return rows;
    }
}
