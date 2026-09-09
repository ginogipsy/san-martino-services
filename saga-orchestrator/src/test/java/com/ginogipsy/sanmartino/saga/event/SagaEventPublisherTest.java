package com.ginogipsy.sanmartino.saga.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Il callback della {@code send} gira sul thread I/O del producer, condiviso fra tutte
 * le pubblicazioni: quello che si rompe in silenzio non è tanto la propagazione, è il
 * ripristino, che se manca attribuisce il correlation id alla saga successiva.
 */
class SagaEventPublisherTest {

    private static final String MDC_KEY = "correlationId";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void runsTheActionWithTheCapturedContext() {
        AtomicReference<String> seen = new AtomicReference<>();

        SagaEventPublisher.withContext(Map.of(MDC_KEY, "abc-123"), () -> seen.set(MDC.get(MDC_KEY)));

        assertThat(seen.get()).isEqualTo("abc-123");
    }

    @Test
    void leavesTheThreadAsItFoundIt() {
        SagaEventPublisher.withContext(Map.of(MDC_KEY, "abc-123"), () -> {
        });

        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    /** Un thread che stava già servendo una richiesta non deve perdere il proprio id. */
    @Test
    void restoresAContextThatWasAlreadyThere() {
        MDC.put(MDC_KEY, "originale");

        SagaEventPublisher.withContext(Map.of(MDC_KEY, "altra-saga"), () -> {
        });

        assertThat(MDC.get(MDC_KEY)).isEqualTo("originale");
    }

    /** Senza contesto catturato (pubblicazione fuori da una richiesta) il thread resta pulito. */
    @Test
    void clearsTheContextWhenNothingWasCaptured() {
        MDC.put(MDC_KEY, "residuo");
        AtomicReference<String> seen = new AtomicReference<>("non-invocata");

        SagaEventPublisher.withContext(null, () -> seen.set(MDC.get(MDC_KEY)));

        assertThat(seen.get()).isNull();
        assertThat(MDC.get(MDC_KEY)).isEqualTo("residuo");
    }

    /** Il ripristino sta in un finally: un log che esplode non deve lasciare l'MDC sporco. */
    @Test
    void restoresTheContextEvenWhenTheActionFails() {
        MDC.put(MDC_KEY, "originale");

        try {
            SagaEventPublisher.withContext(Map.of(MDC_KEY, "altra-saga"), () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // l'eccezione non è l'oggetto del test: conta lo stato dell'MDC dopo
        }

        assertThat(MDC.get(MDC_KEY)).isEqualTo("originale");
    }
}
