package com.ginogipsy.sanmartino.gateway.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Espone le configurazioni pubbliche lette da HashiCorp Vault (`secret/gateway`)
 * necessarie al pannello di controllo frontend (es. Google Maps JavaScript API Key).
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final String googleMapsApiKey;

    public PublicConfigController(@Value("${GOOGLE_MAPS_API_KEY:}") String googleMapsApiKey) {
        this.googleMapsApiKey = googleMapsApiKey != null ? googleMapsApiKey.trim() : "";
    }

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        return ResponseEntity.ok(Map.of("googleMapsApiKey", googleMapsApiKey));
    }
}
