package be.ilyteq.escape;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Contenu éditable du jeu (salles, indices, code final). Voir src/main/resources/content.json. */
public class Content {
    public static class RoomDef {
        public String name;
        public boolean rest;
        public String symbol;
        public String fragment;
        public String bonusHint;
        public String description;
    }

    public String eventName = "Escape Game";
    public String finalRoomName = "Salle finale";
    public String finalCode = "00000";
    public String finalRiddle = "";
    public int maxAttempts = 2;
    public int cooldownSec = 120;
    public List<String> teamNames;
    public List<RoomDef> rooms;

    public static Content load(ObjectMapper om) throws IOException {
        String file = System.getenv("CONTENT_FILE");
        Content c;
        if (file != null && !file.isBlank()) {
            c = om.readValue(Files.readAllBytes(Path.of(file)), Content.class);
        } else {
            try (InputStream in = Content.class.getResourceAsStream("/content.json")) {
                c = om.readValue(in, Content.class);
            }
        }
        if (c.rooms == null || c.rooms.size() != 5) throw new IOException("content.json : il faut exactement 5 salles");
        if (c.teamNames == null || c.teamNames.size() != 10) throw new IOException("content.json : il faut exactement 10 noms d'équipes");
        return c;
    }
}
