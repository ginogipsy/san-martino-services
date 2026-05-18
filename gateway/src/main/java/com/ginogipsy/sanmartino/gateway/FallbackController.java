package com.ginogipsy.sanmartino.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/events")
    public ResponseEntity<ProblemDetail> eventsFallback() {
        return fallback("Events service unavailable",
                "Il microservizio Events non risponde in tempo. Riprova tra qualche secondo.");
    }

    @GetMapping("/stands")
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
