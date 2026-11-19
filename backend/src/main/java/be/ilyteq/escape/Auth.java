package be.ilyteq.escape;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Jetons signés (HMAC-SHA256) pour les rôles, et QR dynamiques des équipes. */
@Component
public class Auth {
    public record Claims(String role, int subject) {}

    private final byte[] key;

    public Auth() {
        String s = System.getenv("APP_SECRET");
        if (s == null || s.isBlank()) s = adminPassword() + "|escape-game";
        this.key = s.getBytes(StandardCharsets.UTF_8);
    }

    public static String adminPassword() {
        String p = System.getenv("ADMIN_PASSWORD");
        return (p == null || p.isBlank()) ? "admin" : p;
    }

    private String hmac(String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(msg.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String role, int subject, long expMs) {
        String payload = role + "|" + subject + "|" + expMs;
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return b64 + "." + hmac(payload);
    }

    /** Lit l'en-tête "Bearer xxx" ; renvoie null si absent, invalide ou expiré. */
    public Claims verify(String header, long now) {
        if (header == null || !header.startsWith("Bearer ")) return null;
        String token = header.substring(7).trim();
        int dot = token.indexOf('.');
        if (dot < 1) return null;
        try {
            String payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            if (!same(hmac(payload), token.substring(dot + 1))) return null;
            String[] p = payload.split("\\|");
            if (p.length != 3) return null;
            if (Long.parseLong(p[2]) < now) return null;
            return new Claims(p[0], Integer.parseInt(p[1]));
        } catch (Exception e) {
            return null;
        }
    }

    /** QR de l'équipe pour une manche donnée : change à chaque manche. */
    public String qr(int team, int round) {
        return "qr." + team + "." + round + "." + hmac("qr|" + team + "|" + round).substring(0, 10);
    }

    /** Renvoie {équipe, manche} si le QR est authentique, sinon null. */
    public int[] parseQr(String qr) {
        if (qr == null) return null;
        String[] p = qr.trim().split("\\.");
        if (p.length != 4 || !p[0].equals("qr")) return null;
        try {
            int team = Integer.parseInt(p[1]);
            int round = Integer.parseInt(p[2]);
            if (!same(qr(team, round), qr.trim())) return null;
            return new int[] {team, round};
        } catch (Exception e) {
            return null;
        }
    }
}
