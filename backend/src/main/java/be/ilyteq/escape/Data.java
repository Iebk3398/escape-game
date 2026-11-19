package be.ilyteq.escape;

import be.ilyteq.escape.core.Machine;
import java.util.ArrayList;
import java.util.List;

/** État complet du jeu, sérialisé en JSON dans la base à chaque changement. Indices d'équipes/salles : 0-based. */
public class Data {
    public static class Team {
        public int idx;
        public String name;
        public String code;
    }

    public static class Presence {
        public int round;
        public int team;
        public long ts;
        public boolean manual;
    }

    public static class Result {
        public int round;
        public int room;
        public int a;
        public int b;
        public String outcome; // A, B, DRAW, REST
        public boolean validated;
        public int pa;
        public int pb;
        public boolean bonusA;
        public boolean bonusB;
        public long ts;
        public String by;
    }

    public static class Attempt {
        public int team;
        public String code;
        public boolean ok;
        public long ts;
    }

    public List<Team> teams = new ArrayList<>();
    public Machine machine = new Machine();
    public List<Presence> presences = new ArrayList<>();
    public List<Result> results = new ArrayList<>();
    public List<Attempt> attempts = new ArrayList<>();
    public List<Integer> finalOrder = new ArrayList<>();
    public List<String> masterPins = new ArrayList<>();
    public String finalPin;
    public boolean freezeRanking = true;
    public long version = 1;
}
