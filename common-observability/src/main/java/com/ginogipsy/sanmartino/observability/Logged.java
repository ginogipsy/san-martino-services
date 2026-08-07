package com.ginogipsy.sanmartino.observability;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Estende il logging automatico a una classe o a un singolo metodo che non sta
 * in un layer già coperto da {@link MethodLoggingAspect} (che intercetta
 * {@code @RestController}, {@code @RestControllerAdvice} e {@code @Service}).
 *
 * <p>Serve per i componenti event-driven: un {@code @KafkaListener} vive su una
 * classe {@code @Component}, che non viene intercettata di default per non
 * trasformare mapper e classi di configurazione in rumore nei log.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Logged {
}
