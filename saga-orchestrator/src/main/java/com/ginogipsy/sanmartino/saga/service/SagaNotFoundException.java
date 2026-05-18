package com.ginogipsy.sanmartino.saga.service;

import java.util.UUID;

public class SagaNotFoundException extends RuntimeException {
    public SagaNotFoundException(UUID id) {
        super("Saga not found: " + id);
    }
}
