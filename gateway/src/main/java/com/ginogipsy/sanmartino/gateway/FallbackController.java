package com.ginogipsy.sanmartino.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risposte del gateway quando un circuito è aperto.
 *
 * <p>I metodi non restringono il verbo HTTP: il filtro {@code CircuitBreaker} usa un
 * {@code forward:}, che conserva il metodo della richiesta originale, e i prefissi
 * instradati espongono anche POST, PUT e DELETE. Con un {@code @GetMapping} una
 * scrittura su un circuito aperto otterrebbe un 405 invece del 503 di questo fallback.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/events")
    public ResponseEntity<ProblemDetail> eventsFallback() {
        return fallback("Events service unavailable",
                "Il microservizio Events non risponde in tempo. Riprova tra qualche secondo.");
    }

    @RequestMapping("/stands")
    public ResponseEntity<ProblemDetail> standsFallback() {
        return fallback("Stands service unavailable",
                "Il microservizio Stands non risponde in tempo. Riprova tra qualche secondo.");
    }

    private ResponseEntity<ProblemDetail> fallback(String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle(title);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }
}
