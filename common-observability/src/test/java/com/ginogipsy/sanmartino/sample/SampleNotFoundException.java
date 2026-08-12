package com.ginogipsy.sanmartino.sample;

/**
 * Errore di dominio: il suffisso {@code NotFoundException} è quello che l'aspect
 * riconosce come "atteso" (WARN senza stack trace) nella configurazione di default.
 */
public class SampleNotFoundException extends RuntimeException {

    public SampleNotFoundException(String message) {
        super(message);
    }
}
