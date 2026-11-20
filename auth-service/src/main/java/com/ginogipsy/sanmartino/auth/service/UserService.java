package com.ginogipsy.sanmartino.auth.service;


import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final Keycloak keycloak;
    @Value("${keycloak.realm}")
    private String realm;
    public void updateUserAttribute(String userId, String attributeKey, String attributeValue) {
        UserResource userResource = keycloak.realm(realm).users().get(userId);
        UserRepresentation user = userResource.toRepresentation();

        // Gestione della mappa degli attributi
        if (user.getAttributes() == null) {
            user.setAttributes(new HashMap<>());
        }

        user.getAttributes().put(attributeKey, Collections.singletonList(attributeValue));

        // Aggiorna l'utente su Keycloak
        userResource.update(user);
    }
    public List<UserRepresentation> getAllUsers(String search, int firstResult, int maxResults) {
        // search può essere null, firstResult è l'offset (es. 0), maxResults è il limite (es. 10)
        return keycloak.realm(realm)
                .users()
                .search(search, firstResult, maxResults);
    }
    public void assignRoleToUser(String userId, String roleName) {
        // 1. Recupera la rappresentazione del ruolo dal Realm
        RoleRepresentation role = keycloak.realm(realm)
                .roles()
                .get(roleName)
                .toRepresentation();

        // 2. Assegna il ruolo all'utente specifico
        keycloak.realm(realm)
                .users()
                .get(userId)
                .roles()
                .realmLevel() // Usiamo ruoli a livello di Realm
                .add(List.of(role));
    }
    public void createUser(String username, String email) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);

        Response response = keycloak.realm(realm).users().create(user);
        if (response.getStatus() != 201) {
            throw new RuntimeException("Errore creazione utente su Keycloak");
        }
    }

    public void registerUser(String username, String email, String password) {
        registerUser(username, email, password, null, null);
    }

    public void registerUser(
            String username,
            String email,
            String password,
            String firstName,
            String lastName
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username obbligatorio");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email obbligatoria");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("La password deve contenere almeno 6 caratteri");
        }

        // 1. Configura i dati base dell'utente (attivo subito per il login da app mobile)
        UserRepresentation user = new UserRepresentation();
        String cleanUsername = username.trim();
        user.setEnabled(true);
        user.setUsername(cleanUsername);
        user.setEmail(email.trim());
        user.setEmailVerified(true);
        user.setFirstName(firstName != null && !firstName.isBlank() ? firstName.trim() : cleanUsername);
        user.setLastName(lastName != null && !lastName.isBlank() ? lastName.trim() : cleanUsername);
        user.setRequiredActions(Collections.emptyList());

        // 2. Imposta la password non temporanea
        user.setCredentials(List.of(createPasswordCredentials(password)));

        // 3. Chiamata a Keycloak
        Response response = keycloak.realm(realm).users().create(user);

        if (response.getStatus() == 201) {
            String userId = CreatedResponseUtil.getCreatedId(response);
            try {
                assignRoleToUser(userId, "USER");
            } catch (Exception _) {
                // Il ruolo USER potrebbe non essere preconfigurato sul realm locale
            }
        } else if (response.getStatus() == 409) {
            throw new IllegalArgumentException("Username o email già registrati");
        } else if (response.getStatus() == 400) {
            throw new IllegalArgumentException("Dati di registrazione non validi: controlla email e password");
        } else if (response.getStatus() == 401 || response.getStatus() == 403) {
            throw new RuntimeException("Servizio di registrazione temporaneamente non autorizzato (permessi Keycloak mancanti)");
        } else {
            throw new RuntimeException("Impossibile completare la registrazione in questo momento. Riprova più tardi.");
        }
    }

    private CredentialRepresentation createPasswordCredentials(final String password) {
        CredentialRepresentation passwordCredentials = new CredentialRepresentation();
        passwordCredentials.setTemporary(false); // Se true, forza il cambio password al primo login
        passwordCredentials.setType(CredentialRepresentation.PASSWORD);
        passwordCredentials.setValue(password);
        return passwordCredentials;
    }
}
