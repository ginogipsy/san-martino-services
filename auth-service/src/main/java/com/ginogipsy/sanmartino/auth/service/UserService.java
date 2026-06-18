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
        // 1. Configura i dati base dell'utente
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailVerified(false); // Inizialmente false

        // 2. Imposta le azioni richieste (es. verifica email)
        user.setRequiredActions(List.of("VERIFY_EMAIL"));

        // 3. Imposta la password
        user.setCredentials(List.of(createPasswordCredentials(password)));

        // 4. Chiamata a Keycloak
        Response response = keycloak.realm(realm).users().create(user);

        if (response.getStatus() == 201) {
            // Recuperiamo l'ID dell'utente appena creato dal path della response
            String userId = CreatedResponseUtil.getCreatedId(response);

            // OPZIONALE: Invia l'email di verifica immediatamente
            keycloak.realm(realm).users().get(userId).sendVerifyEmail();

            System.out.println("Utente creato con ID: " + userId);
        } else {
            throw new RuntimeException("Errore Keycloak: " + response.getStatusInfo().getReasonPhrase());
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
