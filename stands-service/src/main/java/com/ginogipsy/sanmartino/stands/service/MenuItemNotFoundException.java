package com.ginogipsy.sanmartino.stands.service;

import java.util.UUID;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(UUID standId, UUID itemId) {
        super("Menu item " + itemId + " not found in stand " + standId);
    }
}
