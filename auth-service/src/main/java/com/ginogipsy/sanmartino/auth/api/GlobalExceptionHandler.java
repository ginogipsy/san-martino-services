package com.ginogipsy.sanmartino.auth.api;


import com.ginogipsy.sanmartino.auth.api.response.ErrorResponse;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.WebApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // Gestisce le credenziali errate nel wrapper login
    @ExceptionHandler(NotAuthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(NotAuthorizedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Credenziali non valide o sessione scaduta",
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    // Gestisce @PreAuthorize falliti (es. utente non ha il ruolo ADMIN)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Non hai i permessi necessari per questa operazione",
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // Gestisce errori generici di Keycloak (es. utente già esistente)
    @ExceptionHandler(WebApplicationException.class)
    public ResponseEntity<ErrorResponse> handleKeycloakException(WebApplicationException ex) {
        int status = ex.getResponse().getStatus();
        ErrorResponse error = new ErrorResponse(
                status,
                "Errore durante l'operazione su Keycloak: " + ex.getMessage(),
                System.currentTimeMillis()
        );
        return new ResponseEntity<>(error, HttpStatusCode.valueOf(status));
    }
}