package be.ilyteq.escape;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
public class Api {
    private final GameService game;
    private final SseHub hub;

    public Api(GameService game, SseHub hub) {
        this.game = game;
        this.hub = hub;
    }

    // ---------------------------------------------------------------- helpers

    private Auth.Claims need(String header, String... roles) {
        Auth.Claims c = game.claims(header);
        if (c == null) throw new ApiException(401, "AUTH", "Connexion requise.");
        for (String r : roles) if (r.equals(c.role())) return c;
        throw new ApiException(403, "ROLE", "Accès refusé.");
    }

    private static int num(Map<String, Object> b, String field) {
        Object o = b == null ? null : b.get(field);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                // tombe sur l'erreur ci-dessous
            }
        }
        throw new ApiException(400, "BAD_REQUEST", "Champ manquant ou invalide : " + field);
    }

    private static String str(Map<String, Object> b, String field) {
        Object o = b == null ? null : b.get(field);
        return o == null ? null : String.valueOf(o);
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    // ---------------------------------------------------------------- public

    @GetMapping("/health")
    public Map<String, Object> health() {
        return ok();
    }

    @GetMapping("/public")
    public Map<String, Object> publicState() {
        return game.publicState();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return hub.subscribe(game.version());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> b) {
        String role = str(b, "role");
        Integer room = null;
        if (b != null && b.get("room") != null) room = num(b, "room");
        return game.login(role, str(b, "code"), room);
    }

    // ---------------------------------------------------------------- capitaine

    @GetMapping("/captain/me")
    public Map<String, Object> captain(@RequestHeader(value = "Authorization", required = false) String h) {
        Auth.Claims c = need(h, "captain");
        return game.captainView(c.subject());
    }

    // ---------------------------------------------------------------- maître de salle

    @GetMapping("/master/me")
    public Map<String, Object> master(@RequestHeader(value = "Authorization", required = false) String h) {
        Auth.Claims c = need(h, "master");
        return game.masterView(c.subject());
    }

    @PostMapping("/master/scan")
    public Map<String, Object> scan(@RequestHeader(value = "Authorization", required = false) String h,
                                    @RequestBody Map<String, Object> b) {
        Auth.Claims c = need(h, "master");
        return game.scan(c.subject(), str(b, "qr"));
    }

    @PostMapping("/master/present")
    public Map<String, Object> present(@RequestHeader(value = "Authorization", required = false) String h,
                                       @RequestBody Map<String, Object> b) {
        Auth.Claims c = need(h, "master");
        return game.presentManually(c.subject(), num(b, "team"));
    }

    @PostMapping("/master/result")
    public Map<String, Object> result(@RequestHeader(value = "Authorization", required = false) String h,
                                      @RequestBody Map<String, Object> b) {
        Auth.Claims c = need(h, "master");
        return game.masterResult(c.subject(), str(b, "outcome"));
    }

    // ---------------------------------------------------------------- finale

    @GetMapping("/final/overview")
    public Map<String, Object> finalOverview(@RequestHeader(value = "Authorization", required = false) String h) {
        need(h, "final", "admin");
        return game.finalOverview();
    }

    @PostMapping("/final/attempt")
    public Map<String, Object> finalAttempt(@RequestHeader(value = "Authorization", required = false) String h,
                                            @RequestBody Map<String, Object> b) {
        need(h, "final", "admin");
        return game.finalAttempt(num(b, "team"), str(b, "code"));
    }

    // ---------------------------------------------------------------- admin

    @GetMapping("/admin/overview")
    public Map<String, Object> adminOverview(@RequestHeader(value = "Authorization", required = false) String h) {
        need(h, "admin");
        return game.adminOverview();
    }

    @PostMapping("/admin/action")
    public Map<String, Object> adminAction(@RequestHeader(value = "Authorization", required = false) String h,
                                           @RequestBody Map<String, Object> b) {
        need(h, "admin");
        game.adminAction(str(b, "action"), str(b, "confirm"));
        return ok();
    }

    @PostMapping("/admin/team")
    public Map<String, Object> adminTeam(@RequestHeader(value = "Authorization", required = false) String h,
                                         @RequestBody Map<String, Object> b) {
        need(h, "admin");
        game.adminRenameTeam(num(b, "idx"), str(b, "name"));
        return ok();
    }

    @PostMapping("/admin/freeze")
    public Map<String, Object> adminFreeze(@RequestHeader(value = "Authorization", required = false) String h,
                                           @RequestBody Map<String, Object> b) {
        need(h, "admin");
        game.adminFreeze(Boolean.parseBoolean(str(b, "value")));
        return ok();
    }

    @PostMapping("/admin/result")
    public Map<String, Object> adminResult(@RequestHeader(value = "Authorization", required = false) String h,
                                           @RequestBody Map<String, Object> b) {
        need(h, "admin");
        return game.adminSetResult(num(b, "round"), num(b, "room"), str(b, "outcome"));
    }
}
