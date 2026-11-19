package be.ilyteq.escape;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.code);
        body.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatusCode.valueOf(e.status)).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "SERVER");
        body.put("message", "Erreur serveur : " + e.getClass().getSimpleName());
        return ResponseEntity.status(500).body(body);
    }
}
