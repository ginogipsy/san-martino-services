package com.ginogipsy.sanmartino.auth.api;

import com.ginogipsy.sanmartino.auth.api.request.LoginRequest;
import com.ginogipsy.sanmartino.auth.api.request.RegistrationRequest;
import com.ginogipsy.sanmartino.auth.api.response.LoginResponse;
import com.ginogipsy.sanmartino.auth.service.AuthService;
import com.ginogipsy.sanmartino.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

//@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody AuthService.RefreshRequest request) {
        try {
            LoginResponse response = authService.refreshToken(request.refreshToken());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request.username(), request.password());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegistrationRequest request) {
        try {
            if (request.getConfirmPassword() != null
                    && !request.getConfirmPassword().isBlank()
                    && !request.getConfirmPassword().equals(request.getPassword())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "Le password non coincidono"));
            }
            userService.registerUser(
                    request.getUsername(),
                    request.getEmail(),
                    request.getPassword(),
                    request.getFirstName(),
                    request.getLastName()
            );
            LoginResponse loginResponse = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body(loginResponse);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Non autenticato"));
        }
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");
        return ResponseEntity.ok(Map.of(
                "userId", jwt.getSubject(),
                "username", username != null ? username : jwt.getSubject(),
                "email", email != null ? email : "",
                "displayName", name != null && !name.isBlank() ? name : (username != null ? username : jwt.getSubject())
        ));
    }

    @PostMapping("/logout-user")
    public ResponseEntity<String> logout(@AuthenticationPrincipal Jwt jwt) {
        if (jwt != null) {
            String userId = jwt.getSubject();
            authService.logoutUser(userId);
        }
        return ResponseEntity.ok("Sessioni utente invalidate su Keycloak");
    }
}