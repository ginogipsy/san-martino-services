package com.ginogipsy.sanmartino.auth.api;

import com.ginogipsy.sanmartino.auth.api.request.LoginRequest;
import com.ginogipsy.sanmartino.auth.api.response.LoginResponse;
import com.ginogipsy.sanmartino.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody AuthService.RefreshRequest request) {
        try {
            LoginResponse response = authService.refreshToken(request.refreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request.username(), request.password());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/logout-user")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Jwt jwt) {
        // Estraiamo il 'sub' (Subject) che è l'ID utente di Keycloak dal token JWT
        String userId = jwt.getSubject();
        authService.logoutUser(userId);
        return ResponseEntity.ok("Sessioni utente invalidate su Keycloak");
    }



}