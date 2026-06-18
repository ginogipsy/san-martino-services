package com.ginogipsy.sanmartino.auth.service;


import com.ginogipsy.sanmartino.auth.api.response.LoginResponse;
import jakarta.ws.rs.NotAuthorizedException;
import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AuthService {
    @Value("${keycloak.server-url}")
    private String serverUrl;
    @Value("${keycloak.realm}")
    private String realm;
    @Value("${keycloak.client-id}")
    private String clientId;
    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private Keycloak keycloak;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri; // Es: http://localhost:8080/realms/mio-realm

    public LoginResponse refreshToken(String refreshToken) {
        String url = issuerUri + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "refresh_token");
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url, entity, LoginResponse.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Refresh token non valido o scaduto");
        }
    }
    public LoginResponse login(String username, String password) {
        try {
            // Creiamo un client temporaneo con le credenziali dell'utente
            Keycloak tempKeycloak = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm(realm)
                    .grantType(OAuth2Constants.PASSWORD) // Modalità Password Grant
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .username(username)
                    .password(password)
                    .build();

            // Recuperiamo il token
            AccessTokenResponse token = tempKeycloak.tokenManager().getAccessToken();

            return new LoginResponse(
                    token.getToken(),
                    token.getRefreshToken(),
                    token.getExpiresIn(),
                    token.getTokenType()
            );
        } catch (NotAuthorizedException e) {
            throw new RuntimeException("Credenziali errate");
        }
    }
    public void logoutUser(String userId) {
        // Questo comando invalida tutte le sessioni attive per quell'utente su Keycloak
        keycloak.realm(realm).users().get(userId).logout();
    }

    public LoginResponse loginWithSocialCode(String code, String redirectUri) {
        String url = issuerUri + "/protocol/openid-connect/token";

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "authorization_code");
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        map.add("code", code);
        map.add("redirect_uri", redirectUri); // Deve essere la stessa usata nel primo step

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url, entity, LoginResponse.class);
            return response.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Errore durante lo scambio del codice social");
        }
    }

    public record RefreshRequest(String refreshToken) {}
}

