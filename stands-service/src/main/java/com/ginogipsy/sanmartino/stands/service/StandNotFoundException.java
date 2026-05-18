package com.ginogipsy.sanmartino.stands.service;

import java.util.UUID;

public class StandNotFoundException extends RuntimeException {
    public StandNotFoundException(UUID id) {
        super("Stand not found: " + id);
    }
}
