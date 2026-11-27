package com.ginogipsy.sanmartino.stands.service;

import java.util.UUID;

public class ReviewNotFoundException extends RuntimeException {
    public ReviewNotFoundException(UUID reviewId) {
        super("Review non trovata: " + reviewId);
    }
}
