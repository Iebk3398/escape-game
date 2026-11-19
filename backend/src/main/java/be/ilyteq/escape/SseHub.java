package be.ilyteq.escape;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Diffusion temps réel : les clients reçoivent un numéro de version et rechargent leur état. */
@Component
public class SseHub {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(long version) {
        SseEmitter e = new SseEmitter(1_800_000L);
        emitters.add(e);
        e.onCompletion(() -> emitters.remove(e));
        e.onTimeout(() -> emitters.remove(e));
        e.onError(t -> emitters.remove(e));
        try {
            e.send(SseEmitter.event().name("state").data(String.valueOf(version)));
        } catch (Exception ex) {
            emitters.remove(e);
        }
        return e;
    }

    public void broadcast(long version) {
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("state").data(String.valueOf(version)));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }

    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().comment("hb"));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }
}
