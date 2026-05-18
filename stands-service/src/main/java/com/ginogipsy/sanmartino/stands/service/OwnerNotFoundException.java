package com.ginogipsy.sanmartino.stands.service;

import java.util.UUID;

public class OwnerNotFoundException extends RuntimeException {
    public OwnerNotFoundException(UUID standId, UUID ownerId) {
        super("Owner " + ownerId + " not found in stand " + standId);
    }
}
