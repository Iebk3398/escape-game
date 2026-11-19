package be.ilyteq.escape;

import be.ilyteq.escape.core.Durations;
import be.ilyteq.escape.core.Machine;
import be.ilyteq.escape.core.Phase;
import be.ilyteq.escape.core.Rotation;
import be.ilyteq.escape.core.Scoring;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cœur de l'application : un seul état en mémoire (synchronisé), sauvegardé en base à chaque changement.
 * Une seule instance du serveur doit tourner.
 */
@Service
public class GameService {
    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    private static final long TOKEN_TTL_MS = 12L * 3600 * 1000;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ObjectMapper om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final Store store;
    private final Auth auth;
    private final SseHub hub;
    private final SecureRandom rnd = new SecureRandom();

    /** Mode démo : 2 équipes qui font ensemble les 4 épreuves, le repos, puis la finale. */
    public final boolean demo = "true".equalsIgnoreCase(System.getenv("ESCAPE_DEMO"));
    private final int teamCount = demo ? 2 : Rotation.TEAMS;

    public final Content content;
    public final Durations dur = new Durations();
    public final Scoring scoring = new Scoring();
    private Data data;

    public GameService(Store store, Auth auth, SseHub hub) {
        this.store = store;
        this.auth = auth;
        this.hub = hub;
        try {
            this.content = Content.load(om);
        } catch (Exception e) {
            throw new IllegalStateException("content.json invalide : " + e.getMessage(), e);
        }
        if (demo) {
            dur.roundSec = 60;
            dur.validationSec = 30;
            dur.transitionSec = 20;
            dur.finalTransitSec = 15;
            dur.finalSec = 300;
        }
        dur.roundSec = envInt("ESCAPE_ROUND_SEC", dur.roundSec);
        dur.validationSec = envInt("ESCAPE_VALIDATION_SEC", dur.validationSec);
        dur.transitionSec = envInt("ESCAPE_TRANSITION_SEC", dur.transitionSec);
        dur.finalTransitSec = envInt("ESCAPE_FINAL_TRANSIT_SEC", dur.finalTransitSec);
        dur.finalSec = envInt("ESCAPE_FINAL_SEC", dur.finalSec);

        String json = store.load();
        if (json != null) {
            try {
                data = om.readValue(json, Data.class);
                if (data.teams.size() != teamCount || data.masterPins.size() != Rotation.ROOMS) data = null;
            } catch (Exception e) {
                log.error("Etat sauvegardé illisible, on repart de zéro : {}", e.toString());
                data = null;
            }
        }
        if (data == null) {
            data = fresh();
            persist();
        }
        if (demo) {
            log.warn("MODE DÉMO actif : 2 équipes, codes fixes (DEMO1, DEMO2), durées courtes.");
        } else if (Auth.adminPassword().equals("admin")) {
            log.warn("ADMIN_PASSWORD n'est pas défini : le mot de passe admin est 'admin'. A changer avant l'événement !");
        }
    }

    private static int envInt(String name, int def) {
        try {
            String v = System.getenv(name);
            return (v == null || v.isBlank()) ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // ------------------------------------------------------------------ état / persistance

    private Data fresh() {
        Data d = new Data();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (int i = 0; i < teamCount; i++) {
            Data.Team t = new Data.Team();
            t.idx = i;
            t.name = content.teamNames.get(i);
            String code;
            if (demo) {
                t.code = "DEMO" + (i + 1);
                used.add(t.code);
                d.teams.add(t);
                continue;
            }
            do {
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < 5; k++) sb.append(CODE_ALPHABET.charAt(rnd.nextInt(CODE_ALPHABET.length())));
                code = sb.toString();
            } while (!used.add(code));
            t.code = code;
            d.teams.add(t);
        }
        for (int r = 0; r < Rotation.ROOMS; r++) {
            d.masterPins.add(demo ? String.valueOf(r + 1).repeat(4) : String.format("%04d", rnd.nextInt(10000)));
        }
        d.finalPin = demo ? "9999" : String.format("%04d", rnd.nextInt(10000));
        return d;
    }

    /** Planning : par salle, les deux équipes {a, b}, ou {-1, -1} si la salle est vide cette manche. */
    private List<int[]> pairs(int round) {
        if (!demo) return Rotation.pairs(round);
        List<int[]> res = new ArrayList<>();
        for (int r = 0; r < Rotation.ROOMS; r++) res.add(r == round ? new int[] {0, 1} : new int[] {-1, -1});
        return res;
    }

    private int roomOf(int team, int round) {
        return demo ? round : Rotation.room(team, round);
    }

    private int opponentOf(int team, int round) {
        return demo ? (team == 0 ? 1 : 0) : Rotation.opponent(team, round);
    }

    private void persist() {
        try {
            store.save(om.writeValueAsString(data));
        } catch (Exception e) {
            log.error("Sérialisation impossible : {}", e.toString());
        }
    }

    private void commit() {
        data.version++;
        persist();
        hub.broadcast(data.version);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    public synchronized long version() {
        return data.version;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private Map<String, Object> teamRef(int idx) {
        return m("idx", idx, "number", idx + 1, "name", data.teams.get(idx).name);
    }

    private Map<String, Object> roomRef(int idx) {
        Content.RoomDef r = content.rooms.get(idx);
        return m("idx", idx, "name", r.name, "rest", r.rest, "symbol", r.symbol, "description", r.description);
    }

    private Data.Result find(int round, int room) {
        for (Data.Result r : data.results) if (r.round == round && r.room == room) return r;
        return null;
    }

    private int[][] totals() {
        int[] pts = new int[teamCount];
        int[] wins = new int[teamCount];
        for (Data.Result r : data.results) {
            if (!r.validated) continue;
            pts[r.a] += r.pa;
            pts[r.b] += r.pb;
            if ("A".equals(r.outcome)) wins[r.a]++;
            else if ("B".equals(r.outcome)) wins[r.b]++;
        }
        for (int k = 0; k < data.finalOrder.size(); k++) pts[data.finalOrder.get(k)] += scoring.finalPoints(k + 1);
        return new int[][] {pts, wins};
    }

    private List<Scoring.Row> standings() {
        int[][] t = totals();
        return Scoring.standings(t[0], t[1]);
    }

    private boolean riddleVisible() {
        Phase p = data.machine.phase;
        return p == Phase.FINAL_TRANSIT || p == Phase.FINAL || p == Phase.FINISHED;
    }

    // ------------------------------------------------------------------ temps

    public synchronized void tick() {
        List<Machine.Event> ev = data.machine.tick(now(), dur);
        if (!ev.isEmpty()) {
            handle(ev);
            commit();
        }
    }

    private void handle(List<Machine.Event> events) {
        for (Machine.Event e : events) {
            if ("ROUND_ENDED".equals(e.type())) addRestResults(e.round());
        }
    }

    /** La salle de repos n'a pas de maître à valider : on enregistre le repos automatiquement. */
    private void addRestResults(int round) {
        List<int[]> pairs = pairs(round);
        for (int room = 0; room < Rotation.ROOMS; room++) {
            if (!content.rooms.get(room).rest || pairs.get(room)[0] < 0 || find(round, room) != null) continue;
            Data.Result r = new Data.Result();
            r.round = round;
            r.room = room;
            r.a = pairs.get(room)[0];
            r.b = pairs.get(room)[1];
            r.outcome = "REST";
            r.validated = true;
            r.ts = now();
            r.by = "auto";
            data.results.add(r);
        }
    }

    // ------------------------------------------------------------------ vues publiques

    private List<Map<String, Object>> assignments(int round) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<int[]> pairs = pairs(round);
        for (int room = 0; room < Rotation.ROOMS; room++) {
            int[] pr = pairs.get(room);
            out.add(m("room", room, "a", pr[0] < 0 ? null : teamRef(pr[0]), "b", pr[1] < 0 ? null : teamRef(pr[1])));
        }
        return out;
    }

    public synchronized Map<String, Object> publicState() {
        Machine mc = data.machine;
        long now = now();
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (int i = 0; i < Rotation.ROOMS; i++) rooms.add(roomRef(i));
        List<Map<String, Object>> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) teams.add(teamRef(i));

        List<Map<String, Object>> ranking = null;
        boolean frozen = data.freezeRanking && mc.phase == Phase.FINAL;
        if (!frozen) {
            ranking = new ArrayList<>();
            for (Scoring.Row row : standings()) {
                Map<String, Object> e = teamRef(row.team());
                e.put("points", row.points());
                e.put("wins", row.wins());
                e.put("rank", row.rank());
                ranking.add(e);
            }
        }
        Map<String, Object> winner = data.finalOrder.isEmpty() ? null : teamRef(data.finalOrder.get(0));
        List<Integer> solved = new ArrayList<>();
        for (int idx : data.finalOrder) solved.add(idx + 1);

        Map<String, Object> out = m(
                "serverNow", now,
                "version", data.version,
                "event", content.eventName,
                "demo", demo,
                "phase", mc.phase.name(),
                "round", mc.round,
                "totalRounds", dur.rounds,
                "phaseEndsAt", mc.phaseEndsAt,
                "remainingMs", mc.remainingMs(now),
                "paused", mc.paused,
                "rooms", rooms,
                "teams", teams,
                "assignments", assignments(Math.min(mc.round, dur.rounds - 1)),
                "ranking", ranking,
                "rankingFrozen", frozen,
                "finalWinner", winner,
                "finalSolved", solved,
                "finalRoomName", content.finalRoomName);
        out.put("finalRiddle", riddleVisible() ? content.finalRiddle : null);
        out.put("durations", m(
                "roundSec", dur.roundSec,
                "validationSec", dur.validationSec,
                "transitionSec", dur.transitionSec,
                "finalTransitSec", dur.finalTransitSec,
                "finalSec", dur.finalSec));
        return out;
    }

    // ------------------------------------------------------------------ connexion

    public synchronized Map<String, Object> login(String role, String code, Integer room) {
        String c = code == null ? "" : code.trim();
        long exp = now() + TOKEN_TTL_MS;
        if ("captain".equals(role)) {
            for (Data.Team t : data.teams) {
                if (t.code.equalsIgnoreCase(c)) {
                    return m("token", auth.issue("captain", t.idx, exp), "role", role, "team", teamRef(t.idx));
                }
            }
        } else if ("master".equals(role) && room != null && room >= 0 && room < Rotation.ROOMS) {
            if (data.masterPins.get(room).equals(c)) {
                return m("token", auth.issue("master", room, exp), "role", role, "room", roomRef(room));
            }
        } else if ("final".equals(role)) {
            if (data.finalPin.equals(c)) return m("token", auth.issue("final", 0, exp), "role", role);
        } else if ("admin".equals(role)) {
            if (Auth.adminPassword().equals(code == null ? "" : code)) {
                return m("token", auth.issue("admin", 0, exp), "role", role);
            }
        }
        throw new ApiException(401, "BAD_CODE", "Code incorrect.");
    }

    // ------------------------------------------------------------------ capitaine

    private Map<String, Object> assignmentFor(int team, int round) {
        int room = roomOf(team, round);
        return m("round", round, "room", roomRef(room), "opponent", teamRef(opponentOf(team, round)));
    }

    private List<Map<String, Object>> cluesFor(int team) {
        List<Data.Result> mine = new ArrayList<>();
        for (Data.Result r : data.results) if (r.validated && (r.a == team || r.b == team)) mine.add(r);
        mine.sort((x, y) -> Integer.compare(x.round, y.round));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Data.Result r : mine) {
            Content.RoomDef def = content.rooms.get(r.room);
            boolean bonus = (r.a == team && r.bonusA) || (r.b == team && r.bonusB);
            String hint = (bonus && def.bonusHint != null && !def.bonusHint.isEmpty()) ? def.bonusHint : null;
            out.add(m("round", r.round, "room", r.room, "roomName", def.name, "symbol", def.symbol,
                    "fragment", def.fragment, "bonus", hint));
        }
        return out;
    }

    private int attemptsUsed(int team) {
        int n = 0;
        for (Data.Attempt a : data.attempts) if (a.team == team) n++;
        return n;
    }

    private long cooldownMs(int team) {
        long last = 0;
        for (Data.Attempt a : data.attempts) if (a.team == team && !a.ok) last = Math.max(last, a.ts);
        if (last == 0) return 0;
        return Math.max(0, last + content.cooldownSec * 1000L - now());
    }

    public synchronized Map<String, Object> captainView(int team) {
        Machine mc = data.machine;
        int round = Math.min(mc.round, dur.rounds - 1);
        Scoring.Row row = null;
        for (Scoring.Row r : standings()) if (r.team() == team) row = r;
        boolean frozen = data.freezeRanking && mc.phase == Phase.FINAL;
        Map<String, Object> out = m(
                "team", teamRef(team),
                "qr", auth.qr(team, round),
                "assignment", assignmentFor(team, round),
                "next", (mc.phase == Phase.VALIDATION && round + 1 < dur.rounds) ? assignmentFor(team, round + 1) : null,
                "clues", cluesFor(team),
                "points", frozen ? null : (row == null ? 0 : row.points()),
                "rank", frozen ? null : (row == null ? 0 : row.rank()),
                "attemptsLeft", Math.max(0, content.maxAttempts - attemptsUsed(team)),
                "cooldownMs", cooldownMs(team),
                "solved", data.finalOrder.contains(team),
                "finalRoomName", content.finalRoomName);
        out.put("riddle", riddleVisible() ? content.finalRiddle : null);
        return out;
    }

    // ------------------------------------------------------------------ maître de salle

    private int pendingRound(int room) {
        if (content.rooms.get(room).rest) return -1;
        for (int r = 0; r <= data.machine.lastFinishedRound; r++) {
            if (pairs(r).get(room)[0] < 0) continue;
            Data.Result res = find(r, room);
            if (res == null || !res.validated) return r;
        }
        return -1;
    }

    private boolean present(int round, int team) {
        for (Data.Presence p : data.presences) if (p.round == round && p.team == team) return true;
        return false;
    }

    public synchronized Map<String, Object> masterView(int room) {
        Machine mc = data.machine;
        int round = Math.min(mc.round, dur.rounds - 1);
        int[] pair = pairs(round).get(room);
        List<Map<String, Object>> expected = new ArrayList<>();
        for (int t : pair) {
            if (t < 0) continue;
            Map<String, Object> e = teamRef(t);
            e.put("present", present(round, t));
            expected.add(e);
        }
        Map<String, Object> pending = null;
        int pr = pendingRound(room);
        if (pr >= 0) {
            int[] pp = pairs(pr).get(room);
            pending = m("round", pr, "a", teamRef(pp[0]), "b", teamRef(pp[1]));
        }
        List<Map<String, Object>> done = new ArrayList<>();
        for (Data.Result r : data.results) {
            if (r.room == room && r.validated) {
                done.add(m("round", r.round, "a", teamRef(r.a), "b", teamRef(r.b), "outcome", r.outcome));
            }
        }
        boolean canScan = mc.phase == Phase.TRANSITION || mc.phase == Phase.ROUND;
        return m("room", roomRef(room), "round", round, "phase", mc.phase.name(), "expected", expected,
                "canScan", canScan, "pending", pending, "done", done,
                "remainingMs", mc.remainingMs(now()), "serverNow", now());
    }

    private void requireScanPhase() {
        Phase p = data.machine.phase;
        if (p != Phase.TRANSITION && p != Phase.ROUND) {
            throw new ApiException(409, "PHASE", "Le scan n'est possible que pendant la transition ou la manche.");
        }
    }

    private Map<String, Object> markPresent(int room, int team, boolean manual) {
        int round = data.machine.round;
        int expectedRoom = roomOf(team, round);
        if (expectedRoom != room) {
            throw new ApiException(409, "WRONG_ROOM", teamRef(team).get("name") + " doit aller en : " + content.rooms.get(expectedRoom).name);
        }
        boolean already = present(round, team);
        if (!already) {
            Data.Presence p = new Data.Presence();
            p.round = round;
            p.team = team;
            p.ts = now();
            p.manual = manual;
            data.presences.add(p);
            commit();
        }
        return m("team", teamRef(team), "already", already);
    }

    public synchronized Map<String, Object> scan(int room, String qr) {
        requireScanPhase();
        int[] p = auth.parseQr(qr);
        if (p == null || p[0] < 0 || p[0] >= teamCount) throw new ApiException(400, "QR_INVALID", "QR code non reconnu.");
        if (p[1] != data.machine.round) throw new ApiException(409, "QR_OBSOLETE", "QR périmé : l'équipe doit rafraîchir son écran.");
        return markPresent(room, p[0], false);
    }

    public synchronized Map<String, Object> presentManually(int room, int teamNumber) {
        requireScanPhase();
        int idx = teamNumber - 1;
        if (idx < 0 || idx >= teamCount) throw new ApiException(400, "TEAM", "Équipe inconnue.");
        return markPresent(room, idx, true);
    }

    private Scoring.Outcome parseOutcome(String s) {
        if (s == null) throw new ApiException(400, "OUTCOME", "Résultat manquant.");
        switch (s.trim().toUpperCase()) {
            case "A": return Scoring.Outcome.A;
            case "B": return Scoring.Outcome.B;
            case "DRAW": return Scoring.Outcome.DRAW;
            default: throw new ApiException(400, "OUTCOME", "Résultat inconnu (A, B ou DRAW).");
        }
    }

    private void applyResult(Data.Result r, Scoring.Outcome o, String by) {
        int[] pts = scoring.points(o);
        boolean[] bonus = scoring.bonus(o);
        r.outcome = o.name();
        r.pa = pts[0];
        r.pb = pts[1];
        r.bonusA = bonus[0];
        r.bonusB = bonus[1];
        r.validated = true;
        r.ts = now();
        r.by = by;
    }

    private Data.Result ensureResult(int round, int room) {
        Data.Result r = find(round, room);
        if (r == null) {
            int[] pair = pairs(round).get(room);
            if (pair[0] < 0) throw new ApiException(409, "NO_MATCH", "Aucune équipe dans cette salle à cette manche.");
            r = new Data.Result();
            r.round = round;
            r.room = room;
            r.a = pair[0];
            r.b = pair[1];
            data.results.add(r);
        }
        return r;
    }

    public synchronized Map<String, Object> masterResult(int room, String outcome) {
        if (content.rooms.get(room).rest) throw new ApiException(409, "REST_ROOM", "La salle de repos n'a rien à valider.");
        int round = pendingRound(room);
        if (round < 0) throw new ApiException(409, "NOTHING", "Aucun score à valider pour le moment.");
        Scoring.Outcome o = parseOutcome(outcome);
        Data.Result r = ensureResult(round, room);
        applyResult(r, o, "master:" + room);
        commit();
        return m("round", round, "outcome", o.name(), "pa", r.pa, "pb", r.pb);
    }

    // ------------------------------------------------------------------ finale

    public synchronized Map<String, Object> finalOverview() {
        List<Map<String, Object>> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            Map<String, Object> t = teamRef(i);
            t.put("attemptsLeft", Math.max(0, content.maxAttempts - attemptsUsed(i)));
            t.put("cooldownMs", cooldownMs(i));
            t.put("solved", data.finalOrder.contains(i));
            t.put("arrival", data.finalOrder.indexOf(i) + 1);
            teams.add(t);
        }
        Machine mc = data.machine;
        return m("phase", mc.phase.name(), "remainingMs", mc.remainingMs(now()), "serverNow", now(), "teams", teams,
                "open", mc.phase == Phase.FINAL);
    }

    public synchronized Map<String, Object> finalAttempt(int teamNumber, String code) {
        Machine mc = data.machine;
        if (mc.phase != Phase.FINAL) throw new ApiException(409, "NOT_FINAL", "La finale n'est pas en cours.");
        int idx = teamNumber - 1;
        if (idx < 0 || idx >= teamCount) throw new ApiException(400, "TEAM", "Équipe inconnue.");
        if (data.finalOrder.contains(idx)) throw new ApiException(409, "ALREADY_SOLVED", "Cette équipe a déjà trouvé le code.");
        if (attemptsUsed(idx) >= content.maxAttempts) throw new ApiException(409, "NO_ATTEMPTS", "Cette équipe n'a plus d'essai.");
        long cd = cooldownMs(idx);
        if (cd > 0) throw new ApiException(409, "COOLDOWN", "Patientez encore " + ((cd + 999) / 1000) + " s avant le prochain essai.");

        String norm = code == null ? "" : code.replaceAll("\\s", "").toUpperCase();
        boolean ok = !norm.isEmpty() && norm.equals(content.finalCode.replaceAll("\\s", "").toUpperCase());
        Data.Attempt a = new Data.Attempt();
        a.team = idx;
        a.code = norm;
        a.ok = ok;
        a.ts = now();
        data.attempts.add(a);
        int rank = 0;
        if (ok) {
            data.finalOrder.add(idx);
            rank = data.finalOrder.size();
        }
        // Fin anticipée si plus personne ne peut jouer
        boolean everyoneDone = true;
        for (int i = 0; i < teamCount; i++) {
            if (!data.finalOrder.contains(i) && attemptsUsed(i) < content.maxAttempts) everyoneDone = false;
        }
        if (everyoneDone) handle(mc.skip(now(), dur));
        commit();
        return m("ok", ok, "rank", rank, "attemptsLeft", Math.max(0, content.maxAttempts - attemptsUsed(idx)),
                "team", teamRef(idx));
    }

    // ------------------------------------------------------------------ admin

    public synchronized Map<String, Object> adminOverview() {
        Machine mc = data.machine;
        List<Map<String, Object>> teams = new ArrayList<>();
        for (Data.Team t : data.teams) {
            Map<String, Object> e = teamRef(t.idx);
            e.put("code", t.code);
            teams.add(e);
        }
        List<Map<String, Object>> pins = new ArrayList<>();
        for (int i = 0; i < Rotation.ROOMS; i++) {
            Map<String, Object> e = roomRef(i);
            e.put("pin", data.masterPins.get(i));
            pins.add(e);
        }
        List<Map<String, Object>> pending = new ArrayList<>();
        for (int r = 0; r <= mc.lastFinishedRound; r++) {
            for (int room = 0; room < Rotation.ROOMS; room++) {
                if (content.rooms.get(room).rest || pairs(r).get(room)[0] < 0) continue;
                Data.Result res = find(r, room);
                if (res == null || !res.validated) {
                    int[] pair = pairs(r).get(room);
                    pending.add(m("round", r, "room", room, "roomName", content.rooms.get(room).name,
                            "a", teamRef(pair[0]), "b", teamRef(pair[1])));
                }
            }
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Data.Result r : data.results) {
            results.add(m("round", r.round, "room", r.room, "roomName", content.rooms.get(r.room).name,
                    "a", teamRef(r.a), "b", teamRef(r.b), "outcome", r.outcome, "validated", r.validated, "by", r.by));
        }
        int present = 0;
        for (Data.Presence p : data.presences) if (p.round == mc.round) present++;
        return m("teams", teams, "masterPins", pins, "finalPin", data.finalPin, "pending", pending, "results", results,
                "freezeRanking", data.freezeRanking, "presentThisRound", present,
                "phase", mc.phase.name(), "round", mc.round, "paused", mc.paused,
                "remainingMs", mc.remainingMs(now()), "serverNow", now());
    }

    public synchronized void adminAction(String action, String confirm) {
        Machine mc = data.machine;
        long now = now();
        switch (action == null ? "" : action) {
            case "start" -> {
                if (mc.phase != Phase.LOBBY) throw new ApiException(409, "STATE", "Le jeu est déjà lancé.");
                mc.start(now, dur);
            }
            case "pause" -> mc.pause(now);
            case "resume" -> mc.resume(now);
            case "skip" -> handle(mc.skip(now, dur));
            case "reset" -> {
                if (!"RESET".equals(confirm)) throw new ApiException(400, "CONFIRM", "Tapez RESET pour confirmer.");
                mc.reset();
                data.presences.clear();
                data.results.clear();
                data.attempts.clear();
                data.finalOrder.clear();
            }
            default -> throw new ApiException(400, "ACTION", "Action inconnue.");
        }
        commit();
    }

    public synchronized void adminRenameTeam(int idx, String name) {
        if (idx < 0 || idx >= teamCount) throw new ApiException(400, "TEAM", "Équipe inconnue.");
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 30) throw new ApiException(400, "NAME", "Nom de 1 à 30 caractères.");
        data.teams.get(idx).name = n;
        commit();
    }

    public synchronized void adminFreeze(boolean value) {
        data.freezeRanking = value;
        commit();
    }

    public synchronized Map<String, Object> adminSetResult(int round, int room, String outcome) {
        if (room < 0 || room >= Rotation.ROOMS || content.rooms.get(room).rest) {
            throw new ApiException(400, "ROOM", "Salle invalide.");
        }
        if (round < 0 || round > data.machine.lastFinishedRound) {
            throw new ApiException(409, "ROUND", "Cette manche n'est pas terminée.");
        }
        Scoring.Outcome o = parseOutcome(outcome);
        Data.Result r = ensureResult(round, room);
        applyResult(r, o, "admin");
        commit();
        return m("round", round, "outcome", o.name());
    }

    public Auth.Claims claims(String header) {
        return auth.verify(header, now());
    }
}
