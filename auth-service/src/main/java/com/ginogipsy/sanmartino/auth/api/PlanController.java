package com.ginogipsy.sanmartino.auth.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class PlanController {

    @GetMapping("/check-plan")
    public ResponseEntity<String> getPlan(@AuthenticationPrincipal Jwt jwt) {
        // Recupera il claim personalizzato
        String plan = jwt.getClaimAsString("user_plan");

        return ResponseEntity.ok("Il tuo piano attuale è: " + plan);
    }

    @PostMapping("/premium-feature")
    @PreAuthorize("principal.claims['user_plan'] == 'premium'")
    public String getPremiumData() {
        return "Contenuto esclusivo per utenti Premium";
    }
}
