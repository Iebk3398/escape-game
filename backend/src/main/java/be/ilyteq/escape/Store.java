package be.ilyteq.escape;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Persistance : un seul enregistrement JSON (l'état complet). Fonctionne avec PostgreSQL et H2. */
@Component
public class Store {
    private static final Logger log = LoggerFactory.getLogger(Store.class);
    private final JdbcTemplate jdbc;

    public Store(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        jdbc.execute("CREATE TABLE IF NOT EXISTS snapshot (id INTEGER PRIMARY KEY, json TEXT NOT NULL)");
    }

    public String load() {
        List<String> rows = jdbc.query("SELECT json FROM snapshot WHERE id = 1", (rs, i) -> rs.getString(1));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Meilleur effort : une erreur de base est journalisée mais ne bloque pas le jeu. */
    public void save(String json) {
        try {
            int n = jdbc.update("UPDATE snapshot SET json = ? WHERE id = 1", json);
            if (n == 0) jdbc.update("INSERT INTO snapshot (id, json) VALUES (1, ?)", json);
        } catch (Exception e) {
            log.error("Sauvegarde en base impossible : {}", e.toString());
        }
    }
}
