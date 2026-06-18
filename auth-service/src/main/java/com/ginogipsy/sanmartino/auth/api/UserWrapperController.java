package com.ginogipsy.sanmartino.auth.api;

import com.ginogipsy.sanmartino.auth.api.request.RegistrationRequest;
import com.ginogipsy.sanmartino.auth.dto.UserDto;
import com.ginogipsy.sanmartino.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserWrapperController {

    private final UserService userService;

    @GetMapping
    public List<UserRepresentation> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int first,
            @RequestParam(defaultValue = "10") int max) {
        return userService.getAllUsers(search, first, max);
    }

    @PostMapping("/{userId}/roles")
    public ResponseEntity<String> addRole(@PathVariable String userId, @RequestBody String roleName) {
        userService.assignRoleToUser(userId, roleName);
        return ResponseEntity.ok("Ruolo assegnato con successo");
    }
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegistrationRequest req) {
        try {
            userService.registerUser(req.getUsername(), req.getEmail(), req.getPassword());
            return ResponseEntity.status(HttpStatus.CREATED).body("Registrazione completata");
        } catch (Exception e) {
            // Logga l'errore e restituisci un messaggio parlante alla UI
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')") // Solo chi ha il ruolo ADMIN su Keycloak può farlo
    public ResponseEntity<String> create(@RequestBody UserDto userDto) {
        userService.createUser(userDto.getUsername(), userDto.getEmail());
        return ResponseEntity.ok("Utente creato con successo");
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public String getProfile() {
        return "Accesso consentito a utenti base e admin";
    }
}