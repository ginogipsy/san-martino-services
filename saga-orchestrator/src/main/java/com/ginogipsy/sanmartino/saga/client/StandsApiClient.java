package com.ginogipsy.sanmartino.saga.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Component
public class StandsApiClient {

    private final RestClient restClient;

    public StandsApiClient(@Qualifier("standsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Verifica che la cantina esista. Ritorna true se 2xx, false se 404.
     * Propaga le altre eccezioni HTTP perche' indicano problemi di infrastruttura
     * (5xx, timeout, network) che meritano una compensation chiara.
     */
    public boolean standExists(UUID standId) {
        try {
            restClient.get()
                    .uri("/v1/stands/{id}", standId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 404) {
                return false;
            }
            throw ex;
        }
    }
}
