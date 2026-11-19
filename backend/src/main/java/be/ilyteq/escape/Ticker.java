package be.ilyteq.escape;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Ticker {
    private final GameService game;

    public Ticker(GameService game) {
        this.game = game;
    }

    @Scheduled(fixedRate = 500)
    public void tick() {
        game.tick();
    }
}
