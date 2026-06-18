package com.ginogipsy.sanmartino.auth.api;

import com.ginogipsy.sanmartino.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class UserAdminController {

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
}
